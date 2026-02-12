package org.pantry.models

import kotlinx.serialization.Serializable

@Serializable
data class SignupRequest(
    val email: String,
    val password: String
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class AuthResponse(
    val token: String,
    val userId: String,
    val email: String
)

@Serializable
data class SignupResponse(
    val message: String,
    val email: String
)

@Serializable
data class ResendVerificationRequest(
    val email: String
)

@Serializable
data class User(
    val id: String,
    val email: String,
    val passwordHash: String,
    val isVerified: Boolean = false,
    val verificationToken: String? = null
)