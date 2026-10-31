package org.pantry.repositories

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inSubQuery
import org.jetbrains.exposed.sql.transactions.transaction
import org.pantry.models.MealPlan
import org.pantry.models.MealPlanEntry
import org.pantry.postgres.tables.HouseholdMemberTable
import org.pantry.postgres.tables.MealPlanEntryTable
import org.pantry.postgres.tables.MealPlanTable
import org.pantry.postgres.tables.RecipeTable
import java.time.LocalDate
import java.util.UUID

// What fills a slot: a recipe or free text
data class PlannedMeal(val recipeId: UUID?, val text: String?, val servings: Int)

// The same access rule as lists: the owner, or a member of the plan's household.
// Entry queries take a plan ID the caller has already checked with get().
class MealPlanRepository {

    private fun accessibleBy(userId: UUID): Op<Boolean> {
        val userHouseholds = HouseholdMemberTable
            .select(HouseholdMemberTable.householdId)
            .where { HouseholdMemberTable.userId eq userId }
        return (MealPlanTable.userId eq userId) or (MealPlanTable.householdId inSubQuery userHouseholds)
    }

    // Latest first
    fun getAll(userId: UUID): List<MealPlan> = transaction {
        MealPlanTable.selectAll().where { accessibleBy(userId) }
            .orderBy(
                MealPlanTable.startDate to SortOrder.DESC,
                MealPlanTable.createdAt to SortOrder.DESC,
                MealPlanTable.id to SortOrder.ASC
            )
            .map { it.toMealPlan() }
    }

    fun get(id: UUID, userId: UUID): MealPlan? = transaction {
        MealPlanTable.selectAll().where { accessibleBy(userId) and (MealPlanTable.id eq id) }
            .map { it.toMealPlan() }
            .singleOrNull()
    }

    fun create(id: UUID, userId: UUID, householdId: UUID?, startDate: LocalDate, days: Int, people: Int) = transaction {
        MealPlanTable.insert {
            it[MealPlanTable.id] = id
            it[MealPlanTable.userId] = userId
            it[MealPlanTable.householdId] = householdId
            it[MealPlanTable.startDate] = startDate
            it[MealPlanTable.days] = days
            it[MealPlanTable.people] = people
        }
    }

    fun delete(id: UUID, userId: UUID): Boolean = transaction {
        MealPlanTable.deleteWhere { accessibleBy(userId) and (MealPlanTable.id eq id) } > 0
    }

    // Replaces whatever was in the slot
    fun setEntry(planId: UUID, date: LocalDate, meal: String, planned: PlannedMeal) = transaction {
        deleteEntry(planId, date, meal)
        insertEntry(planId, date, meal, planned)
    }

    fun removeEntry(planId: UUID, date: LocalDate, meal: String): Boolean = transaction {
        deleteEntry(planId, date, meal) > 0
    }

    // Moves a meal to another slot; if that slot has a meal, the two swap. False if [from] is empty.
    fun move(planId: UUID, from: Pair<LocalDate, String>, to: Pair<LocalDate, String>): Boolean = transaction {
        val moving = entryAt(planId, from.first, from.second) ?: return@transaction false
        val other = entryAt(planId, to.first, to.second)
        deleteEntry(planId, from.first, from.second)
        deleteEntry(planId, to.first, to.second)
        insertEntry(planId, to.first, to.second, moving)
        if (other != null) insertEntry(planId, from.first, from.second, other)
        true
    }

    fun setListId(planId: UUID, listId: UUID) = transaction {
        MealPlanTable.update({ MealPlanTable.id eq planId }) {
            it[MealPlanTable.listId] = listId
        }
    }

    private fun entryAt(planId: UUID, date: LocalDate, meal: String): PlannedMeal? =
        MealPlanEntryTable.selectAll()
            .where { (MealPlanEntryTable.planId eq planId) and (MealPlanEntryTable.date eq date) and (MealPlanEntryTable.meal eq meal) }
            .map { PlannedMeal(it[MealPlanEntryTable.recipeId], it[MealPlanEntryTable.text], it[MealPlanEntryTable.servings]) }
            .singleOrNull()

    private fun deleteEntry(planId: UUID, date: LocalDate, meal: String): Int =
        MealPlanEntryTable.deleteWhere {
            (MealPlanEntryTable.planId eq planId) and (MealPlanEntryTable.date eq date) and (MealPlanEntryTable.meal eq meal)
        }

    private fun insertEntry(planId: UUID, date: LocalDate, meal: String, planned: PlannedMeal) {
        MealPlanEntryTable.insert {
            it[MealPlanEntryTable.planId] = planId
            it[MealPlanEntryTable.date] = date
            it[MealPlanEntryTable.meal] = meal
            it[MealPlanEntryTable.recipeId] = planned.recipeId
            it[MealPlanEntryTable.text] = planned.text
            it[MealPlanEntryTable.servings] = planned.servings
        }
    }

    private fun ResultRow.toMealPlan(): MealPlan {
        val id = this[MealPlanTable.id]
        val start = this[MealPlanTable.startDate]
        val days = this[MealPlanTable.days]
        val entries = (MealPlanEntryTable leftJoin RecipeTable)
            .select(MealPlanEntryTable.date, MealPlanEntryTable.meal, MealPlanEntryTable.recipeId, MealPlanEntryTable.text, RecipeTable.name, MealPlanEntryTable.servings)
            .where { MealPlanEntryTable.planId eq id }
            .map {
                MealPlanEntry(
                    date = it[MealPlanEntryTable.date].toString(),
                    meal = it[MealPlanEntryTable.meal],
                    recipeId = it[MealPlanEntryTable.recipeId]?.toString(),
                    recipeName = it[MealPlanEntryTable.text] ?: it[RecipeTable.name],
                    servings = it[MealPlanEntryTable.servings]
                )
            }
            // Day by day, lunch before dinner
            .sortedWith(compareBy({ it.date }, { MEALS.indexOf(it.meal) }))
        return MealPlan(
            id = id.toString(),
            userId = this[MealPlanTable.userId].toString(),
            householdId = this[MealPlanTable.householdId]?.toString(),
            startDate = start.toString(),
            endDate = start.plusDays(days - 1L).toString(),
            days = days,
            people = this[MealPlanTable.people],
            listId = this[MealPlanTable.listId]?.toString(),
            entries = entries
        )
    }

    companion object {
        val MEALS = listOf("lunch", "dinner")
    }
}
