package de.birneklub.drop.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import de.birneklub.drop.android.ui.ButtonKind
import de.birneklub.drop.android.ui.ConfirmDialog
import de.birneklub.drop.android.ui.Drops
import de.birneklub.drop.android.ui.DropsCard
import de.birneklub.drop.android.ui.DropsType
import de.birneklub.drop.android.ui.DropsViewModel
import de.birneklub.drop.android.ui.FieldRow
import de.birneklub.drop.android.ui.FormCard
import de.birneklub.drop.android.ui.FormField
import de.birneklub.drop.android.ui.FormSection
import de.birneklub.drop.android.ui.PillButton
import de.birneklub.drop.android.ui.Routes
import de.birneklub.drop.android.ui.SectionHeader
import de.birneklub.drop.android.ui.Segmented
import de.birneklub.drop.android.ui.Space
import de.birneklub.drop.android.ui.TaskRow
import de.birneklub.drop.android.ui.TextAction
import de.birneklub.drop.core.domain.EquipmentDraft
import de.birneklub.drop.core.domain.TaskDraft
import de.birneklub.drop.core.model.EquipmentKind
import de.birneklub.drop.core.model.IntervalUnit
import de.birneklub.drop.core.sync.DropsJson

private val EquipmentSaver = Saver<EquipmentDraft, String>(
    save = { DropsJson.encodeToString(EquipmentDraft.serializer(), it) },
    restore = { DropsJson.decodeFromString(EquipmentDraft.serializer(), it) },
)

private val TaskSaver = Saver<TaskDraft, String>(
    save = { DropsJson.encodeToString(TaskDraft.serializer(), it) },
    restore = { DropsJson.decodeFromString(TaskDraft.serializer(), it) },
)

/**
 * Details of the machine or grinder: name, water, counters, the grinder's dial
 * and its care plan. Without a device of this kind it opens the catalog.
 */
@Composable
fun EquipmentScreen(vm: DropsViewModel, nav: NavController, kind: EquipmentKind) {
    val lib by vm.library.collectAsStateWithLifecycle()
    val equipment = lib.equipment.firstOrNull { it.kind == kind }
    if (equipment == null) {
        if (lib.loaded) EquipmentReplaceScreen(vm, nav, kind, replacing = false)
        return
    }
    val c = Drops.colors
    val machine = kind == EquipmentKind.MACHINE
    val uri = LocalUriHandler.current
    // Re-seeded when the device is swapped, so the form never shows the old one.
    var draft by rememberSaveable(equipment.id, stateSaver = EquipmentSaver) { mutableStateOf(EquipmentDraft.of(equipment)) }
    var tried by rememberSaveable { mutableStateOf(false) }
    var replacing by rememberSaveable { mutableStateOf(false) }
    val errors = if (tried) draft.errors(kind) else emptyMap()
    val plan = lib.carePlan(vm.now()).filter { it.task.equipmentId == equipment.id }

    ScreenColumn {
        TextAction("‹ Zurück", { nav.popBackStack() })
        ScreenTitle(if (machine) "Maschine" else "Mühle")

        FormCard {
            FormField("Name", draft.name, { draft = draft.copy(name = it) }, error = errors[EquipmentDraft.Field.NAME])
            FormField("Details", draft.details, { draft = draft.copy(details = it) }, placeholder = if (machine) "z. B. Dualboiler · E61" else "z. B. 64 mm Flat")
            FormField(
                if (machine) "Shots gesamt" else "Gemahlen (kg)", draft.counter, { draft = draft.copy(counter = it) },
                type = if (machine) KeyboardType.Number else KeyboardType.Decimal, error = errors[EquipmentDraft.Field.COUNTER],
            )
        }
        if (machine) {
            FormSection("Wasser") {
                FormCard {
                    FieldRow {
                        FormField("Leitung (°dH)", draft.waterHardness, { draft = draft.copy(waterHardness = it) }, Modifier.weight(1f).fillMaxHeight(), KeyboardType.Decimal, placeholder = "optional", error = errors[EquipmentDraft.Field.WATER])
                        FormField("Nach Filter (°dH)", draft.filteredHardness, { draft = draft.copy(filteredHardness = it) }, Modifier.weight(1f).fillMaxHeight(), KeyboardType.Decimal, placeholder = "optional", error = errors[EquipmentDraft.Field.FILTERED])
                    }
                }
            }
        } else {
            FormSection("Mahlskala") {
                FormCard {
                    FieldRow {
                        FormField("Von", draft.scaleMin, { draft = draft.copy(scaleMin = it) }, Modifier.weight(1f).fillMaxHeight(), KeyboardType.Decimal)
                        FormField("Bis", draft.scaleMax, { draft = draft.copy(scaleMax = it) }, Modifier.weight(1f).fillMaxHeight(), KeyboardType.Decimal)
                        FormField("Schritt", draft.scaleStep, { draft = draft.copy(scaleStep = it) }, Modifier.weight(1f).fillMaxHeight(), KeyboardType.Decimal)
                    }
                    FieldRow {
                        FormField("Espresso ab", draft.espressoFrom, { draft = draft.copy(espressoFrom = it) }, Modifier.weight(1f).fillMaxHeight(), KeyboardType.Decimal)
                        FormField("Espresso bis", draft.espressoTo, { draft = draft.copy(espressoTo = it) }, Modifier.weight(1f).fillMaxHeight(), KeyboardType.Decimal)
                    }
                }
                errors[EquipmentDraft.Field.SCALE]?.let { Text(it, style = DropsType.small, color = c.bad) }
                Text("Der Schritt bestimmt, in welchen Stufen Drops. den Mahlgrad vorschlägt.", style = DropsType.small, color = c.muted)
            }
        }
        PillButton("Änderungen speichern", {
            tried = true
            if (draft.errors(kind).isEmpty()) {
                vm.saveEquipment(draft.applyTo(equipment, vm.now()), "Gespeichert")
                tried = false
            }
        }, Modifier.fillMaxWidth(), kind = ButtonKind.Ink)

        Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
            SectionHeader("Pflege", "+ Aufgabe", { nav.navigate(Routes.task(equipment.id)) })
            if (plan.isEmpty()) {
                DropsCard(Modifier.fillMaxWidth()) {
                    Text("Noch keine Pflegeaufgabe.", style = DropsType.bodyStrong, color = c.ink)
                    Text("Leg z. B. Rückspülen alle 14 Tage an, dann erinnert dich Drops. rechtzeitig.", style = DropsType.small, color = c.muted)
                }
            }
            plan.forEach { s ->
                DropsCard(Modifier.fillMaxWidth(), onClick = { nav.navigate(Routes.task(equipment.id, s.task.id)) }, padding = PaddingValues(0.dp)) {
                    TaskRow(s, compact = false, onBuy = { uri.openUri(vm.open(vm.supplyLink(it), reorder = false)) }, buySponsored = vm.supplyLinksSponsored) { vm.completeTask(s) }
                }
            }
            if (plan.isNotEmpty()) Text("Tippe auf eine Aufgabe, um sie zu ändern oder zu löschen.", style = DropsType.small, color = c.muted)
        }

        PillButton(if (machine) "Maschine tauschen" else "Mühle tauschen", { replacing = true }, Modifier.fillMaxWidth(), kind = ButtonKind.Ghost)
    }
    if (replacing) {
        ConfirmDialog(
            title = if (machine) "Maschine tauschen?" else "Mühle tauschen?",
            text = "${equipment.name} und ${if (plan.size == 1) "ihre Pflegeaufgabe" else "ihre ${plan.size} Pflegeaufgaben"} werden durch das neue Gerät ersetzt, der Zähler beginnt bei 0. Deine Shots und Rezepte bleiben.",
            confirm = "Neues Gerät wählen",
            onConfirm = { nav.navigate(Routes.replaceEquipment(kind)) },
            onDismiss = { replacing = false },
        )
    }
}

