package org.pantry.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.mindrot.jbcrypt.BCrypt
import org.pantry.models.AuthResponse
import org.pantry.models.SignupResponse
import org.pantry.repositories.HouseholdRepository
import org.pantry.repositories.UserRepository
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.Base64
import java.util.Date
import java.util.UUID

class AuthService(
    private val userRepository: UserRepository,
    private val emailService: EmailService,
    private val householdRepository: HouseholdRepository,
    private val jwtSecret: String,
    private val jwtIssuer: String,
    private val jwtAudience: String,
    private val clock: Clock = Clock.systemUTC()
) {

    suspend fun signup(rawEmail: String, password: String, redirectPath: String? = null): SignupResponse {
        val email = normalizeEmail(rawEmail)
        requireSafeRedirectPath(redirectPath)

        // Validate input
        if (email.isBlank() || !email.contains("@")) {
            throw IllegalArgumentException("Invalid email")
        }
        if (password.length < 8) {
            throw IllegalArgumentException("Password must be at least 8 characters")
        }

        // Check if user already exists
        if (userRepository.getByEmail(email) != null) {
            throw IllegalArgumentException("An account with this email already exists. Log in, or reset your password if you forgot it.")
        }

        // Hash password
        val passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(10))

        // Create user with verification token
        val userId = UUID.randomUUID()
        val verificationToken = UUID.randomUUID()
        userRepository.create(userId, email, passwordHash, verificationToken)

        // Send verification email; if it fails, remove the user so signup can be retried
        try {
            emailService.sendVerificationEmail(email, verificationToken, redirectPath)
        } catch (e: Exception) {
            userRepository.delete(userId)
            throw e
        }

        return SignupResponse(
            message = "Please check your email to verify your account.",
            email = email
        )
    }

    fun login(email: String, password: String): AuthResponse? {
        // Get user
        val user = userRepository.getByEmail(normalizeEmail(email)) ?: return null

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

    suspend fun resendVerification(rawEmail: String, redirectPath: String? = null) {
        val email = normalizeEmail(rawEmail)
        requireSafeRedirectPath(redirectPath)
        val user = userRepository.getByEmail(email)
            ?: throw IllegalArgumentException("No account found with this email.")

        if (user.isVerified) {
            throw IllegalArgumentException("Email is already verified.")
        }

        val newToken = UUID.randomUUID()
        userRepository.updateVerificationToken(UUID.fromString(user.id), newToken)
        emailService.sendVerificationEmail(email, newToken, redirectPath)
    }

    /**
     * Emails a link to reset the password. Does nothing for an unknown email, and the route answers the
     * same either way, so the form doesn't tell strangers who has an account.
     */
    suspend fun requestPasswordReset(rawEmail: String) {
        val user = userRepository.getByEmail(normalizeEmail(rawEmail)) ?: return

        val token = newResetToken()
        userRepository.setPasswordResetToken(
            UUID.fromString(user.id),
            sha256(token),
            clock.instant().plus(PASSWORD_RESET_VALIDITY)
        )
        emailService.sendPasswordResetEmail(user.email, token)
    }

    fun resetPassword(token: String, newPassword: String) {
        if (newPassword.length < 8) {
            throw IllegalArgumentException("Password must be at least 8 characters")
        }
        val newHash = BCrypt.hashpw(newPassword, BCrypt.gensalt(10))
        if (!userRepository.resetPassword(sha256(token), newHash, clock.instant())) {
            throw IllegalArgumentException("This reset link is invalid or has expired. Ask for a new one.")
        }
    }

    fun changePassword(userId: UUID, currentPassword: String, newPassword: String) {
        val user = userRepository.getById(userId)
            ?: throw IllegalArgumentException("User not found")

        if (!BCrypt.checkpw(currentPassword, user.passwordHash)) {
            throw SecurityException("Current password is incorrect")
        }

        if (newPassword.length < 8) {
            throw IllegalArgumentException("New password must be at least 8 characters")
        }

        val newHash = BCrypt.hashpw(newPassword, BCrypt.gensalt(10))
        userRepository.updatePassword(userId, newHash)
    }

    fun deleteAccount(userId: UUID, password: String) {
        val user = userRepository.getById(userId)
            ?: throw IllegalArgumentException("User not found")

        if (!BCrypt.checkpw(password, user.passwordHash)) {
            throw SecurityException("Password is incorrect")
        }

        // Hands the user's shared lists to the other members, so they aren't deleted with the account
        householdRepository.removeMemberFromAll(userId)
        userRepository.delete(userId)
    }

    private fun normalizeEmail(email: String): String = email.trim().lowercase()

    // 32 random bytes, URL-safe so it fits in the emailed link as is
    private fun newResetToken(): String {
        val bytes = ByteArray(32).also { secureRandom.nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        val PASSWORD_RESET_VALIDITY: Duration = Duration.ofHours(1)
        private val secureRandom = SecureRandom()
    }

    private fun requireSafeRedirectPath(path: String?) {
        if (path != null && !isSafeRedirectPath(path)) {
            throw IllegalArgumentException("Invalid redirect path")
        }
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

/**
 * True for a path inside the frontend ("/households?join=ABC"), so it can't be used to send
 * users to another site ("//evil.com", "https://evil.com", "/\\evil.com").
 */
fun isSafeRedirectPath(path: String): Boolean =
    path.length <= 500 &&
        path.startsWith("/") &&
        !path.startsWith("//") &&
        '\\' !in path &&
        path.none { it.isWhitespace() || it.isISOControl() }
