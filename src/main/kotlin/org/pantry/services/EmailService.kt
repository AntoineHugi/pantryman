package org.pantry.services

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.UUID

class EmailService(
    private val apiKey: String,
    private val fromEmail: String,
    private val verificationBaseUrl: String
) {
    private val logger = LoggerFactory.getLogger(EmailService::class.java)

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun sendVerificationEmail(to: String, token: UUID) {
        val verificationLink = "$verificationBaseUrl/auth/verify?token=$token"

        try {
            client.post("https://api.resend.com/emails") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(mapOf(
                    "from" to fromEmail,
                    "to" to listOf(to),
                    "subject" to "Verify your Pantryman account",
                    "html" to """
                        <h2>Welcome to Pantryman!</h2>
                        <p>Click the link below to verify your email address:</p>
                        <p><a href="$verificationLink">Verify my email</a></p>
                        <p>If you didn't create an account, you can ignore this email.</p>
                    """.trimIndent()
                ))
            }
            logger.info("Verification email sent to $to")
        } catch (e: Exception) {
            logger.error("Failed to send verification email to $to", e)
            throw RuntimeException("Failed to send verification email")
        }
    }
}
