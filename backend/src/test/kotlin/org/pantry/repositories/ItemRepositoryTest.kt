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
import org.pantry.postgres.tables.UserTable
import org.pantry.postgres.tables.GroceryListTable
import org.pantry.postgres.tables.HouseholdMemberTable
import org.pantry.postgres.tables.HouseholdTable
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
            SchemaUtils.create(UserTable, HouseholdTable, HouseholdMemberTable, GroceryListTable, ItemTable)
        }
        // Lists reference users, so the owner needs a real user row
        createUser(testUserId)
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
    fun `1 - create creates item with correct parameters`() {
        val id = UUID.randomUUID()
        val listId = UUID.randomUUID()
        groceryRepo.create(listId, "Groceries", testUserId)
        val created = repo.create(id, listId, "Butter", 3, false)

        assertEquals("Butter", created.name)
        assertEquals(3, created.quantity)

        val result = repo.getById(listId, id)
        assertNotNull(result)
    }

    @Test
    fun `2 - getAll returns full list`() {
        val listId = UUID.randomUUID()
        groceryRepo.create(listId, "Groceries", testUserId)
        val item1 = repo.create(UUID.randomUUID(), listId, "Milk", 2, false)
        val item2 = repo.create(UUID.randomUUID(), listId, "Bread", 1, false)

        val result = repo.getByListId(listId)

        assertEquals(2, result.size)
        assertEquals(setOf(item1.name, item2.name), result.map { it.name }.toSet())
    }

    @Test
    fun `3 - getById returns correct item`() {
        val id = UUID.randomUUID()
        val listId = UUID.randomUUID()
        groceryRepo.create(listId, "Groceries", testUserId)
        repo.create(id, listId, "Eggs", 12, false)
        val result = checkNotNull(repo.getById(listId, id))

        assertNotNull(result)
        assertEquals("Eggs", result.name)
        assertEquals(12, result.quantity)
    }

    @Test
    fun `4 - update updates item correctly`() {
        val id = UUID.randomUUID()
        val listId = UUID.randomUUID()
        groceryRepo.create(listId, "Groceries", testUserId)
        repo.create(id, listId, "Eggs", 12, false)

        val updated = repo.update(listId, id, "Green Apples", 10, true)
        assertTrue(updated)

        val result = checkNotNull(repo.getById(listId, id))
        assertEquals("Green Apples", result.name)
        assertEquals(10, result.quantity)
        assertTrue(result.isChecked)
    }

    @Test
    fun `5 - delete deletes item`() {
        val id = UUID.randomUUID()
        val listId = UUID.randomUUID()
        groceryRepo.create(listId, "Groceries", testUserId)
        val created = repo.create(id, listId, "Oranges", 4, false)

        val deleted = repo.delete(listId, id)
        val afterDelete = repo.getById(listId, id)

        assertTrue(deleted)
        assertNull(afterDelete)
    }

    @Test
    fun `6 - item operations are scoped to their list`() {
        val ownListId = UUID.randomUUID()
        val otherListId = UUID.randomUUID()
        groceryRepo.create(ownListId, "Mine", testUserId)
        groceryRepo.create(otherListId, "Theirs", UUID.randomUUID().also { createUser(it) })
        val otherItemId = UUID.randomUUID()
        repo.create(otherItemId, otherListId, "Their milk", 1, false)

        assertNull(repo.getById(ownListId, otherItemId))
        assertFalse(repo.update(ownListId, otherItemId, "Hacked", 1, true))
        assertFalse(repo.delete(ownListId, otherItemId))
        assertEquals("Their milk", repo.getById(otherListId, otherItemId)?.name)
    }

    @Test
    fun `7 - getByListId returns items in creation order after updates`() {
        val listId = UUID.randomUUID()
        groceryRepo.create(listId, "Groceries", testUserId)
        val names = listOf("Milk", "Bread", "Eggs", "Butter")
        val ids = names.map { name -> UUID.randomUUID().also { repo.create(it, listId, name, 1, false) } }

        repo.update(listId, ids[0], "Milk", 1, true)
        repo.update(listId, ids[2], "Eggs", 6, true)

        assertEquals(names, repo.getByListId(listId).map { it.name })
    }

    private fun createUser(id: UUID) {
        UserRepository().create(id, "$id@example.com", "hash", UUID.randomUUID())
    }

    @Test
    fun `8 - deleteMany deletes only the given items of the given list`() {
        val listId = UUID.randomUUID()
        val otherListId = UUID.randomUUID()
        groceryRepo.create(listId, "Mine", testUserId)
        groceryRepo.create(otherListId, "Theirs", UUID.randomUUID().also { createUser(it) })
        val milk = UUID.randomUUID().also { repo.create(it, listId, "Milk", 1, false) }
        val eggs = UUID.randomUUID().also { repo.create(it, listId, "Eggs", 1, false) }
        val bread = UUID.randomUUID().also { repo.create(it, listId, "Bread", 1, false) }
        val theirs = UUID.randomUUID().also { repo.create(it, otherListId, "Their milk", 1, false) }

        val deleted = repo.deleteMany(listId, listOf(milk, eggs, theirs, UUID.randomUUID()))

        assertEquals(2, deleted)
        assertEquals(listOf("Bread"), repo.getByListId(listId).map { it.name })
        assertNotNull(repo.getById(listId, bread))
        assertNotNull(repo.getById(otherListId, theirs))
    }
}
