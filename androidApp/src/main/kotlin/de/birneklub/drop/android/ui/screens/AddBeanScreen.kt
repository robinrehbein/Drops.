package de.birneklub.drop.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import de.birneklub.drop.android.ui.ChoiceRow
import de.birneklub.drop.android.ui.DateField
import de.birneklub.drop.android.ui.Drops
import de.birneklub.drop.android.ui.DropsType
import de.birneklub.drop.android.ui.DropsViewModel
import de.birneklub.drop.android.ui.FieldRow
import de.birneklub.drop.android.ui.FormCard
import de.birneklub.drop.android.ui.FormField
import de.birneklub.drop.android.ui.FormSection
import de.birneklub.drop.android.ui.PillButton
import de.birneklub.drop.android.ui.Routes
import de.birneklub.drop.android.ui.TextAction
import de.birneklub.drop.core.catalog.Countries
import de.birneklub.drop.core.domain.BeanDraft
import de.birneklub.drop.core.domain.BeanDraft.Field
import de.birneklub.drop.core.model.GeoPoint
import de.birneklub.drop.core.model.Process
import de.birneklub.drop.core.model.PurchaseChannel
import de.birneklub.drop.core.sync.DropsJson

val Cities = linkedMapOf(
    "Hamburg" to GeoPoint(53.55, 9.99), "Berlin" to GeoPoint(52.52, 13.40), "Leipzig" to GeoPoint(51.34, 12.37), "München" to GeoPoint(48.14, 11.58),
    "Köln" to GeoPoint(50.94, 6.96), "Frankfurt" to GeoPoint(50.11, 8.68), "Kopenhagen" to GeoPoint(55.68, 12.57), "Amsterdam" to GeoPoint(52.37, 4.90),
    "Wien" to GeoPoint(48.21, 16.37), "Zürich" to GeoPoint(47.37, 8.54),
)

/** Chip label for "not on the bag / don't remember"; every choice on this screen may stay unknown. */
private const val UNKNOWN = "Weiß nicht"

private val RoastLevels = listOf("Hell", "Hell-mittel", "Mittel", "Mittel-dunkel", "Dunkel")
private val Processes = listOf(Process.WASHED, Process.NATURAL, Process.HONEY, Process.ANAEROBIC)
private val Channels = listOf(PurchaseChannel.IN_STORE, PurchaseChannel.ONLINE, PurchaseChannel.TRAVEL)

private val DraftSaver = Saver<BeanDraft, String>(
    save = { DropsJson.encodeToString(BeanDraft.serializer(), it) },
    restore = { DropsJson.decodeFromString(BeanDraft.serializer(), it) },
)

/** Options for a chip row: unknown first, then the list, plus a value typed or imported elsewhere. */
private fun options(list: List<String>, current: String) = listOf(UNKNOWN) + list + listOfNotNull(current.takeIf { it.isNotBlank() && it !in list })

/** "Neue Bohne" when [beanId] is null, else "Bohne bearbeiten" with the same form. */
@Composable
fun AddBeanScreen(vm: DropsViewModel, nav: NavController, beanId: String? = null) {
    val lib by vm.library.collectAsStateWithLifecycle()
    val base = beanId?.let { lib.bean(it) }
    when {
        beanId == null -> BeanForm(vm, nav, null)
        base != null -> BeanForm(vm, nav, base)
        lib.loaded -> MissingBean(nav)
    }
}

