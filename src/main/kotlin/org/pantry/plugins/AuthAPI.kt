package org.pantry.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import org.pantry.models.LoginRequest
import org.pantry.models.SignupRequest
import org.pantry.models.AuthResponse
import org.pantry.services.AuthService

fun Application.authAPI() {
    val authService by inject<AuthService>()

    routing {
        route("/auth") {
            post("/signup") {
                try {
                    val request = call.receive<SignupRequest>()
                    val response = authService.signup(request.email, request.password)
                    call.respond(HttpStatusCode.Created, response)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Signup failed"))
                }
            }

            post("/login") {
                try {
                    val request = call.receive<LoginRequest>()
                    val response = authService.login(request.email, request.password)
                    if (response != null) {
                        call.respond(HttpStatusCode.OK, response)
                    } else {
                        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid credentials"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Login failed"))
                }
            }
        }
    }
}