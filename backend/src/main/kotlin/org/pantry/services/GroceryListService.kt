package org.pantry.services

import org.pantry.models.GroceryList
import org.pantry.repositories.GroceryListRepository
import java.util.UUID

class GroceryListService(
    private val repo: GroceryListRepository
) {
    fun getAll(userID: UUID): List<GroceryList> = repo.getAll(userID)

    fun getById(id: UUID, userID: UUID): GroceryList? = repo.getById(id, userID)

    // The route checks that the user belongs to householdId
    fun create(name: String, userID: UUID, householdId: UUID? = null): GroceryList {
        val newId = UUID.randomUUID()
        return repo.create(newId, name, userID, householdId)
    }

    fun update(id: UUID, newName: String, userID: UUID): Boolean {
        if (newName.isBlank()) throw IllegalArgumentException("Name cannot be blank")

        // Returns false when the list doesn't exist or the user can't access it
        return repo.update(id, newName, userID)
    }

    // The route checks that the user owns the list and belongs to householdId
    fun setHousehold(id: UUID, householdId: UUID?, ownerId: UUID): Boolean =
        repo.setHousehold(id, householdId, ownerId)

    fun delete(id: UUID, userID: UUID): Boolean = repo.delete(id, userID)
}
