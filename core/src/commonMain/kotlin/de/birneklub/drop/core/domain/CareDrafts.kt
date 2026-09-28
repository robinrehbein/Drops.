package de.birneklub.drop.core.domain

import de.birneklub.drop.core.format.Format
import de.birneklub.drop.core.model.Equipment
import de.birneklub.drop.core.model.EquipmentKind
import de.birneklub.drop.core.model.GrindScale
import de.birneklub.drop.core.model.IntervalUnit
import de.birneklub.drop.core.model.MaintenanceTask
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

private fun num(v: Double?) = v?.let { Format.compact(it, 2).replace(".", "") }.orEmpty()

/** Editing a machine or grinder: name, notes, water, counters and the grinder's dial. */
@Serializable
data class EquipmentDraft(
    val name: String,
    val details: String = "",
    val waterHardness: String = "",
    val filteredHardness: String = "",
    /** Shots for a machine, kg for a grinder. */
    val counter: String = "0",
    val scaleMin: String = "",
    val scaleMax: String = "",
    val scaleStep: String = "",
    val espressoFrom: String = "",
    val espressoTo: String = "",
) {
    enum class Field { NAME, WATER, FILTERED, COUNTER, SCALE }

    fun errors(kind: EquipmentKind): Map<Field, String> = buildMap {
        if (name.isBlank()) put(Field.NAME, "Bitte einen Namen eintragen.")
        fun hardnessOk(t: String) = t.isBlank() || Format.parseDecimal(t)?.let { it in 0.0..40.0 } == true
        if (!hardnessOk(waterHardness)) put(Field.WATER, "Härte in °dH zwischen 0 und 40 eintragen oder leer lassen.")
        if (!hardnessOk(filteredHardness)) put(Field.FILTERED, "Härte in °dH zwischen 0 und 40 eintragen oder leer lassen.")
        when (kind) {
            EquipmentKind.MACHINE -> if (counter.trim().toIntOrNull()?.let { it >= 0 } != true) put(Field.COUNTER, "Anzahl Shots als ganze Zahl eintragen, z. B. 1284.")
            EquipmentKind.GRINDER -> {
                if (Format.parseDecimal(counter)?.let { it >= 0 } != true) put(Field.COUNTER, "Gemahlene Menge in kg eintragen, z. B. 21,6.")
                if (!scaleBlank() && scale() == null) put(Field.SCALE, "Skala prüfen: von < bis, Schritt größer 0, Espresso-Bereich innerhalb der Skala.")
            }
        }
    }

    /** No dial entered: the grinder keeps working with free numbers. */
    fun scaleBlank() = listOf(scaleMin, scaleMax, scaleStep, espressoFrom, espressoTo).all { it.isBlank() }

    /** The dial as typed, or null when it does not make sense. */
    fun scale(): GrindScale? {
        val min = Format.parseDecimal(scaleMin) ?: return null
        val max = Format.parseDecimal(scaleMax) ?: return null
        val step = Format.parseDecimal(scaleStep) ?: return null
        val from = Format.parseDecimal(espressoFrom) ?: return null
        val to = Format.parseDecimal(espressoTo) ?: return null
        if (min >= max || step <= 0 || step > max - min || from > to || from < min || to > max) return null
        return GrindScale(min, max, step, from, to)
    }

    fun applyTo(e: Equipment, now: Instant): Equipment {
        require(errors(e.kind).isEmpty()) { "Draft has errors" }
        return e.copy(
            name = name.trim(), details = details.trim(),
            waterHardness = Format.parseDecimal(waterHardness), filteredHardness = Format.parseDecimal(filteredHardness),
            shotCount = if (e.kind == EquipmentKind.MACHINE) counter.trim().toInt() else e.shotCount,
            groundKg = if (e.kind == EquipmentKind.GRINDER) Format.parseDecimal(counter)!! else e.groundKg,
            grindScale = if (e.kind == EquipmentKind.GRINDER && !scaleBlank()) scale()!!.copy(label = e.grindScale?.label.orEmpty()) else if (e.kind == EquipmentKind.GRINDER) null else e.grindScale,
            updatedAt = now,
        )
    }

    companion object {
        fun of(e: Equipment) = EquipmentDraft(
            name = e.name, details = e.details, waterHardness = num(e.waterHardness), filteredHardness = num(e.filteredHardness),
            counter = if (e.kind == EquipmentKind.MACHINE) e.shotCount.toString() else num(e.groundKg),
            scaleMin = num(e.grindScale?.min), scaleMax = num(e.grindScale?.max), scaleStep = num(e.grindScale?.step),
            espressoFrom = num(e.grindScale?.espressoFrom), espressoTo = num(e.grindScale?.espressoTo),
        )
    }
}

/** Adding or changing a care task. */
@Serializable
data class TaskDraft(
    val name: String = "",
    val description: String = "",
    val interval: String = "14",
    val unit: IntervalUnit = IntervalUnit.DAYS,
    val supply: String = "",
) {
    enum class Field { NAME, INTERVAL }

    fun errors(): Map<Field, String> = buildMap {
        if (name.isBlank()) put(Field.NAME, "Bitte einen Namen eintragen, z. B. Rückspülen.")
        val max = when (unit) { IntervalUnit.DAYS -> 730.0; IntervalUnit.KILOGRAMS -> 500.0; IntervalUnit.SHOTS -> 10_000.0 }
        if (Format.parseDecimal(interval)?.let { it > 0 && it <= max } != true) {
            put(Field.INTERVAL, "Intervall größer 0 und höchstens ${Format.number(max, 0)} ${unitLabel(unit)} eintragen.")
        }
    }

    /**
     * A new task starts counting now (so it is not due at once); an edited task
     * keeps when it was last done.
     */
    fun toTask(id: String, equipment: Equipment, base: MaintenanceTask?, now: Instant): MaintenanceTask {
        require(errors().isEmpty()) { "Draft has errors" }
        val fresh = base == null || base.intervalUnit != unit
        val counter = when (unit) {
            IntervalUnit.DAYS -> 0.0
            IntervalUnit.KILOGRAMS -> equipment.groundKg
            IntervalUnit.SHOTS -> equipment.shotCount.toDouble()
        }
        return MaintenanceTask(
            id = id, equipmentId = equipment.id, name = name.trim(), description = description.trim(),
            intervalValue = Format.parseDecimal(interval)!!, intervalUnit = unit,
            lastDoneAt = if (fresh) now else base!!.lastDoneAt,
            lastDoneCounter = if (fresh) counter else base!!.lastDoneCounter,
            supply = supply.trim().ifBlank { null }, updatedAt = now,
        )
    }

    companion object {
        fun of(t: MaintenanceTask) = TaskDraft(t.name, t.description, num(t.intervalValue), t.intervalUnit, t.supply.orEmpty())

        fun unitLabel(unit: IntervalUnit) = when (unit) {
            IntervalUnit.DAYS -> "Tage"
            IntervalUnit.KILOGRAMS -> "kg"
            IntervalUnit.SHOTS -> "Shots"
        }
    }
}
