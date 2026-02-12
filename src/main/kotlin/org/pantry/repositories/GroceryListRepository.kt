package org.pantry.repositories

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.pantry.models.GroceryList
import org.pantry.postgres.tables.GroceryListTable
import org.pantry.mappers.toGroceryList
import java.util.UUID

class GroceryListRepository(
    private val itemRepo: ItemRepository
) {
    fun getAll(userID: UUID): List<GroceryList> = transaction {
        GroceryListTable.selectAll().where { GroceryListTable.userID eq userID }
            .map { it.toGroceryList(itemRepo) }
    }

    fun getById(id: UUID, userID: UUID): GroceryList? = transaction {
        GroceryListTable
            .selectAll().where { (GroceryListTable.userID eq userID) and (GroceryListTable.id eq id) }
            .map { it.toGroceryList(itemRepo) }
            .singleOrNull()
    }

    fun create(newId: UUID, name: String, userID: UUID): GroceryList = transaction {
        GroceryListTable.insert {
            it[id] = newId
            it[GroceryListTable.name] = name
            it[GroceryListTable.userID] = userID
        }
        GroceryList(newId.toString(), name, emptyList(), userID.toString())
    }

    fun update(id: UUID, name: String, userID: UUID): Boolean = transaction {
        GroceryListTable.update({ (GroceryListTable.userID eq userID) and (GroceryListTable.id eq id) }) {
            it[GroceryListTable.name] = name
        } > 0
    }

    fun delete(id: UUID, userID: UUID): Boolean = transaction {
        GroceryListTable.deleteWhere { (GroceryListTable.userID eq userID) and (GroceryListTable.id eq id) }  > 0
    }
}
