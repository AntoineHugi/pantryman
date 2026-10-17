package org.pantry.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
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
import org.pantry.models.ForgotPasswordRequest
import org.pantry.models.LoginRequest
import org.pantry.models.ResendVerificationRequest
import org.pantry.models.ResetPasswordRequest
import org.pantry.models.SignupRequest
import org.pantry.models.VerifyEmailRequest
import org.pantry.models.VerifyEmailResponse
import org.pantry.services.AuthService
import org.pantry.services.isSafeRedirectPath
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
                    val response = authService.signup(request.email, request.password, request.redirectPath)
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
                    authService.resendVerification(request.email, request.redirectPath)
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Verification email sent."))
                }

                post("/forgot-password") {
                    val request = call.receive<ForgotPasswordRequest>()
                    authService.requestPasswordReset(request.email)
                    // Same answer whether or not the account exists
                    call.respond(
                        HttpStatusCode.OK,
                        mapOf("message" to "If an account exists for this email, we've sent a link to reset the password.")
                    )
                }

                post("/reset-password") {
                    val request = call.receive<ResetPasswordRequest>()
                    authService.resetPassword(request.token, request.newPassword)
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Password updated. You can now log in."))
                }
            }

            // Called by the frontend's /verify page, which verification emails link to
            post("/verify") {
                val request = call.receive<VerifyEmailRequest>()
                val token = runCatching { UUID.fromString(request.token) }.getOrNull()
                    ?: throw BadRequestException("Invalid token")
                call.respond(VerifyEmailResponse(authService.verifyEmail(token)))
            }

            // Emails sent before 2026-10-17 link here directly; keep it so those links still work
            get("/verify") {
                val token = call.request.queryParameters["token"]
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: throw BadRequestException("Missing or invalid token")
                val verified = authService.verifyEmail(token)
                // Carry the signup's redirect path (e.g. an invite link) on to the login page.
                // An unsafe value is dropped rather than failing the verification.
                val next = call.request.queryParameters["next"]?.takeIf(::isSafeRedirectPath)
                val nextParam = next?.let { "&next=${it.encodeURLParameter()}" } ?: ""
                call.respondRedirect("$frontendBaseUrl/login?verified=$verified$nextParam")
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
