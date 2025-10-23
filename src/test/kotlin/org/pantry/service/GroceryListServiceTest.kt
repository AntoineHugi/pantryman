package org.pantry.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jetbrains.exposed.sql.ResultRow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.pantry.models.GroceryList
import org.pantry.models.Item
import org.pantry.repositories.GroceryListRepository
import org.pantry.repositories.ItemRepository
import org.pantry.services.GroceryListService
import org.pantry.postgres.tables.GroceryListTable
import org.pantry.postgres.tables.ItemTable
import java.util.UUID


class GroceryListRepositoryTest {

    @Test
    fun `getAll returns full list`() {

    }

    @Test
    fun `getById returns correct list`() {

    }

    @Test
    fun `create creates list with correct parameters`() {

    }

    @Test
    fun `update updates list correctly`() {

    }

    @Test
    fun `delete deletes list`() {

    }

}