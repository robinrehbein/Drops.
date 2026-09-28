package de.birneklub.drop.android.ui.screens

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import de.birneklub.drop.android.ui.rememberLargeFont
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.mutableStateOf
import de.birneklub.drop.android.ui.Space
import de.birneklub.drop.android.ui.ConfirmDialog
import de.birneklub.drop.core.format.Format
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import de.birneklub.drop.android.ui.ButtonKind
import de.birneklub.drop.android.ui.Chip
import de.birneklub.drop.android.ui.Drops
import de.birneklub.drop.android.ui.DropsCard
import de.birneklub.drop.android.ui.DropsIcons
import de.birneklub.drop.android.ui.DropsType
import de.birneklub.drop.android.ui.DropsViewModel
import de.birneklub.drop.android.ui.Eyebrow
import de.birneklub.drop.android.ui.Icon
import de.birneklub.drop.android.ui.MonoFamily
import de.birneklub.drop.android.ui.PillButton
import de.birneklub.drop.android.ui.Routes
import de.birneklub.drop.android.ui.Segmented
import de.birneklub.drop.android.ui.TextAction
import de.birneklub.drop.android.ui.a11y
import de.birneklub.drop.core.model.Bean
import de.birneklub.drop.core.model.BeanStatus
import de.birneklub.drop.core.model.Recipe
import de.birneklub.drop.core.model.Shot
import de.birneklub.drop.core.model.Process

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BeanDetailScreen(vm: DropsViewModel, nav: NavController, beanId: String) {
    val lib by vm.library.collectAsStateWithLifecycle()
    val bean = lib.bean(beanId)
    val c = Drops.colors
    if (bean == null) {
        if (lib.loaded) MissingBean(nav)
        return
    }
    val recipes = lib.recipesFor(bean.id)
    val recipe = lib.selectedRecipe(bean)
    val shots = lib.shotsFor(bean.id)

    Column(Modifier.fillMaxSize().background(c.paper)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // Header
            Column(Modifier.fillMaxWidth().background(c.hero).statusBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    HeaderButton(DropsIcons.Back, "Zurück") { nav.popBackStack() }
                    HeaderButton(DropsIcons.Edit, "Bohne bearbeiten") { nav.navigate(Routes.editBean(bean.id)) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Format.joinOrNull(bean.roaster, bean.purchase?.city)?.let { Eyebrow(it, c.heroAccent) }
                    Text(bean.name, style = DropsType.display.copy(fontSize = 46.sp, lineHeight = 48.sp), color = c.heroInk)
                    Format.joinOrNull(bean.country, bean.region, bean.altitude)?.let { Text(it, style = DropsType.body, color = c.heroMuted) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s), itemVerticalAlignment = Alignment.CenterVertically) {
                    val (label, color) = when (bean.status) {
                        BeanStatus.OPEN -> (if (bean.inHopper) "Offen · im Trichter" else "Offen") to c.statusOpen
                        BeanStatus.FROZEN -> "Eingefroren · ${bean.frozenDoses} ${if (bean.frozenDoses == 1) "Dose" else "Dosen"}" to c.statusFrozen
                        BeanStatus.ARCHIVED -> "Archiv" to c.statusArchived
                    }
                    Box(Modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(16.dp)).background(color).padding(horizontal = Space.m, vertical = Space.xs), contentAlignment = Alignment.Center) {
                        Text(label, style = DropsType.small, color = c.heroInk)
                    }
                    when {
                        bean.status == BeanStatus.FROZEN -> PillButton("Dose auftauen", { vm.thawDose(bean) }, kind = ButtonKind.GhostOnHero, height = Space.touch)
                        !bean.inHopper && bean.status == BeanStatus.OPEN -> PillButton("In den Trichter", { vm.putInHopper(bean) }, kind = ButtonKind.GhostOnHero, height = Space.touch)
                    }
                }
                if (bean.tastingNotes.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        bean.tastingNotes.forEach {
                            Text(it, style = DropsType.small, color = c.heroInk, modifier = Modifier.border(1.dp, c.heroOutline, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                    }
                }
            }

            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                FactsGrid(
                    listOf(
                        "Aufbereitung" to (if (bean.process == Process.OTHER) Format.MISSING else processLabel(bean.process)),
                        "Röstgrad" to bean.roastLevel.ifBlank { Format.MISSING },
                        "Varietät" to bean.variety.ifBlank { Format.MISSING },
                        "Geröstet am" to Format.date(bean.roastDate),
                        "Menge" to "${Format.grams(bean.remainingGrams, 0)} von ${Format.grams(bean.weightGrams.toDouble(), 0)}",
                        "Preis" to Format.euros(bean.purchase?.priceCents),
                    ),
                )

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Rezepte", style = DropsType.title.copy(fontSize = 28.sp), color = c.ink)
                        TextAction("+ Neu", { nav.navigate(Routes.recipe(bean.id)) })
                    }
                    if (recipe == null) {
                        DropsCard(Modifier.fillMaxWidth()) {
                            Text("Noch kein Rezept. Leg eins an oder brüh einen Shot und übernimm ihn als Rezept.", style = DropsType.body, color = c.muted)
                        }
                    } else {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            recipes.forEach { r -> Chip(r.name, r.id == recipe.id) { vm.selectRecipe(bean, r) } }
                        }
                        RecipeCard(recipe)
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                            TextAction("Bearbeiten", { nav.navigate(Routes.recipe(bean.id, recipe.id)) })
                            TextAction("Kopieren", { nav.navigate(Routes.recipe(bean.id, copyOf = recipe.id)) })
                        }
                    }
                }

                if (shots.size > 1) DialInChart(shots, onHistory = { nav.navigate(Routes.shots(bean.id)) })
                else if (shots.size == 1) de.birneklub.drop.android.ui.SectionHeader("1 Shot", "Verlauf", { nav.navigate(Routes.shots(bean.id)) })

                DropsCard(Modifier.fillMaxWidth()) {
                    Text("Dein Urteil", style = DropsType.small, color = c.muted)
                    Text(
                        if (bean.rating == null) "Noch nicht bewertet" else Format.rating(bean.rating),
                        style = if (bean.rating == null) DropsType.body else DropsType.headline, color = if (bean.rating == null) c.muted else c.ink,
                    )
                    Row(Modifier.padding(top = Space.xs)) {
                        (1..5).forEach { i ->
                            val filled = (bean.rating ?: 0.0) >= i - 0.5
                            val half = bean.rating == i - 0.5
                            Box(
                                Modifier.size(Space.touch).clickable(role = Role.Button, onClickLabel = "$i von 5 Sternen vergeben") { vm.rate(bean, i) }
                                    .a11y("$i Sterne${if (filled && !half) ", vergeben" else if (half) ", halb vergeben" else ""}"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(if (filled) DropsIcons.StarFilled else DropsIcons.Star, null, tint = c.accent, size = 26.dp)
                            }
                        }
                    }
                    Text("Nochmal tippen für einen halben Stern.", style = DropsType.caption, color = c.muted)
                    Text("Wieder kaufen?", style = DropsType.small, color = c.muted, modifier = Modifier.padding(top = Space.l, bottom = Space.s))
                    Segmented(listOf("Ja", "Nein"), when (bean.wouldRebuy) { true -> 0; false -> 1; null -> -1 }, { vm.setRebuy(bean, it == 0) }, Modifier.fillMaxWidth())
                    if (bean.rating != null || bean.wouldRebuy != null) {
                        TextAction("Urteil zurücksetzen", { vm.resetVerdict(bean) }, c.muted)
                    }
                    bean.purchase?.let { p ->
                        Text(
                            "Gekauft bei " + listOf(p.shopName, p.city).filter { it.isNotBlank() }.joinToString(", ") + (p.purchasedOn?.let { " am ${Format.date(it)}" } ?: ""),
                            style = DropsType.small, color = c.muted, modifier = Modifier.padding(top = Space.s),
                        )
                    }
                    if (bean.wouldRebuy != false) {
                        val uri = LocalUriHandler.current
                        val link = vm.reorderLink(bean)
                        if (link.sponsored) Row(Modifier.padding(top = 12.dp)) { de.birneklub.drop.android.ui.AdLabel() }
                        PillButton(
                            if (bean.purchase?.url != null) "Im Shop nachkaufen" else "Online suchen",
                            { uri.openUri(vm.open(link, reorder = true)) },
                            Modifier.fillMaxWidth().padding(top = Space.m), kind = ButtonKind.Ghost, height = Space.touch, icon = DropsIcons.Cart,
                        )
                    }
                }

                BagSection(bean, onStatus = { status, doses -> vm.setStatus(bean, status, doses) }, onDelete = {
                    vm.deleteBean(bean)
                    nav.popBackStack()
                }, shots = shots.size, recipes = recipes.size)
            }
        }

        if (bean.status == BeanStatus.OPEN) {
            Column(Modifier.fillMaxWidth().background(c.paper).navigationBarsPadding()) {
                de.birneklub.drop.android.ui.Divider()
                PillButton(if (recipe != null) "Mit „${recipe.name}“ brühen" else "Shot brühen", { nav.navigate(Routes.shot(bean.id)) }, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), height = 56.dp)
            }
        }
    }
}

