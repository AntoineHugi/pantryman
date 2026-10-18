package org.pantry.repositories

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.pantry.models.Recipe
import org.pantry.models.RecipeIngredient
import org.pantry.models.RecipeInput
import org.pantry.models.RecipeSummary
import org.pantry.postgres.tables.RecipeIngredientTable
import org.pantry.postgres.tables.RecipeStepTable
import org.pantry.postgres.tables.RecipeTable
import org.pantry.postgres.tables.RecipeTagTable
import java.math.BigDecimal
import java.util.UUID

// Recipes are shared with every user, so reads aren't scoped; the routes check the creator for edits
class RecipeRepository {

    // Alphabetical, so the recipes page reads like a cookbook index
    fun getAll(): List<RecipeSummary> = transaction {
        val tags = RecipeTagTable.selectAll()
            .orderBy(RecipeTagTable.tag to SortOrder.ASC)
            .groupBy({ it[RecipeTagTable.recipeId] }, { it[RecipeTagTable.tag] })
        RecipeTable.selectAll()
            .orderBy(RecipeTable.name.lowerCase() to SortOrder.ASC, RecipeTable.id to SortOrder.ASC)
            .map { it.toSummary(tags[it[RecipeTable.id]].orEmpty()) }
    }

    fun get(id: UUID): Recipe? = transaction {
        val row = RecipeTable.selectAll().where { RecipeTable.id eq id }.singleOrNull() ?: return@transaction null
        val ingredients = RecipeIngredientTable.selectAll()
            .where { RecipeIngredientTable.recipeId eq id }
            .orderBy(RecipeIngredientTable.position to SortOrder.ASC)
            .map {
                RecipeIngredient(
                    name = it[RecipeIngredientTable.name],
                    amount = it[RecipeIngredientTable.amount]?.toDouble(),
                    unit = it[RecipeIngredientTable.unit],
                    note = it[RecipeIngredientTable.note],
                    section = it[RecipeIngredientTable.section],
                    optional = it[RecipeIngredientTable.optional]
                )
            }
        val steps = RecipeStepTable.selectAll()
            .where { RecipeStepTable.recipeId eq id }
            .orderBy(RecipeStepTable.position to SortOrder.ASC)
            .map { it[RecipeStepTable.text] }
        val summary = row.toSummary(tagsOf(id))
        Recipe(
            id = summary.id,
            createdBy = summary.createdBy,
            name = summary.name,
            servings = summary.servings,
            description = summary.description,
            prepMinutes = summary.prepMinutes,
            cookMinutes = summary.cookMinutes,
            tags = summary.tags,
            sourceUrl = row[RecipeTable.sourceUrl],
            ingredients = ingredients,
            steps = steps,
            notes = row[RecipeTable.notes]
        )
    }

    // Lowercase names of the user's own recipes, so an import can skip ones they already added
    fun namesCreatedBy(userId: UUID): Set<String> = transaction {
        RecipeTable.select(RecipeTable.name)
            .where { RecipeTable.createdBy eq userId }
            .map { it[RecipeTable.name].lowercase() }
            .toSet()
    }

    // All or nothing: one transaction, so a failing recipe leaves none of the others behind
    fun createAll(recipes: List<RecipeInput>, createdBy: UUID): List<UUID> = transaction {
        recipes.map { recipe ->
            val id = UUID.randomUUID()
            RecipeTable.insert {
                it[RecipeTable.id] = id
                it[RecipeTable.createdBy] = createdBy
                writeFields(it, recipe)
            }
            insertChildren(id, recipe)
            id
        }
    }

    fun update(id: UUID, recipe: RecipeInput) = transaction {
        RecipeTable.update({ RecipeTable.id eq id }) {
            writeFields(it, recipe)
            it[updatedAt] = CurrentTimestamp
        }
        deleteChildren(id)
        insertChildren(id, recipe)
    }

    // Ingredients, steps and tags go with it (ON DELETE CASCADE)
    fun delete(id: UUID): Boolean = transaction {
        RecipeTable.deleteWhere { RecipeTable.id eq id } > 0
    }

    private fun RecipeTable.writeFields(it: UpdateBuilder<*>, recipe: RecipeInput) {
        it[name] = recipe.name
        it[servings] = recipe.servings
        it[description] = recipe.description
        it[prepMinutes] = recipe.prepMinutes
        it[cookMinutes] = recipe.cookMinutes
        it[sourceUrl] = recipe.sourceUrl
        it[notes] = recipe.notes
    }

    private fun insertChildren(id: UUID, recipe: RecipeInput) {
        RecipeIngredientTable.batchInsert(recipe.ingredients.withIndex()) { (index, ingredient) ->
            this[RecipeIngredientTable.recipeId] = id
            this[RecipeIngredientTable.position] = index
            this[RecipeIngredientTable.name] = ingredient.name
            this[RecipeIngredientTable.amount] = ingredient.amount?.let { BigDecimal.valueOf(it) }
            this[RecipeIngredientTable.unit] = ingredient.unit
            this[RecipeIngredientTable.note] = ingredient.note
            this[RecipeIngredientTable.section] = ingredient.section
            this[RecipeIngredientTable.optional] = ingredient.optional
        }
        RecipeStepTable.batchInsert(recipe.steps.withIndex()) { (index, step) ->
            this[RecipeStepTable.recipeId] = id
            this[RecipeStepTable.position] = index
            this[RecipeStepTable.text] = step
        }
        RecipeTagTable.batchInsert(recipe.tags) { tag ->
            this[RecipeTagTable.recipeId] = id
            this[RecipeTagTable.tag] = tag
        }
    }

    private fun deleteChildren(id: UUID) {
        RecipeIngredientTable.deleteWhere { recipeId eq id }
        RecipeStepTable.deleteWhere { recipeId eq id }
        RecipeTagTable.deleteWhere { recipeId eq id }
    }

    private fun tagsOf(id: UUID): List<String> =
        RecipeTagTable.select(RecipeTagTable.tag)
            .where { RecipeTagTable.recipeId eq id }
            .orderBy(RecipeTagTable.tag to SortOrder.ASC)
            .map { it[RecipeTagTable.tag] }

    private fun ResultRow.toSummary(tags: List<String>) = RecipeSummary(
        id = this[RecipeTable.id].toString(),
        createdBy = this[RecipeTable.createdBy]?.toString(),
        name = this[RecipeTable.name],
        servings = this[RecipeTable.servings],
        description = this[RecipeTable.description],
        prepMinutes = this[RecipeTable.prepMinutes],
        cookMinutes = this[RecipeTable.cookMinutes],
        tags = tags
    )

    // Summaries of the given recipes, in the given order (e.g. what an import just added)
    fun getSummaries(ids: List<UUID>): List<RecipeSummary> = transaction {
        if (ids.isEmpty()) return@transaction emptyList()
        val tags = RecipeTagTable.selectAll()
            .where { RecipeTagTable.recipeId inList ids }
            .orderBy(RecipeTagTable.tag to SortOrder.ASC)
            .groupBy({ it[RecipeTagTable.recipeId] }, { it[RecipeTagTable.tag] })
        val byId = RecipeTable.selectAll()
            .where { RecipeTable.id inList ids }
            .associate { it[RecipeTable.id] to it.toSummary(tags[it[RecipeTable.id]].orEmpty()) }
        ids.mapNotNull { byId[it] }
    }
}
