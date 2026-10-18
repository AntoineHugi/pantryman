package org.pantry.services

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.pantry.models.RecipeIngredient
import org.pantry.models.RecipeInput
import org.pantry.repositories.RecipeRepository

class RecipeServiceTest {

    private val service = RecipeService(mockk<RecipeRepository>())

    private fun recipe(vararg ingredients: RecipeIngredient) = RecipeInput(
        name = "Soup",
        servings = 4,
        ingredients = ingredients.toList().ifEmpty { listOf(RecipeIngredient("water", 1.0, "l")) }
    )

    private fun reason(input: RecipeInput) = assertThrows<IllegalArgumentException> { service.validate(input) }.message!!

    @Test
    fun `trims text, lowercases names, units and tags, and drops blank steps and tags`() {
        val result = service.validate(
            recipe(RecipeIngredient("  Red  Onion ", 2.0, " TBSP ", note = "  ", section = " Base ")).copy(
                name = "  Tomato   Soup ",
                tags = listOf(" Vegan", "vegan", " "),
                steps = listOf(" Chop. ", "", "  "),
                description = "   "
            )
        )

        assertEquals("Tomato Soup", result.name)
        assertEquals(listOf("vegan"), result.tags)
        assertEquals(listOf("Chop."), result.steps)
        assertNull(result.description)
        val ingredient = result.ingredients.single()
        assertEquals("red onion", ingredient.name)
        assertEquals("tbsp", ingredient.unit)
        assertNull(ingredient.note)
        assertEquals("Base", ingredient.section)
    }

    @Test
    fun `rounds amounts to 3 decimals and allows no amount or unit`() {
        val result = service.validate(recipe(RecipeIngredient("flour", 0.33333, "kg"), RecipeIngredient("salt")))

        assertEquals(0.333, result.ingredients[0].amount)
        assertNull(result.ingredients[1].amount)
        assertNull(result.ingredients[1].unit)
    }

    @Test
    fun `rejects invalid recipes with a readable reason`() {
        assertEquals("name cannot be blank", reason(recipe().copy(name = " ")))
        assertEquals("servings must be between 1 and 100", reason(recipe().copy(servings = 0)))
        assertEquals("A recipe needs at least one ingredient", reason(recipe().copy(ingredients = emptyList())))
        assertEquals("Unsupported formatVersion 2", reason(recipe().copy(formatVersion = 2)))
        assertEquals("sourceUrl must start with http:// or https://", reason(recipe().copy(sourceUrl = "javascript:alert(1)")))
        assertEquals("prepMinutes must be between 0 and 10080", reason(recipe().copy(prepMinutes = -5)))
        assertTrue(reason(recipe(RecipeIngredient("flour", 0.0, "g"))).startsWith("Ingredient 1: amount must be more than 0"))
        assertTrue(reason(recipe(RecipeIngredient("water", 1.0, "l"), RecipeIngredient("", 1.0))).startsWith("Ingredient 2: name cannot be blank"))
        assertTrue(reason(recipe(RecipeIngredient("flour", 1.0, "cups"))).startsWith("Ingredient 1: Unknown unit \"cups\""))
    }
}
