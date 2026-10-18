package org.pantry.models

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
data class ItemUpdateRequest(
    val name: String? = null,
    val quantity: Double? = null,
    // null or missing leaves the unit as it is; "" removes it
    val unit: String? = null,
    val isChecked: Boolean? = null
)

@Serializable
data class CreateItemRequest(
    val name: String,
    val quantity: Double,
    val unit: String? = null
)

// Older app versions read quantity as a number and ignore unit and fromRecipes.
// @EncodeDefault: the server's Json leaves out fields equal to their default otherwise.
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Item(
    val id: String,
    val name: String,
    val quantity: Double,
    val isChecked: Boolean = false,
    // Null for a count ("3 eggs")
    @EncodeDefault val unit: String? = null,
    // e.g. "Bolognese, Lasagna" when added from recipes
    @EncodeDefault val fromRecipes: String? = null
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

@Serializable
data class AddRecipeToListRequest(
    val recipeId: String,
    // Defaults to the recipe's own servings
    val servings: Int? = null,
    // Positions in the recipe's ingredient list to add. Defaults to every non-optional one.
    val ingredientIndexes: List<Int>? = null
)

@Serializable
data class AddRecipeToListResponse(
    val list: GroceryList,
    // New items, and existing items whose quantity went up
    val added: Int,
    val merged: Int
)