private val Units = listOf(IntervalUnit.DAYS, IntervalUnit.SHOTS, IntervalUnit.KILOGRAMS)

/** Adds a care task to a device, or changes or deletes one. */
@Composable
fun TaskScreen(vm: DropsViewModel, nav: NavController, equipmentId: String, taskId: String?) {
    val lib by vm.library.collectAsStateWithLifecycle()
    val equipment = lib.equipment.firstOrNull { it.id == equipmentId }
    val base = taskId?.let { id -> lib.tasks.firstOrNull { it.id == id } }
    if (equipment == null || (taskId != null && base == null)) {
        if (lib.loaded) LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    val c = Drops.colors
    var draft by rememberSaveable(stateSaver = TaskSaver) { mutableStateOf(base?.let(TaskDraft::of) ?: TaskDraft()) }
    var tried by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val errors = if (tried) draft.errors() else emptyMap()

    ScreenColumn {
        TextAction("Abbrechen", { nav.popBackStack() })
        ScreenTitle(if (base == null) "Neue Aufgabe" else "Aufgabe bearbeiten")
        Text(equipment.name, style = DropsType.body, color = c.muted)
        FormCard {
            FormField("Name", draft.name, { draft = draft.copy(name = it) }, placeholder = "z. B. Rückspülen", error = errors[TaskDraft.Field.NAME])
            FormField("Beschreibung", draft.description, { draft = draft.copy(description = it) }, placeholder = "optional, z. B. Blindsieb, 5 × 10 s", singleLine = false)
            FormField("Verbrauchsmaterial", draft.supply, { draft = draft.copy(supply = it) }, placeholder = "optional, für den Nachkauf-Link")
        }
        FormSection("Wie oft") {
            Segmented(Units.map(TaskDraft::unitLabel), Units.indexOf(draft.unit), { draft = draft.copy(unit = Units[it]) }, Modifier.fillMaxWidth())
            FormCard {
                FormField("Alle … ${TaskDraft.unitLabel(draft.unit)}", draft.interval, { draft = draft.copy(interval = it) }, type = KeyboardType.Decimal, error = errors[TaskDraft.Field.INTERVAL])
            }
            Text(
                if (base == null || base.intervalUnit != draft.unit) "Die Aufgabe zählt ab jetzt." else "Wann sie zuletzt erledigt wurde, bleibt erhalten.",
                style = DropsType.small, color = c.muted,
            )
        }
        if (errors.isNotEmpty()) Text("Bitte die markierten Felder prüfen.", style = DropsType.small, color = c.bad)
        PillButton(if (base == null) "Aufgabe anlegen" else "Änderungen speichern", {
            tried = true
            if (draft.errors().isEmpty()) {
                val task = draft.toTask(base?.id ?: vm.newId(), equipment, base, vm.now())
                vm.saveTask(task, if (base == null) "„${task.name}“ angelegt" else "Aufgabe gespeichert")
                nav.popBackStack()
            }
        }, Modifier.fillMaxWidth(), kind = ButtonKind.Ink, height = 56.dp)
        if (base != null) PillButton("Aufgabe löschen", { deleting = true }, Modifier.fillMaxWidth(), kind = ButtonKind.Ghost)
    }
    if (deleting && base != null) {
        ConfirmDialog(
            title = "„${base.name}“ löschen?",
            text = "Es gibt dann keine Erinnerung mehr dafür. Du kannst das gleich danach rückgängig machen.",
            confirm = "Löschen",
            onConfirm = { vm.deleteTask(base); nav.popBackStack() },
            onDismiss = { deleting = false },
        )
    }
}
