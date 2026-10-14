package org.pantry.postgres.tables

import org.jetbrains.exposed.sql.Table

object UserTable : Table("users") {
    val id = uuid("id")
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val isVerified = bool("is_verified").default(false)
    val verificationToken = uuid("verification_token").nullable()

    override val primaryKey = PrimaryKey(id)
}