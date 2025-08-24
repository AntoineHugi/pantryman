package org.pantry.mappers

import org.jetbrains.exposed.sql.ResultRow
import org.pantry.postgres.tables.ItemTable
import org.pantry.models.Item

// Item mapper
fun ResultRow.toItem() = Item(
    id = this[ItemTable.id].toString(),
    name = this[ItemTable.name],
    quantity = this[ItemTable.quantity],
    isChecked = this[ItemTable.isChecked],
    isFavorite = this[ItemTable.isFavorite],
    listID = this

)
