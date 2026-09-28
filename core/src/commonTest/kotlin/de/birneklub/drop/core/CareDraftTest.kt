package de.birneklub.drop.core

import de.birneklub.drop.core.domain.EquipmentDraft
import de.birneklub.drop.core.domain.Maintenance
import de.birneklub.drop.core.domain.TaskDraft
import de.birneklub.drop.core.domain.TaskState
import de.birneklub.drop.core.model.Equipment
import de.birneklub.drop.core.model.EquipmentKind
import de.birneklub.drop.core.model.GrindScale
import de.birneklub.drop.core.model.IntervalUnit
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class CareDraftTest {
    private val now = Instant.parse("2026-09-25T08:00:00Z")
    private val grinder = Equipment("g", EquipmentKind.GRINDER, "Niche Zero", groundKg = 21.6, grindScale = GrindScale(0.0, 50.0, 1.0, 10.0, 20.0, "Stufen"), updatedAt = now)
    private val machine = Equipment("m", EquipmentKind.MACHINE, "Silvia", shotCount = 1284, waterHardness = 14.0, updatedAt = now)

    @Test
    fun equipmentRoundTripKeepsTheScaleLabel() {
        val draft = EquipmentDraft.of(grinder)
        assertEquals("21,6", draft.counter)
        val edited = draft.copy(name = "Niche", counter = "0", scaleStep = "0,5").applyTo(grinder, now)
        assertEquals("Niche", edited.name)
        assertEquals(0.0, edited.groundKg)
        assertEquals(0.5, edited.grindScale?.step)
        assertEquals("Stufen", edited.grindScale?.label)
        assertEquals(machine, EquipmentDraft.of(machine).applyTo(machine, now))
    }

    @Test
    fun equipmentRejectsNonsense() {
        val bad = EquipmentDraft.of(grinder).copy(scaleMin = "50", scaleMax = "10", waterHardness = "-3", counter = "x")
        assertEquals(setOf(EquipmentDraft.Field.SCALE, EquipmentDraft.Field.WATER, EquipmentDraft.Field.COUNTER), bad.errors(EquipmentKind.GRINDER).keys)
        assertTrue(EquipmentDraft.of(machine).copy(counter = "12,5").errors(EquipmentKind.MACHINE).containsKey(EquipmentDraft.Field.COUNTER))
        assertNull(EquipmentDraft.of(grinder).copy(espressoTo = "60").scale())
        val noDial = grinder.copy(grindScale = null)
        assertTrue(EquipmentDraft.of(noDial).errors(EquipmentKind.GRINDER).isEmpty(), "a grinder without a dial can be saved")
        assertNull(EquipmentDraft.of(noDial).applyTo(noDial, now).grindScale)
    }

    @Test
    fun newTasksStartCountingNow() {
        val task = TaskDraft("Mühle reinigen", interval = "3", unit = IntervalUnit.KILOGRAMS).toTask("t", grinder, null, now)
        assertEquals(21.6, task.lastDoneCounter)
        assertEquals(TaskState.OK, Maintenance.status(task, grinder, now).state)
        val days = TaskDraft("Rückspülen").toTask("t2", machine, null, now)
        assertEquals(now, days.lastDoneAt)
    }

    @Test
    fun editingATaskKeepsItsHistoryUnlessTheUnitChanges() {
        val base = TaskDraft("Rückspülen").toTask("t", machine, null, now - 20.days)
        val renamed = TaskDraft.of(base).copy(name = "Rückspülen mit Reiniger", interval = "7").toTask("t", machine, base, now)
        assertEquals(now - 20.days, renamed.lastDoneAt)
        assertEquals(TaskState.OVERDUE, Maintenance.status(renamed, machine, now).state)
        val byShots = TaskDraft.of(base).copy(unit = IntervalUnit.SHOTS, interval = "200").toTask("t", machine, base, now)
        assertEquals(1284.0, byShots.lastDoneCounter)
    }

    @Test
    fun taskLimits() {
        assertEquals(setOf(TaskDraft.Field.NAME, TaskDraft.Field.INTERVAL), TaskDraft(name = "", interval = "0").errors().keys)
        assertTrue(TaskDraft("X", interval = "1000").errors().containsKey(TaskDraft.Field.INTERVAL))
    }
}
