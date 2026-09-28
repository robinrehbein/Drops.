package de.birneklub.drop.android.ui.screens

import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import de.birneklub.drop.android.ui.a11y
import de.birneklub.drop.android.ui.rememberLargeFont
import de.birneklub.drop.android.ui.Space
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import de.birneklub.drop.core.format.Format
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import de.birneklub.drop.android.ui.ButtonKind
import de.birneklub.drop.android.ui.Drops
import de.birneklub.drop.android.ui.DropsCard
import de.birneklub.drop.android.ui.DropsIcons
import de.birneklub.drop.android.ui.DropsType
import de.birneklub.drop.android.ui.DropsViewModel
import de.birneklub.drop.android.ui.Eyebrow
import de.birneklub.drop.android.ui.HeroCard
import de.birneklub.drop.android.ui.PillButton
import de.birneklub.drop.android.ui.Routes
import de.birneklub.drop.android.ui.SectionHeader
import de.birneklub.drop.android.ui.Stat
import de.birneklub.drop.android.ui.TaskRow
import de.birneklub.drop.android.ui.label
import de.birneklub.drop.core.domain.FreshnessPhase
import de.birneklub.drop.core.domain.RoastFreshness
import de.birneklub.drop.core.model.Bean
import de.birneklub.drop.core.model.Recipe
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toLocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor
import kotlin.time.Duration.Companion.days

/** Scrollable screen body shared by the tab screens. */
@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = Space.xl, end = Space.xl, top = Space.xxl, bottom = Space.xxxl),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxSize().background(Drops.colors.paper).statusBarsPadding().verticalScroll(rememberScrollState()).padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Space.xl),
        content = content,
    )
}

@Composable
fun ScreenTitle(title: String, trailing: String? = null, trailingColor: Color = Drops.colors.muted) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text(title, style = DropsType.display, color = Drops.colors.ink)
        if (trailing != null) Text(trailing, style = DropsType.caption.copy(fontFamily = de.birneklub.drop.android.ui.MonoFamily), color = trailingColor)
    }
}

/** One wording for the taste scale everywhere (shot, history, chart, today). */
val TasteLabels = listOf("Sauer", "Leicht sauer", "Ausgewogen", "Leicht bitter", "Bitter")

@Composable
/** Marker colours for the taste scale; each reaches 3:1 against cards in light and dark mode. */
fun tasteColor(index: Int): Color = with(Drops.colors) { listOf(bad, accent, ok, ice, ink)[index] }

