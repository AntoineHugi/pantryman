package org.pantry.repositories

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SchemaUtils
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.testcontainers.containers.PostgreSQLContainer
import org.pantry.postgres.tables.ItemTable
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.DisplayName::class)
class ItemRepositoryTest {

    private val postgres = PostgreSQLContainer("postgres:15").apply {
        withDatabaseName("pantryman_test")
        withUsername("user")
        withPassword("password")
        start()
    }

    private val repo = ItemRepository()
    private val groceryRepo = GroceryListRepository(repo)

    @BeforeAll
    fun setup() {
        Database.connect(
            url = postgres.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = postgres.username,
            password = postgres.password
        )
        transaction { SchemaUtils.create(ItemTable) }
    }

    @AfterEach
    fun cleanup() {
        transaction {
            ItemTable.deleteAll()
        }
    }

    @AfterAll
    fun teardown() {
        postgres.stop()
    }

    @Test
    fun `1 - create creates item with correct parameters`() {
        val id = UUID.randomUUID()
        val listId = UUID.randomUUID()
        groceryRepo.create(listId, "Groceries")
        val created = repo.create(id, listId.toString(), "Butter", 3, false)

        assertEquals("Butter", created.name)
        assertEquals(3, created.quantity)

        val result = repo.getById(id)
        assertNotNull(result)
    }

    @Test
    fun `2 - getAll returns full list`() {
        val listId = UUID.randomUUID()
        groceryRepo.create(listId, "Groceries")
        val item1 = repo.create(UUID.randomUUID(), listId.toString(), "Milk", 2, false)
        val item2 = repo.create(UUID.randomUUID(), listId.toString(), "Bread", 1, false)

        val result = repo.getAll(listId)

        assertEquals(2, result.size)
        assertEquals(setOf(item1.name, item2.name), result.map { it.name }.toSet())
    }

    @Test
    fun `3 - getById returns correct item`() {
        val id = UUID.randomUUID()
        val listId = UUID.randomUUID()
        groceryRepo.create(listId, "Groceries")
        repo.create(id, listId.toString(), "Eggs", 12, false)
        val result = checkNotNull(repo.getById(id))

        assertNotNull(result)
        assertEquals("Eggs", result.name)
        assertEquals(12, result.quantity)
    }

    @Test
    fun `4 - update updates item correctly`() {
        val id = UUID.randomUUID()
        val listId = UUID.randomUUID()
        groceryRepo.create(listId, "Groceries")
        repo.create(id, listId.toString(), "Eggs", 12, false)

        val updated = repo.update(id, "Green Apples", 10, true, true)
        assertTrue(updated)

        val result = checkNotNull(repo.getById(id))
        assertEquals("Green Apples", result.name)
        assertEquals(10, result.quantity)
        assertTrue(result.isChecked)
        assertTrue(result.isFavorite)
    }

    @Test
    fun `5 - delete deletes item`() {
        val id = UUID.randomUUID()
        val listId = UUID.randomUUID()
        groceryRepo.create(listId, "Groceries")
        val created = repo.create(id, listId.toString(), "Oranges", 4, false)

        val deleted = repo.delete(id)
        val afterDelete = repo.getById(id)

        assertTrue(deleted)
        assertNull(afterDelete)
    }
}
