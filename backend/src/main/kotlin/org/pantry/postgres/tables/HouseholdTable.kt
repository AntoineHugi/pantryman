package org.pantry.postgres.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object HouseholdTable : Table("households") {
    val id = uuid("id")
    val name = varchar("name", 255)
    val inviteCode = varchar("invite_code", 16).uniqueIndex()
    // Can rename the household and remove members
    val ownerId = uuid("owner_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)
}

object HouseholdMemberTable : Table("household_members") {
    val householdId = uuid("household_id").references(HouseholdTable.id, onDelete = ReferenceOption.CASCADE)
    val userId = uuid("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE).index()
    val joinedAt = timestamp("joined_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(householdId, userId)
}
