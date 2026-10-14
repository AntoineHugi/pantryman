package org.pantry.repositories

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.pantry.models.Item
import org.pantry.postgres.tables.ItemTable
import org.pantry.mappers.toItem
import java.util.UUID

// Every item lookup and mutation is scoped by listId, so callers that have
// verified list ownership can't reach items in someone else's list.
class ItemRepository {
    fun getByListId(listId: UUID): List<Item> = transaction {
        ItemTable.selectAll()
            .where { ItemTable.listId eq listId }
            .orderBy(ItemTable.createdAt to SortOrder.ASC, ItemTable.id to SortOrder.ASC)
            .map { it.toItem() }
    }

    fun getById(listId: UUID, id: UUID): Item? = transaction {
        ItemTable.selectAll()
            .where { (ItemTable.listId eq listId) and (ItemTable.id eq id) }
            .map { it.toItem() }
            .singleOrNull()
    }

    fun create(newId: UUID, listId: UUID, name: String, quantity: Int, initBool : Boolean): Item = transaction {
        ItemTable.insert {
            it[id] = newId
            it[ItemTable.name] = name
            it[ItemTable.quantity] = quantity
            it[ItemTable.isChecked] = initBool
            it[ItemTable.isFavorite] = initBool
            it[ItemTable.listId] = listId
        }
        Item(newId.toString(), name, quantity, initBool, initBool)
    }

    fun update(listId: UUID, id: UUID, name: String, quantity: Int, isChecked: Boolean, isFavorite: Boolean): Boolean = transaction {
        ItemTable.update({ (ItemTable.listId eq listId) and (ItemTable.id eq id) }) {
            it[ItemTable.name] = name
            it[ItemTable.quantity] = quantity
            it[ItemTable.isChecked] = isChecked
            it[ItemTable.isFavorite] = isFavorite
        } > 0
    }

    fun delete(listId: UUID, id: UUID): Boolean = transaction {
        ItemTable.deleteWhere { (ItemTable.listId eq listId) and (ItemTable.id eq id) } > 0
    }
}
