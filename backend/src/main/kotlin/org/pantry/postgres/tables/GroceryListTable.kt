package org.pantry.postgres.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object GroceryListTable : Table("grocery_lists") {
    val id = uuid("id").autoGenerate().uniqueIndex()
    val name = varchar("name", 255)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    val userID = uuid("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    // Null for a personal list; otherwise every member of the household can use the list
    val householdId = uuid("household_id").references(HouseholdTable.id, onDelete = ReferenceOption.SET_NULL).nullable().index()

    override val primaryKey = PrimaryKey(id)
}

