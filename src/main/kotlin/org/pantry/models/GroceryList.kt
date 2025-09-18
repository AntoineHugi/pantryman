package org.pantry.models

import kotlinx.serialization.Serializable
import java.util.UUID
//import kotlin.uuid.Uuid

@Serializable
data class CreateGroceryListRequest(
    val name: String
)

@Serializable
data class GroceryList(
    val id: String,
    val name: String,
    val items: List<Item>
)
