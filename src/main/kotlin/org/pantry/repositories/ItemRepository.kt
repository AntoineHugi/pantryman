package org.pantry.repositories

import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.pantry.models.Item
import org.pantry.postgres.tables.ItemTable
import org.pantry.mappers.toItem
import java.util.UUID

class ItemRepository {
    fun getAll(): List<Item> = transaction {
        ItemTable.selectAll().map { it.toItem() }
    }

    fun getById(id: UUID): Item? = transaction {
        ItemTable.selectAll().where { ItemTable.id eq id }
            .map { it.toItem() }
            .singleOrNull()
    }

    fun getByListId(listId: UUID): List<Item> = transaction {
        ItemTable.selectAll().where { ItemTable.listId eq listId }
            .map { row ->
                Item(
                    id = row[ItemTable.id].toString(),
                    name = row[ItemTable.name],
                    quantity = row[ItemTable.quantity],
                    isChecked = row[ItemTable.isChecked],
                    isFavorite = row[ItemTable.isFavorite]
                )
            }
    }

    fun create(name: String, quantity: Int, isChecked: Boolean, isFavorite: Boolean): Item = transaction {
        val newId = UUID.randomUUID()
        ItemTable.insert {
            it[id] = newId
            it[ItemTable.name] = name
            it[ItemTable.quantity] = quantity
            it[ItemTable.isChecked] = isChecked
            it[ItemTable.isFavorite] = isFavorite
        }
        Item(newId.toString(), name, quantity, isChecked, isFavorite)
    }

    fun update(id: UUID, name: String, quantity: Int, isChecked: Boolean, isFavorite: Boolean): Boolean = transaction {
        ItemTable.update({ ItemTable.id eq id }) {
            it[ItemTable.name] = name
            it[ItemTable.quantity] = quantity
            it[ItemTable.isChecked] = isChecked
            it[ItemTable.isFavorite] = isFavorite
        } > 0
    }

    fun delete(id: UUID): Boolean = transaction {
        ItemTable.deleteWhere { ItemTable.id eq id }  > 0
    }
}
