package org.pantry.models

import java.util.UUID

data class GroceryList(
    val id: UUID,
    val name: String,
    val items: List<Item>,
    ) {

}
