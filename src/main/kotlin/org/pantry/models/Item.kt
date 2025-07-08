package org.pantry.models

import java.util.UUID

data class Item(
    val id: UUID,
    val name: String,
    val quantity: Int,
    val isChecked: Boolean = false,
    val isFavorite: Boolean = false
) {
}