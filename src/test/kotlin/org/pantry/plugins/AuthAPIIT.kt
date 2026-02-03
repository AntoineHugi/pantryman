package org.pantry.plugins

import io.ktor.server.application.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import io.ktor.server.testing.ApplicationTestBuilder
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import io.ktor.serialization.kotlinx.json.*
import org.junit.jupiter.api.assertNotNull
import org.koin.core.component.inject
import org.koin.dsl.module
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SchemaUtils
import org.koin.test.KoinTest
import org.koin.test.junit5.KoinTestExtension
import org.junit.jupiter.api.extension.RegisterExtension
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import org.junit.jupiter.api.BeforeEach
import com.auth0.jwt.JWT

import org.pantry.models.SignupRequest
import org.pantry.models.LoginRequest
import org.pantry.models.AuthResponse
import org.pantry.repositories.UserRepository
import org.pantry.services.AuthService
import org.pantry.postgres.tables.UserTable

class RoutingAuthApiIT : KoinTest {

    @JvmField
    @RegisterExtension
    val koinTestExtension = KoinTestExtension.create {
        modules(
            module {
                single { UserRepository() }
                single {
                    AuthService(
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
            SchemaUtils.create(UserTable)
        }
    }

    private fun ApplicationTestBuilder.setupTestApplication() {
        application {
            install(ContentNegotiation) {
                json()
            }
            authAPI()
        }
    }

    @Test
    fun `POST signup returns 201 and creates user with valid credentials`() = testApplication {
        setupTestApplication()

        val signupRequest = SignupRequest(
            email = "test@example.com",
            password = "password123"
        )

        val response = client.post("/auth/signup") {
            contentType(ContentType.Application.Json)
            setBody(signupRequest)
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val authResponse: AuthResponse = response.body()
        assertNotNull(authResponse)
        assertEquals(signupRequest.email, authResponse.email)
        assertNotNull(authResponse.token)
        assertNotNull(authResponse.userId)

        // Verify user was created in database
        val savedUser = userRepo.getByEmail(signupRequest.email)
        assertNotNull(savedUser)
        assertEquals(signupRequest.email, savedUser?.email)
    }

    @Test
    fun `POST signup returns 400 with blank email`() = testApplication {
        setupTestApplication()

        val signupRequest = SignupRequest(
            email = "",
            password = "password123"
        )

        val response = client.post("/auth/signup") {
            contentType(ContentType.Application.Json)
            setBody(signupRequest)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val errorResponse: Map<String, String> = response.body()
        assertEquals("Invalid email", errorResponse["error"])
    }

    @Test
    fun `POST signup returns 400 with invalid email`() = testApplication {
        setupTestApplication()

        val signupRequest = SignupRequest(
            email = "notanemail",
            password = "password123"
        )

        val response = client.post("/auth/signup") {
            contentType(ContentType.Application.Json)
            setBody(signupRequest)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val errorResponse: Map<String, String> = response.body()
        assertEquals("Invalid email", errorResponse["error"])
    }

    @Test
    fun `POST signup returns 400 with short password`() = testApplication {
        setupTestApplication()

        val signupRequest = SignupRequest(
            email = "test@example.com",
            password = "short"
        )

        val response = client.post("/auth/signup") {
            contentType(ContentType.Application.Json)
            setBody(signupRequest)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val errorResponse: Map<String, String> = response.body()
        assertEquals("Password must be at least 8 characters", errorResponse["error"])
    }

    @Test
    fun `POST signup returns 400 when user already exists`() = testApplication {
        setupTestApplication()

        val signupRequest = SignupRequest(
            email = "duplicate@example.com",
            password = "password123"
        )

        // First signup should succeed
        val firstResponse = client.post("/auth/signup") {
            contentType(ContentType.Application.Json)
            setBody(signupRequest)
        }
        assertEquals(HttpStatusCode.Created, firstResponse.status)

        // Second signup with same email should fail
        val secondResponse = client.post("/auth/signup") {
            contentType(ContentType.Application.Json)
            setBody(signupRequest)
        }

        assertEquals(HttpStatusCode.BadRequest, secondResponse.status)
        val errorResponse: Map<String, String> = secondResponse.body()
        assertEquals("User already exists", errorResponse["error"])
    }

    @Test
    fun `POST signup returns valid JWT token`() = testApplication {
        setupTestApplication()

        val signupRequest = SignupRequest(
            email = "test@example.com",
            password = "password123"
        )

        val response = client.post("/auth/signup") {
            contentType(ContentType.Application.Json)
            setBody(signupRequest)
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val authResponse: AuthResponse = response.body()

        // Decode and verify JWT token
        val decodedJWT = JWT.decode(authResponse.token)
        assertEquals(authResponse.userId, decodedJWT.getClaim("userId").asString())
        assertEquals("test-issuer", decodedJWT.issuer)
        assertEquals("test-audience", decodedJWT.audience[0])
        assertNotNull(decodedJWT.expiresAt)
    }

    @Test
    fun `POST login returns 200 with valid credentials`() = testApplication {
        setupTestApplication()

        // First create a user
        val signupRequest = SignupRequest(
            email = "login@example.com",
            password = "password123"
        )
        client.post("/auth/signup") {
            contentType(ContentType.Application.Json)
            setBody(signupRequest)
        }

        // Then try to login
        val loginRequest = LoginRequest(
            email = "login@example.com",
            password = "password123"
        )

        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(loginRequest)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val authResponse: AuthResponse = response.body()
        assertNotNull(authResponse)
        assertEquals(loginRequest.email, authResponse.email)
        assertNotNull(authResponse.token)
        assertNotNull(authResponse.userId)
    }

    @Test
    fun `POST login returns 401 with non-existent email`() = testApplication {
        setupTestApplication()

        val loginRequest = LoginRequest(
            email = "notfound@example.com",
            password = "password123"
        )

        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(loginRequest)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val errorResponse: Map<String, String> = response.body()
        assertEquals("Invalid credentials", errorResponse["error"])
    }

    @Test
    fun `POST login returns 401 with incorrect password`() = testApplication {
        setupTestApplication()

        // First create a user
        val signupRequest = SignupRequest(
            email = "test@example.com",
            password = "correctpassword"
        )
        client.post("/auth/signup") {
            contentType(ContentType.Application.Json)
            setBody(signupRequest)
        }

        // Try to login with wrong password
        val loginRequest = LoginRequest(
            email = "test@example.com",
            password = "wrongpassword"
        )

        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(loginRequest)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val errorResponse: Map<String, String> = response.body()
        assertEquals("Invalid credentials", errorResponse["error"])
    }

    @Test
    fun `POST login returns valid JWT token`() = testApplication {
        setupTestApplication()

        // First create a user
        val signupRequest = SignupRequest(
            email = "test@example.com",
            password = "password123"
        )
        val signupResponse = client.post("/auth/signup") {
            contentType(ContentType.Application.Json)
            setBody(signupRequest)
        }
        val signupAuth: AuthResponse = signupResponse.body()

        // Then login
        val loginRequest = LoginRequest(
            email = "test@example.com",
            password = "password123"
        )

        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(loginRequest)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val authResponse: AuthResponse = response.body()

        // Verify JWT token
        val decodedJWT = JWT.decode(authResponse.token)
        assertEquals(signupAuth.userId, decodedJWT.getClaim("userId").asString())
        assertEquals("test-issuer", decodedJWT.issuer)
        assertEquals("test-audience", decodedJWT.audience[0])
    }

    @Test
    fun `login and signup return same userId for same user`() = testApplication {
        setupTestApplication()

        // Signup
        val signupRequest = SignupRequest(
            email = "test@example.com",
            password = "password123"
        )
        val signupResponse = client.post("/auth/signup") {
            contentType(ContentType.Application.Json)
            setBody(signupRequest)
        }
        val signupAuth: AuthResponse = signupResponse.body()

        // Login
        val loginRequest = LoginRequest(
            email = "test@example.com",
            password = "password123"
        )
        val loginResponse = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(loginRequest)
        }
        val loginAuth: AuthResponse = loginResponse.body()

        // Should return same userId
        assertEquals(signupAuth.userId, loginAuth.userId)
    }
}