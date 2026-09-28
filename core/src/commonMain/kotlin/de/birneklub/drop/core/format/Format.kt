package de.birneklub.drop.core.format

import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * The one place that turns numbers into text for the user: German format with
 * decimal comma and thousands dot, units after a no-break space ("93 °C", "27 s"). Missing
 * or impossible values (null, NaN, infinity) become [MISSING], never "NaN" or
 * "Infinity".
 */
object Format {
    const val MISSING = "–"
    /** No-break space: a number never wraps away from its unit ("93 °C"). */
    const val NBSP = "\u00A0"
    private const val SEP = " · "

    /** 15.0 → "15,0"; 1284.0 with 0 decimals → "1.284". */
    fun number(value: Double?, decimals: Int = 1): String {
        if (value == null || value.isNaN() || value.isInfinite()) return MISSING
        val factor = 10.0.pow(decimals)
        val scaled = (abs(value) * factor).roundToLong()
        val whole = scaled / factor.toLong()
        val frac = scaled % factor.toLong()
        val sign = if (value < 0 && scaled != 0L) "-" else ""
        val fracText = if (decimals > 0) "," + frac.toString().padStart(decimals, '0') else ""
        return sign + group(whole) + fracText
    }

    /** Whole number with thousands dot: 1284 → "1.284". */
    fun integer(value: Int?): String = value?.let { (if (it < 0) "-" else "") + group(abs(it.toLong())) } ?: MISSING

    /** Drops trailing zeros: 14.0 → "14", 14.5 → "14,5", 15.25 → "15,25". For grind settings. */
    fun compact(value: Double?, maxDecimals: Int = 2): String {
        val text = number(value, maxDecimals)
        return if (text.contains(',')) text.trimEnd('0').trimEnd(',') else text
    }

    fun grams(value: Double?, decimals: Int = 1): String = unit(number(value, decimals), "g")
    fun kilograms(value: Double?): String = unit(number(value, 1), "kg")
    fun seconds(value: Double?, decimals: Int = 0): String = unit(number(value, decimals), "s")
    fun seconds(value: Int?): String = unit(integer(value), "s")
    fun celsius(value: Int?): String = unit(integer(value), "°C")
    fun hardness(value: Double?): String = unit(number(value, 0), "°dH")
    fun grind(value: Double?): String = compact(value)

    /** "26–30 s" */
    fun secondsRange(min: Int, max: Int): String = if (min == max) seconds(min) else "${integer(min)}–${integer(max)}${NBSP}s"

    /** "18,0 → 36,0 g" */
    fun doseToYield(dose: Double, yield: Double): String = "${number(dose)}$NBSP→ ${grams(yield)}"

    /** Brew ratio "1:2,0"; without a dose there is no ratio. */
    fun ratio(dose: Double, yield: Double): String = if (dose > 0 && yield >= 0) "1:${number(yield / dose)}" else MISSING

    /** "4,5/5" */
    fun rating(value: Double?): String = if (value == null) MISSING else "${number(value)}/5"

    /** 1650 → "16,50 €" */
    fun euros(cents: Int?): String = cents?.let { unit(number(it / 100.0, 2), "€") } ?: MISSING

    /** "11.09.2026" */
    fun date(value: LocalDate?): String =
        value?.let { "${it.dayOfMonth.toString().padStart(2, '0')}.${it.monthNumber.toString().padStart(2, '0')}.${it.year}" } ?: MISSING

    /** Joins the parts that are present with " · ", or [MISSING] when none are. */
    fun join(vararg parts: String?): String = joinOrNull(*parts) ?: MISSING

    /** Like [join] but null when nothing is present, for lines that should disappear. */
    fun joinOrNull(vararg parts: String?): String? =
        parts.mapNotNull { it?.trim()?.takeIf { p -> p.isNotEmpty() && p != MISSING } }.takeIf { it.isNotEmpty() }?.joinToString(SEP)

    /** Parses what a user typed: "16,5", "16.5" and " 16 " are all fine; anything else is null. */
    fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    private fun unit(number: String, unit: String) = if (number == MISSING) MISSING else "$number$NBSP$unit"

    private fun group(value: Long): String = value.toString().reversed().chunked(3).joinToString(".").reversed()
}
