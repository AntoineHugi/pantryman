package org.pantry.services

import org.pantry.models.GroceryList
import org.pantry.repositories.GroceryListRepository
import java.util.UUID

class GroceryListService(
    private val repo: GroceryListRepository
) {
    fun getAll(userID: UUID): List<GroceryList> = repo.getAll(userID)

    fun getById(id: UUID, userID: UUID): GroceryList? = repo.getById(id, userID)

    fun create(name: String, userID: UUID ): GroceryList {
        val newId = UUID.randomUUID()
        return repo.create(newId, name, userID)
    }

    fun update(id: UUID, newName: String, userID: UUID): Boolean {
        val existing = repo.getById(id, userID) ?: return false

        if (newName.isBlank()) throw IllegalArgumentException("Name cannot be blank")

        return repo.update(id, newName, userID)
    }

    fun delete(id: UUID, userID: UUID): Boolean = repo.delete(id, userID)
}
