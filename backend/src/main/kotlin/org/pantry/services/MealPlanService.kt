package org.pantry.services

import org.pantry.models.CreateMealPlanRequest
import org.pantry.models.GroceryList
import org.pantry.models.MealPlan
import org.pantry.models.MealSlot
import org.pantry.repositories.GroceryListRepository
import org.pantry.repositories.ItemRepository
import org.pantry.repositories.MealPlanRepository
import org.pantry.repositories.PantryScope
import org.pantry.repositories.PlannedMeal
import org.pantry.repositories.RecipeRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID

class MealPlanService(
    private val repo: MealPlanRepository,
    private val recipeRepo: RecipeRepository,
    private val listRepo: GroceryListRepository,
    private val itemRepo: ItemRepository,
    private val pantryService: PantryService
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
    fun setMeal(plan: MealPlan, slot: MealSlot, recipeId: UUID?, text: String?, servings: Int?, onList: Boolean? = null): MealPlan {
        val (date, meal) = validSlot(plan, slot)
        val cleanText = text?.trim()?.ifEmpty { null }
        if ((recipeId == null) == (cleanText == null)) throw IllegalArgumentException("A meal needs a recipe or some text")
        if (cleanText != null && cleanText.length > MAX_TEXT_LENGTH) throw IllegalArgumentException("Text must be at most $MAX_TEXT_LENGTH characters")
        if (recipeId != null) recipeRepo.get(recipeId) ?: throw IllegalArgumentException("Recipe not found")
        val id = UUID.fromString(plan.id)
        val current = plan.entries.find { it.date == date.toString() && it.meal == meal }
        // Changing the number of people doesn't touch the list, so the meal isn't new
        val stillOnList = recipeId != null && (onList ?: (current?.recipeId == recipeId.toString() && current.onList))
        repo.setEntry(id, date, meal, PlannedMeal(recipeId, cleanText, validServings(servings ?: plan.people, "servings"), stillOnList))
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

    // A new day after the last one
    fun addDay(plan: MealPlan) {
        if (plan.days >= MAX_DAYS) throw IllegalArgumentException("A plan can have at most $MAX_DAYS days")
        repo.setDays(UUID.fromString(plan.id), LocalDate.parse(plan.startDate), plan.days + 1)
    }

    // A plan has no gaps, so only its first or last day can go. The day's meals go with it,
    // and what they put on the grocery list stays there.
    fun removeDay(plan: MealPlan, value: String) {
        val date = parseDate(value)
        val start = LocalDate.parse(plan.startDate)
        if (date != start && date != LocalDate.parse(plan.endDate)) {
            throw IllegalArgumentException("Only the first or last day can be removed")
        }
        if (plan.days == 1) throw IllegalArgumentException("A plan needs at least one day")
        repo.removeDay(UUID.fromString(plan.id), date, if (date == start) start.plusDays(1) else start, plan.days - 1)
    }

    /**
     * Puts the plan's meals on its grocery list, each scaled to its servings and merged (ListMerge).
     * The first time, this creates the list "Meal plan - 20.10/26.10", shared like the plan, with
     * the pantry defaults, like any new list. After that, only meals not on the list yet are added.
     * Free-text meals, optional ingredients and pantry staples are left out.
     */
    fun confirm(plan: MealPlan, userId: UUID): GroceryList {
        val existingList = plan.listId?.let(UUID::fromString)
        val meals = plan.entries.filter { it.recipeId != null && (existingList == null || !it.onList) }
        if (meals.isEmpty()) {
            throw IllegalArgumentException(if (existingList == null) "Add a recipe first" else "Every meal is already on the list")
        }
        // Its owner can stop sharing the list without the plan following
        if (existingList != null && listRepo.getById(existingList, userId) == null) {
            throw IllegalArgumentException("You don't have access to this plan's grocery list")
        }

        val scope = plan.householdId?.let { PantryScope.Household(UUID.fromString(it)) } ?: PantryScope.Personal(userId)
        val staples = pantryService.stapleNames(scope)
        val ingredients = meals.flatMap { entry ->
            val recipe = entry.recipeId?.let { recipeRepo.get(UUID.fromString(it)) } ?: return@flatMap emptyList()
            val scale = entry.servings.toDouble() / recipe.servings
            recipe.ingredients.filter { !it.optional && PantryService.normalize(it.name) !in staples }.map {
                IngredientToAdd(it.name, it.amount?.let { amount -> amount * scale }, it.unit, recipe.name)
            }
        }
        val listId = existingList ?: UUID.randomUUID()
        if (existingList == null) listRepo.create(listId, listName(plan), userId, plan.householdId?.let(UUID::fromString))
        itemRepo.merge(listId) { items -> ListMerge.plan(items, ingredients) }
        if (existingList == null) pantryService.addDefaults(listId, scope, null)
        repo.setList(UUID.fromString(plan.id), listId)
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
        const val MAX_TEXT_LENGTH = 200
        private val LIST_DATE = DateTimeFormatter.ofPattern("dd.MM")

        fun listName(plan: MealPlan): String =
            "Meal plan - ${LocalDate.parse(plan.startDate).format(LIST_DATE)}/${LocalDate.parse(plan.endDate).format(LIST_DATE)}"
    }
}
