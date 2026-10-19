package org.pantry.repositories

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.pantry.models.Item
import org.pantry.postgres.tables.ItemTable
import org.pantry.mappers.toItem
import org.pantry.services.MergePlan
import java.math.BigDecimal
import java.time.Instant
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

    // On any list: client-made IDs must not collide with another list's items
    fun exists(id: UUID): Boolean = transaction {
        ItemTable.select(ItemTable.id).where { ItemTable.id eq id }.any()
    }

    fun create(newId: UUID, listId: UUID, name: String, quantity: Double, initBool : Boolean, unit: String? = null): Item = transaction {
        ItemTable.insert {
            it[id] = newId
            it[ItemTable.name] = name
            it[ItemTable.quantity] = BigDecimal.valueOf(quantity)
            it[ItemTable.unit] = unit
            it[ItemTable.isChecked] = initBool
            it[ItemTable.listId] = listId
        }
        Item(newId.toString(), name, quantity, initBool, unit)
    }

    fun update(listId: UUID, id: UUID, name: String, quantity: Double, isChecked: Boolean, unit: String?): Boolean = transaction {
        ItemTable.update({ (ItemTable.listId eq listId) and (ItemTable.id eq id) }) {
            it[ItemTable.name] = name
            it[ItemTable.quantity] = BigDecimal.valueOf(quantity)
            it[ItemTable.unit] = unit
            it[ItemTable.isChecked] = isChecked
        } > 0
    }

    /**
     * Adds recipe ingredients to the list. The plan is made from the list's items inside the
     * same transaction, so a change by another member can't slip in between.
     */
    fun merge(listId: UUID, plan: (List<Item>) -> MergePlan): MergePlan = transaction {
        val result = plan(getByListId(listId))
        for (change in result.changes) {
            ItemTable.update({ (ItemTable.listId eq listId) and (ItemTable.id eq UUID.fromString(change.itemId)) }) {
                it[quantity] = BigDecimal.valueOf(change.quantity)
                it[unit] = change.unit
                it[fromRecipes] = change.fromRecipes
            }
        }
        // Explicit, increasing timestamps: the list is ordered by created_at, and a batch inserted
        // at the same instant would come back in any order
        val now = Instant.now()
        result.newItems.forEachIndexed { index, item ->
            ItemTable.insert {
                it[id] = UUID.randomUUID()
                it[name] = item.name
                it[quantity] = BigDecimal.valueOf(item.quantity)
                it[unit] = item.unit
                it[fromRecipes] = item.fromRecipes
                it[ItemTable.listId] = listId
                it[createdAt] = now.plusNanos(index * 1000L)
            }
        }
        result
    }

    fun delete(listId: UUID, id: UUID): Boolean = transaction {
        ItemTable.deleteWhere { (ItemTable.listId eq listId) and (ItemTable.id eq id) } > 0
    }

    // One statement, so either all of them go or none. Returns how many were deleted.
    fun deleteMany(listId: UUID, ids: Collection<UUID>): Int = transaction {
        ItemTable.deleteWhere { (ItemTable.listId eq listId) and (ItemTable.id inList ids) }
    }
}
