package de.birneklub.drop.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import de.birneklub.drop.android.ui.ButtonKind
import de.birneklub.drop.android.ui.ConfirmDialog
import de.birneklub.drop.android.ui.Divider
import de.birneklub.drop.android.ui.Drops
import de.birneklub.drop.android.ui.DropsCard
import de.birneklub.drop.android.ui.DropsType
import de.birneklub.drop.android.ui.DropsViewModel
import de.birneklub.drop.android.ui.Eyebrow
import de.birneklub.drop.android.ui.FieldRow
import de.birneklub.drop.android.ui.FormCard
import de.birneklub.drop.android.ui.FormField
import de.birneklub.drop.android.ui.MonoFamily
import de.birneklub.drop.android.ui.PillButton
import de.birneklub.drop.android.ui.Routes
import de.birneklub.drop.android.ui.Segmented
import de.birneklub.drop.android.ui.Space
import de.birneklub.drop.android.ui.TextAction
import de.birneklub.drop.core.domain.ShotDraft
import de.birneklub.drop.core.domain.ShotDraft.Field
import de.birneklub.drop.core.format.Format
import de.birneklub.drop.core.model.Shot
import de.birneklub.drop.core.model.Taste
import de.birneklub.drop.core.sync.DropsJson
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val Weekdays = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")

/** Every shot of one bean, newest first and grouped by day; tap one to correct or delete it. */
@Composable
fun ShotsScreen(vm: DropsViewModel, nav: NavController, beanId: String) {
    val lib by vm.library.collectAsStateWithLifecycle()
    val bean = lib.bean(beanId)
    if (bean == null) {
        if (lib.loaded) MissingBean(nav)
        return
    }
    val c = Drops.colors
    val zone = TimeZone.currentSystemDefault()
    val shots = lib.shotsFor(beanId).reversed()
    val recipes = lib.recipes.associateBy { it.id }

    ScreenColumn {
        TextAction("‹ Zurück", { nav.popBackStack() })
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Eyebrow(bean.name)
            ScreenTitle("Shots", "${shots.size} gesamt")
        }
        if (shots.isEmpty()) {
            DropsCard(Modifier.fillMaxWidth()) {
                Text("Noch kein Shot mit dieser Bohne.", style = DropsType.bodyStrong, color = c.ink)
                Text("Gespeicherte Shots erscheinen hier und lassen sich korrigieren oder löschen.", style = DropsType.small, color = c.muted, modifier = Modifier.padding(top = Space.xs))
            }
            if (bean.status == de.birneklub.drop.core.model.BeanStatus.OPEN) {
                PillButton("Ersten Shot brühen", { nav.navigate(Routes.shot(bean.id)) }, Modifier.fillMaxWidth(), kind = ButtonKind.Ink)
            }
        }
        shots.groupBy { it.pulledAt.toLocalDateTime(zone).date }.forEach { (day, list) ->
            Eyebrow("${Weekdays[day.dayOfWeek.ordinal]}, ${Format.date(day)}")
            DropsCard(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
                list.forEachIndexed { i, s ->
                    if (i > 0) Divider()
                    ShotRow(s, recipes[s.recipeId]?.name) { nav.navigate(Routes.editShot(s.id)) }
                }
            }
        }
    }
}

