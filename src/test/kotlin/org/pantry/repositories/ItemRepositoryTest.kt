package org.pantry.repositories

import org.jetbrains.exposed.sql.*
import org.junit.jupiter.api.*
import org.testcontainers.containers.PostgreSQLContainer
import org.pantry.models.Item
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
    fun cleanup() = transaction { ItemTable.deleteAll() }

    @AfterAll
    fun teardown() {
        postgres.stop()
    }

    @Test
    fun `1 - getAll returns full list`() {
        val listId = UUID.randomUUID()
        val item1 = repo.create(UUID.randomUUID(), listId.toString(), "Milk", 2, false)
        val item2 = repo.create(UUID.randomUUID(), listId.toString(), "Bread", 1, false)

        val result = repo.getAll(listId)

        assertEquals(2, result.size)
        assertEquals(setOf(item1.name, item2.name), result.map { it.name }.toSet())
    }

    @Test
    fun `2 - getById returns correct item`() {
        val listId = UUID.randomUUID()
        val created = repo.create(UUID.randomUUID(), listId.toString(), "Eggs", 12, false)

        val found = repo.getById(UUID.fromString(created.id))

        assertNotNull(found)
        assertEquals("Eggs", found.name)
        assertEquals(12, found.quantity)
    }

    @Test
    fun `3 - create creates item with correct parameters`() {
        val listId = UUID.randomUUID()
        val created = repo.create(UUID.randomUUID(), listId.toString(), "Butter", 3, false)

        assertEquals("Butter", created.name)
        assertEquals(3, created.quantity)

        val fromDb = repo.getById(UUID.fromString(created.id))
        assertNotNull(fromDb)
    }

    @Test
    fun `4 - update updates item correctly`() {
        val listId = UUID.randomUUID()
        val created = repo.create(UUID.randomUUID(), listId.toString(), "Apples", 5, false)
        val id = UUID.fromString(created.id)

        val updated = repo.update(id, "Green Apples", 10, true, true)

        assertTrue(updated)
        val result = repo.getById(id)
        assertNotNull(found)
        assertEquals("Green Apples", result.name)
        assertEquals(10, result.quantity)
        assertTrue(result.isChecked)
        assertTrue(result.isFavorite)
    }

    @Test
    fun `5 - delete deletes item`() {
        val listId = UUID.randomUUID()
        val created = repo.create(UUID.randomUUID(), listId.toString(), "Oranges", 4, false)
        val id = UUID.fromString(created.id)

        val deleted = repo.delete(id)
        val afterDelete = repo.getById(id)

        assertTrue(deleted)
        assertNull(afterDelete)
    }
}
