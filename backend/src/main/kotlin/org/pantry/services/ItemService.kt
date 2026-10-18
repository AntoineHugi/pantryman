package org.pantry.services

import org.pantry.models.Item
import org.pantry.models.ItemUpdateRequest
import org.pantry.repositories.ItemRepository
import java.util.UUID
import kotlin.math.roundToLong

class ItemService(
    private val repo: ItemRepository
) {
    fun getAll(listId: UUID): List<Item> = repo.getByListId(listId)

    fun getById(listId: UUID, id: UUID): Item? = repo.getById(listId, id)

    fun create(listId: UUID, name: String, quantity: Double, unit: String? = null): Item {
        val initBool = false
        val newId = UUID.randomUUID()
        return repo.create(newId, listId, name, validQuantity(quantity), initBool, validUnit(unit))
    }

    fun update(listId: UUID, id: UUID, update: ItemUpdateRequest): Boolean {
        val existing = repo.getById(listId, id) ?: return false

        val newName = update.name ?: existing.name
        val newQuantity = validQuantity(update.quantity ?: existing.quantity)
        // Missing: unchanged. "": no unit.
        val newUnit = if (update.unit == null) existing.unit else validUnit(update.unit)
        val newIsChecked = update.isChecked ?: existing.isChecked

        if (newName.isBlank()) throw IllegalArgumentException("Name cannot be blank")

        return repo.update(listId, id, newName, newQuantity, newIsChecked, newUnit)
    }

    // More than 0 (0.5 l is fine), kept to the 3 decimals the column stores
    private fun validQuantity(quantity: Double): Double {
        if (quantity.isNaN() || quantity <= 0) throw IllegalArgumentException("Quantity must be more than 0")
        if (quantity > MAX_QUANTITY) throw IllegalArgumentException("Quantity is too large")
        return (quantity * 1000).roundToLong() / 1000.0
    }

    private fun validUnit(unit: String?): String? {
        val trimmed = unit?.trim()?.lowercase()?.ifEmpty { null } ?: return null
        if (trimmed !in RecipeService.UNITS) throw IllegalArgumentException("Unknown unit \"$trimmed\"")
        return trimmed
    }

    fun delete(listId: UUID, id: UUID): Boolean = repo.delete(listId, id)

    // "Clear ticked items". The client sends the IDs it showed as ticked rather than the server
    // deleting every ticked item, so an item another member ticks meanwhile isn't deleted unseen.
    // IDs not on the list are skipped.
    fun deleteMany(listId: UUID, itemIds: List<String>): Int {
        if (itemIds.isEmpty()) throw IllegalArgumentException("itemIds cannot be empty")
        if (itemIds.size > MAX_BULK_DELETE) throw IllegalArgumentException("At most $MAX_BULK_DELETE items at a time")
        val ids = itemIds.map {
            try {
                UUID.fromString(it)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid item ID")
            }
        }.toSet()
        return repo.deleteMany(listId, ids)
    }

    companion object {
        const val MAX_BULK_DELETE = 500
        const val MAX_QUANTITY = 100_000.0
    }
}
