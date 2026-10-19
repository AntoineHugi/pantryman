package org.pantry.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import org.pantry.models.AddRecipeToListRequest
import org.pantry.models.AddRecipeToListResponse
import org.pantry.models.BulkDeleteItemsRequest
import org.pantry.models.BulkDeleteItemsResponse
import org.pantry.models.CreateGroceryListRequest
import org.pantry.models.CreateItemRequest
import org.pantry.models.ItemUpdateRequest
import org.pantry.models.UpdateGroceryListRequest
import org.pantry.models.UpdateListHouseholdRequest
import org.pantry.services.GroceryListService
import org.pantry.services.HouseholdService
import org.pantry.services.ItemService
import org.pantry.services.PantryService
import org.pantry.services.RecipeListService
import java.util.UUID

fun Application.groceriesApi() {
    val groceryListService by inject <GroceryListService>()
    val itemService by inject <ItemService>()
    val householdService by inject <HouseholdService>()
    val recipeListService by inject <RecipeListService>()
    val pantryService by inject <PantryService>()

    // Item routes act on a list the caller must have access to (owner or household member);
    // returns the list ID.
    fun ApplicationCall.requireAccessibleListId(): UUID {
        val listId = uuidParameter("listId")
        groceryListService.getById(listId, userId) ?: throw ForbiddenException("List not found or unauthorized")
        return listId
    }

    // Parses a householdId from a request body; the caller must be a member. Null stays null.
    fun ApplicationCall.requireMemberHouseholdId(raw: String?): UUID? {
        if (raw == null) return null
        val householdId = try {
            UUID.fromString(raw)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid householdId")
        }
        if (!householdService.isMember(householdId, userId)) {
            throw ForbiddenException("You are not a member of this household")
        }
        return householdId
    }

    routing {
        authenticate("auth-jwt") {
            route("/lists") {
                post {
                    val request = call.receive<CreateGroceryListRequest>()
                    val householdId = call.requireMemberHouseholdId(request.householdId)
                    val created = groceryListService.create(request.name, call.userId, householdId)
                    val createdId = UUID.fromString(created.id)
                    pantryService.addDefaults(createdId, pantryService.scopeOf(created), request.defaultItemIds)
                    call.respond(HttpStatusCode.Created, groceryListService.getById(createdId, call.userId) ?: created)
                }
                get {
                    call.respond(groceryListService.getAll(call.userId))
                }
                route("/{listId}") {
                    get {
                        val list = groceryListService.getById(call.uuidParameter("listId"), call.userId)
                            ?: throw NotFoundException("List not found")
                        call.respond(list)
                    }
                    patch {
                        val id = call.uuidParameter("listId")
                        val request = call.receive<UpdateGroceryListRequest>()
                        if (!groceryListService.update(id, request.name, call.userId)) {
                            throw NotFoundException("List not found")
                        }
                        call.respond(HttpStatusCode.OK)
                    }
                    // Share the list with a household, or make it personal again (householdId = null)
                    put("/household") {
                        val id = call.uuidParameter("listId")
                        val list = groceryListService.getById(id, call.userId)
                            ?: throw NotFoundException("List not found")
                        if (list.userId != call.userId.toString()) {
                            throw ForbiddenException("Only the list's owner can change who it is shared with")
                        }
                        val request = call.receive<UpdateListHouseholdRequest>()
                        val householdId = call.requireMemberHouseholdId(request.householdId)
                        groceryListService.setHousehold(id, householdId, call.userId)
                        call.respond(groceryListService.getById(id, call.userId) ?: throw NotFoundException("List not found"))
                    }
                    delete {
                        if (!groceryListService.delete(call.uuidParameter("listId"), call.userId)) {
                            throw NotFoundException("List not found")
                        }
                        call.respond(HttpStatusCode.NoContent)
                    }
                    // Adds a recipe's ingredients, scaled and merged into the items already there
                    post("/recipes") {
                        val listId = call.uuidParameter("listId")
                        val list = groceryListService.getById(listId, call.userId)
                            ?: throw ForbiddenException("List not found or unauthorized")
                        val request = call.receive<AddRecipeToListRequest>()
                        val recipeId = try {
                            UUID.fromString(request.recipeId)
                        } catch (e: IllegalArgumentException) {
                            throw IllegalArgumentException("Invalid recipeId")
                        }
                        val result = recipeListService.addRecipe(
                            listId, recipeId, request.servings, request.ingredientIndexes,
                            skipNames = pantryService.stapleNames(pantryService.scopeOf(list))
                        )
                            ?: throw NotFoundException("Recipe not found")
                        call.respond(
                            AddRecipeToListResponse(
                                list = groceryListService.getById(listId, call.userId) ?: throw NotFoundException("List not found"),
                                added = result.newItems.size,
                                merged = result.changes.size
                            )
                        )
                    }
                    route("/items") {
                        post {
                            val listId = call.requireAccessibleListId()
                            val request = call.receive<CreateItemRequest>()
                            val item = itemService.create(
                                listId = listId,
                                name = request.name,
                                quantity = request.quantity,
                                unit = request.unit,
                                clientId = request.id,
                                isChecked = request.isChecked ?: false
                            )
                            call.respond(HttpStatusCode.Created, item)
                        }
                        get {
                            val listId = call.requireAccessibleListId()
                            call.respond(itemService.getAll(listId))
                        }
                        // Deletes several items of this list at once ("Clear ticked items")
                        post("/bulk-delete") {
                            val listId = call.requireAccessibleListId()
                            val request = call.receive<BulkDeleteItemsRequest>()
                            call.respond(BulkDeleteItemsResponse(itemService.deleteMany(listId, request.itemIds)))
                        }
                        route("/{itemId}") {
                            delete {
                                val listId = call.requireAccessibleListId()
                                if (!itemService.delete(listId, call.uuidParameter("itemId"))) {
                                    throw NotFoundException("Item not found")
                                }
                                call.respond(HttpStatusCode.NoContent)
                            }
                            patch {
                                val listId = call.requireAccessibleListId()
                                val itemId = call.uuidParameter("itemId")
                                val request = call.receive<ItemUpdateRequest>()
                                if (!itemService.update(listId, itemId, request)) {
                                    throw NotFoundException("Item not found")
                                }
                                call.respond(HttpStatusCode.OK, mapOf("message" to "Item updated"))
                            }
                        }
                    }
                }
            }
        }
    }
}
