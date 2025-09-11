package org.pantry.models

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class GroceryList(
    val id: String,
    val name: String,
    val items: List<Item>
) {

}
