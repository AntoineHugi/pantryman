package org.pantry.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import org.pantry.models.ConfirmMealPlanResponse
import org.pantry.models.CreateMealPlanRequest
import org.pantry.models.MealPlan
import org.pantry.models.MealSlot
import org.pantry.models.MoveMealRequest
import org.pantry.models.SetMealRequest
import org.pantry.services.HouseholdService
import org.pantry.services.MealPlanService
import java.util.UUID

fun Application.mealPlansApi() {
    val mealPlanService by inject<MealPlanService>()
    val householdService by inject<HouseholdService>()

    // Owner or household member; anyone else gets a 404, like an unknown plan
    fun ApplicationCall.requirePlan(): MealPlan =
        mealPlanService.get(uuidParameter("planId"), userId) ?: throw NotFoundException("Meal plan not found")

    fun ApplicationCall.slot(): MealSlot =
        MealSlot(parameters["date"] ?: throw IllegalArgumentException("Missing date"), parameters["meal"] ?: "")

    routing {
        authenticate("auth-jwt") {
            route("/meal-plans") {
                get {
                    call.respond(mealPlanService.getAll(call.userId))
                }
                post {
                    val request = call.receive<CreateMealPlanRequest>()
                    val householdId = request.householdId?.let {
                        val id = try {
                            UUID.fromString(it)
                        } catch (e: IllegalArgumentException) {
                            throw IllegalArgumentException("Invalid householdId")
                        }
                        if (!householdService.isMember(id, call.userId)) {
                            throw ForbiddenException("You are not a member of this household")
                        }
                        id
                    }
                    call.respond(HttpStatusCode.Created, mealPlanService.create(request, householdId, call.userId))
                }
                route("/{planId}") {
                    get {
                        call.respond(call.requirePlan())
                    }
                    delete {
                        if (!mealPlanService.delete(call.uuidParameter("planId"), call.userId)) {
                            throw NotFoundException("Meal plan not found")
                        }
                        call.respond(HttpStatusCode.NoContent)
                    }
                    // A slot is /entries/2026-10-20/dinner
                    put("/entries/{date}/{meal}") {
                        val plan = call.requirePlan()
                        val request = call.receive<SetMealRequest>()
                        val recipeId = request.recipeId?.let {
                            try {
                                UUID.fromString(it)
                            } catch (e: IllegalArgumentException) {
                                throw IllegalArgumentException("Invalid recipeId")
                            }
                        }
                        call.respond(mealPlanService.setMeal(plan, call.slot(), recipeId, request.text, request.servings, request.onList))
                    }
                    delete("/entries/{date}/{meal}") {
                        val plan = call.requirePlan()
                        if (!mealPlanService.removeMeal(plan, call.slot())) throw NotFoundException("No meal there")
                        call.respond(call.requirePlan())
                    }
                    post("/move") {
                        val plan = call.requirePlan()
                        val request = call.receive<MoveMealRequest>()
                        if (!mealPlanService.moveMeal(plan, request.from, request.to)) throw NotFoundException("No meal to move")
                        call.respond(call.requirePlan())
                    }
                    post("/days") {
                        mealPlanService.addDay(call.requirePlan())
                        call.respond(call.requirePlan())
                    }
                    delete("/days/{date}") {
                        val plan = call.requirePlan()
                        mealPlanService.removeDay(plan, call.parameters["date"] ?: throw IllegalArgumentException("Missing date"))
                        call.respond(call.requirePlan())
                    }
                    post("/confirm") {
                        val plan = call.requirePlan()
                        val list = mealPlanService.confirm(plan, call.userId)
                        call.respond(ConfirmMealPlanResponse(call.requirePlan(), list))
                    }
                }
            }
        }
    }
}
