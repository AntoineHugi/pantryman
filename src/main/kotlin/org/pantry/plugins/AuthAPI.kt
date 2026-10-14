package org.pantry.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import org.pantry.models.ChangePasswordRequest
import org.pantry.models.DeleteAccountRequest
import org.pantry.models.LoginRequest
import org.pantry.models.ResendVerificationRequest
import org.pantry.models.SignupRequest
import org.pantry.services.AuthService
import java.util.UUID

// Errors are mapped to responses in StatusPages.kt
fun Application.authAPI() {
    val authService by inject<AuthService>()
    val frontendBaseUrl = environment.config.property("resend.frontendBaseUrl").getString()

    routing {
        route("/auth") {
            rateLimit(AUTH_RATE_LIMIT) {
                post("/signup") {
                    val request = call.receive<SignupRequest>()
                    val response = authService.signup(request.email, request.password)
                    call.respond(HttpStatusCode.Created, response)
                }

                post("/login") {
                    val request = call.receive<LoginRequest>()
                    val response = authService.login(request.email, request.password)
                    if (response != null) {
                        call.respond(HttpStatusCode.OK, response)
                    } else {
                        call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid credentials"))
                    }
                }

                post("/resend-verification") {
                    val request = call.receive<ResendVerificationRequest>()
                    authService.resendVerification(request.email)
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Verification email sent."))
                }
            }

            get("/verify") {
                val token = call.request.queryParameters["token"]
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: throw BadRequestException("Missing or invalid token")
                val verified = authService.verifyEmail(token)
                call.respondRedirect("$frontendBaseUrl/login?verified=$verified")
            }

            authenticate("auth-jwt") {
                put("/password") {
                    val request = call.receive<ChangePasswordRequest>()
                    authService.changePassword(call.userId, request.currentPassword, request.newPassword)
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Password updated successfully."))
                }

                delete("/account") {
                    val request = call.receive<DeleteAccountRequest>()
                    authService.deleteAccount(call.userId, request.password)
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Account deleted successfully."))
                }
            }
        }
    }
}
