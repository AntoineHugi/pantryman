package org.pantry.models

import kotlinx.serialization.Serializable

@Serializable
data class ItemUpdateRequest(
    val name: String? = null,
    val quantity: Int? = null,
    val isChecked: Boolean? = null
)

@Serializable
data class CreateItemRequest(
    val name: String,
    val quantity: Int
)

@Serializable
data class Item(
    val id: String,
    val name: String,
    val quantity: Int,
    val isChecked: Boolean = false
)
@Serializable
data class BulkDeleteItemsRequest(
    val itemIds: List<String>
)

@Serializable
data class BulkDeleteItemsResponse(
    // IDs that weren't on the list (e.g. already deleted by another member) aren't counted
    val deleted: Int
)
