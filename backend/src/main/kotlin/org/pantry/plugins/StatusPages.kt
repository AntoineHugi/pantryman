package org.pantry.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable
import org.pantry.services.EmailNotVerifiedException
import java.util.UUID

@Serializable
data class ErrorResponse(val error: String)

/** The caller is authenticated but may not access this resource. */
class ForbiddenException(message: String) : RuntimeException(message)

/**
 * Maps exceptions to `{"error": "..."}` responses, so routes can just throw.
 * Services throw IllegalArgumentException for bad input (400).
 */
fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            // Thrown by call.receive for malformed or incomplete JSON
            call.application.log.debug("Bad request", cause)
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Invalid request"))
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Invalid request"))
        }
        exception<NotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse(cause.message ?: "Not found"))
        }
        exception<SecurityException> { call, cause ->
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse(cause.message ?: "Unauthorized"))
        }
        exception<EmailNotVerifiedException> { call, cause ->
            call.respond(HttpStatusCode.Forbidden, ErrorResponse(cause.message ?: "Email not verified"))
        }
        exception<ForbiddenException> { call, cause ->
            call.respond(HttpStatusCode.Forbidden, ErrorResponse(cause.message ?: "Forbidden"))
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled error on ${call.request.local.uri}", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Something went wrong"))
        }
    }
}

/** Reads a UUID path parameter; a missing or malformed value is a 400, not a 500. */
fun ApplicationCall.uuidParameter(name: String): UUID {
    val raw = parameters[name] ?: throw BadRequestException("Missing $name")
    return try {
        UUID.fromString(raw)
    } catch (e: IllegalArgumentException) {
        throw BadRequestException("Invalid $name")
    }
}
