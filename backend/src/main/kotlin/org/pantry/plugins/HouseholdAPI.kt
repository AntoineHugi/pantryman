package org.pantry.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import org.pantry.models.CreateHouseholdRequest
import org.pantry.models.Household
import org.pantry.models.JoinHouseholdRequest
import org.pantry.models.RenameHouseholdRequest
import org.pantry.services.HouseholdService
import java.util.UUID

fun Application.householdsApi() {
    val householdService by inject<HouseholdService>()

    // Renaming and removing members are for the owner only
    fun ApplicationCall.requireOwnedHousehold(): Household {
        val household = householdService.getForMember(uuidParameter("householdId"), userId)
            ?: throw NotFoundException("Household not found")
        if (household.ownerId != userId.toString()) {
            throw ForbiddenException("Only the household's owner can do this")
        }
        return household
    }

    routing {
        authenticate("auth-jwt") {
            route("/households") {
                get {
                    call.respond(householdService.getForUser(call.userId))
                }
                post {
                    val request = call.receive<CreateHouseholdRequest>()
                    call.respond(HttpStatusCode.Created, householdService.create(request.name, call.userId))
                }
                // Rate limited so invite codes can't be guessed
                rateLimit(JOIN_RATE_LIMIT) {
                    post("/join") {
                        val request = call.receive<JoinHouseholdRequest>()
                        val household = householdService.join(request.inviteCode, call.userId)
                            ?: throw NotFoundException("No household has this invite code")
                        call.respond(household)
                    }
                }
                route("/{householdId}") {
                    patch {
                        val household = call.requireOwnedHousehold()
                        val request = call.receive<RenameHouseholdRequest>()
                        val id = UUID.fromString(household.id)
                        householdService.rename(id, request.name)
                        call.respond(householdService.getForMember(id, call.userId)!!)
                    }
                    delete("/members/{memberId}") {
                        val household = call.requireOwnedHousehold()
                        val memberId = call.uuidParameter("memberId")
                        if (memberId == call.userId) {
                            throw IllegalArgumentException("Use leave to remove yourself")
                        }
                        if (!householdService.removeMember(UUID.fromString(household.id), memberId)) {
                            throw NotFoundException("Member not found")
                        }
                        call.respond(HttpStatusCode.NoContent)
                    }
                    post("/invite-code") {
                        val household = householdService.regenerateInviteCode(
                            call.uuidParameter("householdId"), call.userId
                        ) ?: throw NotFoundException("Household not found")
                        call.respond(household)
                    }
                    post("/leave") {
                        if (!householdService.leave(call.uuidParameter("householdId"), call.userId)) {
                            throw NotFoundException("Household not found")
                        }
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
            }
        }
    }
}
