package org.pantry.services

import org.pantry.models.Item
import kotlin.math.ceil
import kotlin.math.roundToLong

// An ingredient on its way to a grocery list, already scaled to the servings wanted
data class IngredientToAdd(
    val name: String,
    // Null for "salt to taste"
    val amount: Double?,
    val unit: String?,
    val recipeName: String
)

data class ItemChange(val itemId: String, val quantity: Double, val unit: String?, val fromRecipes: String?)

data class NewItem(val name: String, val quantity: Double, val unit: String?, val fromRecipes: String?)

data class MergePlan(val changes: List<ItemChange>, val newItems: List<NewItem>)

/**
 * How recipe ingredients land on a grocery list.
 * - Ingredients with the same name and a compatible unit are added up (g and kg, ml and l, or
 *   the same unit), and so is an unticked item already on the list. Ticked items are in the
 *   cart already, so they are left alone. Different units stay separate lines, except that a
 *   plain item typed by hand ("milk": no unit, quantity 1) takes the recipe's amount ("1 l").
 * - Weights and volumes switch to kg / l from 1000 g / ml. Counts and units like cans round up
 *   (1.5 eggs → 2); spoons and cups keep 2 decimals.
 * - Ingredients without an amount (salt) are added once, and not at all if the list or another
 *   ingredient already has that name.
 * - Each item notes the recipes it was added for ("Bolognese, Lasagna").
 */
object ListMerge {
    private const val MAX_FROM_RECIPES_LENGTH = 500

    fun plan(existing: List<Item>, ingredients: List<IngredientToAdd>): MergePlan {
        val unticked = existing.filter { !it.isChecked }

        // Ingredients with an amount, added up by name and unit family
        val groups = linkedMapOf<Pair<String, String?>, Group>()
        for (ingredient in ingredients.filter { it.amount != null }) {
            val family = family(ingredient.unit)
            val group = groups.getOrPut(key(ingredient.name) to family) { Group(ingredient.name, family) }
            group.base += toBase(ingredient.amount!!, ingredient.unit)
            group.recipes += ingredient.recipeName
        }

        val changes = linkedMapOf<String, ItemChange>()
        val newItems = mutableListOf<NewItem>()

        for ((groupKey, group) in groups) {
            val (name, family) = groupKey
            val match = unticked.firstOrNull { key(it.name) == name && family(it.unit) == family }
            // "milk" typed by hand (no unit, quantity 1) is a reminder: the recipe's "1 l" replaces it
            val reminder = if (family == null) null else unticked.firstOrNull {
                key(it.name) == name && it.unit == null && it.quantity == 1.0 && it.fromRecipes == null && it.id !in changes
            }
            if (match != null) {
                val (quantity, unit) = present(toBase(match.quantity, match.unit) + group.base, family)
                changes[match.id] = ItemChange(match.id, quantity, unit, addRecipes(match.fromRecipes, group.recipes))
            } else if (reminder != null) {
                val (quantity, unit) = present(group.base, family)
                changes[reminder.id] = ItemChange(reminder.id, quantity, unit, addRecipes(null, group.recipes))
            } else {
                val (quantity, unit) = present(group.base, family)
                newItems += NewItem(group.displayName, quantity, unit, addRecipes(null, group.recipes))
            }
        }

        // Ingredients without an amount: just make sure the name is on the list once
        val amountless = ingredients.filter { it.amount == null }.groupBy { key(it.name) }
        for ((name, same) in amountless) {
            val recipes = same.map { it.recipeName }
            val group = groups.entries.firstOrNull { it.key.first == name }?.value
            val match = unticked.firstOrNull { key(it.name) == name }
            when {
                // Already being added with an amount: note the recipe there
                group != null -> {
                    val change = match?.let { changes[it.id] }
                    if (change != null) {
                        changes[change.itemId] = change.copy(fromRecipes = addRecipes(change.fromRecipes, recipes))
                    } else {
                        val index = newItems.indexOfFirst { key(it.name) == name }
                        if (index >= 0) newItems[index] = newItems[index].let { it.copy(fromRecipes = addRecipes(it.fromRecipes, recipes)) }
                    }
                }
                match != null -> {
                    val change = changes[match.id]
                        ?: ItemChange(match.id, match.quantity, match.unit, match.fromRecipes)
                    changes[match.id] = change.copy(fromRecipes = addRecipes(change.fromRecipes, recipes))
                }
                else -> newItems += NewItem(same.first().name, 1.0, null, addRecipes(null, recipes))
            }
        }

        return MergePlan(changes.values.toList(), newItems)
    }

    private class Group(val displayName: String, val family: String?) {
        var base = 0.0
        val recipes = mutableListOf<String>()
    }

    private fun key(name: String) = name.trim().replace(Regex("\\s+"), " ").lowercase()

    // Units that can be added up: g with kg, ml with l, anything else only with itself
    private fun family(unit: String?): String? = when (unit) {
        "g", "kg" -> "g"
        "ml", "l" -> "ml"
        else -> unit
    }

    private fun toBase(amount: Double, unit: String?): Double = when (unit) {
        "kg", "l" -> amount * 1000
        else -> amount
    }

    private fun round(value: Double, decimals: Int): Double {
        val factor = Math.pow(10.0, decimals.toDouble())
        return (value * factor).roundToLong() / factor
    }

    // Rounding first, so 2.0000001 (floating point) doesn't round up to 3
    private fun roundUp(value: Double) = ceil(round(value, 3))

    private fun present(base: Double, family: String?): Pair<Double, String?> = when (family) {
        "g", "ml" -> {
            val whole = roundUp(base)
            val large = if (family == "g") "kg" else "l"
            if (whole >= 1000) round(whole / 1000, 3) to large else whole to family
        }
        "tsp", "tbsp", "cup", "pinch" -> round(base, 2) to family
        // Counts, cans, cloves, bunches...: you can't buy half of one
        else -> roundUp(base) to family
    }

    private fun addRecipes(current: String?, recipes: List<String>): String? {
        val names = (current?.split(", ").orEmpty() + recipes).filter { it.isNotBlank() }.distinct()
        if (names.isEmpty()) return null
        val joined = names.joinToString(", ")
        return if (joined.length <= MAX_FROM_RECIPES_LENGTH) joined else current
    }
}
