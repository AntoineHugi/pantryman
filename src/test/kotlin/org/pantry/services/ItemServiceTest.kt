package org.pantry.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.clearMocks
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import org.pantry.models.Item
import org.pantry.models.ItemUpdateRequest
import org.pantry.repositories.ItemRepository
import java.util.UUID


class ItemServiceTest {

    private val repo = mockk<ItemRepository>()
    private val service = ItemService(repo)

    private val listId = UUID.randomUUID()

    @AfterEach fun tearDown() = clearMocks(repo)

    @Test
    fun `getAll should return items from repo`() {
        val items = listOf(Item(UUID.randomUUID().toString(), "Milk", 2, false, false))

        every { repo.getByListId(listId) } returns items

        val result = service.getAll(listId)

        assertEquals(items, result)
        verify(exactly = 1) { repo.getByListId(listId) }
    }

    @Test
    fun `getById should return item from repo`() {
        val id = UUID.randomUUID()
        val item = Item(id.toString(), "Milk", 1, false, false)

        every { repo.getById(listId, id) } returns item

        val result = service.getById(listId, id)

        assertEquals(item, result)
        verify(exactly = 1) { repo.getById(listId, id) }
    }

    @Test
    fun `getById with wrong id returns null`() {
        val id = UUID.randomUUID()
        every { repo.getById(listId, id) } returns null

        val result = service.getById(listId, id)

        assertNull(result)
        verify(exactly = 1) { repo.getById(listId, id) }
    }

    @Test
    fun `create should call repo with generated UUID and return created item`() {
        val name = "Bread"
        val quantity = 3
        val createdItem = Item(UUID.randomUUID().toString(), name, quantity, false, false)

        every { repo.create(any(), listId, name, quantity, false) } returns createdItem

        val result = service.create(listId, name, quantity)

        assertEquals(createdItem, result)
        verify(exactly = 1) { repo.create(any(), listId, name, quantity, false) }
    }


    @Test
    fun `update should modify and return true`() {
        val id = UUID.randomUUID()
        val existing = Item(id.toString(), "Eggs", 5, false, false)
        val update = ItemUpdateRequest(name = "Milk", quantity = 6, isChecked = true, isFavorite = true)

        every { repo.getById(listId, id) } returns existing
        every { repo.update(listId, id, "Milk", 6, true, true) } returns true

        val result = service.update(listId, id, update)

        assertTrue(result)
        verify {
            repo.getById(listId, id)
            repo.update(listId, id, "Milk", 6, true, true)
        }
    }

    @Test
    fun `update should return false if item not found`() {
        val id = UUID.randomUUID()
        every { repo.getById(listId, id) } returns null

        val result = service.update(listId, id, ItemUpdateRequest(name = "Nothing"))

        assertFalse(result)
        verify(exactly = 1) { repo.getById(listId, id) }
        verify(exactly = 0) { repo.update(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `update should throw if name is blank`() {
        val id = UUID.randomUUID()
        val existing = Item(id.toString(), "Milk", 2, false, false)
        every { repo.getById(listId, id) } returns existing

        val update = ItemUpdateRequest(name = " ")

        val exception = assertThrows<IllegalArgumentException> {
            service.update(listId, id, update)
        }

        assertEquals("Name cannot be blank", exception.message)
    }

    @Test
    fun `update should throw if quantity below 1`() {
        val id = UUID.randomUUID()
        val existing = Item(id.toString(), "Milk", 2, false, false)
        every { repo.getById(listId, id) } returns existing

        val update = ItemUpdateRequest(quantity = 0)

        val exception = assertThrows<IllegalArgumentException> {
            service.update(listId, id, update)
        }

        assertEquals("Quantity cannot be below 1", exception.message)
    }

    @Test
    fun `delete should delegate to repo`() {
        val id = UUID.randomUUID()
        every { repo.delete(listId, id) } returns true

        val result = service.delete(listId, id)

        assertTrue(result)
        verify(exactly = 1) { repo.delete(listId, id) }
    }
}
