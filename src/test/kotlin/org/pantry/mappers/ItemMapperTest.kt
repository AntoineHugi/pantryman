package org.pantry.mappers

import io.mockk.every
import io.mockk.mockk
import org.jetbrains.exposed.sql.ResultRow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.pantry.postgres.tables.ItemTable
import java.util.UUID

class ItemMapperTest {
    @Test
    fun `toItem maps id name quantity and filters`() {
        val row = mockk<ResultRow>()
        val id = UUID.randomUUID()
        val name = "Milk"
        val quantity = 1
        val isChecked = false
        val isFavorite = true

        every { row[ItemTable.id] } returns id
        every { row[ItemTable.name] } returns name
        every { row[ItemTable.quantity] } returns quantity
        every { row[ItemTable.isChecked] } returns isChecked
        every { row[ItemTable.isFavorite] } returns isFavorite

        val result = row.toItem()

        assertEquals(id.toString(), result.id)
        assertEquals(name, result.name)
        assertEquals(quantity, result.quantity)
        assertEquals(isChecked, result.isChecked)
        assertEquals(isFavorite, result.isFavorite)
    }
}
