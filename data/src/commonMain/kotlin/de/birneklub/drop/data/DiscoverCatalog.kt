package de.birneklub.drop.data

import de.birneklub.drop.core.domain.FlavorCategory
import de.birneklub.drop.core.domain.Palate
import de.birneklub.drop.core.format.Format
import de.birneklub.drop.core.model.Bean
import de.birneklub.drop.core.model.GeoPoint
import de.birneklub.drop.core.model.Process

/**
 * Example content for the "Entdecken" map layer until a real catalogue of
 * cafés, roasters and beans is connected. Names are fictional.
 */
object DiscoverCatalog {
    data class Place(
        val id: String,
        val name: String,
        val location: GeoPoint,
        val isRoaster: Boolean,
        val servesCoffee: Boolean,
        val distanceMeters: Int,
        val description: String,
        val openNow: Boolean,
        val hours: String,
        val note: String? = null,
    )

    data class Recommendation(
        val id: String,
        val name: String,
        val country: String,
        val region: String,
        val roaster: String,
        val roasterCity: String,
        val roasterLocation: GeoPoint,
        val process: Process,
        val flavors: List<String>,
        val priceCents: Int,
        val grams: Int,
    )

    data class CityGuide(val id: String, val city: String, val location: GeoPoint, val cafes: Int, val roasters: Int)

    /** Example user position (Hamburg-Ottensen) for the "nearby" view. */
    val examplePosition = GeoPoint(53.5545, 9.9345)
    const val EXAMPLE_AREA = "Hamburg-Ottensen"

    val places = listOf(
        Place("siebwerk", "Siebwerk", GeoPoint(53.5531, 9.9265), false, true, 350, "Espressobar · Gaströster wechseln wöchentlich", true, "Geöffnet bis 18:00"),
        Place("kontor17", "Kontor 17", GeoPoint(53.5487, 9.9502), false, true, 1200, "Filter-Bar · V60, Batch Brew, Cupping samstags", true, "Geöffnet bis 17:00"),
        Place("brise", "Bohne & Brise", GeoPoint(53.5642, 9.9588), true, true, 2400, "Café mit eigener Rösterei · Frühstück", false, "Öffnet morgen 08:00"),
        Place("hafen", "Hafenrösterei", GeoPoint(53.5431, 9.9853), true, false, 3100, "Rösterei mit Probierbar", true, "Geöffnet bis 19:00"),
        Place("nord", "Röstwerk Nord", GeoPoint(53.5712, 9.9981), true, false, 4800, "Rösterei · Werksverkauf", false, "Öffnet Samstag 10:00"),
    )

    val recommendations = listOf(
        Recommendation("r1", "Kamundu Peaberry", "Kenia", "Kiambu", "Röstwerk Nord", "Hamburg", GeoPoint(53.5712, 9.9981), Process.WASHED, listOf("Cassis", "Grapefruit"), 1890, 250),
        Recommendation("r2", "Gatukuza", "Burundi", "Kayanza", "Bohnenwerk", "Berlin", GeoPoint(52.52, 13.40), Process.WASHED, listOf("Hibiskus", "Rote Johannisbeere"), 1750, 250),
        Recommendation("r3", "Bensa Shantawene", "Äthiopien", "Sidama", "Nordlys Kaffe", "Kopenhagen", GeoPoint(55.68, 12.57), Process.NATURAL, listOf("Blaubeere", "Kakaonibs"), 1900, 250),
    )

    /**
     * Why a suggestion fits, from the user's own ratings only; null when their
     * ratings say nothing about it (then the card shows no reason at all).
     */
    fun reason(r: Recommendation, beans: List<Bean>): String? {
        val sameCountry = beans.filter { it.country == r.country && (it.rating ?: 0.0) >= 4.0 }.maxByOrNull { it.rating!! }
        if (sameCountry != null) return "Du hast ${sameCountry.name} aus ${r.country} mit ${Format.rating(sameCountry.rating)} bewertet."
        val profile = Palate.profile(beans)
        if (profile.ratedBeans == 0) return null
        val liked = FlavorCategory.entries.filter { (profile.scores[it] ?: 0.0) >= 0.5 }
        val match = liked.firstOrNull { cat -> r.flavors.any { cat.matches(it) } } ?: return null
        return "Passt zu deiner Vorliebe für ${flavorWord(match)}."
    }

    private fun flavorWord(c: FlavorCategory) = when (c) {
        FlavorCategory.FLORAL -> "Florales"
        FlavorCategory.FRUITY -> "Fruchtiges"
        FlavorCategory.SWEET -> "Süßes"
        FlavorCategory.CHOCOLATE -> "Schokoladiges"
    }

    val cityGuides = listOf(
        CityGuide("cph", "Kopenhagen", GeoPoint(55.68, 12.57), 14, 5),
        CityGuide("vie", "Wien", GeoPoint(48.21, 16.37), 11, 3),
        CityGuide("ber", "Berlin", GeoPoint(52.52, 13.40), 23, 9),
        CityGuide("ams", "Amsterdam", GeoPoint(52.37, 4.90), 17, 6),
    )
}
