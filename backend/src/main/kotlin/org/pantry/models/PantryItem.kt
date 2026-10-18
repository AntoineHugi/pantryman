package org.pantry.models

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PantryItem(
    val id: String,
    // Null for the user's personal items
    @EncodeDefault val householdId: String? = null,
    val name: String,
    // "default": added to new lists. "staple": always at home, so recipes skip it.
    val kind: String,
    // For defaults: what goes on the list
    val quantity: Double,
    @EncodeDefault val unit: String? = null
)

@Serializable
data class AddPantryItemRequest(
    // Omit for a personal item
    val householdId: String? = null,
    val name: String,
    val kind: String,
    val quantity: Double = 1.0,
    val unit: String? = null
)
