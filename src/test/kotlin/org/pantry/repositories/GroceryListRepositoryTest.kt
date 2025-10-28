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
        val created = repo.create(listId, "Groceries")

        assertEquals("Groceries", created.name)
    }

    @Test
    fun `2 - getAll returns all lists`() {
        val list1 = repo.create(UUID.randomUUID(), "Weekly Groceries")
        val list2 = repo.create(UUID.randomUUID(), "Groceries")

        val result = repo.getAll()

        assertEquals(2, result.size)
        assertEquals(setOf(list1.name, list2.name), result.map { it.name }.toSet())
    }

    @Test
    fun `3 - getById returns correct list`() {
        val listId = UUID.randomUUID()
        repo.create(listId, "Groceries")

        val result = checkNotNull(repo.getById(listId))
        assertEquals("Groceries", result.name)
    }

    @Test
    fun `4 - update updates list correctly`() {
        val listId = UUID.randomUUID()
        repo.create(listId, "Groceries")

        val updated = repo.update(listId, "Weekly Groceries")
        assertTrue(updated)

        val result = checkNotNull(repo.getById(listId))
        assertNotNull(result)
        assertEquals("Weekly Groceries", result.name)
    }

    @Test
    fun `5 - delete deletes list`() {
        val listId = UUID.randomUUID()
        repo.create(listId, "Groceries")

        val deleted = repo.delete(listId)
        val afterDelete = repo.getById(listId)

        assertTrue(deleted)
        assertNull(afterDelete)
    }
}
