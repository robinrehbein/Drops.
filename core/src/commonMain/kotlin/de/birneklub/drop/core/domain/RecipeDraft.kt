package de.birneklub.drop.core.domain

import de.birneklub.drop.core.format.Format
import de.birneklub.drop.core.model.Bean
import de.birneklub.drop.core.model.Recipe
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

object Recipes {
    /** The recipe the bean is brewed with: the chosen one, else the first by name. */
    fun selected(bean: Bean, recipes: List<Recipe>): Recipe? {
        val own = recipes.filter { it.beanId == bean.id }
        return own.firstOrNull { it.id == bean.recipeId } ?: own.minByOrNull { it.name.lowercase() }
    }
}

/** Plausible ranges for espresso; the form and the shot screen stay inside them. */
object BrewLimits {
    val dose = 5.0..30.0
    val yield = 5.0..120.0
    val time = 5..120
    val temperature = 80..100
    val rpm = 0..3000
}

/** The recipe form while the user types; numbers stay text until saved. */
@Serializable
data class RecipeDraft(
    val name: String = "Espresso",
    val grind: String = "",
    val rpm: String = "",
    val dose: String = "18",
    val yield: String = "36",
    val timeMin: String = "25",
    val timeMax: String = "30",
    val temperature: String = "93",
    val preinfusion: String = "",
    val notes: String = "",
) {
    enum class Field { NAME, GRIND, RPM, DOSE, YIELD, TIME, TEMPERATURE }

    fun errors(): Map<Field, String> = buildMap {
        if (name.isBlank()) put(Field.NAME, "Bitte einen Namen eintragen.")
        if (grind.isNotBlank() && (Format.parseDecimal(grind)?.let { it < 0 } != false)) put(Field.GRIND, "Mahlgrad als Zahl eintragen, z. B. 14,5.")
        if (rpm.isNotBlank() && rpm.trim().toIntOrNull()?.let { it in BrewLimits.rpm } != true) put(Field.RPM, "Drehzahl zwischen 0 und ${Format.integer(BrewLimits.rpm.last)} eintragen oder leer lassen.")
        if (Format.parseDecimal(dose)?.let { it in BrewLimits.dose } != true) put(Field.DOSE, "Dosis zwischen ${Format.integer(5)} und ${Format.integer(30)} g eintragen.")
        if (Format.parseDecimal(yield)?.let { it in BrewLimits.yield } != true) put(Field.YIELD, "Ertrag zwischen 5 und 120 g eintragen.")
        val min = timeMin.trim().toIntOrNull()
        val max = timeMax.trim().toIntOrNull()
        if (min == null || max == null || min !in BrewLimits.time || max !in BrewLimits.time || min > max) {
            put(Field.TIME, "Zeitfenster in Sekunden eintragen, z. B. 25 bis 30.")
        }
        if (temperature.trim().toIntOrNull()?.let { it in BrewLimits.temperature } != true) put(Field.TEMPERATURE, "Temperatur zwischen 80 und 100 °C eintragen.")
    }

    fun toRecipe(id: String, beanId: String, base: Recipe?, now: Instant): Recipe {
        require(errors().isEmpty()) { "Draft has errors" }
        return Recipe(
            id = id, beanId = beanId, name = name.trim(),
            grindSetting = Format.parseDecimal(grind) ?: 0.0,
            rpm = rpm.trim().toIntOrNull(),
            doseGrams = Format.parseDecimal(dose)!!, yieldGrams = Format.parseDecimal(yield)!!,
            targetTimeMinSec = timeMin.trim().toInt(), targetTimeMaxSec = timeMax.trim().toInt(),
            temperatureC = temperature.trim().toInt(),
            preinfusion = preinfusion.trim(), equipmentNotes = notes.trim(),
            source = base?.source, updatedAt = now,
        )
    }

    companion object {
        private fun num(v: Double) = Format.compact(v, 2).replace(".", "")

        fun of(r: Recipe, name: String = r.name) = RecipeDraft(
            name = name, grind = if (r.grindSetting > 0) num(r.grindSetting) else "", rpm = r.rpm?.toString().orEmpty(),
            dose = num(r.doseGrams), yield = num(r.yieldGrams), timeMin = r.targetTimeMinSec.toString(), timeMax = r.targetTimeMaxSec.toString(),
            temperature = r.temperatureC.toString(), preinfusion = r.preinfusion, notes = r.equipmentNotes,
        )

        /** A new recipe for a grinder with this espresso start setting. */
        fun empty(grindStart: Double?) = RecipeDraft(grind = grindStart?.let(::num).orEmpty())

        /** "Espresso", or "Espresso 2", "Espresso 3" … when taken. */
        fun freeName(name: String, taken: Collection<String>): String =
            if (name !in taken) name else (2..99).map { "$name $it" }.firstOrNull { it !in taken } ?: name

        /** "Espresso" → "Espresso (Kopie)", then "Espresso (Kopie 2)" … */
        fun copyName(name: String, taken: Collection<String>): String {
            val base = "$name (Kopie)"
            if (base !in taken) return base
            return (2..99).map { "$name (Kopie $it)" }.firstOrNull { it !in taken } ?: base
        }
    }
}

/** Rounded to one decimal, the way the shot screen steps. */
fun roundTenth(v: Double): Double = (v * 10).roundToInt() / 10.0
