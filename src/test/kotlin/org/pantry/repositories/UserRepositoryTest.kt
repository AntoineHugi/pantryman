package org.pantry.repositories

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SchemaUtils
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.testcontainers.containers.PostgreSQLContainer
import org.pantry.postgres.tables.UserTable
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.DisplayName::class)
class UserRepositoryTest {

    private val postgres = PostgreSQLContainer("postgres:15").apply {
        withDatabaseName("pantryman_test")
        withUsername("user")
        withPassword("password")
        start()
    }

    private val repo = UserRepository()

    @BeforeAll
    fun setup() {
        Database.connect(
            url = postgres.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = postgres.username,
            password = postgres.password
        )
        transaction {
            SchemaUtils.create(UserTable)
        }
    }

    @AfterEach
    fun cleanup() {
        transaction {
            UserTable.deleteAll()
        }
    }

    @AfterAll
    fun teardown() {
        postgres.stop()
    }

    @Test
    fun `1 - create creates user with correct parameters`() {
        val userId = UUID.randomUUID()
        val email = "test@example.com"
        val passwordHash = "hashedpassword123"

        val created = repo.create(userId, email, passwordHash, UUID.randomUUID())

        assertEquals(userId.toString(), created.id)
        assertEquals(email, created.email)
        assertEquals(passwordHash, created.passwordHash)
    }

    @Test
    fun `2 - getByEmail returns correct user`() {
        val userId = UUID.randomUUID()
        val email = "test@example.com"
        val passwordHash = "hashedpassword123"
        repo.create(userId, email, passwordHash, UUID.randomUUID())

        val result = repo.getByEmail(email)
        assertTrue(result != null)

        assertEquals(userId.toString(), result?.id)
        assertEquals(email, result?.email)
        assertEquals(passwordHash, result?.passwordHash)
    }

    @Test
    fun `3 - getByEmail returns null for non-existent email`() {
        val result = repo.getByEmail("notfound@example.com")
        assertTrue(result == null)
    }

    @Test
    fun `4 - getById returns correct user`() {
        val userId = UUID.randomUUID()
        val email = "test@example.com"
        val passwordHash = "hashedpassword123"
        repo.create(userId, email, passwordHash, UUID.randomUUID())

        val result = repo.getById(userId)
        assertTrue(result != null)

        assertEquals(userId.toString(), result?.id)
        assertEquals(email, result?.email)
        assertEquals(passwordHash, result?.passwordHash)
    }

    @Test
    fun `5 - getById returns null for non-existent id`() {
        val result = repo.getById(UUID.randomUUID())
        assertTrue(result == null)
    }

    @Test
    fun `6 - create enforces unique email constraint`() {
        val userId1 = UUID.randomUUID()
        val userId2 = UUID.randomUUID()
        val email = "duplicate@example.com"

        repo.create(userId1, email, "password1", UUID.randomUUID())
        assertThrows<Exception> {
            repo.create(userId2, email, "password2", UUID.randomUUID())
        }
    }


    @Test
    fun `7 - getByEmail is case insensitive`() {
        val userId = UUID.randomUUID()
        repo.create(userId, "Test@Example.com", "password", UUID.randomUUID())

        val foundExact = repo.getByEmail("Test@Example.com")
        val foundLower = repo.getByEmail("test@example.com")

        assertEquals(userId.toString(), foundExact?.id)
        assertEquals(userId.toString(), foundLower?.id)
    }
}
