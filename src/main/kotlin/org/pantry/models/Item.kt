package org.pantry.models

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ItemUpdateRequest(
    val name: String? = null,
    val quantity: Int? = null,
    val isChecked: Boolean? = null,
    val isFavorite: Boolean? = null
)

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
)