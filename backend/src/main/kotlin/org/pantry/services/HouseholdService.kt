package org.pantry.services

import org.pantry.models.Household
import org.pantry.repositories.HouseholdRepository
import java.security.SecureRandom
import java.util.UUID

class HouseholdService(
    private val repo: HouseholdRepository
) {
    private val random = SecureRandom()

    fun getForUser(userId: UUID): List<Household> = repo.getForUser(userId)

    fun isMember(householdId: UUID, userId: UUID): Boolean = repo.isMember(householdId, userId)

    // Null if the user isn't a member
    fun getForMember(householdId: UUID, userId: UUID): Household? = repo.getForMember(householdId, userId)

    fun create(name: String, userId: UUID): Household =
        repo.create(UUID.randomUUID(), validateName(name), newInviteCode(), userId)

    // The route checks that the user is the owner
    fun rename(householdId: UUID, name: String) {
        repo.rename(householdId, validateName(name))
    }

    /**
     * The route checks that the caller is the owner. Also replaces the invite code,
     * so the removed member can't rejoin with it. Returns false if they weren't a member.
     */
    fun removeMember(householdId: UUID, memberId: UUID): Boolean {
        if (!repo.removeMember(householdId, memberId)) return false
        repo.updateInviteCode(householdId, newInviteCode())
        return true
    }

    private fun validateName(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isBlank()) throw IllegalArgumentException("Name cannot be blank")
        if (trimmed.length > MAX_NAME_LENGTH) throw IllegalArgumentException("Name is too long")
        return trimmed
    }

    // Null when no household has this code
    fun join(inviteCode: String, userId: UUID): Household? {
        val householdId = repo.getIdByInviteCode(normalizeInviteCode(inviteCode)) ?: return null
        repo.addMember(householdId, userId)
        return repo.getForMember(householdId, userId)
    }

    // Invalidates the old code, e.g. if it was shared with the wrong person. Null if not a member.
    fun regenerateInviteCode(householdId: UUID, userId: UUID): Household? {
        if (!repo.isMember(householdId, userId)) return null
        repo.updateInviteCode(householdId, newInviteCode())
        return repo.getForMember(householdId, userId)
    }

    fun leave(householdId: UUID, userId: UUID): Boolean = repo.removeMember(householdId, userId)

    private fun newInviteCode(): String =
        (1..INVITE_CODE_LENGTH).map { INVITE_CODE_ALPHABET[random.nextInt(INVITE_CODE_ALPHABET.length)] }.joinToString("")

    companion object {
        const val MAX_NAME_LENGTH = 100
        const val INVITE_CODE_LENGTH = 8

        // No 0/O or 1/I, so codes read out loud or typed from a screenshot work
        private const val INVITE_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        // People type codes by hand: ignore case, spaces and dashes
        fun normalizeInviteCode(code: String): String = code.uppercase().filter { it.isLetterOrDigit() }
    }
}
