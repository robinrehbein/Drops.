package de.birneklub.drop.core

import de.birneklub.drop.core.domain.RecipeDraft
import de.birneklub.drop.core.domain.Recipes
import de.birneklub.drop.core.model.Bean
import de.birneklub.drop.core.model.Recipe
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecipeTest {
    private val now = Instant.parse("2026-09-25T08:00:00Z")
    private fun recipe(id: String, name: String, bean: String = "b") = Recipe(id, bean, name, 14.5, 1200, 18.0, 36.0, 25, 30, 93, updatedAt = now)

    @Test
    fun selectedRecipeFallsBackToTheFirstByName() {
        val recipes = listOf(recipe("r2", "Morgen"), recipe("r1", "Cortado"), recipe("x", "Anders", bean = "other"))
        val bean = Bean("b", "Guji", "", "", updatedAt = now)
        assertEquals("r1", Recipes.selected(bean, recipes)?.id)
        assertEquals("r2", Recipes.selected(bean.copy(recipeId = "r2"), recipes)?.id)
        assertEquals("r1", Recipes.selected(bean.copy(recipeId = "gone"), recipes)?.id, "a deleted choice falls back")
        assertEquals("r1", Recipes.selected(bean.copy(recipeId = "x"), recipes)?.id, "another bean's recipe never counts")
        assertNull(Recipes.selected(bean, emptyList()))
    }

    @Test
    fun limitsKeepRatiosFinite() {
        val errors = RecipeDraft(dose = "0", yield = "36", timeMin = "30", timeMax = "25", temperature = "120").errors()
        assertEquals(setOf(RecipeDraft.Field.DOSE, RecipeDraft.Field.TIME, RecipeDraft.Field.TEMPERATURE), errors.keys)
        assertTrue(RecipeDraft().errors().isEmpty())
        assertEquals(setOf(RecipeDraft.Field.RPM), RecipeDraft(rpm = "fast").errors().keys)
    }

    @Test
    fun roundTripsThroughTheForm() {
        val r = recipe("r1", "Morgen").copy(yieldGrams = 38.5, preinfusion = "5 s")
        val draft = RecipeDraft.of(r)
        assertEquals("14,5", draft.grind)
        assertEquals("38,5", draft.yield)
        val back = draft.toRecipe("r1", "b", r, now)
        assertEquals(r, back)
    }

    @Test
    fun copiesGetAFreeName() {
        assertEquals("Espresso (Kopie)", RecipeDraft.copyName("Espresso", listOf("Espresso")))
        assertEquals("Espresso 2", RecipeDraft.freeName("Espresso", listOf("Espresso")))
        assertEquals("Espresso (Kopie 2)", RecipeDraft.copyName("Espresso", listOf("Espresso", "Espresso (Kopie)")))
    }
}
