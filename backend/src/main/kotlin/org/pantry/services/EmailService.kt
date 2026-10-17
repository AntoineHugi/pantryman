package org.pantry.services

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.UUID

@Serializable
data class ResendEmailRequest(
    val from: String,
    val to: List<String>,
    val subject: String,
    val html: String
)

class EmailService(
    private val apiKey: String,
    private val fromEmail: String,
    private val frontendBaseUrl: String
) {
    private val logger = LoggerFactory.getLogger(EmailService::class.java)

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    // redirectPath must already be checked with isSafeRedirectPath
    suspend fun sendVerificationEmail(to: String, token: UUID, redirectPath: String?) {
        val nextParam = redirectPath?.let { "&next=${it.encodeURLParameter()}" } ?: ""
        // Links to the frontend's /verify page rather than the backend, so that on a phone with the
        // app installed the link opens the app directly (Android App Links don't follow redirects reliably)
        val verificationLink = "$frontendBaseUrl/verify?token=$token$nextParam"

        send(
            to,
            subject = "Verify your Pantryman account",
            html = """
                <h2>Welcome to Pantryman!</h2>
                <p>Click the link below to verify your email address:</p>
                <p><a href="$verificationLink">Verify my email</a></p>
                <p>If you didn't create an account, you can ignore this email.</p>
            """.trimIndent(),
            kind = "verification"
        )
    }

    suspend fun sendPasswordResetEmail(to: String, token: String) {
        val resetLink = "$frontendBaseUrl/reset-password?token=${token.encodeURLParameter()}"

        send(
            to,
            subject = "Reset your Pantryman password",
            html = """
                <h2>Reset your password</h2>
                <p>Click the link below to choose a new password. The link works once, for one hour.</p>
                <p><a href="$resetLink">Reset my password</a></p>
                <p>If you didn't ask for this, you can ignore this email; your password stays the same.</p>
            """.trimIndent(),
            kind = "password reset"
        )
    }

    private suspend fun send(to: String, subject: String, html: String, kind: String) {
        try {
            val response = client.post("https://api.resend.com/emails") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(ResendEmailRequest(from = fromEmail, to = listOf(to), subject = subject, html = html))
            }

            if (response.status.value in 200..299) {
                logger.info("Sent $kind email to $to")
            } else {
                val body = response.bodyAsText()
                logger.error("Resend API error (${response.status}): $body")
                throw RuntimeException("Resend API error: ${response.status}")
            }
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to send $kind email to $to", e)
            throw RuntimeException("Failed to send $kind email")
        }
    }
}
