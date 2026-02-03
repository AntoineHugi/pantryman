package org.pantry.repositories

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.pantry.models.User
import org.pantry.postgres.tables.UserTable
import java.util.UUID

class UserRepository {

    fun create(id: UUID, email: String, passwordHash: String): User {
        return transaction {
            UserTable.insert {
                it[UserTable.id] = id
                it[UserTable.email] = email
                it[UserTable.passwordHash] = passwordHash
            }
            User(id.toString(), email, passwordHash)
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
}

private fun ResultRow.toUser() = User(
    id = this[UserTable.id].toString(),
    email = this[UserTable.email],
    passwordHash = this[UserTable.passwordHash]
)