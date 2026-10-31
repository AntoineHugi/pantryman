package org.pantry.postgres.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestamp

// Like a list: personal, or shared with a household
object MealPlanTable : Table("meal_plans") {
    val id = uuid("id")
    val userId = uuid("user_id").references(UserTable.id, onDelete = ReferenceOption.CASCADE).index()
    val householdId = uuid("household_id").references(HouseholdTable.id, onDelete = ReferenceOption.SET_NULL).nullable().index()
    val startDate = date("start_date")
    val days = integer("days")
    // Servings for a meal that comes without any; only older apps rely on it
    val people = integer("people")
    // The list made on confirming
    val listId = uuid("list_id").references(GroceryListTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)
}

object MealPlanEntryTable : Table("meal_plan_entries") {
    val planId = uuid("plan_id").references(MealPlanTable.id, onDelete = ReferenceOption.CASCADE)
    val date = date("date")
    // "lunch" or "dinner"
    val meal = varchar("meal", 10)
    // A meal is a recipe or free text, never both
    val recipeId = uuid("recipe_id").references(RecipeTable.id, onDelete = ReferenceOption.CASCADE).nullable()
    val text = varchar("text", 200).nullable()
    val servings = integer("servings")
    // Already on the plan's grocery list
    val onList = bool("on_list").default(false)

    override val primaryKey = PrimaryKey(planId, date, meal)
}
