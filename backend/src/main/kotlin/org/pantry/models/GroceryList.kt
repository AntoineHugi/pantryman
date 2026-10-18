package org.pantry.models

import kotlinx.serialization.Serializable

@Serializable
data class CreateGroceryListRequest(
    val name: String,
    // Omit for a personal list
    val householdId: String? = null,
    // The pantry defaults to put on the new list. Omitted (older app versions): all of them.
    val defaultItemIds: List<String>? = null
)

@Serializable
data class UpdateGroceryListRequest(
    val name: String
)

@Serializable
data class GroceryList(
    val id: String,
    val name: String,
    val items: List<Item>,
    // The owner: the creator, or the member who took over when the creator left the household
    val userId: String,
    val householdId: String? = null
)

@Serializable
data class UpdateListHouseholdRequest(
    // null makes the list personal again
    val householdId: String? = null
)
