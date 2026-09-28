package de.birneklub.drop.core.domain

import de.birneklub.drop.core.catalog.Countries
import de.birneklub.drop.core.format.Format
import de.birneklub.drop.core.model.Bean
import de.birneklub.drop.core.model.GeoPoint
import de.birneklub.drop.core.model.Process
import de.birneklub.drop.core.model.Purchase
import de.birneklub.drop.core.model.PurchaseChannel
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * What the bean form holds while the user types: numbers stay text until saved.
 * The same draft serves "Neue Bohne" and "Bohne bearbeiten"; fields the form
 * does not show (status, rating, hopper, frozen doses) are kept from the bean.
 */
@Serializable
data class BeanDraft(
    val name: String = "",
    val roaster: String = "",
    val country: String = "",
    val region: String = "",
    val process: Process? = null,
    val variety: String = "",
    val altitude: String = "",
    val roastLevel: String = "",
    val roastDate: LocalDate? = null,
    val weight: String = "250",
    /** Empty = same as [weight] (a fresh bag). */
    val remaining: String = "",
    val notes: String = "",
    val channel: PurchaseChannel? = null,
    val shop: String = "",
    val city: String = "",
    val price: String = "",
    val url: String = "",
    val purchasedOn: LocalDate? = null,
) {
    enum class Field { NAME, WEIGHT, REMAINING, PRICE, URL }

    /** Messages per field that say how to fix the input; empty when the draft can be saved. */
    fun errors(): Map<Field, String> = buildMap {
        if (name.isBlank()) put(Field.NAME, "Bitte einen Namen eintragen.")
        val w = weight.trim().toIntOrNull()
        if (w == null || w !in 1..MAX_WEIGHT) put(Field.WEIGHT, "Menge in Gramm zwischen 1 und ${Format.integer(MAX_WEIGHT)} eintragen.")
        if (remaining.isNotBlank()) {
            val r = Format.parseDecimal(remaining)
            if (r == null || r < 0 || (w != null && r > w)) put(Field.REMAINING, "Restmenge zwischen 0 und ${w?.let { Format.integer(it) } ?: "der Menge"} g eintragen.")
        }
        if (price.isNotBlank()) {
            val p = Format.parseDecimal(price)
            if (p == null || p < 0 || p > MAX_PRICE) put(Field.PRICE, "Preis in Euro eintragen, z. B. 16,50.")
        }
        if (url.isNotBlank() && !(url.trim().startsWith("https://") || url.trim().startsWith("http://"))) {
            put(Field.URL, "Link muss mit https:// beginnen.")
        }
    }

    /**
     * The bean to store. [base] is the bean being edited (null for a new one);
     * [cityPoint] places a shop's city on the map.
     */
    fun toBean(id: String, base: Bean?, now: Instant, cityPoint: (String) -> GeoPoint?): Bean {
        require(errors().isEmpty()) { "Draft has errors" }
        val grams = weight.trim().toInt()
        val left = Format.parseDecimal(remaining) ?: if (base != null && base.weightGrams == grams) base.remainingGrams else grams.toDouble()
        val knownCountry = country.trim()
        val originPoint = if (base != null && base.country == knownCountry && base.origin != null) base.origin else Countries.lookup(knownCountry)?.second
        val cityName = city.trim().takeIf { channel != PurchaseChannel.ONLINE }.orEmpty()
        val priceCents = Format.parseDecimal(price)?.let { (it * 100).roundToInt() }
        val link = url.trim().takeIf { it.isNotEmpty() }
        val hasPurchase = channel != null || cityName.isNotEmpty() || priceCents != null || link != null || purchasedOn != null || shop.isNotBlank()
        val purchase = if (!hasPurchase) null else Purchase(
            shopName = shop.trim().ifBlank { roaster.trim() },
            city = cityName,
            location = cityName.takeIf { it.isNotEmpty() }?.let { base?.purchase?.takeIf { p -> p.city == it }?.location ?: cityPoint(it) },
            channel = channel ?: PurchaseChannel.IN_STORE,
            priceCents = priceCents,
            purchasedOn = purchasedOn,
            url = link,
        )
        val fields = (base ?: Bean(id = id, name = "", roaster = "", country = "", updatedAt = now)).copy(
            name = name.trim(),
            roaster = roaster.trim(),
            country = knownCountry,
            region = if (knownCountry.isEmpty()) "" else region.trim(),
            origin = if (knownCountry.isEmpty()) null else originPoint,
            process = process ?: Process.OTHER,
            variety = variety.trim(),
            altitude = altitude.trim(),
            roastLevel = roastLevel.trim(),
            roastDate = roastDate,
            weightGrams = grams,
            remainingGrams = left.coerceIn(0.0, grams.toDouble()),
            tastingNotes = notes.split(',').map { it.trim() }.filter { it.isNotEmpty() },
            purchase = purchase,
            updatedAt = now,
        )
        return fields
    }

    companion object {
        const val MAX_WEIGHT = 5000
        const val MAX_PRICE = 1000.0

        /** The form pre-filled with an existing bean. */
        fun of(bean: Bean): BeanDraft = BeanDraft(
            name = bean.name, roaster = bean.roaster, country = bean.country, region = bean.region,
            process = bean.process.takeIf { it != Process.OTHER }, variety = bean.variety, altitude = bean.altitude,
            roastLevel = bean.roastLevel, roastDate = bean.roastDate, weight = bean.weightGrams.toString(),
            remaining = Format.compact(bean.remainingGrams, 1).replace(".", ""), notes = bean.tastingNotes.joinToString(", "),
            channel = bean.purchase?.channel, shop = bean.purchase?.shopName?.takeIf { it != bean.roaster }.orEmpty(),
            city = bean.purchase?.city.orEmpty(),
            price = bean.purchase?.priceCents?.let { Format.number(it / 100.0, 2).replace(".", "") }.orEmpty(),
            url = bean.purchase?.url.orEmpty(), purchasedOn = bean.purchase?.purchasedOn,
        )
    }
}
