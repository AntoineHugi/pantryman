package org.pantry.repositories

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.pantry.models.User
import org.pantry.postgres.tables.UserTable
import org.pantry.mappers.toUser
import java.time.Instant
import java.util.UUID

class UserRepository {

    fun create(id: UUID, email: String, passwordHash: String, verificationToken: UUID): User {
        return transaction {
            UserTable.insert {
                it[UserTable.id] = id
                it[UserTable.email] = email
                it[UserTable.passwordHash] = passwordHash
                it[UserTable.verificationToken] = verificationToken
            }
            User(id.toString(), email, passwordHash, isVerified = false, verificationToken = verificationToken.toString())
        }
    }

    fun getByEmail(email: String): User? {
        return transaction {
            UserTable.selectAll()
                // Case-insensitive so accounts created before emails were normalized still match
                .where { UserTable.email.lowerCase() eq email.lowercase() }
                .map { it.toUser() }
                .singleOrNull()
        }
    }

    fun getById(id: UUID): User? {
        return transaction {
            UserTable.selectAll()
                .where { UserTable.id eq id }
                .map { it.toUser() }
                .singleOrNull()
        }
    }

    fun getByVerificationToken(token: UUID): User? {
        return transaction {
            UserTable.selectAll()
                .where { UserTable.verificationToken eq token }
                .map { it.toUser() }
                .singleOrNull()
        }
    }

    fun markAsVerified(userId: UUID) {
        transaction {
            UserTable.update({ UserTable.id eq userId }) {
                it[isVerified] = true
                it[verificationToken] = null
            }
        }
    }

    fun updateVerificationToken(userId: UUID, token: UUID) {
        transaction {
            UserTable.update({ UserTable.id eq userId }) {
                it[verificationToken] = token
            }
        }
    }

    fun updatePassword(userId: UUID, passwordHash: String) {
        transaction {
            UserTable.update({ UserTable.id eq userId }) {
                it[UserTable.passwordHash] = passwordHash
            }
        }
    }

    // Replaces any earlier pending reset, so only the latest emailed link works
    fun setPasswordResetToken(userId: UUID, tokenHash: String, expiresAt: Instant) {
        transaction {
            UserTable.update({ UserTable.id eq userId }) {
                it[passwordResetTokenHash] = tokenHash
                it[passwordResetExpiresAt] = expiresAt
            }
        }
    }

    /**
     * Sets the new password if [tokenHash] matches a reset that hasn't expired, and uses the reset up.
     * One UPDATE, so a link can't be used twice. Following the emailed link proves the user owns the
     * address, so this also verifies the email. Returns false for an unknown, used or expired token.
     */
    fun resetPassword(tokenHash: String, passwordHash: String, now: Instant): Boolean {
        return transaction {
            UserTable.update({
                (UserTable.passwordResetTokenHash eq tokenHash) and (UserTable.passwordResetExpiresAt greater now)
            }) {
                it[UserTable.passwordHash] = passwordHash
                it[passwordResetTokenHash] = null
                it[passwordResetExpiresAt] = null
                it[isVerified] = true
                it[verificationToken] = null
            } == 1
        }
    }

    fun delete(userId: UUID) {
        transaction {
            UserTable.deleteWhere { id eq userId }
        }
    }
}