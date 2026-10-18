package org.pantry.models

import kotlinx.serialization.Serializable

@Serializable
data class CreateMealPlanRequest(
    // "2026-10-20"
    val startDate: String,
    val days: Int = 7,
    // Omit for a personal plan
    val householdId: String? = null,
    // Default servings for a new meal
    val people: Int = 2
)

@Serializable
data class MealSlot(
    // "2026-10-20"
    val date: String,
    // "lunch" or "dinner"
    val meal: String
)

@Serializable
data class SetMealRequest(
    val recipeId: String,
    // Defaults to the plan's people
    val servings: Int? = null
)

@Serializable
data class MoveMealRequest(
    val from: MealSlot,
    // If a meal is already there, the two swap places
    val to: MealSlot
)

@Serializable
data class MealPlanEntry(
    val date: String,
    val meal: String,
    val recipeId: String,
    val recipeName: String,
    val servings: Int
)

@Serializable
data class MealPlan(
    val id: String,
    // The owner; any member of the household can edit a shared plan
    val userId: String,
    val householdId: String?,
    val startDate: String,
    // The last day, included
    val endDate: String,
    val days: Int,
    val people: Int,
    // The list made on confirming; null before that, or once the list is deleted
    val listId: String?,
    val entries: List<MealPlanEntry>
)

@Serializable
data class ConfirmMealPlanResponse(
    val plan: MealPlan,
    val list: GroceryList
)
