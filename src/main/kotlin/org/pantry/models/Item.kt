package org.pantry.models

import kotlinx.serialization.Serializable

@Serializable
data class CreateItemRequest(
    val listId: String,
    val name: String,
    val quantity: Int
)

@Serializable
data class Item(
    val id: String,
    val name: String,
    val quantity: Int,
    val isChecked: Boolean = false,
    val isFavorite: Boolean = false
) {

}