@Composable
fun TodayScreen(vm: DropsViewModel, nav: NavController) {
    val lib by vm.library.collectAsStateWithLifecycle()
    val now = vm.now()
    val zone = TimeZone.currentSystemDefault()
    val date = DateTimeFormatter.ofPattern("EEEE · d.\u00A0MMMM", Locale.GERMANY).format(now.toJavaInstant().atZone(java.time.ZoneId.systemDefault()))

    ScreenColumn {
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Eyebrow(date)
            Text(greeting(now.toLocalDateTime(zone).hour), style = DropsType.display, color = Drops.colors.ink)
        }

        val bean = lib.hopperBean
        if (bean != null) {
            HopperCard(bean, lib.selectedRecipe(bean), now.toLocalDateTime(zone).date,
                onShot = { nav.navigate(Routes.shot(bean.id)) }, onRecipe = { nav.navigate(Routes.bean(bean.id)) })
        } else if (lib.loaded) {
            HeroCard {
                Eyebrow("Im Trichter", Drops.colors.heroAccent)
                Text(if (lib.beans.isEmpty()) "Noch keine Bohne." else "Keine offene Tüte.", style = DropsType.headline, color = Drops.colors.heroInk)
                if (lib.beans.isEmpty()) PillButton("Bohne anlegen", { nav.navigate(Routes.ADD_BEAN) }, kind = ButtonKind.Hero)
                else PillButton("Bohne auswählen", { nav.navigate(Routes.BEANS) }, kind = ButtonKind.Hero)
            }
        }

        val uri = LocalUriHandler.current
        val low = lib.runningLow().take(2)
        if (low.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                SectionHeader("Geht zur Neige")
                DropsCard(padding = PaddingValues(0.dp)) {
                    low.forEachIndexed { i, (b, left) ->
                        if (i > 0) de.birneklub.drop.android.ui.Divider()
                        val link = vm.reorderLink(b)
                        AdaptiveRow(
                            Modifier.padding(horizontal = Space.l, vertical = Space.m),
                            main = {
                                Text(b.name, style = DropsType.bodyStrong, color = Drops.colors.ink)
                                Text(
                                    Format.join(if (left == 0) "leer" else "noch $left ${if (left == 1) "Shot" else "Shots"}", b.roaster),
                                    style = DropsType.caption, color = Drops.colors.warn,
                                )
                            },
                            side = {
                                PillButton("Nachkaufen", { uri.openUri(vm.open(link, reorder = true)) }, kind = ButtonKind.Ink, height = Space.touch)
                                if (link.sponsored) de.birneklub.drop.android.ui.AdLabel()
                            },
                        )
                    }
                }
            }
        }

        val due = lib.carePlan(now).filter { it.progress >= 0.8 }.take(3)
        Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
            SectionHeader("Fällig", "Alle Pflege", { nav.navigate(Routes.SETUP) })
            if (due.isEmpty()) {
                DropsCard(Modifier.fillMaxWidth()) {
                    Text(if (lib.tasks.isEmpty()) "Noch kein Pflegeplan." else "Alles gepflegt.", style = DropsType.body, color = if (lib.tasks.isEmpty()) Drops.colors.muted else Drops.colors.ok)
                }
            } else {
                DropsCard(padding = PaddingValues(0.dp)) {
                    due.forEachIndexed { i, s ->
                        if (i > 0) de.birneklub.drop.android.ui.Divider()
                        TaskRow(s, compact = true, onBuy = { uri.openUri(vm.open(vm.supplyLink(it), reorder = false)) }, buySponsored = vm.supplyLinksSponsored) { vm.completeTask(s) }
                    }
                }
            }
        }

        val recent = lib.shots.sortedByDescending { it.pulledAt }.take(3)
        if (recent.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                val week = lib.shots.count { now - it.pulledAt < 7.days }
                SectionHeader("Letzte Shots", trailing = "$week diese Woche")
                DropsCard(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
                    recent.forEachIndexed { i, s ->
                        if (i > 0) de.birneklub.drop.android.ui.Divider()
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = Space.touch).clickable(role = Role.Button, onClickLabel = "Bohne öffnen") { nav.navigate(Routes.bean(s.beanId)) }
                                .padding(horizontal = Space.l, vertical = Space.m),
                            horizontalArrangement = Arrangement.spacedBy(Space.m),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(tasteColor(s.taste.ordinal)))
                            Column(Modifier.weight(1f)) {
                                Text("${Format.doseToYield(s.doseGrams, s.yieldGrams)} · ${Format.seconds(s.timeSec)}", style = DropsType.bodyStrong.copy(fontFamily = de.birneklub.drop.android.ui.MonoFamily), color = Drops.colors.ink)
                                Text(Format.join(relativeDay(s.pulledAt, now), lib.bean(s.beanId)?.name, TasteLabels[s.taste.ordinal]), style = DropsType.small, color = Drops.colors.muted)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun greeting(hour: Int) = when (hour) {
    in 5..10 -> "Guten Morgen."
    in 11..16 -> "Guten Tag."
    else -> "Guten Abend."
}

fun relativeDay(at: kotlinx.datetime.Instant, now: kotlinx.datetime.Instant): String {
    val zone = TimeZone.currentSystemDefault()
    val d = at.toLocalDateTime(zone)
    val days = d.date.daysUntilSafe(now.toLocalDateTime(zone).date)
    return when {
        days == 0 -> "Heute %02d:%02d".format(d.hour, d.minute)
        days == 1 -> "Gestern"
        days < 7 -> listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")[d.dayOfWeek.ordinal]
        else -> Format.date(d.date)
    }
}

private fun kotlinx.datetime.LocalDate.daysUntilSafe(other: kotlinx.datetime.LocalDate): Int =
    (other.toEpochDays() - toEpochDays())

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HopperCard(bean: Bean, recipe: Recipe?, today: kotlinx.datetime.LocalDate, onShot: () -> Unit, onRecipe: () -> Unit) {
    val c = Drops.colors
    val large = rememberLargeFont()
    HeroCard {
        val shots = de.birneklub.drop.core.reminders.Reminders.shotsLeft(bean, recipe?.doseGrams ?: 18.0)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Eyebrow("Im Trichter", c.heroAccent)
            Text(
                "${Format.number(bean.remainingGrams, 0)} / ${Format.grams(bean.weightGrams.toDouble(), 0)} · ≈\u00A0$shots\u00A0${if (shots == 1) "Shot" else "Shots"}",
                style = DropsType.caption.copy(fontFamily = de.birneklub.drop.android.ui.MonoFamily), color = c.heroMuted,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
            Text(bean.name, style = DropsType.title.copy(fontSize = 34.sp), color = c.heroInk)
            Format.joinOrNull(bean.roaster, bean.country, bean.process.takeIf { it != de.birneklub.drop.core.model.Process.OTHER }?.let(::processLabel))?.let {
                Text(it, style = DropsType.small.copy(fontSize = 14.sp), color = c.heroMuted)
            }
        }
        bean.roastDate?.let { roast ->
            val f = RoastFreshness.evaluate(roast, today)
            val (label, color) = when (f.phase) {
                FreshnessPhase.RESTING -> "Noch ruhen lassen" to c.heroMuted
                FreshnessPhase.PEAK -> "Beste Zeit" to c.heroInk
                FreshnessPhase.FADING -> "Bald aufbrauchen" to c.heroAccent
            }
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Tag ${f.daysSinceRoast} nach Röstung", style = DropsType.caption, color = c.heroMuted)
                    Text(label, style = DropsType.caption, color = color)
                }
                FreshnessBar(f.daysSinceRoast)
            }
        }
        if (recipe != null) {
            val stats = listOf(
                "Mahlgrad" to (if (recipe.grindSetting > 0) Format.grind(recipe.grindSetting) else Format.MISSING),
                "Verhältnis" to Format.ratio(recipe.doseGrams, recipe.yieldGrams),
                "Zeit" to Format.secondsRange(recipe.targetTimeMinSec, recipe.targetTimeMaxSec),
                "Temperatur" to Format.celsius(recipe.temperatureC),
            )
            // Two by two: four columns do not fit long values or large text on a narrow phone.
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                stats.chunked(if (large) 1 else 2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        row.forEach { (k, v) -> Stat(k, v, c.heroMuted, c.heroInk, Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (large) {
            PillButton("Shot starten", onShot, Modifier.fillMaxWidth(), kind = ButtonKind.Hero, icon = DropsIcons.Timer)
            PillButton("Rezept", onRecipe, Modifier.fillMaxWidth(), kind = ButtonKind.GhostOnHero)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                PillButton("Shot starten", onShot, Modifier.weight(1f), kind = ButtonKind.Hero, icon = DropsIcons.Timer)
                PillButton("Rezept", onRecipe, kind = ButtonKind.GhostOnHero)
            }
        }
    }
}

@Composable
private fun FreshnessBar(days: Int) {
    val c = Drops.colors
    val max = 45f
    Column(Modifier.a11y("Frische: Tag $days nach Röstung, beste Zeit von Tag 7 bis 28"), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(16.dp)) {
            val w = maxWidth
            Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.heroLine))
            Box(Modifier.align(Alignment.CenterStart).offset(x = w * (7 / max)).width(w * (21 / max)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.heroWindow))
            Box(Modifier.align(Alignment.CenterStart).offset(x = w * (days.coerceAtMost(45) / max) - 2.dp).width(4.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(c.heroAccent))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0", "7", "28 Tage", "45").forEach { Text(it, style = DropsType.eyebrow.copy(fontSize = 10.sp, letterSpacing = 0.sp), color = c.heroMuted) }
        }
    }
}

fun processLabel(p: de.birneklub.drop.core.model.Process) = when (p) {
    de.birneklub.drop.core.model.Process.WASHED -> "Gewaschen"
    de.birneklub.drop.core.model.Process.NATURAL -> "Natural"
    de.birneklub.drop.core.model.Process.HONEY -> "Honey"
    de.birneklub.drop.core.model.Process.ANAEROBIC -> "Anaerob"
    de.birneklub.drop.core.model.Process.OTHER -> "Andere"
}

/**
 * Text on the left, actions on the right; with large text the actions move
 * below so the text keeps the full width.
 */
@Composable
fun AdaptiveRow(modifier: Modifier = Modifier, main: @Composable ColumnScope.() -> Unit, side: @Composable ColumnScope.() -> Unit) {
    if (rememberLargeFont()) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Column(content = main)
            side()
        }
    } else {
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            Column(Modifier.weight(1f), content = main)
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Space.xs), content = side)
        }
    }
}
