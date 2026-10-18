package org.pantry.repositories

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.pantry.models.Household
import org.pantry.models.HouseholdMember
import org.pantry.postgres.tables.GroceryListTable
import org.pantry.postgres.tables.HouseholdMemberTable
import org.pantry.postgres.tables.HouseholdTable
import org.pantry.postgres.tables.MealPlanTable
import org.pantry.postgres.tables.UserTable
import java.time.Instant
import java.util.UUID

class HouseholdRepository {

    fun create(id: UUID, name: String, inviteCode: String, inviteCodeExpiresAt: Instant, creatorId: UUID): Household = transaction {
        HouseholdTable.insert {
            it[HouseholdTable.id] = id
            it[HouseholdTable.name] = name
            it[HouseholdTable.inviteCode] = inviteCode
            it[HouseholdTable.inviteCodeExpiresAt] = inviteCodeExpiresAt
            it[HouseholdTable.ownerId] = creatorId
        }
        HouseholdMemberTable.insert {
            it[householdId] = id
            it[userId] = creatorId
        }
        load(id)!!
    }

    fun getForUser(userId: UUID): List<Household> = transaction {
        (HouseholdTable innerJoin HouseholdMemberTable)
            .select(HouseholdTable.id)
            .where { HouseholdMemberTable.userId eq userId }
            .orderBy(HouseholdTable.createdAt to SortOrder.ASC, HouseholdTable.id to SortOrder.ASC)
            .mapNotNull { load(it[HouseholdTable.id]) }
    }

    // Null unless the user is a member, so a household's details never leak to outsiders
    fun getForMember(id: UUID, userId: UUID): Household? = transaction {
        if (isMember(id, userId)) load(id) else null
    }

    fun isMember(householdId: UUID, userId: UUID): Boolean = transaction {
        HouseholdMemberTable.selectAll()
            .where { (HouseholdMemberTable.householdId eq householdId) and (HouseholdMemberTable.userId eq userId) }
            .any()
    }

    fun getIdByInviteCode(inviteCode: String): UUID? = getInvite(inviteCode)?.first

    // The household with this code and when the code expires, expired or not
    fun getInvite(inviteCode: String): Pair<UUID, Instant>? = transaction {
        HouseholdTable.select(HouseholdTable.id, HouseholdTable.inviteCodeExpiresAt)
            .where { HouseholdTable.inviteCode eq inviteCode }
            .map { it[HouseholdTable.id] to it[HouseholdTable.inviteCodeExpiresAt] }
            .singleOrNull()
    }

    // No-op if the user is already a member
    fun addMember(householdId: UUID, userId: UUID) = transaction {
        if (!isMember(householdId, userId)) {
            HouseholdMemberTable.insert {
                it[HouseholdMemberTable.householdId] = householdId
                it[HouseholdMemberTable.userId] = userId
            }
        }
    }

    fun rename(householdId: UUID, name: String) = transaction {
        HouseholdTable.update({ HouseholdTable.id eq householdId }) {
            it[HouseholdTable.name] = name
        }
    }

    fun updateInviteCode(householdId: UUID, inviteCode: String, expiresAt: Instant) = transaction {
        HouseholdTable.update({ HouseholdTable.id eq householdId }) {
            it[HouseholdTable.inviteCode] = inviteCode
            it[HouseholdTable.inviteCodeExpiresAt] = expiresAt
        }
    }

    /**
     * Removes the user from the household, whether they leave or the owner removes them.
     * The household keeps the lists (and meal plans) the user shared with it: they pass to the owner, or to
     * the longest-standing remaining member if the owner is the one leaving (who then also
     * becomes the owner). When the last member leaves, the household is deleted and its
     * lists become that member's personal lists. Returns false if the user wasn't a member.
     */
    fun removeMember(householdId: UUID, userId: UUID): Boolean = transaction {
        val removed = HouseholdMemberTable.deleteWhere {
            (HouseholdMemberTable.householdId eq householdId) and (HouseholdMemberTable.userId eq userId)
        } > 0
        if (!removed) return@transaction false

        val ownerId = HouseholdTable.select(HouseholdTable.ownerId)
            .where { HouseholdTable.id eq householdId }
            .single()[HouseholdTable.ownerId]
        val heir = if (ownerId != userId) ownerId else HouseholdMemberTable.select(HouseholdMemberTable.userId)
            .where { HouseholdMemberTable.householdId eq householdId }
            .orderBy(HouseholdMemberTable.joinedAt to SortOrder.ASC, HouseholdMemberTable.userId to SortOrder.ASC)
            .limit(1)
            .map { it[HouseholdMemberTable.userId] }
            .singleOrNull()

        if (heir == null) {
            GroceryListTable.update({ GroceryListTable.householdId eq householdId }) {
                it[GroceryListTable.householdId] = null
            }
            MealPlanTable.update({ MealPlanTable.householdId eq householdId }) {
                it[MealPlanTable.householdId] = null
            }
            HouseholdTable.deleteWhere { HouseholdTable.id eq householdId }
        } else {
            if (heir != ownerId) {
                HouseholdTable.update({ HouseholdTable.id eq householdId }) {
                    it[HouseholdTable.ownerId] = heir
                }
            }
            GroceryListTable.update({
                (GroceryListTable.householdId eq householdId) and (GroceryListTable.userID eq userId)
            }) {
                it[GroceryListTable.userID] = heir
            }
            MealPlanTable.update({
                (MealPlanTable.householdId eq householdId) and (MealPlanTable.userId eq userId)
            }) {
                it[MealPlanTable.userId] = heir
            }
        }
        true
    }

    // Before an account is deleted, so its shared lists stay with the household
    fun removeMemberFromAll(userId: UUID) = transaction {
        HouseholdMemberTable.select(HouseholdMemberTable.householdId)
            .where { HouseholdMemberTable.userId eq userId }
            .map { it[HouseholdMemberTable.householdId] }
            .forEach { removeMember(it, userId) }
    }

    private fun load(id: UUID): Household? {
        val row = HouseholdTable.selectAll().where { HouseholdTable.id eq id }.singleOrNull() ?: return null
        val members = (HouseholdMemberTable innerJoin UserTable)
            .select(UserTable.id, UserTable.email)
            .where { HouseholdMemberTable.householdId eq id }
            .orderBy(HouseholdMemberTable.joinedAt to SortOrder.ASC, UserTable.id to SortOrder.ASC)
            .map { HouseholdMember(it[UserTable.id].toString(), it[UserTable.email]) }
        return Household(
            id = id.toString(),
            name = row[HouseholdTable.name],
            inviteCode = row[HouseholdTable.inviteCode],
            inviteCodeExpiresAt = row[HouseholdTable.inviteCodeExpiresAt].toString(),
            ownerId = row[HouseholdTable.ownerId].toString(),
            members = members
        )
    }
}