@Composable
private fun BeanForm(vm: DropsViewModel, nav: NavController, base: de.birneklub.drop.core.model.Bean?) {
    val c = Drops.colors
    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf(base?.let(BeanDraft::of) ?: BeanDraft()) }
    var tried by rememberSaveable { mutableStateOf(false) }
    // Errors show after the first save attempt and disappear as soon as a field is fixed.
    val errors = if (tried) draft.errors() else emptyMap()

    ScreenColumn {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextAction("Abbrechen", { nav.popBackStack() })
        }
        ScreenTitle(if (base == null) "Neue Bohne" else "Bohne bearbeiten")
        Text("Nur der Name ist Pflicht, alles andere ist optional.", style = DropsType.small, color = c.muted)

        FormCard {
            FormField("Name", draft.name, { draft = draft.copy(name = it) }, error = errors[Field.NAME])
            FormField("Rösterei", draft.roaster, { draft = draft.copy(roaster = it) })
            DateField("Röstdatum", draft.roastDate, { draft = draft.copy(roastDate = it) })
            FieldRow {
                FormField("Menge (g)", draft.weight, { draft = draft.copy(weight = it) }, cell, KeyboardType.Number, error = errors[Field.WEIGHT])
                FormField(
                    "Übrig (g)", draft.remaining, { draft = draft.copy(remaining = it) }, cell, KeyboardType.Decimal,
                    placeholder = "volle Tüte", error = errors[Field.REMAINING],
                )
            }
            FormField("Aromen", draft.notes, { draft = draft.copy(notes = it) }, placeholder = "z. B. Beere, Kakao", singleLine = false)
        }

        FormSection("Herkunft") {
            ChoiceRow("Land", options(Countries.names, draft.country), draft.country.ifBlank { UNKNOWN }) { draft = draft.copy(country = if (it == UNKNOWN) "" else it) }
            ChoiceRow("Aufbereitung", listOf(UNKNOWN) + Processes.map(::processLabel), draft.process?.let(::processLabel) ?: UNKNOWN) { label ->
                draft = draft.copy(process = Processes.firstOrNull { processLabel(it) == label })
            }
            ChoiceRow("Röstgrad", options(RoastLevels, draft.roastLevel), draft.roastLevel.ifBlank { UNKNOWN }) { draft = draft.copy(roastLevel = if (it == UNKNOWN) "" else it) }
            FormCard {
                if (draft.country.isNotBlank()) FormField("Region", draft.region, { draft = draft.copy(region = it) }, placeholder = "z. B. Yirgacheffe")
                FieldRow {
                    FormField("Varietät", draft.variety, { draft = draft.copy(variety = it) }, cell, placeholder = "z. B. Bourbon")
                    FormField("Anbauhöhe", draft.altitude, { draft = draft.copy(altitude = it) }, cell, placeholder = "z. B. 1.900 m")
                }
            }
        }

        FormSection("Kauf") {
            ChoiceRow("Wie gekauft", listOf(UNKNOWN) + Channels.map(::channelLabel), draft.channel?.let(::channelLabel) ?: UNKNOWN) { label ->
                draft = draft.copy(channel = Channels.firstOrNull { channelLabel(it) == label })
            }
            // A city only makes sense for a shop you walked into; online orders have no place on the map.
            if (draft.channel != PurchaseChannel.ONLINE) {
                ChoiceRow("Wo gekauft", options(Cities.keys.toList(), draft.city), draft.city.ifBlank { UNKNOWN }) { draft = draft.copy(city = if (it == UNKNOWN) "" else it) }
            }
            FormCard {
                FormField("Shop", draft.shop, { draft = draft.copy(shop = it) }, placeholder = draft.roaster.ifBlank { "wie Rösterei" })
                DateField("Gekauft am", draft.purchasedOn, { draft = draft.copy(purchasedOn = it) })
                FieldRow {
                    FormField("Preis (€)", draft.price, { draft = draft.copy(price = it) }, cell, KeyboardType.Decimal, error = errors[Field.PRICE])
                    FormField("Shop-Link", draft.url, { draft = draft.copy(url = it) }, cell, KeyboardType.Uri, placeholder = "zum Nachkaufen", error = errors[Field.URL])
                }
            }
        }

        if (errors.isNotEmpty()) Text("Bitte die markierten Felder prüfen.", style = DropsType.small, color = c.bad)
        PillButton(if (base == null) "Speichern" else "Änderungen speichern", {
            tried = true
            if (draft.errors().isEmpty()) {
                val bean = draft.toBean(base?.id ?: vm.newId(), base, vm.now()) { Cities[it] }
                if (base == null) {
                    vm.addBean(bean)
                    nav.popBackStack()
                    nav.navigate(Routes.bean(bean.id))
                } else {
                    vm.saveBean(bean, "Änderungen gespeichert")
                    nav.popBackStack()
                }
            }
        }, Modifier.fillMaxWidth(), kind = ButtonKind.Ink, height = 56.dp)
    }
}

/** Shown when a link points to a bean that no longer exists (deleted, or removed on another device). */
@Composable
fun MissingBean(nav: NavController) {
    ScreenColumn {
        TextAction("‹ Zurück", { if (!nav.popBackStack()) nav.navigate(Routes.BEANS) })
        ScreenTitle("Bohne nicht gefunden")
        Text("Diese Bohne gibt es nicht mehr. Vielleicht wurde sie gelöscht oder auf einem anderen Gerät entfernt.", style = DropsType.body, color = Drops.colors.muted)
        PillButton("Zu den Bohnen", { nav.navigate(Routes.BEANS) { popUpTo(Routes.BEANS) { inclusive = true } } }, Modifier.fillMaxWidth(), kind = ButtonKind.Ghost)
    }
}
