package org.pantry.plugins

import io.ktor.server.application.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import io.ktor.server.testing.ApplicationTestBuilder
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import io.ktor.serialization.kotlinx.json.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.Runs
import org.koin.core.component.inject
import org.koin.dsl.module
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SchemaUtils
import org.koin.test.KoinTest
import org.koin.test.junit5.KoinTestExtension
import org.junit.jupiter.api.extension.RegisterExtension
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.config.MapApplicationConfig
import org.junit.jupiter.api.BeforeEach
import com.auth0.jwt.JWT
import java.util.UUID

import org.pantry.models.SignupRequest
import org.pantry.models.SignupResponse
import org.pantry.models.LoginRequest
import org.pantry.models.AuthResponse
import org.pantry.models.ForgotPasswordRequest
import org.pantry.models.ResetPasswordRequest
import org.pantry.models.VerifyEmailRequest
import org.pantry.models.VerifyEmailResponse
import org.pantry.repositories.UserRepository
import org.pantry.services.AuthService
import org.pantry.services.EmailService
import org.pantry.postgres.tables.UserTable
import org.pantry.postgres.tables.HouseholdMemberTable
import org.pantry.postgres.tables.HouseholdTable
import org.pantry.repositories.HouseholdRepository

class RoutingAuthApiIT : KoinTest {

    private val emailService = mockk<EmailService>().also {
        coEvery { it.sendVerificationEmail(any(), any(), any()) } just Runs
    }
    // The token from the last password reset email
    private val resetToken = slot<String>().also { token ->
        coEvery { emailService.sendPasswordResetEmail(any(), capture(token)) } just Runs
    }

    @JvmField
    @RegisterExtension
    val koinTestExtension = KoinTestExtension.create {
        modules(
            module {
                single { UserRepository() }
                single { HouseholdRepository() }
                single {
                    AuthService(
                        get(),
                        emailService,
                        get(),
                        "test-secret-key-for-testing-only",
                        "test-issuer",
                        "test-audience"
                    )
                }
            })
    }

    private val userRepo: UserRepository by inject()

    @BeforeEach
    fun setupDatabase() {
        Database.connect(
            url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.create(UserTable, HouseholdTable, HouseholdMemberTable)
            UserTable.deleteAll()
        }
    }

    private fun ApplicationTestBuilder.setupTestApplication() {
        environment {
            config = MapApplicationConfig(
                "jwt.secret" to "test-secret-key-for-testing-only",
                "jwt.issuer" to "test-issuer",
                "jwt.audience" to "test-audience",
                "resend.frontendBaseUrl" to "http://frontend.test"
            )
        }
        application {
            install(ContentNegotiation) {
                json()
            }
            configureStatusPages()
            configureSecurity()
            configureRateLimiting()
            authAPI()
        }
    }

    // The default test client has no JSON serializer, so request bodies need this one.
    private val ApplicationTestBuilder.jsonClient
        get() = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

    private suspend fun ApplicationTestBuilder.signup(
        email: String,
        password: String = "password123",
        redirectPath: String? = null
    ) =
        jsonClient.post("/auth/signup") {
            contentType(ContentType.Application.Json)
            setBody(SignupRequest(email, password, redirectPath))
        }

