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
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import org.koin.core.component.inject
import org.koin.dsl.module
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.deleteAll
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
import org.pantry.repositories.UserRepository
import org.pantry.services.AuthService
import org.pantry.services.EmailService
import org.pantry.postgres.tables.UserTable
import org.pantry.postgres.tables.HouseholdMemberTable
import org.pantry.postgres.tables.HouseholdTable
import org.pantry.repositories.HouseholdRepository

class RoutingAuthApiIT : KoinTest {

    private val emailService = mockk<EmailService>().also {
        coEvery { it.sendVerificationEmail(any(), any()) } just Runs
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

    private suspend fun ApplicationTestBuilder.signup(email: String, password: String = "password123") =
        jsonClient.post("/auth/signup") {
            contentType(ContentType.Application.Json)
            setBody(SignupRequest(email, password))
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
        assertEquals("Registration Failed", errorResponse["error"])
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