/** Label/value pairs in two columns (one with large text), rows of equal height. */
@Composable
private fun FactsGrid(facts: List<Pair<String, String>>) {
    val c = Drops.colors
    val columns = if (rememberLargeFont()) 1 else 2
    Column(Modifier.clip(RoundedCornerShape(16.dp)).border(1.dp, c.line, RoundedCornerShape(16.dp)).background(c.line), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        facts.chunked(columns).forEach { row ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                row.forEach { (k, v) ->
                    Column(Modifier.weight(1f).fillMaxHeight().background(c.surface).padding(Space.m)) {
                        Text(k, style = DropsType.caption, color = c.muted)
                        Text(v, style = DropsType.bodyStrong.copy(fontSize = 14.sp), color = c.ink)
                    }
                }
                if (row.size < columns) Box(Modifier.weight(1f).fillMaxHeight().background(c.surface))
            }
        }
    }
}

@Composable
private fun RecipeCard(r: Recipe) {
    val c = Drops.colors
    val columns = if (rememberLargeFont()) 1 else 2
    DropsCard(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
        val cells = listOf(
            Triple("Mahlgrad", if (r.grindSetting > 0) Format.grind(r.grindSetting) else "noch offen", true),
            Triple("Drehzahl", r.rpm?.let { "${Format.integer(it)}\u00A0U/min" } ?: Format.MISSING, false),
            Triple("Dosis → Ertrag", Format.doseToYield(r.doseGrams, r.yieldGrams), false),
            Triple("Zeit", Format.secondsRange(r.targetTimeMinSec, r.targetTimeMaxSec), false),
            Triple("Brühtemperatur", Format.celsius(r.temperatureC), false),
            Triple("Vorbrühen", r.preinfusion.ifBlank { Format.MISSING }, false),
        )
        Column(Modifier.padding(vertical = Space.s)) {
            cells.chunked(columns).forEach { row ->
                Row(Modifier.padding(horizontal = Space.l, vertical = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    row.forEach { (k, v, accent) ->
                        Column(Modifier.weight(1f)) {
                            Text(k, style = DropsType.caption, color = c.muted)
                            Text(v, style = DropsType.bodyStrong.copy(fontSize = 18.sp), color = if (accent) c.accent else c.ink)
                        }
                    }
                }
            }
        }
        de.birneklub.drop.android.ui.Divider()
        Text(
            Format.join("Verhältnis ${Format.ratio(r.doseGrams, r.yieldGrams)}", r.equipmentNotes),
            style = DropsType.small, color = c.muted, modifier = Modifier.padding(Space.l),
        )
    }
}

@Composable
private fun HeaderButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val c = Drops.colors
    Box(
        Modifier.size(Space.touch).clip(RoundedCornerShape(24.dp)).background(c.heroLine).clickable(role = Role.Button, onClick = onClick).a11y(label),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = c.heroInk, size = 20.dp) }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DialInChart(shots: List<Shot>, onHistory: () -> Unit) {
    val c = Drops.colors
    val colors = (0..4).map { tasteColor(it) }
    val grinds = shots.map { it.grindSetting }
    val min = (grinds.min() * 2).let { kotlin.math.floor(it) / 2 } - 0.5
    val max = (grinds.max() * 2).let { kotlin.math.ceil(it) / 2 } + 0.5
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        de.birneklub.drop.android.ui.SectionHeader("Dial-in · ${shots.size} Shots", "Verlauf", onHistory)
        DropsCard(Modifier.fillMaxWidth()) {
            Row {
                Column(Modifier.height(120.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    listOf(max, (max + min) / 2, min).forEach { Text(Format.grind(it), style = DropsType.caption.copy(fontFamily = MonoFamily, fontSize = 10.sp), color = c.muted) }
                }
                Canvas(Modifier.weight(1f).height(120.dp).padding(start = 8.dp).a11y("Mahlgrad von ${Format.grind(grinds.first())} auf ${Format.grind(grinds.last())}")) {
                    val pad = 8.dp.toPx()
                    fun x(i: Int) = pad + if (shots.size == 1) 0f else i * (size.width - 2 * pad) / (shots.size - 1)
                    fun y(g: Double) = (pad + (max - g) / (max - min) * (size.height - 2 * pad)).toFloat()
                    listOf(max, (max + min) / 2, min).forEach { drawLine(c.track, Offset(0f, y(it)), Offset(size.width, y(it)), 1.dp.toPx()) }
                    val path = Path().apply { shots.forEachIndexed { i, s -> if (i == 0) moveTo(x(i), y(s.grindSetting)) else lineTo(x(i), y(s.grindSetting)) } }
                    drawPath(path, c.ink, style = Stroke(1.5.dp.toPx()))
                    shots.forEachIndexed { i, s ->
                        val last = i == shots.lastIndex
                        drawCircle(colors[s.taste.ordinal], radius = (if (last) 6 else 5).dp.toPx(), center = Offset(x(i), y(s.grindSetting)))
                        if (last) drawCircle(c.ink, radius = 6.dp.toPx(), center = Offset(x(i), y(s.grindSetting)), style = Stroke(2.dp.toPx()))
                    }
                }
            }
            FlowRow(Modifier.padding(top = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.l), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                shots.map { it.taste.ordinal }.distinct().sorted().forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(colors[t]))
                        Text(TasteLabels[t], style = DropsType.caption, color = c.muted)
                    }
                }
            }
        }
    }
}

