package org.pantry.mappers

import io.mockk.every
import io.mockk.mockk
import org.jetbrains.exposed.sql.ResultRow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.pantry.postgres.tables.UserTable
import java.util.UUID

class UserMapperTest {
    @Test
    fun `toUser maps id email passwordHash and verification fields`() {
        val row = mockk<ResultRow>()
        val id = UUID.randomUUID()
        val email = "testemail@email.com"
        val passwordHash = "passwordHash"
        val token = UUID.randomUUID()

        every { row[UserTable.id] } returns id
        every { row[UserTable.email] } returns email
        every { row[UserTable.passwordHash] } returns passwordHash
        every { row[UserTable.isVerified] } returns true
        every { row[UserTable.verificationToken] } returns token

        val result = row.toUser()

        assertEquals(id.toString(), result.id)
        assertEquals(email, result.email)
        assertEquals(passwordHash, result.passwordHash)
        assertEquals(true, result.isVerified)
        assertEquals(token.toString(), result.verificationToken)
    }
}
