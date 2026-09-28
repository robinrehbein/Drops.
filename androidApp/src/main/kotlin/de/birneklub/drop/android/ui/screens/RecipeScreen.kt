package de.birneklub.drop.android.ui.screens

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import de.birneklub.drop.android.ui.ButtonKind
import de.birneklub.drop.android.ui.ConfirmDialog
import de.birneklub.drop.android.ui.Drops
import de.birneklub.drop.android.ui.DropsType
import de.birneklub.drop.android.ui.DropsViewModel
import de.birneklub.drop.android.ui.FieldRow
import de.birneklub.drop.android.ui.FormCard
import de.birneklub.drop.android.ui.FormField
import de.birneklub.drop.android.ui.FormSection
import de.birneklub.drop.android.ui.PillButton
import de.birneklub.drop.android.ui.TextAction
import de.birneklub.drop.core.domain.RecipeDraft
import de.birneklub.drop.core.domain.RecipeDraft.Field
import de.birneklub.drop.core.format.Format
import de.birneklub.drop.core.model.EquipmentKind
import de.birneklub.drop.core.model.Recipe
import de.birneklub.drop.core.sync.DropsJson

private val DraftSaver = Saver<RecipeDraft, String>(
    save = { DropsJson.encodeToString(RecipeDraft.serializer(), it) },
    restore = { DropsJson.decodeFromString(RecipeDraft.serializer(), it) },
)

/**
 * Creates a recipe (empty, or as a copy of [copyOf]) or edits [recipeId]:
 * name, grind, speed, dose, yield, time window, temperature, pre-infusion.
 */
@Composable
fun RecipeScreen(vm: DropsViewModel, nav: NavController, beanId: String, recipeId: String?, copyOf: String?) {
    val lib by vm.library.collectAsStateWithLifecycle()
    val bean = lib.bean(beanId)
    if (bean == null) {
        if (lib.loaded) MissingBean(nav)
        return
    }
    val base: Recipe? = recipeId?.let { id -> lib.recipes.firstOrNull { it.id == id } }
    if (recipeId != null && base == null) {
        // Deleted meanwhile (undo window, other device): nothing left to edit.
        if (lib.loaded) nav.popBackStack()
        return
    }
    val source = copyOf?.let { id -> lib.recipes.firstOrNull { it.id == id } }
    val taken = lib.recipesFor(beanId).map { it.name }
    val scale = lib.equipment.firstOrNull { it.kind == EquipmentKind.GRINDER }?.grindScale
    val c = Drops.colors

    var draft by rememberSaveable(stateSaver = DraftSaver) {
        mutableStateOf(
            when {
                base != null -> RecipeDraft.of(base)
                source != null -> RecipeDraft.of(source, RecipeDraft.copyName(source.name, taken))
                else -> RecipeDraft.empty(scale?.espressoStart).copy(name = RecipeDraft.freeName("Espresso", taken))
            },
        )
    }
    var tried by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val errors = if (tried) draft.errors() else emptyMap()
    val ratio = Format.parseDecimal(draft.dose)?.let { d -> Format.parseDecimal(draft.yield)?.let { y -> Format.ratio(d, y) } } ?: Format.MISSING

    ScreenColumn {
        TextAction("Abbrechen", { nav.popBackStack() })
        ScreenTitle(if (base == null) "Neues Rezept" else "Rezept bearbeiten")
        Text(bean.name, style = DropsType.body, color = c.muted)

        FormCard {
            FormField("Name", draft.name, { draft = draft.copy(name = it) }, error = errors[Field.NAME])
        }
        FormSection("Mahlen") {
            FormCard {
                FieldRow {
                    FormField(
                        scale?.label?.takeIf { it == "Klicks" } ?: "Mahlgrad", draft.grind, { draft = draft.copy(grind = it) }, Modifier.weight(1f).fillMaxHeight(),
                        KeyboardType.Decimal, placeholder = "noch offen", error = errors[Field.GRIND],
                    )
                    FormField("Drehzahl (U/min)", draft.rpm, { draft = draft.copy(rpm = it) }, Modifier.weight(1f).fillMaxHeight(), KeyboardType.Number, placeholder = "optional", error = errors[Field.RPM])
                }
            }
        }
        FormSection("Brühen") {
            FormCard {
                FieldRow {
                    FormField("Dosis (g)", draft.dose, { draft = draft.copy(dose = it) }, Modifier.weight(1f).fillMaxHeight(), KeyboardType.Decimal, error = errors[Field.DOSE])
                    FormField("Ertrag (g)", draft.yield, { draft = draft.copy(yield = it) }, Modifier.weight(1f).fillMaxHeight(), KeyboardType.Decimal, error = errors[Field.YIELD])
                }
                FieldRow {
                    FormField("Zeit ab (s)", draft.timeMin, { draft = draft.copy(timeMin = it) }, Modifier.weight(1f).fillMaxHeight(), KeyboardType.Number, error = errors[Field.TIME])
                    FormField("Zeit bis (s)", draft.timeMax, { draft = draft.copy(timeMax = it) }, Modifier.weight(1f).fillMaxHeight(), KeyboardType.Number)
                }
                FieldRow {
                    FormField("Temperatur (°C)", draft.temperature, { draft = draft.copy(temperature = it) }, Modifier.weight(1f).fillMaxHeight(), KeyboardType.Number, error = errors[Field.TEMPERATURE])
                    FormField("Vorbrühen", draft.preinfusion, { draft = draft.copy(preinfusion = it) }, Modifier.weight(1f).fillMaxHeight(), placeholder = "z. B. 5 s")
                }
                FormField("Notizen", draft.notes, { draft = draft.copy(notes = it) }, placeholder = "Sieb, Puck-Screen, WDT …", singleLine = false)
            }
            Text("Verhältnis $ratio", style = DropsType.small, color = c.muted)
        }

        if (errors.isNotEmpty()) Text("Bitte die markierten Felder prüfen.", style = DropsType.small, color = c.bad)
        PillButton(if (base == null) "Rezept anlegen" else "Änderungen speichern", {
            tried = true
            if (draft.errors().isEmpty()) {
                val recipe = draft.toRecipe(base?.id ?: vm.newId(), beanId, base, vm.now())
                vm.saveAndSelectRecipe(recipe, if (base == null) "Rezept „${recipe.name}“ angelegt" else "Rezept gespeichert")
                nav.popBackStack()
            }
        }, Modifier.fillMaxWidth(), kind = ButtonKind.Ink, height = 56.dp)
        if (base != null) {
            PillButton("Rezept löschen", { deleting = true }, Modifier.fillMaxWidth(), kind = ButtonKind.Ghost)
        }
    }
    if (deleting && base != null) {
        ConfirmDialog(
            title = "„${base.name}“ löschen?",
            text = "Deine Shots mit diesem Rezept bleiben erhalten. Du kannst das gleich danach rückgängig machen.",
            confirm = "Löschen",
            onConfirm = { vm.deleteRecipe(base); nav.popBackStack() },
            onDismiss = { deleting = false },
        )
    }
}
