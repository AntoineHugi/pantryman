package org.pantry.mappers

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jetbrains.exposed.sql.ResultRow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.pantry.postgres.tables.GroceryListTable
import org.pantry.repositories.ItemRepository
import org.pantry.models.Item
import java.util.UUID

class GroceryListMapperTest {

    private val testUserId = UUID.randomUUID()

    @Test
    fun `toGroceryList maps id name items and userId`() {
        val row = mockk<ResultRow>()
        val repo = mockk<ItemRepository>()
        val listId: UUID = UUID.randomUUID()
        val name = "Weekly Shopping"
        val items = listOf(Item(id = "1", name = "Milk", quantity = 1))

        every { row[GroceryListTable.id] } returns listId
        every { row[GroceryListTable.name] } returns name
        every { row[GroceryListTable.userID] } returns testUserId
        every { repo.getByListId(listId) } returns items

        val result = row.toGroceryList(repo)

        assertEquals(listId.toString(), result.id)
        assertEquals(name, result.name)
        assertEquals(items, result.items)
        assertEquals(testUserId.toString(), result.userId)

        verify(exactly = 1) { repo.getByListId(listId) }
    }
}