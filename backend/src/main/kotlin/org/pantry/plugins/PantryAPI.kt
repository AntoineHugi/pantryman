package org.pantry.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import org.pantry.models.AddPantryItemRequest
import org.pantry.repositories.PantryScope
import org.pantry.services.HouseholdService
import org.pantry.services.PantryService
import java.util.UUID

// Pantry items: a household's (any member can change them), or the user's own for personal lists
fun Application.pantryApi() {
    val pantryService by inject<PantryService>()
    val householdService by inject<HouseholdService>()

    // No householdId: the user's personal items
    fun ApplicationCall.requireScope(rawHouseholdId: String?): PantryScope {
        if (rawHouseholdId.isNullOrEmpty()) return PantryScope.Personal(userId)
        val householdId = try {
            UUID.fromString(rawHouseholdId)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid householdId")
        }
        if (!householdService.isMember(householdId, userId)) throw ForbiddenException("You are not a member of this household")
        return PantryScope.Household(householdId)
    }

    fun ApplicationCall.canUse(scope: PantryScope): Boolean = when (scope) {
        is PantryScope.Personal -> scope.userId == userId
        is PantryScope.Household -> householdService.isMember(scope.householdId, userId)
    }

    routing {
        authenticate("auth-jwt") {
            route("/pantry-items") {
                // ?householdId=... for a household's items
                get {
                    call.respond(pantryService.getAll(call.requireScope(call.request.queryParameters["householdId"])))
                }
                // Adding a name that's already there updates it (e.g. moves it from defaults to staples)
                post {
                    val request = call.receive<AddPantryItemRequest>()
                    val scope = call.requireScope(request.householdId)
                    val item = pantryService.add(scope, request.name, request.kind, request.quantity, request.unit)
                    call.respond(HttpStatusCode.Created, item)
                }
                delete("/{itemId}") {
                    val itemId = call.uuidParameter("itemId")
                    val (scope, _) = pantryService.get(itemId) ?: throw NotFoundException("Item not found")
                    // Someone else's item looks like an unknown one
                    if (!call.canUse(scope)) throw NotFoundException("Item not found")
                    pantryService.delete(itemId)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}
