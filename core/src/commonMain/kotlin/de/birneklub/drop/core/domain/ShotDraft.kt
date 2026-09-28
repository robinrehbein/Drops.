package de.birneklub.drop.core.domain

import de.birneklub.drop.core.format.Format
import de.birneklub.drop.core.model.Shot
import de.birneklub.drop.core.model.Taste
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/** Correcting a logged shot: the numbers stay text until saved. */
@Serializable
data class ShotDraft(
    val grind: String,
    val dose: String,
    val yield: String,
    val time: String,
    val temperature: String,
    val taste: Taste,
) {
    enum class Field { GRIND, DOSE, YIELD, TIME, TEMPERATURE }

    fun errors(): Map<Field, String> = buildMap {
        if (Format.parseDecimal(grind)?.let { it >= 0 } != true) put(Field.GRIND, "Mahlgrad als Zahl eintragen, z. B. 14,5.")
        if (Format.parseDecimal(dose)?.let { it in BrewLimits.dose } != true) put(Field.DOSE, "Dosis zwischen 5 und 30 g eintragen.")
        if (Format.parseDecimal(yield)?.let { it in BrewLimits.yield } != true) put(Field.YIELD, "Ertrag zwischen 5 und 120 g eintragen.")
        if (Format.parseDecimal(time)?.let { it in 0.0..BrewLimits.time.last.toDouble() } != true) put(Field.TIME, "Zeit zwischen 0 und 120 s eintragen.")
        if (temperature.trim().toIntOrNull()?.let { it in BrewLimits.temperature } != true) put(Field.TEMPERATURE, "Temperatur zwischen 80 und 100 °C eintragen.")
    }

    fun applyTo(shot: Shot, now: Instant): Shot {
        require(errors().isEmpty()) { "Draft has errors" }
        return shot.copy(
            grindSetting = Format.parseDecimal(grind)!!, doseGrams = Format.parseDecimal(dose)!!, yieldGrams = Format.parseDecimal(yield)!!,
            timeSec = Format.parseDecimal(time)!!, temperatureC = temperature.trim().toInt(), taste = taste, updatedAt = now,
        )
    }

    companion object {
        private fun num(v: Double) = Format.compact(v, 2).replace(".", "")
        fun of(s: Shot) = ShotDraft(num(s.grindSetting), num(s.doseGrams), num(s.yieldGrams), num(s.timeSec), s.temperatureC.toString(), s.taste)
    }
}
