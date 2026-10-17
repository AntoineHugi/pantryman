package org.pantry.repositories

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inSubQuery
import org.pantry.models.GroceryList
import org.pantry.postgres.tables.GroceryListTable
import org.pantry.postgres.tables.HouseholdMemberTable
import org.pantry.mappers.toGroceryList
import java.util.UUID

// A user can use a list they own, or one shared with a household they belong to.
// Every query is scoped by that rule, so an unknown list and someone else's list look the same.
class GroceryListRepository(
    private val itemRepo: ItemRepository
) {
    private fun accessibleBy(userID: UUID): Op<Boolean> {
        val userHouseholds = HouseholdMemberTable
            .select(HouseholdMemberTable.householdId)
            .where { HouseholdMemberTable.userId eq userID }
        return (GroceryListTable.userID eq userID) or (GroceryListTable.householdId inSubQuery userHouseholds)
    }

    fun getAll(userID: UUID): List<GroceryList> = transaction {
        GroceryListTable.selectAll().where { accessibleBy(userID) }
            .orderBy(GroceryListTable.createdAt to SortOrder.ASC, GroceryListTable.id to SortOrder.ASC)
            .map { it.toGroceryList(itemRepo) }
    }

    fun getById(id: UUID, userID: UUID): GroceryList? = transaction {
        GroceryListTable
            .selectAll().where { accessibleBy(userID) and (GroceryListTable.id eq id) }
            .map { it.toGroceryList(itemRepo) }
            .singleOrNull()
    }

    fun create(newId: UUID, name: String, userID: UUID, householdId: UUID? = null): GroceryList = transaction {
        GroceryListTable.insert {
            it[id] = newId
            it[GroceryListTable.name] = name
            it[GroceryListTable.userID] = userID
            it[GroceryListTable.householdId] = householdId
        }
        GroceryList(newId.toString(), name, emptyList(), userID.toString(), householdId?.toString())
    }

    fun update(id: UUID, name: String, userID: UUID): Boolean = transaction {
        GroceryListTable.update({ accessibleBy(userID) and (GroceryListTable.id eq id) }) {
            it[GroceryListTable.name] = name
        } > 0
    }

    // Only the owner may change who a list is shared with
    fun setHousehold(id: UUID, householdId: UUID?, ownerId: UUID): Boolean = transaction {
        GroceryListTable.update({ (GroceryListTable.userID eq ownerId) and (GroceryListTable.id eq id) }) {
            it[GroceryListTable.householdId] = householdId
        } > 0
    }

    fun delete(id: UUID, userID: UUID): Boolean = transaction {
        GroceryListTable.deleteWhere { accessibleBy(userID) and (GroceryListTable.id eq id) } > 0
    }
}
