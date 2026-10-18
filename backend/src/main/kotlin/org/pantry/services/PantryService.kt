package org.pantry.services

import org.pantry.models.GroceryList
import org.pantry.models.PantryItem
import org.pantry.repositories.ItemRepository
import org.pantry.repositories.PantryRepository
import org.pantry.repositories.PantryScope
import java.util.UUID
import kotlin.math.roundToLong

/**
 * A household's (or one user's) usual items. Defaults ("always buy": milk) are added to new
 * lists; staples ("always at home": salt) are skipped when recipes go on a list. A shared list
 * uses its household's items, a personal list its owner's.
 */
class PantryService(
    private val repo: PantryRepository,
    private val itemRepo: ItemRepository
) {
    fun getAll(scope: PantryScope): List<PantryItem> = repo.getAll(scope)

    // The route checks the user may use the scope
    fun add(scope: PantryScope, name: String, kind: String, quantity: Double, unit: String?): PantryItem {
        val trimmed = name.trim().replace(Regex("\\s+"), " ")
        if (trimmed.isEmpty()) throw IllegalArgumentException("Name cannot be blank")
        if (trimmed.length > MAX_NAME_LENGTH) throw IllegalArgumentException("Name is too long")
        if (kind !in KINDS) throw IllegalArgumentException("kind must be default or staple")
        if (quantity.isNaN() || quantity <= 0 || quantity > ItemService.MAX_QUANTITY) throw IllegalArgumentException("Quantity must be more than 0")
        val validUnit = unit?.trim()?.lowercase()?.ifEmpty { null }
        if (validUnit != null && validUnit !in RecipeService.UNITS) throw IllegalArgumentException("Unknown unit \"$validUnit\"")
        return repo.upsert(scope, trimmed, kind, (quantity * 1000).roundToLong() / 1000.0, validUnit)
    }

    // The item and the scope it belongs to, for the route's access check
    fun get(id: UUID): Pair<PantryScope, PantryItem>? = repo.get(id)

    fun delete(id: UUID): Boolean = repo.delete(id)

    fun scopeOf(list: GroceryList): PantryScope =
        list.householdId?.let { PantryScope.Household(UUID.fromString(it)) } ?: PantryScope.Personal(UUID.fromString(list.userId))

    // Lowercase names, to compare with ingredient names
    fun stapleNames(scope: PantryScope): Set<String> =
        repo.getAll(scope).filter { it.kind == STAPLE }.map { normalize(it.name) }.toSet()

    fun defaults(scope: PantryScope): List<PantryItem> = repo.getAll(scope).filter { it.kind == DEFAULT }

    /**
     * Puts defaults on a new list: [ids] picks some (the create-list checklist); null means all of
     * them, for app versions that don't show the checklist. IDs that aren't defaults of the list's
     * scope are ignored.
     */
    fun addDefaults(listId: UUID, scope: PantryScope, ids: List<String>?) {
        val chosen = defaults(scope).filter { ids == null || it.id in ids }
        if (chosen.isEmpty()) return
        itemRepo.merge(listId) { items ->
            // A plain default ("milk") is just a reminder: skip it when a recipe already put milk on the list
            val onList = items.filter { !it.isChecked }.map { normalize(it.name) }.toSet()
            val wanted = chosen.filter { it.unit != null || normalize(it.name) !in onList }
            ListMerge.plan(items, wanted.map { IngredientToAdd(it.name, it.quantity, it.unit, recipeName = "") })
        }
    }

    companion object {
        const val DEFAULT = "default"
        const val STAPLE = "staple"
        val KINDS = listOf(DEFAULT, STAPLE)
        const val MAX_NAME_LENGTH = 100

        fun normalize(name: String) = name.trim().replace(Regex("\\s+"), " ").lowercase()
    }
}
