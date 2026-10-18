package org.pantry.models

import kotlinx.serialization.Serializable

@Serializable
data class CreateHouseholdRequest(
    val name: String
)

@Serializable
data class RenameHouseholdRequest(
    val name: String
)

@Serializable
data class JoinHouseholdRequest(
    val inviteCode: String
)

@Serializable
data class HouseholdMember(
    val userId: String,
    val email: String
)

@Serializable
data class Household(
    val id: String,
    val name: String,
    // Any member can see it and pass it on; whoever has it can join
    val inviteCode: String,
    // ISO-8601 instant. A member who loads the household after this gets a new code.
    val inviteCodeExpiresAt: String,
    // Can rename the household and remove members
    val ownerId: String,
    val members: List<HouseholdMember>
)
