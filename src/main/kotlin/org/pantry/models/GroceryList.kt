package org.pantry.models

import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class GroceryList(
    val id: String,
    val name: String,
    //val items: List<Item>,
    ) {

}
