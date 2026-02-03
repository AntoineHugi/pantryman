package org.pantry.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.clearMocks
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import org.pantry.models.GroceryList
import org.pantry.models.Item
import org.pantry.repositories.GroceryListRepository
import org.pantry.repositories.ItemRepository
import java.util.UUID

class GroceryListServiceTest {

    private val repo = mockk<GroceryListRepository>()
    private val itemRepo = mockk<ItemRepository>()
    private val service = GroceryListService(repo)
    private val itemService = ItemService(itemRepo)

    // Shared test user ID
    private val testUserId = UUID.randomUUID()

    @AfterEach
    fun tearDown() {
        clearMocks(repo, itemRepo)
    }

    @Test
    fun `getAll should return grocery lists from repo for user`() {
        val id = UUID.randomUUID()
        val itemId = UUID.randomUUID()
        val groceryList = GroceryList(
            id.toString(),
            "Groceries",
            listOf(Item(itemId.toString(), "Milk", 2, false, false)),
            testUserId.toString()
        )

        every { repo.getAll(testUserId) } returns listOf(groceryList)

        val result = service.getAll(testUserId)

        assertEquals(listOf(groceryList), result)
        verify(exactly = 1) { repo.getAll(testUserId) }
    }

    @Test
    fun `getById returns correct list for user`() {
        val id = UUID.randomUUID()
        val itemId = UUID.randomUUID()
        val groceryList = GroceryList(
            id.toString(),
            "Groceries",
            listOf(Item(itemId.toString(), "Milk", 2, false, false)),
            testUserId.toString()
        )

        every { repo.getById(id, testUserId) } returns groceryList

        val result = service.getById(id, testUserId)

        assertEquals(groceryList, result)
        verify(exactly = 1) { repo.getById(id, testUserId) }
    }

    @Test
    fun `getById with wrong id returns null`() {
        val id = UUID.randomUUID()
        every { repo.getById(id, testUserId) } returns null

        val result = service.getById(id, testUserId)

        assertNull(result)
        verify(exactly = 1) { repo.getById(id, testUserId) }
    }

    @Test
    fun `create creates list with correct parameters`() {
        val name = "Groceries"
        val created = GroceryList(
            UUID.randomUUID().toString(),
            name,
            emptyList(),
            testUserId.toString()
        )
        every { repo.create(any(), name, testUserId) } returns created

        val result = service.create(name, testUserId)

        assertEquals(created, result)
        verify(exactly = 1) { repo.create(any(), name, testUserId) }
    }

    @Test
    fun `update should modify and return true for owner`() {
        val id = UUID.randomUUID()
        val itemId = UUID.randomUUID()
        val existing = GroceryList(
            id.toString(),
            "Groceries",
            listOf(Item(itemId.toString(), "Milk", 2, false, false)),
            testUserId.toString()
        )
        val newName = "Weekly Groceries"

        every { repo.getById(id, testUserId) } returns existing
        every { repo.update(id, newName, testUserId) } returns true

        val result = service.update(id, newName, testUserId)

        assertTrue(result)
        verify {
            repo.getById(id, testUserId)
            repo.update(id, newName, testUserId)
        }
    }

    @Test
    fun `update should return false if item not found`() {
        val id = UUID.randomUUID()
        val newName = "Weekly Groceries"
        every { repo.getById(id, testUserId) } returns null

        val result = service.update(id, newName, testUserId)

        assertFalse(result)
        verify(exactly = 1) { repo.getById(id, testUserId) }
        verify(exactly = 0) { repo.update(any(), any(), any()) }
    }

    @Test
    fun `update should throw if name is blank`() {
        val id = UUID.randomUUID()
        val itemId = UUID.randomUUID()
        val existing = GroceryList(
            id.toString(),
            "Groceries",
            listOf(Item(itemId.toString(), "Milk", 2, false, false)),
            testUserId.toString()
        )
        val newName = " "
        every { repo.getById(id, testUserId) } returns existing

        val exception = assertThrows<IllegalArgumentException> {
            service.update(id, newName, testUserId)
        }
        assertEquals("Name cannot be blank", exception.message)
    }

    @Test
    fun `delete should delegate to repo for owner`() {
        val id = UUID.randomUUID()
        every { repo.delete(id, testUserId) } returns true

        val result = service.delete(id, testUserId)

        assertTrue(result)
        verify { repo.delete(id, testUserId) }
    }
}