package org.pantry.services

import org.pantry.models.Recipe
import org.pantry.models.RecipeImportResponse
import org.pantry.models.RecipeIngredient
import org.pantry.models.RecipeInput
import org.pantry.models.RecipeSummary
import org.pantry.repositories.RecipeRepository
import java.util.UUID
import kotlin.math.roundToLong

class RecipeService(
    private val repo: RecipeRepository
) {
    fun getAll(): List<RecipeSummary> = repo.getAll()

    fun get(id: UUID): Recipe? = repo.get(id)

    fun create(input: RecipeInput, userId: UUID): Recipe {
        val id = repo.createAll(listOf(validate(input)), userId).single()
        return repo.get(id)!!
    }

    // The route checks that the user created the recipe
    fun update(id: UUID, input: RecipeInput): Recipe {
        repo.update(id, validate(input))
        return repo.get(id)!!
    }

    fun delete(id: UUID): Boolean = repo.delete(id)

    /**
     * Imports a file of recipes (docs/recipe-format.md). Every recipe is validated first and
     * nothing is saved if one is invalid; the error names the recipe. Recipes the user already
     * added under the same name are skipped, so importing the same file twice is harmless.
     */
    fun import(inputs: List<RecipeInput>, userId: UUID): RecipeImportResponse {
        if (inputs.isEmpty()) throw IllegalArgumentException("The file has no recipes")
        if (inputs.size > MAX_IMPORT) throw IllegalArgumentException("At most $MAX_IMPORT recipes per import")

        val recipes = inputs.mapIndexed { index, input ->
            try {
                validate(input)
            } catch (e: IllegalArgumentException) {
                val label = input.name.trim().ifBlank { null }?.let { "\"$it\"" } ?: "number ${index + 1}"
                throw IllegalArgumentException("Recipe $label: ${e.message}")
            }
        }

        val existing = repo.namesCreatedBy(userId).toMutableSet()
        val (skipped, new) = recipes.partition { !existing.add(it.name.lowercase()) }
        val ids = repo.createAll(new, userId)
        return RecipeImportResponse(imported = repo.getSummaries(ids), skipped = skipped.map { it.name })
    }

    // Trims and normalizes the recipe; throws IllegalArgumentException with a readable message
    fun validate(input: RecipeInput): RecipeInput {
        if (input.formatVersion != 1) throw IllegalArgumentException("Unsupported formatVersion ${input.formatVersion}")

        val name = required(input.name, "name", MAX_NAME_LENGTH)
        if (input.servings !in 1..MAX_SERVINGS) throw IllegalArgumentException("servings must be between 1 and $MAX_SERVINGS")

        val tags = input.tags.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
        if (tags.size > MAX_TAGS) throw IllegalArgumentException("At most $MAX_TAGS tags")
        tags.forEach { if (it.length > MAX_TAG_LENGTH) throw IllegalArgumentException("Tag \"$it\" is too long") }

        val sourceUrl = optional(input.sourceUrl, "sourceUrl", MAX_URL_LENGTH)
        if (sourceUrl != null && !(sourceUrl.startsWith("https://") || sourceUrl.startsWith("http://"))) {
            throw IllegalArgumentException("sourceUrl must start with http:// or https://")
        }

        if (input.ingredients.isEmpty()) throw IllegalArgumentException("A recipe needs at least one ingredient")
        if (input.ingredients.size > MAX_INGREDIENTS) throw IllegalArgumentException("At most $MAX_INGREDIENTS ingredients")
        val ingredients = input.ingredients.mapIndexed { index, ingredient ->
            try {
                validateIngredient(ingredient)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Ingredient ${index + 1}: ${e.message}")
            }
        }

        val steps = input.steps.map { it.trim() }.filter { it.isNotEmpty() }
        if (steps.size > MAX_STEPS) throw IllegalArgumentException("At most $MAX_STEPS steps")
        steps.forEachIndexed { index, step ->
            if (step.length > MAX_TEXT_LENGTH) throw IllegalArgumentException("Step ${index + 1} is too long")
        }

        return RecipeInput(
            name = name,
            servings = input.servings,
            description = optional(input.description, "description", MAX_DESCRIPTION_LENGTH),
            prepMinutes = minutes(input.prepMinutes, "prepMinutes"),
            cookMinutes = minutes(input.cookMinutes, "cookMinutes"),
            tags = tags,
            sourceUrl = sourceUrl,
            ingredients = ingredients,
            steps = steps,
            notes = optional(input.notes, "notes", MAX_TEXT_LENGTH)
        )
    }

    private fun validateIngredient(ingredient: RecipeIngredient): RecipeIngredient {
        // Lowercase so "Onion" and "onion" merge on the grocery list
        val name = required(ingredient.name, "name", MAX_INGREDIENT_NAME_LENGTH).lowercase()
        val amount = ingredient.amount?.let {
            if (it.isNaN() || it <= 0 || it > MAX_AMOUNT) throw IllegalArgumentException("amount must be more than 0 and at most $MAX_AMOUNT")
            // The column keeps 3 decimals
            (it * 1000).roundToLong() / 1000.0
        }
        val unit = ingredient.unit?.trim()?.lowercase()?.ifEmpty { null }
        if (unit != null && unit !in UNITS) {
            throw IllegalArgumentException("Unknown unit \"$unit\" (use one of ${UNITS.joinToString(", ")}, or null for a count)")
        }
        return RecipeIngredient(
            name = name,
            amount = amount,
            unit = unit,
            note = optional(ingredient.note, "note", MAX_NOTE_LENGTH),
            section = optional(ingredient.section, "section", MAX_SECTION_LENGTH),
            optional = ingredient.optional
        )
    }

    private fun required(value: String, field: String, maxLength: Int): String {
        val trimmed = value.trim().replace(Regex("\\s+"), " ")
        if (trimmed.isEmpty()) throw IllegalArgumentException("$field cannot be blank")
        if (trimmed.length > maxLength) throw IllegalArgumentException("$field is too long (at most $maxLength characters)")
        return trimmed
    }

    private fun optional(value: String?, field: String, maxLength: Int): String? {
        val trimmed = value?.trim()?.ifEmpty { null } ?: return null
        if (trimmed.length > maxLength) throw IllegalArgumentException("$field is too long (at most $maxLength characters)")
        return trimmed
    }

    private fun minutes(value: Int?, field: String): Int? {
        if (value != null && value !in 0..MAX_MINUTES) throw IllegalArgumentException("$field must be between 0 and $MAX_MINUTES")
        return value
    }

    companion object {
        // Keep in step with docs/recipe-format.md and the frontend (utils/recipeText.ts)
        val UNITS = listOf("g", "kg", "ml", "l", "tsp", "tbsp", "cup", "pinch", "clove", "can", "bunch", "slice", "pack")

        const val MAX_IMPORT = 200
        const val MAX_NAME_LENGTH = 200
        const val MAX_SERVINGS = 100
        const val MAX_TAGS = 20
        const val MAX_TAG_LENGTH = 50
        const val MAX_URL_LENGTH = 2000
        const val MAX_INGREDIENTS = 100
        const val MAX_INGREDIENT_NAME_LENGTH = 100
        const val MAX_NOTE_LENGTH = 255
        const val MAX_SECTION_LENGTH = 100
        const val MAX_AMOUNT = 100_000.0
        const val MAX_STEPS = 100
        const val MAX_DESCRIPTION_LENGTH = 1000
        const val MAX_TEXT_LENGTH = 5000
        const val MAX_MINUTES = 7 * 24 * 60
    }
}
