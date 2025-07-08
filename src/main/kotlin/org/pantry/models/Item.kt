package org.pantry.models

import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class Item(
    val id: String,
    val name: String,
    val quantity: Int,
    val isChecked: Boolean = false,
    val isFavorite: Boolean = false
) {
}