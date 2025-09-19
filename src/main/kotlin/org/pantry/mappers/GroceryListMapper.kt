package org.pantry.mappers

import org.jetbrains.exposed.sql.ResultRow
import org.pantry.postgres.tables.GroceryListTable
import org.pantry.models.GroceryList
import org.pantry.repositories.ItemRepository

fun ResultRow.toGroceryList(itemRepo: ItemRepository): GroceryList {
    val listId = this[GroceryListTable.id]
    return GroceryList(
            id = listId.toString(),
            name = this[GroceryListTable.name],
            items = itemRepo.getByListId(listId)
    )
}
