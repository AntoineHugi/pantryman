package org.pantry.mappers

import org.jetbrains.exposed.sql.ResultRow
import org.pantry.postgres.tables.UserTable
import org.pantry.models.User

fun ResultRow.toUser() = User(
    id = this[UserTable.id].toString(),
    email = this[UserTable.email],
    passwordHash = this[UserTable.passwordHash],
    isVerified = this[UserTable.isVerified],
    verificationToken = this[UserTable.verificationToken]?.toString()
)
