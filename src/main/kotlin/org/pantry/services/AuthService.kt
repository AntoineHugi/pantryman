package org.pantry.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.mindrot.jbcrypt.BCrypt
import org.pantry.models.AuthResponse
import org.pantry.repositories.UserRepository
import java.util.Date
import java.util.UUID

class AuthService(
    private val userRepository: UserRepository,
    private val jwtSecret: String,
    private val jwtIssuer: String,
    private val jwtAudience: String
) {

    fun signup(email: String, password: String): AuthResponse {
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
        val passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(6))

        // Create user
        val userId = UUID.randomUUID()
        val user = userRepository.create(userId, email, passwordHash)

        // Generate token
        val token = generateToken(userId)

        return AuthResponse(token, user.id, user.email)
    }

    fun login(email: String, password: String): AuthResponse? {
        // Get user
        val user = userRepository.getByEmail(email) ?: return null

        // Verify password
        if (!BCrypt.checkpw(password, user.passwordHash)) {
            return null
        }

        // Generate token
        val token = generateToken(UUID.fromString(user.id))

        return AuthResponse(token, user.id, user.email)
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