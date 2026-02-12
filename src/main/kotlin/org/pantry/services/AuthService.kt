package org.pantry.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.mindrot.jbcrypt.BCrypt
import org.pantry.models.AuthResponse
import org.pantry.models.SignupResponse
import org.pantry.repositories.UserRepository
import java.util.Date
import java.util.UUID

class AuthService(
    private val userRepository: UserRepository,
    private val emailService: EmailService,
    private val jwtSecret: String,
    private val jwtIssuer: String,
    private val jwtAudience: String
) {

    suspend fun signup(email: String, password: String): SignupResponse {
        // Validate input
        if (email.isBlank() || !email.contains("@")) {
            throw IllegalArgumentException("Invalid email")
        }
        if (password.length < 8) {
            throw IllegalArgumentException("Password must be at least 8 characters")
        }

        // Check if user already exists
        if (userRepository.getByEmail(email) != null) {
            throw IllegalArgumentException("Registration Failed")
        }

        // Hash password
        val passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(10))

        // Create user with verification token
        val userId = UUID.randomUUID()
        val verificationToken = UUID.randomUUID()
        userRepository.create(userId, email, passwordHash, verificationToken)

        // Send verification email
        emailService.sendVerificationEmail(email, verificationToken)

        return SignupResponse(
            message = "Please check your email to verify your account.",
            email = email
        )
    }

    fun login(email: String, password: String): AuthResponse? {
        // Get user
        val user = userRepository.getByEmail(email) ?: return null

        // Verify password
        if (!BCrypt.checkpw(password, user.passwordHash)) {
            return null
        }

        // Check if email is verified
        if (!user.isVerified) {
            throw EmailNotVerifiedException("Please verify your email before logging in.")
        }

        // Generate token
        val token = generateToken(UUID.fromString(user.id))

        return AuthResponse(token, user.id, user.email)
    }

    fun verifyEmail(token: UUID): Boolean {
        val user = userRepository.getByVerificationToken(token) ?: return false
        userRepository.markAsVerified(UUID.fromString(user.id))
        return true
    }

    suspend fun resendVerification(email: String) {
        val user = userRepository.getByEmail(email)
            ?: throw IllegalArgumentException("No account found with this email.")

        if (user.isVerified) {
            throw IllegalArgumentException("Email is already verified.")
        }

        val newToken = UUID.randomUUID()
        userRepository.updateVerificationToken(UUID.fromString(user.id), newToken)
        emailService.sendVerificationEmail(email, newToken)
    }

    private fun generateToken(userId: UUID): String {
        return JWT.create()
            .withAudience(jwtAudience)
            .withIssuer(jwtIssuer)
            .withClaim("userId", userId.toString())
            .withExpiresAt(Date(System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000)) // 7 days
            .sign(Algorithm.HMAC256(jwtSecret))
    }
}

class EmailNotVerifiedException(message: String) : Exception(message)
