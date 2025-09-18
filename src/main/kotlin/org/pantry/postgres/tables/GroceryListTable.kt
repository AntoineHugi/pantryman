package org.pantry.postgres.tables

import org.jetbrains.exposed.sql.Table
import java.util.UUID

object GroceryListTable : Table("grocery_lists") {
    val id = uuid("id").autoGenerate().uniqueIndex()
    val name = varchar("name", 255)

    override val primaryKey = PrimaryKey(id)
}

