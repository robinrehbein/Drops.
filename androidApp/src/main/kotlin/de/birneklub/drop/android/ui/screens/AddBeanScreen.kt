package de.birneklub.drop.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import de.birneklub.drop.android.ui.ButtonKind
import de.birneklub.drop.android.ui.Chip
import de.birneklub.drop.android.ui.Drops
import de.birneklub.drop.android.ui.DropsType
import de.birneklub.drop.android.ui.DropsViewModel
import de.birneklub.drop.android.ui.Eyebrow
import de.birneklub.drop.android.ui.PillButton
import de.birneklub.drop.android.ui.Routes
import de.birneklub.drop.android.ui.TextAction
import de.birneklub.drop.android.ui.a11y
import de.birneklub.drop.core.model.Bean
import de.birneklub.drop.core.model.GeoPoint
import de.birneklub.drop.core.model.Process
import de.birneklub.drop.core.model.Purchase
import de.birneklub.drop.core.model.PurchaseChannel
import kotlinx.datetime.LocalDate

/** Approximate coordinates for pins on the map; exact farm locations are rarely known. */
val CoffeeCountries = linkedMapOf(
    "Äthiopien" to GeoPoint(7.0, 38.7), "Kenia" to GeoPoint(-0.4, 37.0), "Ruanda" to GeoPoint(-2.3, 29.5), "Burundi" to GeoPoint(-3.0, 29.9),
    "Kolumbien" to GeoPoint(2.0, -75.5), "Brasilien" to GeoPoint(-19.0, -46.5), "Guatemala" to GeoPoint(15.0, -91.0), "Costa Rica" to GeoPoint(9.7, -84.0),
    "Honduras" to GeoPoint(14.5, -88.0), "El Salvador" to GeoPoint(13.8, -89.0), "Panama" to GeoPoint(8.8, -82.4), "Peru" to GeoPoint(-6.0, -78.0),
    "Mexiko" to GeoPoint(16.5, -92.5), "Indonesien" to GeoPoint(3.5, 98.5), "Jemen" to GeoPoint(15.3, 44.0), "Indien" to GeoPoint(12.5, 75.5),
)

val Cities = linkedMapOf(
    "Hamburg" to GeoPoint(53.55, 9.99), "Berlin" to GeoPoint(52.52, 13.40), "Leipzig" to GeoPoint(51.34, 12.37), "München" to GeoPoint(48.14, 11.58),
    "Köln" to GeoPoint(50.94, 6.96), "Frankfurt" to GeoPoint(50.11, 8.68), "Kopenhagen" to GeoPoint(55.68, 12.57), "Amsterdam" to GeoPoint(52.37, 4.90),
    "Wien" to GeoPoint(48.21, 16.37), "Zürich" to GeoPoint(47.37, 8.54),
)

/** Chip label for "not on the bag / don't remember"; every choice on this screen may stay unknown. */
private const val UNKNOWN = "Weiß nicht"

