package org.pantry.repositories

import org.jetbrains.exposed.sql.*
import org.junit.jupiter.api.*
import org.testcontainers.containers.PostgreSQLContainer
import org.pantry.models.GroceryList
import org.pantry.models.Item
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
        val created = repo.create(listId, "Groceries")
        val found = repo.getById(listId)

        assertNotNull(found)
        assertEquals("Groceries", found.name)
    }

    @Test
    fun `4 - update updates item correctly`() {
        val listId = UUID.randomUUID()
        val created = repo.create(UUID.randomUUID(), "Groceries")
        val id = UUID.fromString(created.id)

        val updated = repo.update(id, "Weekly Groceries")

        assertTrue(updated)
        val result = repo.getById(id)
        assertNotNull(found)
        assertEquals("Weekly Groceries", result.name)
    }

    @Test
    fun `5 - delete deletes item`() {
        val listId = UUID.randomUUID()
        val created = repo.create(UUID.randomUUID(), "Groceries")
        val id = UUID.fromString(created.id)

        val deleted = repo.delete(id)
        val afterDelete = repo.getById(id)

        assertTrue(deleted)
        assertNull(afterDelete)
    }
}
