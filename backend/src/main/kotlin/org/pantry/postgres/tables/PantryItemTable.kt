package org.pantry.postgres.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import java.math.BigDecimal

// Usual items of a household, or of one user for their personal lists (exactly one of the two)
object PantryItemTable : Table("pantry_items") {
    val id = uuid("id")
    val userId = uuid("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE).nullable().index()
    val householdId = uuid("household_id").references(HouseholdTable.id, onDelete = ReferenceOption.CASCADE).nullable().index()
    val name = varchar("name", 100)
    // "default" (added to new lists) or "staple" (always at home: recipes skip it)
    val kind = varchar("kind", 10)
    val quantity = decimal("quantity", 12, 3).default(BigDecimal.ONE)
    val unit = varchar("unit", 16).nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)
}
