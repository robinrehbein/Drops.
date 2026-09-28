package de.birneklub.drop.core

import de.birneklub.drop.core.domain.AccountInput
import de.birneklub.drop.core.domain.AccountInput.Field
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountInputTest {
    @Test
    fun flagsEachProblemAtItsField() {
        assertEquals(setOf(Field.SERVER, Field.EMAIL, Field.PASSWORD), AccountInput.errors("https://", "robin", "kurz").keys)
        assertEquals(setOf(Field.SERVER), AccountInput.errors("http://drops.de", "a@b.de", "12345678").keys)
        assertTrue(AccountInput.errors(" https://drops.example.de ", " a@b.de ", "12345678").isEmpty())
    }
}
