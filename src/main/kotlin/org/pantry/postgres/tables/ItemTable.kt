package org.pantry.postgres.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.ReferenceOption

object ItemTable : Table("item_list") {
    val id = uuid("id").autoGenerate().uniqueIndex()
    val name = varchar("name", 255)
    val quantity = integer("quantity").default(1)
    val isChecked = bool("is_checked").default(false)
    val isFavorite = bool("is_favorite").default(false)

    val listId = uuid("list_id").references(GroceryListTable.id, onDelete = ReferenceOption.CASCADE)
    override val primaryKey = PrimaryKey(id)

}
