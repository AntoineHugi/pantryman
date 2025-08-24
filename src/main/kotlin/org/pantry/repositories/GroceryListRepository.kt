package org.pantry.repositories

import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.pantry.models.GroceryList
import org.pantry.postgres.tables.GroceryListTable
import org.pantry.mappers.toGroceryList
import java.util.UUID
import org.pantry.repositories.ItemRepository


class GroceryListRepository(
    private val itemRepo: ItemRepository = ItemRepository()
) {
    fun getAll(): List<GroceryList> = transaction {
        GroceryListTable.selectAll().map { row ->
            val listId = row[GroceryListTable.id]
            GroceryList(
                id = listId.toString(),
                name = row[GroceryListTable.name],
                items = itemRepo.getByListId(listId)
            )
        }
    }

    fun getById(id: UUID): GroceryList? = transaction {
        GroceryListTable
            .selectAll().where { GroceryListTable.id eq id }
            .map { row ->
                GroceryList(
                    id = row[GroceryListTable.id].toString(),
                    name = row[GroceryListTable.name],
                    items = itemRepo.getByListId(id)
                )
            }
            .singleOrNull()
    }

    fun create(name: String): GroceryList = transaction {
        val newId = UUID.randomUUID()
        GroceryListTable.insert {
            it[id] = newId
            it[GroceryListTable.name] = name
        }
        GroceryList(newId.toString(), name, items = emptyList())
    }

    fun update(id: UUID, name: String): Boolean = transaction {
        GroceryListTable.update({ GroceryListTable.id eq id }) {
            it[GroceryListTable.name] = name
        } > 0
    }

    fun delete(id: UUID): Boolean = transaction {
        GroceryListTable.deleteWhere { GroceryListTable.id eq id }  > 0
    }
}
