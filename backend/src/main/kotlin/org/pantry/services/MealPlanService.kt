package org.pantry.services

import org.pantry.models.CreateMealPlanRequest
import org.pantry.models.GroceryList
import org.pantry.models.MealPlan
import org.pantry.models.MealSlot
import org.pantry.repositories.GroceryListRepository
import org.pantry.repositories.ItemRepository
import org.pantry.repositories.MealPlanRepository
import org.pantry.repositories.RecipeRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID

class MealPlanService(
    private val repo: MealPlanRepository,
    private val recipeRepo: RecipeRepository,
    private val listRepo: GroceryListRepository,
    private val itemRepo: ItemRepository
) {
    fun getAll(userId: UUID): List<MealPlan> = repo.getAll(userId)

    // Null if there's no such plan or the user can't access it
    fun get(id: UUID, userId: UUID): MealPlan? = repo.get(id, userId)

    // The route checks that the user belongs to householdId
    fun create(request: CreateMealPlanRequest, householdId: UUID?, userId: UUID): MealPlan {
        val start = parseDate(request.startDate)
        if (request.days !in 1..MAX_DAYS) throw IllegalArgumentException("days must be between 1 and $MAX_DAYS")
        validServings(request.people, "people")
        val id = UUID.randomUUID()
        repo.create(id, userId, householdId, start, request.days, request.people)
        return repo.get(id, userId)!!
    }

    fun delete(id: UUID, userId: UUID): Boolean = repo.delete(id, userId)

    // The route checks access to the plan
    fun setMeal(plan: MealPlan, slot: MealSlot, recipeId: UUID, servings: Int?): MealPlan {
        val (date, meal) = validSlot(plan, slot)
        recipeRepo.get(recipeId) ?: throw IllegalArgumentException("Recipe not found")
        val id = UUID.fromString(plan.id)
        repo.setEntry(id, date, meal, recipeId, validServings(servings ?: plan.people, "servings"))
        return repo.get(id, UUID.fromString(plan.userId))!!
    }

    // False if the slot was empty
    fun removeMeal(plan: MealPlan, slot: MealSlot): Boolean {
        val (date, meal) = validSlot(plan, slot)
        return repo.removeEntry(UUID.fromString(plan.id), date, meal)
    }

    // False if there's no meal to move
    fun moveMeal(plan: MealPlan, from: MealSlot, to: MealSlot): Boolean =
        repo.move(UUID.fromString(plan.id), validSlot(plan, from), validSlot(plan, to))

    /**
     * Creates the grocery list "Meal plan - 20.10/26.10" from every meal's recipe, scaled to its
     * servings and merged (ListMerge), shared like the plan. Optional ingredients are left out.
     */
    fun confirm(plan: MealPlan, userId: UUID): GroceryList {
        if (plan.listId != null) throw IllegalArgumentException("This plan already has a grocery list")
        if (plan.entries.isEmpty()) throw IllegalArgumentException("Add some meals first")

        val ingredients = plan.entries.flatMap { entry ->
            val recipe = recipeRepo.get(UUID.fromString(entry.recipeId)) ?: return@flatMap emptyList()
            val scale = entry.servings.toDouble() / recipe.servings
            recipe.ingredients.filter { !it.optional }.map {
                IngredientToAdd(it.name, it.amount?.let { amount -> amount * scale }, it.unit, recipe.name)
            }
        }
        val listId = UUID.randomUUID()
        listRepo.create(listId, listName(plan), userId, plan.householdId?.let(UUID::fromString))
        itemRepo.merge(listId) { items -> ListMerge.plan(items, ingredients) }
        repo.setListId(UUID.fromString(plan.id), listId)
        return listRepo.getById(listId, userId)!!
    }

    private fun parseDate(value: String): LocalDate = try {
        LocalDate.parse(value)
    } catch (e: DateTimeParseException) {
        throw IllegalArgumentException("Invalid date \"$value\" (use YYYY-MM-DD)")
    }

    private fun validSlot(plan: MealPlan, slot: MealSlot): Pair<LocalDate, String> {
        val date = parseDate(slot.date)
        if (date < LocalDate.parse(plan.startDate) || date > LocalDate.parse(plan.endDate)) {
            throw IllegalArgumentException("${slot.date} isn't in this plan")
        }
        if (slot.meal !in MealPlanRepository.MEALS) throw IllegalArgumentException("meal must be lunch or dinner")
        return date to slot.meal
    }

    private fun validServings(value: Int, field: String): Int {
        if (value !in 1..RecipeService.MAX_SERVINGS) throw IllegalArgumentException("$field must be between 1 and ${RecipeService.MAX_SERVINGS}")
        return value
    }

    companion object {
        const val MAX_DAYS = 14
        private val LIST_DATE = DateTimeFormatter.ofPattern("dd.MM")

        fun listName(plan: MealPlan): String =
            "Meal plan - ${LocalDate.parse(plan.startDate).format(LIST_DATE)}/${LocalDate.parse(plan.endDate).format(LIST_DATE)}"
    }
}
