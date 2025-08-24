package org.pantry.mappers

import org.jetbrains.exposed.sql.ResultRow
import org.pantry.postgres.tables.GroceryListTable
import org.pantry.models.GroceryList

// GroceryList mapper
fun ResultRow.toGroceryList() = GroceryList(
    id = this[GroceryListTable.id].toString(),
    name = this[GroceryListTable.name]
)
