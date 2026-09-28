package de.birneklub.drop.android.ui.screens

import de.birneklub.drop.android.ui.rememberLargeFont
import de.birneklub.drop.android.ui.Space
import de.birneklub.drop.core.format.Format
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import de.birneklub.drop.android.ui.ButtonKind
import de.birneklub.drop.android.ui.Drops
import de.birneklub.drop.android.ui.DropsCard
import de.birneklub.drop.android.ui.DropsType
import de.birneklub.drop.android.ui.DropsViewModel
import de.birneklub.drop.android.ui.Eyebrow
import de.birneklub.drop.android.ui.PillButton
import de.birneklub.drop.android.ui.Routes
import de.birneklub.drop.android.ui.TextAction
import de.birneklub.drop.core.roaster.RoasterCard

/** Preview of a scanned roaster card before it becomes a bean with a recipe. */
@Composable
fun RoasterCardScreen(vm: DropsViewModel, nav: NavController, payload: String) {
    val card = remember(payload) { RoasterCard.decode(payload) }
    val c = Drops.colors
    ScreenColumn {
        TextAction("‹ Zurück", { if (!nav.popBackStack()) nav.navigate(Routes.TODAY) })
        if (card == null) {
            ScreenTitle("Karte ungültig")
            Text("Dieser Code ist beschädigt oder unvollständig. Frag die Rösterei nach einer neuen Karte oder leg die Bohne selbst an.", style = DropsType.body, color = c.muted)
            PillButton("Bohne selbst anlegen", { nav.navigate(Routes.ADD_BEAN) { popUpTo(Routes.ROASTER_CARD) { inclusive = true } } }, Modifier.fillMaxWidth(), kind = ButtonKind.Ghost)
            return@ScreenColumn
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Eyebrow("Startrezept von ${card.roaster}", c.accent)
            ScreenTitle(card.coffee)
            Format.joinOrNull(card.country, card.region, processLabel(card.process), card.notes.joinToString(", "))?.let {
                Text(it, style = DropsType.body, color = c.muted)
            }
        }
        DropsCard(Modifier.fillMaxWidth()) {
            val facts = listOf(
                "Dosis" to Format.grams(card.doseGrams), "Ertrag" to Format.grams(card.yieldGrams),
                "Zeit" to Format.secondsRange(card.timeMinSec, card.timeMaxSec), "Brühtemperatur" to Format.celsius(card.temperatureC),
            )
            // Two by two (one per line with large text) instead of four squeezed columns.
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                facts.chunked(if (rememberLargeFont()) 1 else 2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) { row.forEach { (k, v) -> Fact(k, v, Modifier.weight(1f)) } }
                }
                Fact("Verhältnis", Format.ratio(card.doseGrams, card.yieldGrams))
            }
            if (card.hint.isNotBlank()) Text(card.hint, style = DropsType.small, color = c.ink, modifier = Modifier.padding(top = Space.m))
        }
        Text("Den Mahlgrad bringt die Karte nicht mit, er hängt von deiner Mühle ab. Drops. startet mit der Espresso-Einstellung deiner Mühle und schlägt nach jedem Shot die nächste vor.", style = DropsType.small, color = c.muted)
        PillButton("Bohne und Rezept übernehmen", {
            val id = vm.addRoasterCard(card)
            nav.navigate(Routes.bean(id)) { popUpTo(Routes.ROASTER_CARD) { inclusive = true } }
        }, Modifier.fillMaxWidth(), kind = ButtonKind.Ink, height = 56.dp)
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = DropsType.caption, color = Drops.colors.muted)
        Text(value, style = DropsType.bodyStrong, color = Drops.colors.ink)
    }
}
