package org.pantry.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jetbrains.exposed.sql.ResultRow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.pantry.models.Item
import org.pantry.repositories.ItemRepository
import org.pantry.services.ItemService
import org.pantry.postgres.tables.ItemTable
import java.util.UUID


class ItemServiceTest {

    @Test
    fun `getAll returns full list`() {

    }

    @Test
    fun `getById returns correct item`() {

    }

    @Test
    fun `create creates item with correct parameters`() {

    }

    @Test
    fun `update updates item correctly`() {

    }

    @Test
    fun `delete deletes item`() {

    }
}
