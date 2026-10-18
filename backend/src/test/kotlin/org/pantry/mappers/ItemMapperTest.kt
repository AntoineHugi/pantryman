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
        val quantity = 1.5
        val isChecked = false

        every { row[ItemTable.id] } returns id
        every { row[ItemTable.name] } returns name
        every { row[ItemTable.quantity] } returns java.math.BigDecimal("1.500")
        every { row[ItemTable.unit] } returns "kg"
        every { row[ItemTable.fromRecipes] } returns null
        every { row[ItemTable.isChecked] } returns isChecked

        val result = row.toItem()

        assertEquals(id.toString(), result.id)
        assertEquals(name, result.name)
        assertEquals(quantity, result.quantity)
        assertEquals("kg", result.unit)
        assertEquals(isChecked, result.isChecked)
    }
}
