package org.pantry.services

import org.pantry.models.Item
import org.pantry.repositories.ItemRepository
import java.util.UUID

class ItemService(
    private val repo: ItemRepository
) {
    fun getAll(id: UUID): List<Item> = repo.getAll(id)
    fun getById(id: UUID): Item? = repo.getById(id)
    fun create(listId: String, name: String, quantity: Int): Item = repo.create(listId, name, quantity)
    fun update(id: UUID, name: String, quantity: Int, isChecked: Boolean, isFavorite: Boolean): Boolean = repo.update(id, name, quantity, isChecked, isFavorite)
    fun delete(id: UUID): Boolean = repo.delete(id)
}
