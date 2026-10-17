package org.pantry.services

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import org.mindrot.jbcrypt.BCrypt
import org.pantry.models.User
import org.pantry.repositories.HouseholdRepository
import org.pantry.repositories.UserRepository
import java.util.UUID

class AuthServiceTest {

    private val repo = mockk<UserRepository>()
    private val emailService = mockk<EmailService>()
    private val householdRepo = mockk<HouseholdRepository>()
    private val jwtSecret = "test-secret"
    private val jwtIssuer = "test-issuer"
    private val jwtAudience = "test-audience"
    private val service = AuthService(repo, emailService, householdRepo, jwtSecret, jwtIssuer, jwtAudience)

    @AfterEach
    fun tearDown() {
        clearMocks(repo, emailService, householdRepo)
    }

    private fun stubSuccessfulCreate() {
        every { repo.create(any(), any(), any(), any()) } answers {
            User(firstArg<UUID>().toString(), secondArg(), thirdArg(), verificationToken = arg<UUID>(3).toString())
        }
        coEvery { emailService.sendVerificationEmail(any(), any()) } just Runs
    }

    @Test
    fun `signup should create user and send verification email`() = runBlocking {
        val email = "test@example.com"
        every { repo.getByEmail(email) } returns null
        stubSuccessfulCreate()

        val result = service.signup(email, "password123")

        assertEquals(email, result.email)
        verify(exactly = 1) { repo.create(any(), email, any(), any()) }
        coVerify(exactly = 1) { emailService.sendVerificationEmail(email, any()) }
    }

    @Test
    fun `signup should normalize email to trimmed lowercase`() = runBlocking {
        every { repo.getByEmail("test@example.com") } returns null
        stubSuccessfulCreate()

        val result = service.signup("  Test@Example.COM ", "password123")

        assertEquals("test@example.com", result.email)
        verify(exactly = 1) { repo.create(any(), "test@example.com", any(), any()) }
    }

    @Test
    fun `signup should delete user and rethrow if verification email fails`() {
        val email = "test@example.com"
        every { repo.getByEmail(email) } returns null
        every { repo.create(any(), any(), any(), any()) } answers {
            User(firstArg<UUID>().toString(), secondArg(), thirdArg())
        }
        every { repo.delete(any()) } just Runs
        coEvery { emailService.sendVerificationEmail(any(), any()) } throws RuntimeException("Resend down")

        assertThrows<RuntimeException> {
            runBlocking { service.signup(email, "password123") }
        }

        val createdId = slot<UUID>()
        verify(exactly = 1) { repo.create(capture(createdId), email, any(), any()) }
        verify(exactly = 1) { repo.delete(createdId.captured) }
    }

    @Test
    fun `signup should throw if email is blank`() {
        val exception = assertThrows<IllegalArgumentException> {
            runBlocking { service.signup("", "password123") }
        }
        assertEquals("Invalid email", exception.message)
        verify(exactly = 0) { repo.create(any(), any(), any(), any()) }
    }

    @Test
    fun `signup should throw if email has no @ symbol`() {
        val exception = assertThrows<IllegalArgumentException> {
            runBlocking { service.signup("notanemail", "password123") }
        }
        assertEquals("Invalid email", exception.message)
        verify(exactly = 0) { repo.create(any(), any(), any(), any()) }
    }

    @Test
    fun `signup should throw if password is less than 8 characters`() {
        val exception = assertThrows<IllegalArgumentException> {
            runBlocking { service.signup("test@example.com", "pass") }
        }
        assertEquals("Password must be at least 8 characters", exception.message)
        verify(exactly = 0) { repo.create(any(), any(), any(), any()) }
    }

    @Test
    fun `signup should throw if user already exists`() {
        val email = "existing@example.com"
        every { repo.getByEmail(email) } returns User(UUID.randomUUID().toString(), email, "hashedpassword")

        val exception = assertThrows<IllegalArgumentException> {
            runBlocking { service.signup(email, "password123") }
        }

        assertEquals("Registration Failed", exception.message)
        verify(exactly = 0) { repo.create(any(), any(), any(), any()) }
    }

    @Test
    fun `signup should hash password before storing`() = runBlocking {
        val password = "password123"
        every { repo.getByEmail(any()) } returns null
        stubSuccessfulCreate()

        service.signup("test@example.com", password)

        verify(exactly = 1) {
            repo.create(any(), any(), match { it.startsWith("$2") && it != password && BCrypt.checkpw(password, it) }, any())
        }
    }

    @Test
    fun `login should return auth response with valid token for verified user`() {
        val email = "test@example.com"
        val password = "password123"
        val userId = UUID.randomUUID()
        every { repo.getByEmail(email) } returns
            User(userId.toString(), email, BCrypt.hashpw(password, BCrypt.gensalt()), isVerified = true)

        val result = checkNotNull(service.login(email, password))

        assertEquals(email, result.email)
        assertEquals(userId.toString(), result.userId)
        val decodedJWT = com.auth0.jwt.JWT.decode(result.token)
        assertEquals(userId.toString(), decodedJWT.getClaim("userId").asString())
        assertEquals(jwtIssuer, decodedJWT.issuer)
        assertEquals(jwtAudience, decodedJWT.audience[0])
        assertNotNull(decodedJWT.expiresAt)
    }

    @Test
    fun `login should look up email case-insensitively`() {
        val password = "password123"
        every { repo.getByEmail("test@example.com") } returns
            User(UUID.randomUUID().toString(), "test@example.com", BCrypt.hashpw(password, BCrypt.gensalt()), isVerified = true)

        assertNotNull(service.login(" TEST@example.com", password))
    }

    @Test
    fun `login should throw if email not verified`() {
        val email = "test@example.com"
        val password = "password123"
        every { repo.getByEmail(email) } returns
            User(UUID.randomUUID().toString(), email, BCrypt.hashpw(password, BCrypt.gensalt()), isVerified = false)

        assertThrows<EmailNotVerifiedException> { service.login(email, password) }
    }

    @Test
    fun `login should return null if user not found`() {
        every { repo.getByEmail(any()) } returns null

        assertNull(service.login("notfound@example.com", "password123"))
    }

    @Test
    fun `login should return null if password is incorrect`() {
        val email = "test@example.com"
        every { repo.getByEmail(email) } returns
            User(UUID.randomUUID().toString(), email, BCrypt.hashpw("password123", BCrypt.gensalt()), isVerified = true)

        assertNull(service.login(email, "wrongpassword"))
    }

    @Test
    fun `deleteAccount leaves households before deleting the user`() {
        val userId = UUID.randomUUID()
        val hash = BCrypt.hashpw("password123", BCrypt.gensalt(4))
        every { repo.getById(userId) } returns User(userId.toString(), "a@example.com", hash, isVerified = true)
        every { householdRepo.removeMemberFromAll(userId) } just Runs
        every { repo.delete(userId) } just Runs

        service.deleteAccount(userId, "password123")

        verifyOrder {
            householdRepo.removeMemberFromAll(userId)
            repo.delete(userId)
        }
    }

    @Test
    fun `deleteAccount with wrong password changes nothing`() {
        val userId = UUID.randomUUID()
        val hash = BCrypt.hashpw("password123", BCrypt.gensalt(4))
        every { repo.getById(userId) } returns User(userId.toString(), "a@example.com", hash, isVerified = true)

        assertThrows<SecurityException> { service.deleteAccount(userId, "wrong-password") }

        verify(exactly = 0) { householdRepo.removeMemberFromAll(any()) }
        verify(exactly = 0) { repo.delete(any()) }
    }
}
