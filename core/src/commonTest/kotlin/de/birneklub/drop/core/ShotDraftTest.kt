package de.birneklub.drop.core

import de.birneklub.drop.core.domain.ShotDraft
import de.birneklub.drop.core.model.Shot
import de.birneklub.drop.core.model.Taste
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShotDraftTest {
    private val t = Instant.parse("2026-09-25T08:00:00Z")
    private val shot = Shot("s", "b", "r", t, 14.25, 18.0, 36.5, 27.4, 93, Taste.BALANCED, t)

    @Test
    fun roundTripsAndValidates() {
        val draft = ShotDraft.of(shot)
        assertEquals("14,25", draft.grind)
        assertEquals("27,4", draft.time)
        assertEquals(shot, draft.applyTo(shot, t))
        assertTrue(draft.copy(dose = "0").errors().containsKey(ShotDraft.Field.DOSE))
        assertTrue(draft.copy(temperature = "200").errors().containsKey(ShotDraft.Field.TEMPERATURE))
        val edited = draft.copy(yield = "40", taste = Taste.SOUR).applyTo(shot, t)
        assertEquals(40.0, edited.yieldGrams)
        assertEquals(Taste.SOUR, edited.taste)
        assertEquals("b", edited.beanId)
    }
}