/** Status of the bag (open, frozen with doses, archived) and deleting it. */
@Composable
private fun BagSection(bean: Bean, shots: Int, recipes: Int, onStatus: (BeanStatus, Int) -> Unit, onDelete: () -> Unit) {
    val c = Drops.colors
    var freezing by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val statuses = listOf(BeanStatus.OPEN, BeanStatus.FROZEN, BeanStatus.ARCHIVED)
    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        de.birneklub.drop.android.ui.SectionHeader("Tüte")
        Segmented(listOf("Offen", "Eingefroren", "Archiv"), statuses.indexOf(bean.status), { i ->
            when (val s = statuses[i]) {
                bean.status -> Unit
                BeanStatus.FROZEN -> freezing = true
                else -> onStatus(s, 0)
            }
        }, Modifier.fillMaxWidth())
        Text(
            when (bean.status) {
                BeanStatus.OPEN -> "In Benutzung. Einfrieren, wenn du einen Teil für später portionierst."
                BeanStatus.FROZEN -> "${bean.frozenDoses} ${if (bean.frozenDoses == 1) "Dose" else "Dosen"} im Gefrierfach. „Dose auftauen“ nimmt eine heraus."
                BeanStatus.ARCHIVED -> "Aufgebraucht. Bewertung, Rezepte und Shots bleiben erhalten."
            },
            style = DropsType.small, color = c.muted,
        )
        PillButton("Bohne löschen", { deleting = true }, Modifier.fillMaxWidth(), kind = ButtonKind.Ghost, height = Space.touch)
    }
    if (freezing) {
        val suggested = (bean.remainingGrams / DOSE_GRAMS).toInt().coerceAtLeast(1)
        var doses by rememberSaveable { mutableIntStateOf(suggested) }
        ConfirmDialog(
            title = "Einfrieren",
            text = "Wie viele Dosen kommen ins Gefrierfach?",
            confirm = "Einfrieren",
            destructive = false,
            onConfirm = { onStatus(BeanStatus.FROZEN, doses) },
            onDismiss = { freezing = false },
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.m), verticalAlignment = Alignment.CenterVertically) {
                PillButton("−", { doses = (doses - 1).coerceAtLeast(1) }, kind = ButtonKind.Ghost, height = Space.touch, modifier = Modifier.a11y("Eine Dose weniger"))
                Text("$doses ${if (doses == 1) "Dose" else "Dosen"}", style = DropsType.numberLarge, color = c.ink, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                PillButton("+", { doses = (doses + 1).coerceAtMost(99) }, kind = ButtonKind.Ghost, height = Space.touch, modifier = Modifier.a11y("Eine Dose mehr"))
            }
        }
    }
    if (deleting) {
        val parts = listOfNotNull(
            recipes.takeIf { it > 0 }?.let { "$it ${if (it == 1) "Rezept" else "Rezepte"}" },
            shots.takeIf { it > 0 }?.let { "$it ${if (it == 1) "Shot" else "Shots"}" },
        )
        ConfirmDialog(
            title = "${bean.name} löschen?",
            text = if (parts.isEmpty()) "Die Bohne wird gelöscht. Du kannst das gleich danach rückgängig machen."
            else "Mit der Bohne werden ${parts.joinToString(" und ")} gelöscht. Du kannst das gleich danach rückgängig machen.",
            confirm = "Löschen",
            onConfirm = onDelete,
            onDismiss = { deleting = false },
        )
    }
}

/** Grams per portion when suggesting how many doses to freeze. */
private const val DOSE_GRAMS = 18.0
