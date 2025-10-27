package org.pantry.services

import org.pantry.models.GroceryList
import org.pantry.repositories.GroceryListRepository
import java.util.UUID

class GroceryListService(
    private val repo: GroceryListRepository
) {
    fun getAll(): List<GroceryList> = repo.getAll()

    fun getById(id: UUID): GroceryList? = repo.getById(id)

    fun create(name: String): GroceryList {
        val newId = UUID.randomUUID()
        return repo.create(newId, name)
    }

    fun update(id: UUID, newName: String): Boolean {
        val existing = repo.getById(id) ?: return false

        if (newName.isBlank()) throw IllegalArgumentException("Name cannot be blank")

        return repo.update(id, newName)
    }

    fun delete(id: UUID): Boolean = repo.delete(id)
}
