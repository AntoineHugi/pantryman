package org.pantry.services

import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import org.mindrot.jbcrypt.BCrypt
import org.pantry.models.User
import org.pantry.repositories.UserRepository
import java.util.UUID

class AuthServiceTest {

    private val repo = mockk<UserRepository>()
    private val jwtSecret = "test-secret"
    private val jwtIssuer = "test-issuer"
    private val jwtAudience = "test-audience"
    private val service = AuthService(repo, jwtSecret, jwtIssuer, jwtAudience)

    @AfterEach
    fun tearDown() {
        clearMocks(repo)
    }

    @Test
    fun `signup should create user and return auth response`() {
        val email = "test@example.com"
        val password = "password123"

        every { repo.getByEmail(email) } returns null
        every { repo.create(any(), email, any()) } answers {
            val id = firstArg<UUID>()
            val passwordHash = thirdArg<String>()
            User(id.toString(), email, passwordHash)
        }

        val result = service.signup(email, password)

        assertNotNull(result)
        assertEquals(email, result.email)
        assertNotNull(result.token)

        verify(exactly = 1) { repo.getByEmail(email) }
        verify(exactly = 1) { repo.create(any(), email, any()) }
    }

    @Test
    fun `signup should throw if email is blank`() {
        val exception = assertThrows<IllegalArgumentException> {
            service.signup("", "password123")
        }
        assertEquals("Invalid email", exception.message)
        verify(exactly = 0) { repo.getByEmail(any()) }
        verify(exactly = 0) { repo.create(any(), any(), any()) }
    }

    @Test
    fun `signup should throw if email has no @ symbol`() {
        val exception = assertThrows<IllegalArgumentException> {
            service.signup("notanemail", "password123")
        }
        assertEquals("Invalid email", exception.message)
        verify(exactly = 0) { repo.getByEmail(any()) }
        verify(exactly = 0) { repo.create(any(), any(), any()) }
    }

    @Test
    fun `signup should throw if password is less than 8 characters`() {
        val exception = assertThrows<IllegalArgumentException> {
            service.signup("test@example.com", "pass")
        }
        assertEquals("Password must be at least 8 characters", exception.message)
        verify(exactly = 0) { repo.getByEmail(any()) }
        verify(exactly = 0) { repo.create(any(), any(), any()) }
    }

    @Test
    fun `signup should throw if user already exists`() {
        val email = "existing@example.com"
        val existingUser = User(UUID.randomUUID().toString(), email, "hashedpassword")

        every { repo.getByEmail(email) } returns existingUser

        val exception = assertThrows<IllegalArgumentException> {
            service.signup(email, "password123")
        }

        assertEquals("Registration Failed", exception.message)
        verify(exactly = 1) { repo.getByEmail(email) }
        verify(exactly = 0) { repo.create(any(), any(), any()) }
    }

    @Test
    fun `login should return auth response for valid credentials`() {
        val email = "test@example.com"
        val password = "password123"
        val passwordHash = BCrypt.hashpw(password, BCrypt.gensalt())
        val userId = UUID.randomUUID()
        val user = User(userId.toString(), email, passwordHash)

        every { repo.getByEmail(email) } returns user

        val result = service.login(email, password)

        assertNotNull(result)
        assertEquals(email, result?.email)
        assertEquals(userId.toString(), result?.userId)
        assertNotNull(result?.token)

        verify(exactly = 1) { repo.getByEmail(email) }
    }

    @Test
    fun `login should return null if user not found`() {
        val email = "notfound@example.com"

        every { repo.getByEmail(email) } returns null

        val result = service.login(email, "password123")

        assertNull(result)
        verify(exactly = 1) { repo.getByEmail(email) }
    }

    @Test
    fun `login should return null if password is incorrect`() {
        val email = "test@example.com"
        val correctPassword = "password123"
        val wrongPassword = "wrongpassword"
        val passwordHash = BCrypt.hashpw(correctPassword, BCrypt.gensalt())
        val userId = UUID.randomUUID()
        val user = User(userId.toString(), email, passwordHash)

        every { repo.getByEmail(email) } returns user

        val result = service.login(email, wrongPassword)

        assertNull(result)
        verify(exactly = 1) { repo.getByEmail(email) }
    }

    @Test
    fun `generated token should contain userId claim`() {
        val email = "test@example.com"
        val password = "password123"

        every { repo.getByEmail(email) } returns null
        every { repo.create(any(), email, any()) } answers {
            val id = firstArg<UUID>()
            val passwordHash = thirdArg<String>()
            User(id.toString(), email, passwordHash)
        }

        val result = service.signup(email, password)

        val decodedJWT = com.auth0.jwt.JWT.decode(result.token)
        assertEquals(result.userId, decodedJWT.getClaim("userId").asString())
        assertEquals(jwtIssuer, decodedJWT.issuer)
        assertEquals(jwtAudience, decodedJWT.audience[0])
        assertNotNull(decodedJWT.expiresAt)
    }

    @Test
    fun `signup should hash password before storing`() {
        val email = "test@example.com"
        val password = "password123"

        every { repo.getByEmail(email) } returns null
        every { repo.create(any(), email, any()) } answers {
            val id = firstArg<UUID>()
            val passwordHash = thirdArg<String>()

            // Verify the password was hashed (BCrypt hashes start with $2a$ or $2b$)
            assertTrue(passwordHash.startsWith("$2"))
            assertNotEquals(password, passwordHash)

            User(id.toString(), email, passwordHash)
        }

        service.signup(email, password)

        verify(exactly = 1) { repo.create(any(), email, match { it.startsWith("$2") }) }
    }
}