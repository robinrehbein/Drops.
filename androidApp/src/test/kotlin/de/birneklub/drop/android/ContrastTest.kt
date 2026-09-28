package de.birneklub.drop.android

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import de.birneklub.drop.android.ui.DarkDropsColors
import de.birneklub.drop.android.ui.DropsColors
import de.birneklub.drop.android.ui.LightDropsColors
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG AA: 4.5:1 for text, 3:1 for icons, markers and other non-text parts. */
class ContrastTest {
    private fun ratio(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun text(c: DropsColors) = listOf(
        "ink/paper" to (c.ink to c.paper), "ink/surface" to (c.ink to c.surface), "muted/paper" to (c.muted to c.paper),
        "muted/surface" to (c.muted to c.surface), "muted/track" to (c.muted to c.track), "ink/track" to (c.ink to c.track),
        "accent/paper" to (c.accent to c.paper), "accent/surface" to (c.accent to c.surface),
        "ok/surface" to (c.ok to c.surface), "warn/surface" to (c.warn to c.surface), "bad/surface" to (c.bad to c.surface),
        "bad/paper" to (c.bad to c.paper), "ok/okSoft" to (c.ok to c.okSoft), "ink/okSoft" to (c.ink to c.okSoft), "ice/surface" to (c.ice to c.surface),
        "onAccent/accent" to (c.onAccent to c.accent), "onInverse/inverse" to (c.onInverse to c.inverse),
        "heroInk/hero" to (c.heroInk to c.hero), "heroMuted/hero" to (c.heroMuted to c.hero), "heroAccent/hero" to (c.heroAccent to c.hero),
        "onHeroAccent/heroAccent" to (c.onHeroAccent to c.heroAccent), "heroInk/heroLine" to (c.heroInk to c.heroLine),
        "heroInk/statusOpen" to (c.heroInk to c.statusOpen), "heroInk/statusFrozen" to (c.heroInk to c.statusFrozen),
        "heroInk/statusArchived" to (c.heroInk to c.statusArchived), "muted/land" to (c.muted to c.land),
    ) + c.bags.mapIndexed { i, bag -> "heroInk/bag$i" to (c.heroInk to bag) }

    private fun graphics(c: DropsColors) = listOf(
        "accent/warnSoft" to (c.accent to c.warnSoft), "bad/badSoft" to (c.bad to c.badSoft), "ok/okSoft" to (c.ok to c.okSoft),
        "ice/iceSoft" to (c.ice to c.iceSoft), "heroFaint/hero" to (c.heroFaint to c.hero), "heroAccent/heroLine" to (c.heroAccent to c.heroLine),
    ) + listOf(c.bad, c.accent, c.ok, c.ice, c.ink).mapIndexed { i, taste -> "taste$i/surface" to (taste to c.surface) }

    private fun check(name: String, c: DropsColors) {
        val failures = text(c).filter { (_, p) -> ratio(p.first, p.second) < 4.5 }.map { "${it.first} ${"%.2f".format(ratio(it.second.first, it.second.second))}" } +
            graphics(c).filter { (_, p) -> ratio(p.first, p.second) < 3.0 }.map { "${it.first} ${"%.2f".format(ratio(it.second.first, it.second.second))}" }
        assertTrue("$name below WCAG AA: $failures", failures.isEmpty())
    }

    @Test fun light() = check("light", LightDropsColors)
    @Test fun dark() = check("dark", DarkDropsColors)
}
