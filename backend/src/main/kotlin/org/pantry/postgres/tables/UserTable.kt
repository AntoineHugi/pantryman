package org.pantry.postgres.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object UserTable : Table("users") {
    val id = uuid("id")
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val isVerified = bool("is_verified").default(false)
    val verificationToken = uuid("verification_token").nullable()
    val passwordResetTokenHash = varchar("password_reset_token_hash", 64).nullable()
    val passwordResetExpiresAt = timestamp("password_reset_expires_at").nullable()

    override val primaryKey = PrimaryKey(id)
}