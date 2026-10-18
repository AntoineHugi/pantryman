package org.pantry.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import org.koin.ktor.ext.inject
import org.pantry.models.Recipe
import org.pantry.models.RecipeInput
import org.pantry.services.RecipeService
import java.util.UUID

private val importJson = Json { ignoreUnknownKeys = true }

// A recipe file holds one recipe (an object) or several (an array)
private fun parseRecipeFile(body: JsonElement): List<RecipeInput> {
    val elements = if (body is JsonArray) body else listOf(body)
    return elements.mapIndexed { index, element ->
        try {
            importJson.decodeFromJsonElement(RecipeInput.serializer(), element)
        } catch (e: SerializationException) {
            // Missing fields, or e.g. a string where a number was expected
            val label = if (elements.size == 1) "The recipe" else "Recipe number ${index + 1}"
            throw IllegalArgumentException("$label doesn't match the recipe format: ${e.message}")
        }
    }
}

fun Application.recipesApi() {
    val recipeService by inject<RecipeService>()

    // Every user can read every recipe; only its creator can change it
    fun ApplicationCall.requireOwnRecipe(): Recipe {
        val recipe = recipeService.get(uuidParameter("recipeId")) ?: throw NotFoundException("Recipe not found")
        if (recipe.createdBy != userId.toString()) {
            throw ForbiddenException("Only the person who added this recipe can change it")
        }
        return recipe
    }

    routing {
        authenticate("auth-jwt") {
            route("/recipes") {
                get {
                    call.respond(recipeService.getAll())
                }
                post {
                    val request = call.receive<RecipeInput>()
                    call.respond(HttpStatusCode.Created, recipeService.create(request, call.userId))
                }
                post("/import") {
                    val recipes = parseRecipeFile(call.receive<JsonElement>())
                    call.respond(recipeService.import(recipes, call.userId))
                }
                route("/{recipeId}") {
                    get {
                        val recipe = recipeService.get(call.uuidParameter("recipeId"))
                            ?: throw NotFoundException("Recipe not found")
                        call.respond(recipe)
                    }
                    put {
                        val recipe = call.requireOwnRecipe()
                        val request = call.receive<RecipeInput>()
                        call.respond(recipeService.update(UUID.fromString(recipe.id), request))
                    }
                    delete {
                        val recipe = call.requireOwnRecipe()
                        recipeService.delete(UUID.fromString(recipe.id))
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
            }
        }
    }
}
