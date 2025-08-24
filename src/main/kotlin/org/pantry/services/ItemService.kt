package org.pantry.services

import org.pantry.models.Item
import org.pantry.repositories.ItemRepository
import java.util.UUID

class ItemService(
    private val repo: ItemRepository = ItemRepository()
) {
    fun getAll(): List<Item> = repo.getAll()
    fun getById(id: UUID): Item? = repo.getById(id)
    fun create(name: String, quantity: Int, isChecked: Boolean, isFavorite: Boolean): Item = repo.create(name, quantity, isChecked, isFavorite)
    fun update(id: UUID, name: String, quantity: Int, isChecked: Boolean, isFavorite: Boolean): Boolean = repo.update(id, name, quantity, isChecked, isFavorite)
    fun delete(id: UUID): Boolean = repo.delete(id)
}
