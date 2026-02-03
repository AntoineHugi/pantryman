package org.pantry.postgres.tables

import org.jetbrains.exposed.sql.Table

object UserTable : Table("users") {
    val id = uuid("id")
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)

    override val primaryKey = PrimaryKey(id)
}