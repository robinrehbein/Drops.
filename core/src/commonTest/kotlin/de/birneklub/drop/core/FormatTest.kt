package de.birneklub.drop.core

import de.birneklub.drop.core.format.Format
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormatTest {
    @Test
    fun numbersUseGermanSeparators() {
        assertEquals("15,0", Format.number(15.0))
        assertEquals("1.284", Format.number(1284.0, 0))
        assertEquals("1.234.567,89", Format.number(1234567.891, 2))
        assertEquals("-0,5", Format.number(-0.5))
        assertEquals("0,0", Format.number(-0.01))
        assertEquals("1.284", Format.integer(1284))
    }

    @Test
    fun impossibleValuesBecomeADash() {
        assertEquals("–", Format.number(null))
        assertEquals("–", Format.number(Double.NaN))
        assertEquals("–", Format.number(Double.POSITIVE_INFINITY))
        assertEquals("–", Format.ratio(0.0, 36.0))
        assertEquals("–", Format.grams(null))
        assertEquals("–", Format.rating(null))
    }

    @Test
    fun unitsAndCompositeValues() {
        assertEquals("18,0 → 36,0 g", Format.doseToYield(18.0, 36.0))
        assertEquals("1:2,0", Format.ratio(18.0, 36.0))
        assertEquals("1:2,1", Format.ratio(18.0, 38.0))
        assertEquals("4,5/5", Format.rating(4.5))
        assertEquals("27 s", Format.seconds(27.4))
        assertEquals("27,4 s", Format.seconds(27.4, 1))
        assertEquals("26–30 s", Format.secondsRange(26, 30))
        assertEquals("93 °C", Format.celsius(93))
        assertEquals("16,50 €", Format.euros(1650))
        assertEquals("21,6 kg", Format.kilograms(21.6))
        assertEquals("11.09.2026", Format.date(LocalDate(2026, 9, 11)))
    }

    @Test
    fun grindDropsTrailingZeros() {
        assertEquals("14", Format.grind(14.0))
        assertEquals("14,5", Format.grind(14.5))
        assertEquals("15,25", Format.grind(15.25))
        assertEquals("1.200", Format.compact(1200.0))
    }

    @Test
    fun joinSkipsMissingParts() {
        assertEquals("Äthiopien · Guji", Format.join("Äthiopien", "", null, "Guji", "–"))
        assertEquals("–", Format.join("", null))
        assertNull(Format.joinOrNull(" ", null))
    }

    @Test
    fun parsesBothDecimalMarks() {
        assertEquals(16.5, Format.parseDecimal("16,5"))
        assertEquals(16.5, Format.parseDecimal(" 16.5 "))
        assertNull(Format.parseDecimal("abc"))
        assertNull(Format.parseDecimal("Infinity"))
    }
}