@Composable
private fun ShotRow(shot: Shot, recipe: String?, onClick: () -> Unit) {
    val c = Drops.colors
    val time = shot.pulledAt.toLocalDateTime(TimeZone.currentSystemDefault())
    Row(
        Modifier.fillMaxWidth().heightIn(min = Space.touch).clickable(role = Role.Button, onClickLabel = "Shot bearbeiten", onClick = onClick)
            .padding(horizontal = Space.l, vertical = Space.m),
        horizontalArrangement = Arrangement.spacedBy(Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(tasteColor(shot.taste.ordinal)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
            Text(
                "${Format.doseToYield(shot.doseGrams, shot.yieldGrams)} · ${Format.seconds(shot.timeSec)}",
                style = DropsType.bodyStrong.copy(fontFamily = MonoFamily), color = c.ink,
            )
            Text(
                Format.join(
                    "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}",
                    TasteLabels[shot.taste.ordinal], "Mahlgrad ${Format.grind(shot.grindSetting)}", Format.celsius(shot.temperatureC), recipe,
                ),
                style = DropsType.small, color = c.muted,
            )
        }
        Text("›", style = DropsType.headline, color = c.muted)
    }
}

private val DraftSaver = Saver<ShotDraft, String>(
    save = { DropsJson.encodeToString(ShotDraft.serializer(), it) },
    restore = { DropsJson.decodeFromString(ShotDraft.serializer(), it) },
)

/** Corrects a shot's numbers and taste, or deletes it; bag and counters follow. */
@Composable
fun ShotEditScreen(vm: DropsViewModel, nav: NavController, shotId: String) {
    val lib by vm.library.collectAsStateWithLifecycle()
    val shot = lib.shots.firstOrNull { it.id == shotId }
    if (shot == null) {
        // Deleted (maybe just now): nothing to edit, go back to the list.
        if (lib.loaded) androidx.compose.runtime.LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    val c = Drops.colors
    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf(ShotDraft.of(shot)) }
    var tried by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val errors = if (tried) draft.errors() else emptyMap()
    val time = shot.pulledAt.toLocalDateTime(TimeZone.currentSystemDefault())

    ScreenColumn {
        TextAction("Abbrechen", { nav.popBackStack() })
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Eyebrow(lib.bean(shot.beanId)?.name ?: "Shot")
            ScreenTitle("Shot bearbeiten")
            Text(
                "Gezogen am ${Format.date(time.date)} um ${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}",
                style = DropsType.body, color = c.muted,
            )
        }
        FormCard {
            FieldRow {
                FormField("Dosis (g)", draft.dose, { draft = draft.copy(dose = it) }, Modifier.weight(1f).fillMaxHeight(), KeyboardType.Decimal, error = errors[Field.DOSE])
                FormField("Ertrag (g)", draft.yield, { draft = draft.copy(yield = it) }, Modifier.weight(1f).fillMaxHeight(), KeyboardType.Decimal, error = errors[Field.YIELD])
            }
            FieldRow {
                FormField("Zeit (s)", draft.time, { draft = draft.copy(time = it) }, Modifier.weight(1f).fillMaxHeight(), KeyboardType.Decimal, error = errors[Field.TIME])
                FormField("Temperatur (°C)", draft.temperature, { draft = draft.copy(temperature = it) }, Modifier.weight(1f).fillMaxHeight(), KeyboardType.Number, error = errors[Field.TEMPERATURE])
            }
            FormField("Mahlgrad", draft.grind, { draft = draft.copy(grind = it) }, type = KeyboardType.Decimal, error = errors[Field.GRIND])
        }
        Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Text("Geschmack", style = DropsType.small, color = c.muted)
            TasteSelector(draft.taste) { draft = draft.copy(taste = it) }
        }
        Text("Ändert sich die Dosis, werden Restmenge der Tüte und der Zähler der Mühle angepasst.", style = DropsType.small, color = c.muted)
        if (errors.isNotEmpty()) Text("Bitte die markierten Felder prüfen.", style = DropsType.small, color = c.bad)
        PillButton("Änderungen speichern", {
            tried = true
            if (draft.errors().isEmpty()) {
                vm.updateShot(draft.applyTo(shot, vm.now()))
                nav.popBackStack()
            }
        }, Modifier.fillMaxWidth(), kind = ButtonKind.Ink, height = 56.dp)
        PillButton("Shot löschen", { deleting = true }, Modifier.fillMaxWidth(), kind = ButtonKind.Ghost)
    }
    if (deleting) {
        ConfirmDialog(
            title = "Shot löschen?",
            text = "${Format.grams(shot.doseGrams)} gehen zurück in die Tüte, die Zähler von Maschine und Mühle werden zurückgerechnet. Du kannst das gleich danach rückgängig machen.",
            confirm = "Löschen",
            onConfirm = { vm.deleteShot(shot); nav.popBackStack() },
            onDismiss = { deleting = false },
        )
    }
}

/** The five-step taste scale, with wording shared by every screen. */
@Composable
fun TasteSelector(selected: Taste, onSelect: (Taste) -> Unit) {
    Segmented(TasteLabels, selected.ordinal, { onSelect(Taste.entries[it]) }, Modifier.fillMaxWidth(), DropsType.caption)
}
