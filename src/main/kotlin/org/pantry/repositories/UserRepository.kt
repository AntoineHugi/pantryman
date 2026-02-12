package org.pantry.repositories

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.pantry.models.User
import org.pantry.postgres.tables.UserTable
import org.pantry.mappers.toUser
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
                .where { UserTable.email eq email }
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
}