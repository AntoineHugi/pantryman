package org.pantry.postgres.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import java.math.BigDecimal

object ItemTable : Table("item_list") {
    val id = uuid("id").autoGenerate().uniqueIndex()
    val name = varchar("name", 255)
    val quantity = decimal("quantity", 12, 3).default(BigDecimal.ONE)
    // Null for a count ("3 eggs"); otherwise one of RecipeService.UNITS
    val unit = varchar("unit", 16).nullable()
    // Names of the recipes the item was added for, e.g. "Bolognese, Lasagna"
    val fromRecipes = varchar("from_recipes", 500).nullable()
    val isChecked = bool("is_checked").default(false)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    val listId = uuid("list_id").references(GroceryListTable.id, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(id)

}
