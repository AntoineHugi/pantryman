package org.pantry.repositories

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.transactions.transaction
import org.pantry.models.PantryItem
import org.pantry.postgres.tables.PantryItemTable
import java.math.BigDecimal
import java.util.UUID

// Where pantry items belong: a household, or one user's personal lists
sealed interface PantryScope {
    data class Personal(val userId: UUID) : PantryScope
    data class Household(val householdId: UUID) : PantryScope
}

class PantryRepository {

    private fun PantryScope.condition(): Op<Boolean> = when (this) {
        is PantryScope.Personal -> (PantryItemTable.userId eq userId) and PantryItemTable.householdId.isNull()
        is PantryScope.Household -> PantryItemTable.householdId eq householdId
    }

    fun getAll(scope: PantryScope): List<PantryItem> = transaction {
        PantryItemTable.selectAll().where { scope.condition() }
            .orderBy(PantryItemTable.createdAt to SortOrder.ASC, PantryItemTable.id to SortOrder.ASC)
            .map { it.toPantryItem() }
    }

    fun get(id: UUID): Pair<PantryScope, PantryItem>? = transaction {
        PantryItemTable.selectAll().where { PantryItemTable.id eq id }.singleOrNull()?.let { row ->
            val scope = row[PantryItemTable.householdId]?.let { PantryScope.Household(it) }
                ?: PantryScope.Personal(row[PantryItemTable.userId]!!)
            scope to row.toPantryItem()
        }
    }

    // One item per name in a scope: adding a name that's there replaces its kind and quantity
    fun upsert(scope: PantryScope, name: String, kind: String, quantity: Double, unit: String?): PantryItem = transaction {
        val existing = PantryItemTable.selectAll()
            .where { scope.condition() and (PantryItemTable.name.lowerCase() eq name.lowercase()) }
            .singleOrNull()
        val id = existing?.get(PantryItemTable.id) ?: UUID.randomUUID()
        if (existing != null) {
            PantryItemTable.update({ PantryItemTable.id eq id }) {
                it[PantryItemTable.name] = name
                it[PantryItemTable.kind] = kind
                it[PantryItemTable.quantity] = BigDecimal.valueOf(quantity)
                it[PantryItemTable.unit] = unit
            }
        } else {
            PantryItemTable.insert {
                it[PantryItemTable.id] = id
                when (scope) {
                    is PantryScope.Personal -> it[userId] = scope.userId
                    is PantryScope.Household -> it[householdId] = scope.householdId
                }
                it[PantryItemTable.name] = name
                it[PantryItemTable.kind] = kind
                it[PantryItemTable.quantity] = BigDecimal.valueOf(quantity)
                it[PantryItemTable.unit] = unit
            }
        }
        PantryItemTable.selectAll().where { PantryItemTable.id eq id }.single().toPantryItem()
    }

    fun delete(id: UUID): Boolean = transaction {
        PantryItemTable.deleteWhere { PantryItemTable.id eq id } > 0
    }

    private fun ResultRow.toPantryItem() = PantryItem(
        id = this[PantryItemTable.id].toString(),
        householdId = this[PantryItemTable.householdId]?.toString(),
        name = this[PantryItemTable.name],
        kind = this[PantryItemTable.kind],
        quantity = this[PantryItemTable.quantity].toDouble(),
        unit = this[PantryItemTable.unit]
    )
}
