package org.pantry.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.clearMocks
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
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

    @AfterEach
    fun tearDown() {
        clearMocks(repo, itemRepo)
    }

    @Test
    fun `getAll should return grocecry lists from repo`() {
        val id = UUID.randomUUID()
        val groceryList = GroceryList(id, "Groceries", listOf(Item(itemId, "Milk", 2, false, false)))

        every { repo.getAll() } returns listOf(groceryList)

        val result = service.getAll()

        assertEquals(listOf(groceryList), result)
        verify(exactly = 1) { repo.getAll() }
    }

    @Test
    fun `getById returns correct list`() {
        val id = UUID.randomUUID()
        val itemId = UUID.randomUUID()
        val groceryList = GroceryList(id, "Groceries", listOf(Item(itemId, "Milk", 2, false, false)))

        every { repo.getById(id) } returns groceryList

        val result = service.getById(id)

        assertEquals(groceryList, result)
        verify(exactly = 1) { repo.getById(id) }
    }

    @Test
    fun `getById with wrong id returns correct list`() {
        val id = UUID.randomUUID()
        every { repo.getById(id) } returns null

        val result = service.getById(id)

        assertNull(result)
        verify(exactly = 1) { repo.getById(id) }
    }

    @Test
    fun `create creates list with correct parameters`() {

        val name = "Groceries"
        val created = GroceryList(UUID.randomUUID(), name)
        every { repo.create(any(), name) } returns created

        val result = service.create(name)

        assertEquals(created, result)
        verify(exactly = 1) { repo.create(any(), name) }
    }

    @Test
    fun `update should modify and return true`() {
        val id = UUID.randomUUID()
        val itemId = UUID.randomUUID()
        val existing = GroceryList(id, "Groceries", listOf(Item(itemId, "Milk", 2, false, false)))
        val newName = "Weekly Groceries"

        every { repo.getById(id) } returns existing
        every { repo.update(id, newName) } returns true

        val result = service.update(id, newName)

        assertTrue(result)
        verify {
            repo.getById(id)
            repo.update(id, newName)
        }
    }

    @Test
    fun `update should return false if item not found`() {
        val id = UUID.randomUUID()
        val newName = "Weekly Groceries"
        every { repo.getById(id) } returns null

        val result = service.update(id, newName)

        assertFalse(result)
        verify(exactly = 1) { repo.getById(id) }
        verify(exactly = 0) { repo.update(any(), any()) }
    }

    @Test
    fun `update should throw if name is blank`() {
        val id = UUID.randomUUID()
        val itemId = UUID.randomUUID()
        val existing = GroceryList(id, "Groceries", listOf(Item(itemId, "Milk", 2, false, false)))
        val newName = " "
        every { repo.getById(id) } returns existing

        val exception = assertThrows<IllegalArgumentException> {
            service.update(id, newName)
        }
        assertEquals("Name cannot be blank", exception.message)
    }

    @Test
    fun `delete should delegate to repo`() {
        val id = UUID.randomUUID()
        every { repo.delete(id) } returns true

        val result = service.delete(id)

        assertTrue(result)
        verify { repo.delete(id) }
    }

}