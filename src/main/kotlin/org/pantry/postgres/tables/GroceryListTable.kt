package org.pantry.postgres.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object GroceryListTable : Table("grocery_lists") {
    val id = uuid("id").autoGenerate().uniqueIndex()
    val name = varchar("name", 255)

    val userID = uuid("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(id)
}

