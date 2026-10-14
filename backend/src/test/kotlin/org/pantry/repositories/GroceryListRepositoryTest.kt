package org.pantry.repositories

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.testcontainers.containers.PostgreSQLContainer
import org.pantry.postgres.tables.GroceryListTable
import org.pantry.postgres.tables.ItemTable
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.DisplayName::class)
class GroceryListRepositoryTest {

    private val postgres = PostgreSQLContainer("postgres:15").apply {
        withDatabaseName("pantryman_test")
        withUsername("user")
        withPassword("password")
        start()
    }

    private val itemRepo = ItemRepository()
    private val repo = GroceryListRepository(itemRepo)

    // Shared test user ID
    private val testUserId = UUID.randomUUID()

    @BeforeAll
    fun setup() {
        Database.connect(
            url = postgres.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = postgres.username,
            password = postgres.password
        )
        transaction {
            SchemaUtils.create(GroceryListTable, ItemTable)
        }
    }

    @AfterEach
    fun cleanup() {
        transaction {
            ItemTable.deleteAll()
            GroceryListTable.deleteAll()
        }
    }

    @AfterAll
    fun teardown() {
        postgres.stop()
    }

    @Test
    fun `1 - create creates list with correct parameters`() {
        val listId = UUID.randomUUID()
        val created = repo.create(listId, "Groceries", testUserId)

        assertEquals("Groceries", created.name)
        assertEquals(testUserId.toString(), created.userId)
    }

    @Test
    fun `2 - getAll returns all lists for user`() {
        val list1 = repo.create(UUID.randomUUID(), "Weekly Groceries", testUserId)
        val list2 = repo.create(UUID.randomUUID(), "Groceries", testUserId)

        // Create a list for a different user (should not be returned)
        val otherUserId = UUID.randomUUID()
        repo.create(UUID.randomUUID(), "Other User's List", otherUserId)

        val result = repo.getAll(testUserId)

        assertEquals(2, result.size)
        assertEquals(setOf(list1.name, list2.name), result.map { it.name }.toSet())
    }

    @Test
    fun `3 - getById returns correct list for user`() {
        val listId = UUID.randomUUID()
        repo.create(listId, "Groceries", testUserId)

        val result = checkNotNull(repo.getById(listId, testUserId))
        assertEquals("Groceries", result.name)
    }

    @Test
    fun `4 - getById returns null for different user`() {
        val listId = UUID.randomUUID()
        repo.create(listId, "Groceries", testUserId)

        val otherUserId = UUID.randomUUID()
        val result = repo.getById(listId, otherUserId)

        assertNull(result)
    }

    @Test
    fun `5 - update updates list correctly for owner`() {
        val listId = UUID.randomUUID()
        repo.create(listId, "Groceries", testUserId)

        val updated = repo.update(listId, "Weekly Groceries", testUserId)
        assertTrue(updated)

        val result = checkNotNull(repo.getById(listId, testUserId))
        assertNotNull(result)
        assertEquals("Weekly Groceries", result.name)
    }

    @Test
    fun `6 - update returns false for different user`() {
        val listId = UUID.randomUUID()
        repo.create(listId, "Groceries", testUserId)

        val otherUserId = UUID.randomUUID()
        val updated = repo.update(listId, "Weekly Groceries", otherUserId)

        assertFalse(updated)
    }

    @Test
    fun `7 - delete deletes list for owner`() {
        val listId = UUID.randomUUID()
        repo.create(listId, "Groceries", testUserId)

        val deleted = repo.delete(listId, testUserId)
        val afterDelete = repo.getById(listId, testUserId)

        assertTrue(deleted)
        assertNull(afterDelete)
    }

    @Test
    fun `8 - delete returns false for different user`() {
        val listId = UUID.randomUUID()
        repo.create(listId, "Groceries", testUserId)

        val otherUserId = UUID.randomUUID()
        val deleted = repo.delete(listId, otherUserId)

        assertFalse(deleted)

        // List should still exist
        val stillExists = repo.getById(listId, testUserId)
        assertNotNull(stillExists)
    }
}