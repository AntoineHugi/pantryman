package org.pantry.services

import org.pantry.models.Item
import org.pantry.models.ItemUpdateRequest
import org.pantry.repositories.ItemRepository
import java.util.UUID

class ItemService(
    private val repo: ItemRepository
) {
    fun getAll(listId: UUID): List<Item> = repo.getByListId(listId)

    fun getById(listId: UUID, id: UUID): Item? = repo.getById(listId, id)

    fun create(listId: UUID, name: String, quantity: Int): Item {
        val initBool = false
        val newId = UUID.randomUUID()
        return repo.create(newId, listId, name, quantity, initBool)
    }

    fun update(listId: UUID, id: UUID, update: ItemUpdateRequest): Boolean {
        val existing = repo.getById(listId, id) ?: return false

        val newName = update.name ?: existing.name
        val newQuantity = update.quantity ?: existing.quantity
        val newIsChecked = update.isChecked ?: existing.isChecked
        val newIsFavorite = update.isFavorite ?: existing.isFavorite

        if (newName.isBlank()) throw IllegalArgumentException("Name cannot be blank")
        if (newQuantity < 1) throw IllegalArgumentException("Quantity cannot be below 1")

        return repo.update(listId, id, newName, newQuantity, newIsChecked, newIsFavorite)
    }

    fun delete(listId: UUID, id: UUID): Boolean = repo.delete(listId, id)
}