    private suspend fun ApplicationTestBuilder.login(email: String, password: String = "password123") =
        jsonClient.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email, password))
        }

    private fun markVerified(email: String) {
        val user = checkNotNull(userRepo.getByEmail(email))
        userRepo.markAsVerified(UUID.fromString(user.id))
    }

    @Test
    fun `POST signup returns 201 and creates unverified user`() = testApplication {
        setupTestApplication()

        val response = signup("test@example.com")

        assertEquals(HttpStatusCode.Created, response.status)
        val body: SignupResponse = response.body()
        assertEquals("test@example.com", body.email)

        val savedUser = assertNotNull(userRepo.getByEmail("test@example.com"))
        assertEquals(false, savedUser.isVerified)
        assertNotNull(savedUser.verificationToken)
    }

    @Test
    fun `POST signup returns 400 with blank email`() = testApplication {
        setupTestApplication()

        val response = signup("")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val errorResponse: Map<String, String> = response.body()
        assertEquals("Invalid email", errorResponse["error"])
    }

    @Test
    fun `POST signup returns 400 with invalid email`() = testApplication {
        setupTestApplication()

        val response = signup("notanemail")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val errorResponse: Map<String, String> = response.body()
        assertEquals("Invalid email", errorResponse["error"])
    }

    @Test
    fun `POST signup returns 400 with short password`() = testApplication {
        setupTestApplication()

        val response = signup("test@example.com", "short")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val errorResponse: Map<String, String> = response.body()
        assertEquals("Password must be at least 8 characters", errorResponse["error"])
    }

    @Test
    fun `POST signup returns 400 when user already exists, regardless of email case`() = testApplication {
        setupTestApplication()

        assertEquals(HttpStatusCode.Created, signup("duplicate@example.com").status)

        val secondResponse = signup("Duplicate@Example.com")

        assertEquals(HttpStatusCode.BadRequest, secondResponse.status)
        val errorResponse: Map<String, String> = secondResponse.body()
        assertEquals("An account with this email already exists. Log in, or reset your password if you forgot it.", errorResponse["error"])
    }

    @Test
    fun `GET verify marks user verified and redirects to login`() = testApplication {
        setupTestApplication()
        signup("verify@example.com")
        val token = checkNotNull(userRepo.getByEmail("verify@example.com")?.verificationToken)

        val noRedirectClient = createClient { followRedirects = false }
        val response = noRedirectClient.get("/auth/verify?token=$token")

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("http://frontend.test/login?verified=true", response.headers[HttpHeaders.Location])
        assertEquals(true, userRepo.getByEmail("verify@example.com")?.isVerified)
    }

    @Test
    fun `signup redirect path goes into the verification link`() = testApplication {
        setupTestApplication()

        val response = signup("invited@example.com", redirectPath = "/households?join=ABCD2345")

        assertEquals(HttpStatusCode.Created, response.status)
        coVerify(exactly = 1) {
            emailService.sendVerificationEmail("invited@example.com", any(), "/households?join=ABCD2345")
        }
    }

    @Test
    fun `POST signup rejects a redirect path to another site`() = testApplication {
        setupTestApplication()

        val response = signup("evil@example.com", redirectPath = "//evil.example.com")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(null, userRepo.getByEmail("evil@example.com"))
    }

    @Test
    fun `GET verify passes the next path on to the login page`() = testApplication {
        setupTestApplication()
        signup("next@example.com")
        val token = checkNotNull(userRepo.getByEmail("next@example.com")?.verificationToken)

        val noRedirectClient = createClient { followRedirects = false }
        val response = noRedirectClient.get("/auth/verify?token=$token&next=%2Fhouseholds%3Fjoin%3DABCD2345")

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals(
            "http://frontend.test/login?verified=true&next=%2Fhouseholds%3Fjoin%3DABCD2345",
            response.headers[HttpHeaders.Location]
        )
    }

    @Test
    fun `GET verify drops an unsafe next path but still verifies`() = testApplication {
        setupTestApplication()
        signup("unsafe@example.com")
        val token = checkNotNull(userRepo.getByEmail("unsafe@example.com")?.verificationToken)

        val noRedirectClient = createClient { followRedirects = false }
        val response = noRedirectClient.get("/auth/verify?token=$token&next=https%3A%2F%2Fevil.example.com")

        assertEquals("http://frontend.test/login?verified=true", response.headers[HttpHeaders.Location])
        assertEquals(true, userRepo.getByEmail("unsafe@example.com")?.isVerified)
    }

    @Test
    fun `POST verify marks user verified once`() = testApplication {
        setupTestApplication()
        signup("post-verify@example.com")
        val token = checkNotNull(userRepo.getByEmail("post-verify@example.com")?.verificationToken)

        suspend fun verify() = jsonClient.post("/auth/verify") {
            contentType(ContentType.Application.Json)
            setBody(VerifyEmailRequest(token))
        }

        val first = verify()
        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals(true, first.body<VerifyEmailResponse>().verified)
        assertEquals(true, userRepo.getByEmail("post-verify@example.com")?.isVerified)

        // The token is used up
        assertEquals(false, verify().body<VerifyEmailResponse>().verified)
    }

    @Test
    fun `POST verify returns 400 for a malformed token`() = testApplication {
        setupTestApplication()

        val response = jsonClient.post("/auth/verify") {
            contentType(ContentType.Application.Json)
            setBody(VerifyEmailRequest("not-a-uuid"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    private suspend fun ApplicationTestBuilder.forgotPassword(email: String) =
        jsonClient.post("/auth/forgot-password") {
            contentType(ContentType.Application.Json)
            setBody(ForgotPasswordRequest(email))
        }

    private suspend fun ApplicationTestBuilder.resetPassword(token: String, newPassword: String) =
        jsonClient.post("/auth/reset-password") {
            contentType(ContentType.Application.Json)
            setBody(ResetPasswordRequest(token, newPassword))
        }

    @Test
    fun `password reset sets a new password, verifies the email, and works only once`() = testApplication {
        setupTestApplication()
        // Never verified: following the reset link proves the address, so login works afterwards
        signup("forgetful@example.com", "oldpassword123")

        assertEquals(HttpStatusCode.OK, forgotPassword("Forgetful@Example.com").status)
        val token = resetToken.captured

        assertEquals(HttpStatusCode.OK, resetPassword(token, "newpassword123").status)

        assertEquals(HttpStatusCode.OK, login("forgetful@example.com", "newpassword123").status)
        assertEquals(HttpStatusCode.Unauthorized, login("forgetful@example.com", "oldpassword123").status)

        val reused = resetPassword(token, "anotherpassword123")
        assertEquals(HttpStatusCode.BadRequest, reused.status)
        assertEquals(HttpStatusCode.OK, login("forgetful@example.com", "newpassword123").status)
    }

    @Test
    fun `forgot password answers the same for an unknown email and sends nothing`() = testApplication {
        setupTestApplication()
        signup("known@example.com")

        val known: Map<String, String> = forgotPassword("known@example.com").body()
        val unknown = forgotPassword("unknown@example.com")

        assertEquals(HttpStatusCode.OK, unknown.status)
        assertEquals(known, unknown.body<Map<String, String>>())
        coVerify(exactly = 1) { emailService.sendPasswordResetEmail(any(), any()) }
    }

    @Test
    fun `only the latest reset link works`() = testApplication {
        setupTestApplication()
        signup("twice@example.com")

        forgotPassword("twice@example.com")
        val first = resetToken.captured
        forgotPassword("twice@example.com")
        val second = resetToken.captured

        assertEquals(HttpStatusCode.BadRequest, resetPassword(first, "newpassword123").status)
        assertEquals(HttpStatusCode.OK, resetPassword(second, "newpassword123").status)
    }

    @Test
    fun `an expired reset link is rejected`() = testApplication {
        setupTestApplication()
        signup("late@example.com")
        forgotPassword("late@example.com")
        transaction {
            UserTable.update({ UserTable.email eq "late@example.com" }) {
                it[passwordResetExpiresAt] = java.time.Instant.now().minusSeconds(60)
            }
        }

        val response = resetPassword(resetToken.captured, "newpassword123")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(
            "This reset link is invalid or has expired. Ask for a new one.",
            response.body<Map<String, String>>()["error"]
        )
    }

    @Test
    fun `reset with an unknown token is rejected`() = testApplication {
        setupTestApplication()

        assertEquals(HttpStatusCode.BadRequest, resetPassword("made-up-token", "newpassword123").status)
    }

    @Test
    fun `POST login returns 403 when email not verified`() = testApplication {
        setupTestApplication()
        signup("unverified@example.com")

        val response = login("unverified@example.com")

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `POST login returns 200 and valid JWT for verified user`() = testApplication {
        setupTestApplication()
        signup("login@example.com")
        markVerified("login@example.com")

        val response = login("login@example.com")

        assertEquals(HttpStatusCode.OK, response.status)
        val authResponse: AuthResponse = response.body()
        assertEquals("login@example.com", authResponse.email)
        val decodedJWT = JWT.decode(authResponse.token)
        assertEquals(authResponse.userId, decodedJWT.getClaim("userId").asString())
        assertEquals("test-issuer", decodedJWT.issuer)
        assertEquals("test-audience", decodedJWT.audience[0])
        assertNotNull(decodedJWT.expiresAt)
    }

    @Test
    fun `POST login accepts email with different case`() = testApplication {
        setupTestApplication()
        signup("Mixed@Example.com")
        markVerified("mixed@example.com")

        val response = login("MIXED@example.COM")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `POST login returns 401 with non-existent email`() = testApplication {
        setupTestApplication()

        val response = login("notfound@example.com")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val errorResponse: Map<String, String> = response.body()
        assertEquals("Invalid credentials", errorResponse["error"])
    }

    @Test
    fun `POST login returns 401 with incorrect password`() = testApplication {
        setupTestApplication()
        signup("test@example.com", "correctpassword")
        markVerified("test@example.com")

        val response = login("test@example.com", "wrongpassword")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val errorResponse: Map<String, String> = response.body()
        assertEquals("Invalid credentials", errorResponse["error"])
    }

    @Test
    fun `POST login is rate limited`() = testApplication {
        setupTestApplication()

        val statuses = (1..11).map { login("nobody@example.com").status }

        assertTrue(statuses.take(10).all { it == HttpStatusCode.Unauthorized })
        assertEquals(HttpStatusCode.TooManyRequests, statuses.last())
    }

    @Test
    fun `rate limit ignores X-Forwarded-For and keys on CF-Connecting-IP`() = testApplication {
        setupTestApplication()

        suspend fun loginWith(header: String, value: String) =
            jsonClient.post("/auth/login") {
                contentType(ContentType.Application.Json)
                header(header, value)
                setBody(LoginRequest("nobody@example.com", "password123"))
            }.status

        // A client rotating X-Forwarded-For values still shares one bucket
        val spoofed = (1..11).map { loginWith("X-Forwarded-For", "10.0.0.$it") }
        assertEquals(HttpStatusCode.TooManyRequests, spoofed.last())

        // Distinct clients behind Cloudflare get their own buckets
        assertEquals(HttpStatusCode.Unauthorized, loginWith("CF-Connecting-IP", "203.0.113.7"))
    }
}
