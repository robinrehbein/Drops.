package de.birneklub.drop.core

import de.birneklub.drop.core.domain.BeanDraft
import de.birneklub.drop.core.model.Bean
import de.birneklub.drop.core.model.BeanStatus
import de.birneklub.drop.core.model.GeoPoint
import de.birneklub.drop.core.model.Process
import de.birneklub.drop.core.model.Purchase
import de.birneklub.drop.core.model.PurchaseChannel
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BeanDraftTest {
    private val now = Instant.parse("2026-09-25T08:00:00Z")
    private val hamburg = GeoPoint(53.55, 9.99)
    private fun city(name: String) = if (name == "Hamburg") hamburg else null

    @Test
    fun onlyTheNameIsRequired() {
        assertEquals(setOf(BeanDraft.Field.NAME), BeanDraft(name = " ").errors().keys)
        val bean = BeanDraft(name = "Guji").toBean("b1", null, now, ::city)
        assertEquals(250, bean.weightGrams)
        assertEquals(250.0, bean.remainingGrams)
        assertEquals(Process.OTHER, bean.process)
        assertNull(bean.purchase)
    }

    @Test
    fun rejectsImpossibleNumbersWithAHint() {
        val errors = BeanDraft(name = "X", weight = "0", price = "abc", url = "shop.de").errors()
        assertEquals(setOf(BeanDraft.Field.WEIGHT, BeanDraft.Field.PRICE, BeanDraft.Field.URL), errors.keys)
        assertTrue(errors.getValue(BeanDraft.Field.URL).contains("https://"))
        assertEquals(setOf(BeanDraft.Field.REMAINING), BeanDraft(name = "X", weight = "250", remaining = "300").errors().keys)
    }

    @Test
    fun editingKeepsWhatTheFormDoesNotShow() {
        val base = Bean(
            "b1", "Guji", "Hafen", "Äthiopien", origin = GeoPoint(5.9, 38.9), weightGrams = 250, remainingGrams = 142.0,
            status = BeanStatus.FROZEN, frozenDoses = 4, rating = 4.5, inHopper = true, updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
            purchase = Purchase("Hafen", "Hamburg", hamburg, PurchaseChannel.IN_STORE, 1650),
        )
        val draft = BeanDraft.of(base).copy(variety = "74110", altitude = "2.000 m", roastLevel = "Hell", purchasedOn = LocalDate(2026, 9, 1))
        assertEquals("142", draft.remaining)
        assertEquals("16,50", draft.price)
        val edited = draft.toBean("ignored", base, now, ::city)
        assertEquals("b1", edited.id)
        assertEquals(BeanStatus.FROZEN, edited.status)
        assertEquals(4, edited.frozenDoses)
        assertEquals(4.5, edited.rating)
        assertTrue(edited.inHopper)
        assertEquals(GeoPoint(5.9, 38.9), edited.origin, "exact origin survives when the country is unchanged")
        assertEquals(142.0, edited.remainingGrams)
        assertEquals("74110", edited.variety)
        assertEquals(1650, edited.purchase?.priceCents)
        assertEquals(LocalDate(2026, 9, 1), edited.purchase?.purchasedOn)
        assertEquals(now, edited.updatedAt)
    }

    @Test
    fun remainingFollowsANewBagSize() {
        val base = Bean("b1", "Guji", "", "", weightGrams = 250, remainingGrams = 250.0, updatedAt = now)
        val bigger = BeanDraft.of(base).copy(weight = "1000", remaining = "").toBean("b1", base, now, ::city)
        assertEquals(1000.0, bigger.remainingGrams)
        val used = BeanDraft.of(base).copy(remaining = "80,5").toBean("b1", base, now, ::city)
        assertEquals(80.5, used.remainingGrams)
    }

    @Test
    fun onlineOrdersHaveNoCity() {
        val bean = BeanDraft(name = "X", roaster = "R", channel = PurchaseChannel.ONLINE, city = "Hamburg").toBean("b", null, now, ::city)
        assertEquals("", bean.purchase?.city)
        assertNull(bean.purchase?.location)
        assertEquals("R", bean.purchase?.shopName)
        val local = BeanDraft(name = "X", channel = PurchaseChannel.IN_STORE, city = "Hamburg").toBean("b", null, now, ::city)
        assertEquals(hamburg, local.purchase?.location)
    }

    @Test
    fun countryFromCatalogGetsAMapPoint() {
        val bean = BeanDraft(name = "X", country = "Kenia", region = "Nyeri").toBean("b", null, now, ::city)
        assertEquals(GeoPoint(-0.4, 37.0), bean.origin)
        assertEquals("", BeanDraft(name = "X", region = "Nyeri").toBean("b", null, now, ::city).region)
    }
}
