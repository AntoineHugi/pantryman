package org.pantry.services

import org.pantry.repositories.ItemRepository
import org.pantry.repositories.RecipeRepository
import java.util.UUID

// Recipes → grocery list. The merging rules are in ListMerge.
class RecipeListService(
    private val itemRepo: ItemRepository,
    private val recipeRepo: RecipeRepository
) {
    /**
     * Adds the recipe's ingredients to the list, scaled from the recipe's servings to [servings].
     * [ingredientIndexes] picks ingredients by position (e.g. without the ones already at home);
     * by default every ingredient that isn't optional. The route checks access to the list.
     * Null if there is no such recipe.
     */
    fun addRecipe(listId: UUID, recipeId: UUID, servings: Int?, ingredientIndexes: List<Int>?): MergePlan? {
        val recipe = recipeRepo.get(recipeId) ?: return null
        val wanted = servings ?: recipe.servings
        if (wanted !in 1..RecipeService.MAX_SERVINGS) {
            throw IllegalArgumentException("servings must be between 1 and ${RecipeService.MAX_SERVINGS}")
        }
        val indexes = ingredientIndexes?.toSet() ?: recipe.ingredients.indices.filter { !recipe.ingredients[it].optional }.toSet()
        if (indexes.any { it !in recipe.ingredients.indices }) throw IllegalArgumentException("Unknown ingredient")
        if (indexes.isEmpty()) throw IllegalArgumentException("Choose at least one ingredient")

        val scale = wanted.toDouble() / recipe.servings
        val ingredients = recipe.ingredients.filterIndexed { index, _ -> index in indexes }.map {
            IngredientToAdd(it.name, it.amount?.let { amount -> amount * scale }, it.unit, recipe.name)
        }
        return itemRepo.merge(listId) { items -> ListMerge.plan(items, ingredients) }
    }
}
