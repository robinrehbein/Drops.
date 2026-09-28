package de.birneklub.drop.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import de.birneklub.drop.core.backup.DropsBackup
import de.birneklub.drop.core.backup.ImportResult
import de.birneklub.drop.core.domain.Maintenance
import de.birneklub.drop.core.reminders.Reminder
import de.birneklub.drop.core.reminders.Reminders
import de.birneklub.drop.core.model.Bean
import de.birneklub.drop.core.model.BeanStatus
import de.birneklub.drop.core.model.Entity
import de.birneklub.drop.core.model.Equipment
import de.birneklub.drop.core.model.EquipmentKind
import de.birneklub.drop.core.model.MaintenanceTask
import de.birneklub.drop.core.model.Recipe
import de.birneklub.drop.core.model.Shot
import de.birneklub.drop.core.sync.DropsJson
import de.birneklub.drop.core.sync.SyncCollections
import de.birneklub.drop.data.db.DropsDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Local-first store for everything the user tracks. All writes mark records
 * dirty so [SyncClient] can push them once the user opts into an account.
 */
/** A deleted bean with everything that went with it, kept in memory for undo. */
data class DeletedBean(val bean: Bean, val recipes: List<Recipe>, val shots: List<Shot>)

class DropsRepository(
    internal val db: DropsDatabase,
    private val clock: Clock = Clock.System,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val q get() = db.recordsQueries

    val beans: Flow<List<Bean>> = observe(SyncCollections.BEANS, Bean.serializer())
    val recipes: Flow<List<Recipe>> = observe(SyncCollections.RECIPES, Recipe.serializer())
    val shots: Flow<List<Shot>> = observe(SyncCollections.SHOTS, Shot.serializer())
    val equipment: Flow<List<Equipment>> = observe(SyncCollections.EQUIPMENT, Equipment.serializer())
    val tasks: Flow<List<MaintenanceTask>> = observe(SyncCollections.TASKS, MaintenanceTask.serializer())

    fun now(): Instant = clock.now()

    @OptIn(ExperimentalUuidApi::class)
    fun newId(): String = Uuid.random().toString()

    // --- generic -----------------------------------------------------------

    private fun <T> observe(collection: String, serializer: KSerializer<T>): Flow<List<T>> =
        q.liveByCollection(collection).asFlow().mapToList(io).map { rows ->
            rows.mapNotNull { row -> row.json?.let { runCatching { DropsJson.decodeFromString(serializer, it) }.getOrNull() } }
        }

    private fun <T> read(collection: String, id: String, serializer: KSerializer<T>): T? =
        q.byId(collection, id).executeAsOneOrNull()
            ?.takeIf { it.deleted == 0L }
            ?.json?.let { DropsJson.decodeFromString(serializer, it) }

    private fun <T : Entity> write(collection: String, serializer: KSerializer<T>, entity: T) {
        q.upsert(collection, entity.id, DropsJson.encodeToString(serializer, entity), entity.updatedAt.toEpochMilliseconds(), 0, 1)
    }

    private fun tombstone(collection: String, id: String) {
        q.upsert(collection, id, null, now().toEpochMilliseconds(), 1, 1)
    }

    // --- beans & recipes ---------------------------------------------------

    suspend fun saveBean(bean: Bean) = withContext(io) { write(SyncCollections.BEANS, Bean.serializer(), bean.copy(updatedAt = now())) }

    /**
     * Deletes the bean with its recipes and shots. Every record leaves a sync
     * tombstone, so other devices delete it too. Returns what was deleted so the
     * user can undo; the machine and grinder counters are not touched, because
     * those shots were really pulled.
     */
    suspend fun deleteBean(id: String): DeletedBean? = withContext(io) {
        db.transactionWithResult {
            val bean = read(SyncCollections.BEANS, id, Bean.serializer()) ?: return@transactionWithResult null
            val recipes = allLive(SyncCollections.RECIPES, Recipe.serializer()).filter { it.beanId == id }
            val shots = allLive(SyncCollections.SHOTS, Shot.serializer()).filter { it.beanId == id }
            tombstone(SyncCollections.BEANS, id)
            recipes.forEach { tombstone(SyncCollections.RECIPES, it.id) }
            shots.forEach { tombstone(SyncCollections.SHOTS, it.id) }
            DeletedBean(bean, recipes, shots)
        }
    }

    /** Undo for [deleteBean]: the records come back newer than their tombstones, so sync restores them everywhere. */
    suspend fun restore(deleted: DeletedBean) = withContext(io) {
        db.transaction {
            val t = now()
            write(SyncCollections.BEANS, Bean.serializer(), deleted.bean.copy(updatedAt = t))
            deleted.recipes.forEach { write(SyncCollections.RECIPES, Recipe.serializer(), it.copy(updatedAt = t)) }
            deleted.shots.forEach { write(SyncCollections.SHOTS, Shot.serializer(), it.copy(updatedAt = t)) }
        }
    }

    /**
     * Opens, freezes or archives a bag. Freezing stores how many doses went into
     * the freezer; a frozen or archived bag leaves the hopper.
     */
    suspend fun setStatus(beanId: String, status: BeanStatus, frozenDoses: Int = 0) = withContext(io) {
        val b = read(SyncCollections.BEANS, beanId, Bean.serializer()) ?: return@withContext
        val updated = when (status) {
            BeanStatus.OPEN -> b.copy(status = status, frozenDoses = 0)
            BeanStatus.FROZEN -> b.copy(status = status, frozenDoses = frozenDoses.coerceAtLeast(1), inHopper = false)
            BeanStatus.ARCHIVED -> b.copy(status = status, frozenDoses = 0, inHopper = false)
        }
        write(SyncCollections.BEANS, Bean.serializer(), updated.copy(updatedAt = now()))
    }

    /** Puts one open bag into the hopper; all others leave it. */
    suspend fun putInHopper(beanId: String) = withContext(io) {
        db.transaction {
            val t = now()
            allLive(SyncCollections.BEANS, Bean.serializer()).forEach { b ->
                val should = b.id == beanId
                if (b.inHopper != should || (should && b.status != BeanStatus.OPEN)) {
                    write(SyncCollections.BEANS, Bean.serializer(), b.copy(inHopper = should, status = if (should) BeanStatus.OPEN else b.status, updatedAt = t))
                }
            }
        }
    }

    /** Takes one frozen dose out; the bag counts as open when it has doses in use. */
    suspend fun thawDose(beanId: String) = withContext(io) {
        val b = read(SyncCollections.BEANS, beanId, Bean.serializer()) ?: return@withContext
        val left = (b.frozenDoses - 1).coerceAtLeast(0)
        write(SyncCollections.BEANS, Bean.serializer(), b.copy(frozenDoses = left, status = if (left == 0) BeanStatus.OPEN else b.status, updatedAt = now()))
    }

    suspend fun saveRecipe(recipe: Recipe) = withContext(io) { write(SyncCollections.RECIPES, Recipe.serializer(), recipe.copy(updatedAt = now())) }

    // --- shots ---------------------------------------------------------------

    /**
     * Stores the shot and updates everything it affects: grams left in the bag,
     * the machine's shot counter and the grinder's kg counter (maintenance).
     */
    suspend fun logShot(shot: Shot) = withContext(io) {
        db.transaction {
            val t = now()
            write(SyncCollections.SHOTS, Shot.serializer(), shot.copy(updatedAt = t))
            read(SyncCollections.BEANS, shot.beanId, Bean.serializer())?.let { b ->
                write(SyncCollections.BEANS, Bean.serializer(), b.copy(remainingGrams = (b.remainingGrams - shot.doseGrams).coerceAtLeast(0.0), updatedAt = t))
            }
            allLive(SyncCollections.EQUIPMENT, Equipment.serializer()).forEach { e ->
                val updated = when (e.kind) {
                    EquipmentKind.MACHINE -> e.copy(shotCount = e.shotCount + 1, updatedAt = t)
                    EquipmentKind.GRINDER -> e.copy(groundKg = e.groundKg + shot.doseGrams / 1000.0, updatedAt = t)
                }
                write(SyncCollections.EQUIPMENT, Equipment.serializer(), updated)
            }
        }
    }

    /**
     * Corrects a logged shot. The bag and the grinder's kg counter move by the
     * difference in dose; the machine's shot counter stays (it is still one shot).
     */
    suspend fun updateShot(shot: Shot) = withContext(io) {
        db.transaction {
            val old = read(SyncCollections.SHOTS, shot.id, Shot.serializer()) ?: return@transaction
            val t = now()
            val fixed = shot.copy(beanId = old.beanId, updatedAt = t)
            write(SyncCollections.SHOTS, Shot.serializer(), fixed)
            val delta = fixed.doseGrams - old.doseGrams
            if (delta != 0.0) {
                adjustBag(old.beanId, -delta, t)
                adjustCounters(shots = 0, grams = delta, t)
            }
        }
    }

    /**
     * Deletes a shot logged by mistake and gives back what it used: grams to the
     * bag, one shot on the machine counter, the dose on the grinder counter.
     * Returns it for undo, which is [logShot] again.
     */
    suspend fun deleteShot(id: String): Shot? = withContext(io) {
        db.transactionWithResult {
            val shot = read(SyncCollections.SHOTS, id, Shot.serializer()) ?: return@transactionWithResult null
            val t = now()
            tombstone(SyncCollections.SHOTS, id)
            adjustBag(shot.beanId, shot.doseGrams, t)
            adjustCounters(shots = -1, grams = -shot.doseGrams, t)
            shot
        }
    }

    private fun adjustBag(beanId: String, grams: Double, t: Instant) {
        read(SyncCollections.BEANS, beanId, Bean.serializer())?.let { b ->
            val left = (b.remainingGrams + grams).coerceIn(0.0, b.weightGrams.toDouble())
            write(SyncCollections.BEANS, Bean.serializer(), b.copy(remainingGrams = left, updatedAt = t))
        }
    }

    private fun adjustCounters(shots: Int, grams: Double, t: Instant) {
        allLive(SyncCollections.EQUIPMENT, Equipment.serializer()).forEach { e ->
            val updated = when (e.kind) {
                EquipmentKind.MACHINE -> if (shots == 0) null else e.copy(shotCount = (e.shotCount + shots).coerceAtLeast(0))
                EquipmentKind.GRINDER -> e.copy(groundKg = (e.groundKg + grams / 1000.0).coerceAtLeast(0.0))
            }
            updated?.let { write(SyncCollections.EQUIPMENT, Equipment.serializer(), it.copy(updatedAt = t)) }
        }
    }

    /**
     * Makes a good shot the baseline. With [overwrite] it replaces the shot's
     * recipe; otherwise (or without one) it becomes a new recipe named [newName].
     * Either way the bean is brewed with that recipe from now on.
     */
    suspend fun adoptShotAsRecipe(shot: Shot, overwrite: Boolean = true, newName: String = "Espresso") = withContext(io) {
        db.transactionWithResult {
            val existing = shot.recipeId?.takeIf { overwrite }?.let { read(SyncCollections.RECIPES, it, Recipe.serializer()) }
            val t = now()
            val recipe = existing?.copy(
                grindSetting = shot.grindSetting, doseGrams = shot.doseGrams, yieldGrams = shot.yieldGrams,
                temperatureC = shot.temperatureC, updatedAt = t,
            ) ?: Recipe(
                id = newId(), beanId = shot.beanId, name = newName, grindSetting = shot.grindSetting,
                doseGrams = shot.doseGrams, yieldGrams = shot.yieldGrams,
                targetTimeMinSec = (shot.timeSec - 1).toInt(), targetTimeMaxSec = (shot.timeSec + 1).toInt(),
                temperatureC = shot.temperatureC, updatedAt = t,
            )
            write(SyncCollections.RECIPES, Recipe.serializer(), recipe)
            read(SyncCollections.BEANS, shot.beanId, Bean.serializer())?.takeIf { it.recipeId != recipe.id }?.let {
                write(SyncCollections.BEANS, Bean.serializer(), it.copy(recipeId = recipe.id, updatedAt = t))
            }
            recipe
        }
    }

    /** Brew the bean with this recipe from now on. */
    suspend fun selectRecipe(beanId: String, recipeId: String) = withContext(io) {
        val b = read(SyncCollections.BEANS, beanId, Bean.serializer()) ?: return@withContext
        if (b.recipeId != recipeId) write(SyncCollections.BEANS, Bean.serializer(), b.copy(recipeId = recipeId, updatedAt = now()))
    }

    /** Deletes a recipe (tombstone for sync); returns it for undo. Shots keep their reference as history. */
    suspend fun deleteRecipe(id: String): Recipe? = withContext(io) {
        db.transactionWithResult {
            val recipe = read(SyncCollections.RECIPES, id, Recipe.serializer()) ?: return@transactionWithResult null
            tombstone(SyncCollections.RECIPES, id)
            read(SyncCollections.BEANS, recipe.beanId, Bean.serializer())?.takeIf { it.recipeId == id }?.let {
                write(SyncCollections.BEANS, Bean.serializer(), it.copy(recipeId = null, updatedAt = now()))
            }
            recipe
        }
    }

    // --- equipment & maintenance -----------------------------------------

    suspend fun saveEquipment(e: Equipment) = withContext(io) { write(SyncCollections.EQUIPMENT, Equipment.serializer(), e.copy(updatedAt = now())) }

    suspend fun saveTask(task: MaintenanceTask) = withContext(io) { write(SyncCollections.TASKS, MaintenanceTask.serializer(), task.copy(updatedAt = now())) }

    /** Marks the task done now; returns how it was before, so the user can undo. */
    suspend fun completeTask(taskId: String): MaintenanceTask? = withContext(io) {
        val task = read(SyncCollections.TASKS, taskId, MaintenanceTask.serializer()) ?: return@withContext null
        val equipment = read(SyncCollections.EQUIPMENT, task.equipmentId, Equipment.serializer())
        write(SyncCollections.TASKS, MaintenanceTask.serializer(), Maintenance.markDone(task, equipment, now()))
        task
    }

    /** Deletes a care task (tombstone for sync); returns it for undo via [saveTask]. */
    suspend fun deleteTask(taskId: String): MaintenanceTask? = withContext(io) {
        val task = read(SyncCollections.TASKS, taskId, MaintenanceTask.serializer()) ?: return@withContext null
        tombstone(SyncCollections.TASKS, taskId)
        task
    }

    // --- reminders -------------------------------------------------------------

    /**
     * Reminders that are due and have not been shown yet. Marks them as shown,
     * so calling this from a periodic job notifies each one exactly once.
     */
    suspend fun takeNewReminders(): List<Reminder> = withContext(io) {
        db.transactionWithResult {
            val due = Reminders.due(
                allLive(SyncCollections.TASKS, MaintenanceTask.serializer()),
                allLive(SyncCollections.EQUIPMENT, Equipment.serializer()),
                allLive(SyncCollections.BEANS, Bean.serializer()),
                allLive(SyncCollections.RECIPES, Recipe.serializer()),
                now(),
            )
            val shown = q.getValue(KEY_REMINDERS_SHOWN).executeAsOneOrNull()?.split('\n')?.toSet().orEmpty()
            // Only keys that are still due are kept, so the list never grows.
            q.setValue(KEY_REMINDERS_SHOWN, due.joinToString("\n") { it.key })
            due.filter { it.key !in shown }
        }
    }

    // --- setup -----------------------------------------------------------------

    /** False only on a fresh install that has not been through onboarding yet. */
    suspend fun isSetupDone(): Boolean = withContext(io) {
        q.getValue(KEY_SETUP_DONE).executeAsOneOrNull() != null ||
            q.getValue(KEY_SEEDED).executeAsOneOrNull() != null ||
            q.countAll().executeAsOne() > 0
    }

    suspend fun markSetupDone() = withContext(io) { q.setValue(KEY_SETUP_DONE, now().toString()) }

    /**
     * Replaces the machine or grinder (and its care plan) with [equipment] and
     * [tasks]. The old device's history stays in the shots; its tasks go.
     */
    suspend fun replaceEquipment(equipment: Equipment, tasks: List<MaintenanceTask>) = withContext(io) {
        db.transaction {
            val old = allLive(SyncCollections.EQUIPMENT, Equipment.serializer()).filter { it.kind == equipment.kind && it.id != equipment.id }
            val oldIds = old.map { it.id }.toSet()
            old.forEach { tombstone(SyncCollections.EQUIPMENT, it.id) }
            allLive(SyncCollections.TASKS, MaintenanceTask.serializer()).filter { it.equipmentId in oldIds || it.equipmentId == equipment.id }
                .forEach { tombstone(SyncCollections.TASKS, it.id) }
            write(SyncCollections.EQUIPMENT, Equipment.serializer(), equipment.copy(updatedAt = now()))
            tasks.forEach { write(SyncCollections.TASKS, MaintenanceTask.serializer(), it.copy(updatedAt = now())) }
        }
    }

    // --- backup ----------------------------------------------------------------

    /** Snapshot of all live records, for a file the user keeps outside the app. */
    suspend fun exportBackup(): DropsBackup = withContext(io) {
        DropsBackup(
            exportedAt = now(),
            beans = allLive(SyncCollections.BEANS, Bean.serializer()),
            recipes = allLive(SyncCollections.RECIPES, Recipe.serializer()),
            shots = allLive(SyncCollections.SHOTS, Shot.serializer()),
            equipment = allLive(SyncCollections.EQUIPMENT, Equipment.serializer()),
            tasks = allLive(SyncCollections.TASKS, MaintenanceTask.serializer()),
        )
    }

    /**
     * Merges a backup into the local store. Each record wins only if it is newer
     * than what the device has (including deletions), so importing an old file
     * never overwrites newer work. Imported records sync like local edits.
     */
    suspend fun importBackup(backup: DropsBackup): ImportResult = withContext(io) {
        var added = 0
        var updated = 0
        var skipped = 0
        fun <T : Entity> merge(collection: String, serializer: KSerializer<T>, items: List<T>) {
            items.forEach { item ->
                val existing = q.byId(collection, item.id).executeAsOneOrNull()
                when {
                    existing == null -> { write(collection, serializer, item); added++ }
                    item.updatedAt.toEpochMilliseconds() > existing.updated_at -> { write(collection, serializer, item); updated++ }
                    else -> skipped++
                }
            }
        }
        db.transaction {
            merge(SyncCollections.BEANS, Bean.serializer(), backup.beans)
            merge(SyncCollections.RECIPES, Recipe.serializer(), backup.recipes)
            merge(SyncCollections.SHOTS, Shot.serializer(), backup.shots)
            merge(SyncCollections.EQUIPMENT, Equipment.serializer(), backup.equipment)
            merge(SyncCollections.TASKS, MaintenanceTask.serializer(), backup.tasks)
            // A restored install must not be filled with sample data afterwards.
            q.setValue(KEY_SEEDED, now().toString())
        }
        ImportResult(added, updated, skipped)
    }

    // --- sample data ---------------------------------------------------------

    /** Fills an empty install with example data so the app opens in a useful state. */
    suspend fun seedIfEmpty(sample: SampleData = SampleData.create(now())) = withContext(io) {
        if (q.getValue(KEY_SEEDED).executeAsOneOrNull() != null || q.countAll().executeAsOne() > 0) return@withContext
        db.transaction {
            sample.beans.forEach { write(SyncCollections.BEANS, Bean.serializer(), it) }
            sample.recipes.forEach { write(SyncCollections.RECIPES, Recipe.serializer(), it) }
            sample.shots.forEach { write(SyncCollections.SHOTS, Shot.serializer(), it) }
            sample.equipment.forEach { write(SyncCollections.EQUIPMENT, Equipment.serializer(), it) }
            sample.tasks.forEach { write(SyncCollections.TASKS, MaintenanceTask.serializer(), it) }
            q.setValue(KEY_SEEDED, now().toString())
        }
    }

    /**
     * Removes everything the example set brought: beans, recipes, shots, the
     * example machine and grinder with their counters and care plan. Recipes and
     * shots the user added to an example bean go too, so nothing is left without
     * its bean. Devices the user set up are kept.
     */
    suspend fun removeSampleData() = withContext(io) {
        db.transaction {
            val sampleBeans = q.liveIds(SyncCollections.BEANS).executeAsList().filter { it.startsWith(SampleData.PREFIX) }.toSet()
            val sampleDevices = q.liveIds(SyncCollections.EQUIPMENT).executeAsList().filter { it.startsWith(SampleData.PREFIX) }.toSet()
            sampleBeans.forEach { tombstone(SyncCollections.BEANS, it) }
            allLive(SyncCollections.RECIPES, Recipe.serializer()).filter { it.beanId in sampleBeans || it.id.startsWith(SampleData.PREFIX) }
                .forEach { tombstone(SyncCollections.RECIPES, it.id) }
            allLive(SyncCollections.SHOTS, Shot.serializer()).filter { it.beanId in sampleBeans || it.id.startsWith(SampleData.PREFIX) }
                .forEach { tombstone(SyncCollections.SHOTS, it.id) }
            sampleDevices.forEach { tombstone(SyncCollections.EQUIPMENT, it) }
            allLive(SyncCollections.TASKS, MaintenanceTask.serializer()).filter { it.equipmentId in sampleDevices || it.id.startsWith(SampleData.PREFIX) }
                .forEach { tombstone(SyncCollections.TASKS, it.id) }
        }
    }

    private fun <T> allLive(collection: String, serializer: KSerializer<T>): List<T> =
        q.liveByCollection(collection).executeAsList().mapNotNull { row -> row.json?.let { DropsJson.decodeFromString(serializer, it) } }

    companion object {
        private const val KEY_SEEDED = "seeded_at"
        private const val KEY_SETUP_DONE = "setup_done_at"
        private const val KEY_REMINDERS_SHOWN = "reminders_shown"
    }
}