@Composable
fun AddBeanScreen(vm: DropsViewModel, nav: NavController) {
    val c = Drops.colors
    var name by rememberSaveable { mutableStateOf("") }
    var roaster by rememberSaveable { mutableStateOf("") }
    var country by rememberSaveable { mutableStateOf(UNKNOWN) }
    var region by rememberSaveable { mutableStateOf("") }
    var roastDate by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("250") }
    var price by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var shopUrl by rememberSaveable { mutableStateOf("") }
    var process by rememberSaveable { mutableStateOf(UNKNOWN) }
    var channel by rememberSaveable { mutableStateOf(UNKNOWN) }
    var city by rememberSaveable { mutableStateOf(UNKNOWN) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    ScreenColumn {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextAction("Abbrechen", { nav.popBackStack() })
        }
        ScreenTitle("Neue Bohne")

        FormCard {
            FormField("Name", name, { name = it })
            FormField("Rösterei (optional)", roaster, { roaster = it })
            FieldRow {
                FormField("Röstdatum", roastDate, { roastDate = it }, Modifier.weight(1f).fillMaxHeight(), KeyboardType.Number, placeholder = "JJJJ-MM-TT")
                FormField("Menge (g)", weight, { weight = it }, Modifier.weight(1f).fillMaxHeight(), KeyboardType.Number)
            }
            FormField("Aromen (optional)", notes, { notes = it }, placeholder = "mit Komma, z. B. Beere, Kakao")
        }

        Section("Herkunft") {
            ChoiceRow("Land", listOf(UNKNOWN) + CoffeeCountries.keys, country) { country = it }
            ChoiceRow("Aufbereitung", listOf(UNKNOWN, "Washed", "Natural", "Honey", "Anaerob"), process) { process = it }
            if (country != UNKNOWN) FormCard { FormField("Region (optional)", region, { region = it }, placeholder = "z. B. Yirgacheffe") }
        }

        Section("Kauf") {
            ChoiceRow("Wie gekauft", listOf(UNKNOWN, "vor Ort", "online", "auf Reisen"), channel) { channel = it }
            // A city only makes sense for a shop you walked into; online orders have no place on the map.
            if (channel != "online") ChoiceRow("Wo gekauft", listOf(UNKNOWN) + Cities.keys, city) { city = it }
            FormCard {
                FieldRow {
                    FormField("Preis (€)", price, { price = it }, Modifier.weight(1f).fillMaxHeight(), KeyboardType.Decimal, placeholder = "optional")
                    FormField("Shop-Link", shopUrl, { shopUrl = it }, Modifier.weight(1f).fillMaxHeight(), KeyboardType.Uri, placeholder = "zum Nachkaufen")
                }
            }
        }

        error?.let { Text(it, style = DropsType.small, color = c.bad) }
        PillButton("Speichern", {
            val date = roastDate.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }
            when {
                name.isBlank() -> error = "Bitte einen Namen eintragen."
                roastDate.isNotBlank() && date == null -> error = "Röstdatum bitte als JJJJ-MM-TT eingeben, z. B. 2026-09-22."
                else -> {
                    val grams = weight.toIntOrNull() ?: 250
                    val knownCountry = country.takeIf { it != UNKNOWN }
                    val knownCity = city.takeIf { it != UNKNOWN && channel != "online" }
                    val priceCents = price.replace(',', '.').toDoubleOrNull()?.let { (it * 100).toInt() }
                    val url = shopUrl.trim().takeIf { it.startsWith("https://") || it.startsWith("http://") }
                    val purchaseChannel = when (channel) { "online" -> PurchaseChannel.ONLINE; "auf Reisen" -> PurchaseChannel.TRAVEL; UNKNOWN -> null; else -> PurchaseChannel.IN_STORE }
                    val purchase = if (purchaseChannel == null && knownCity == null && priceCents == null && url == null) null else Purchase(
                        roaster.trim().ifBlank { "Rösterei" }, knownCity.orEmpty(), knownCity?.let { Cities[it] },
                        purchaseChannel ?: PurchaseChannel.IN_STORE, priceCents, url = url,
                    )
                    val bean = Bean(
                        id = vm.newId(), name = name.trim(), roaster = roaster.trim(), country = knownCountry.orEmpty(),
                        region = if (knownCountry == null) "" else region.trim(), origin = knownCountry?.let { CoffeeCountries[it] },
                        process = when (process) { "Washed" -> Process.WASHED; "Natural" -> Process.NATURAL; "Honey" -> Process.HONEY; "Anaerob" -> Process.ANAEROBIC; else -> Process.OTHER },
                        roastDate = date, weightGrams = grams, remainingGrams = grams.toDouble(),
                        tastingNotes = notes.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                        purchase = purchase,
                        updatedAt = vm.now(),
                    )
                    vm.addBean(bean)
                    nav.popBackStack()
                    nav.navigate(Routes.bean(bean.id))
                }
            }
        }, Modifier.fillMaxWidth(), kind = ButtonKind.Ink, height = 56.dp)
        Text("Nur der Name ist Pflicht. Alles andere kannst du später auf der Bohne ergänzen.", style = DropsType.small, color = c.muted)
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Eyebrow(title)
        content()
    }
}

@Composable
private fun FormCard(content: @Composable ColumnScope.() -> Unit) {
    val c = Drops.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.dp, c.line, RoundedCornerShape(18.dp)).background(c.line),
        verticalArrangement = Arrangement.spacedBy(1.dp), content = content,
    )
}

/** Side-by-side fields share one height, so the divider background never shows below the shorter cell. */
@Composable
private fun FieldRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(1.dp), content = content)
}

@Composable
private fun FormField(
    label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth(),
    type: KeyboardType = KeyboardType.Text, placeholder: String? = null,
) {
    val c = Drops.colors
    Column(modifier.background(c.surface).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(label, style = DropsType.caption, color = c.muted)
        BasicTextField(
            value, onChange, singleLine = true, textStyle = DropsType.body.copy(color = c.ink), cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(keyboardType = type), modifier = Modifier.fillMaxWidth().padding(top = 2.dp).a11y(label),
            decorationBox = { field ->
                Box {
                    if (value.isEmpty() && placeholder != null) Text(placeholder, style = DropsType.body, color = c.muted.copy(alpha = 0.6f), maxLines = 1)
                    field()
                }
            },
        )
    }
}

@Composable
private fun ChoiceRow(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = DropsType.small, color = Drops.colors.muted)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { Chip(it, it == selected) { onSelect(it) } }
        }
    }
}
