package org.pantry.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.*
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
import org.pantry.services.EmailNotVerifiedException
import java.util.UUID

fun Application.authAPI() {
    val authService by inject<AuthService>()
    val frontendBaseUrl = environment.config.property("resend.frontendBaseUrl").getString()

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
                } catch (e: EmailNotVerifiedException) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to e.message))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Login failed"))
                }
            }

            get("/verify") {
                val tokenStr = call.request.queryParameters["token"]
                if (tokenStr == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing token"))
                    return@get
                }

                try {
                    val token = UUID.fromString(tokenStr)
                    val verified = authService.verifyEmail(token)
                    if (verified) {
                        call.respondRedirect("$frontendBaseUrl/login?verified=true")
                    } else {
                        call.respondRedirect("$frontendBaseUrl/login?verified=false")
                    }
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid token format"))
                }
            }

            post("/resend-verification") {
                try {
                    val request = call.receive<ResendVerificationRequest>()
                    authService.resendVerification(request.email)
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Verification email sent."))
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to resend verification email"))
                }
            }

            authenticate("auth-jwt") {
                put("/password") {
                    try {
                        val request = call.receive<ChangePasswordRequest>()
                        authService.changePassword(call.userId, request.currentPassword, request.newPassword)
                        call.respond(HttpStatusCode.OK, mapOf("message" to "Password updated successfully."))
                    } catch (e: SecurityException) {
                        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to e.message))
                    } catch (e: IllegalArgumentException) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to e.message))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to change password"))
                    }
                }

                delete("/account") {
                    try {
                        val request = call.receive<DeleteAccountRequest>()
                        authService.deleteAccount(call.userId, request.password)
                        call.respond(HttpStatusCode.OK, mapOf("message" to "Account deleted successfully."))
                    } catch (e: SecurityException) {
                        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to e.message))
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to delete account"))
                    }
                }
            }
        }
    }
}
