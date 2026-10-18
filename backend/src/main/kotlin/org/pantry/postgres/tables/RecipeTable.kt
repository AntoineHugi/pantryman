package org.pantry.postgres.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

// Shared with every user; only the creator can edit or delete a recipe
object RecipeTable : Table("recipes") {
    val id = uuid("id")
    // Null once the creator has deleted their account
    val createdBy = uuid("created_by").references(UserTable.id, onDelete = ReferenceOption.SET_NULL).nullable().index()
    val name = varchar("name", 200)
    val servings = integer("servings")
    val description = text("description").nullable()
    val prepMinutes = integer("prep_minutes").nullable()
    val cookMinutes = integer("cook_minutes").nullable()
    val sourceUrl = varchar("source_url", 2000).nullable()
    val notes = text("notes").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)
}

object RecipeIngredientTable : Table("recipe_ingredients") {
    val recipeId = uuid("recipe_id").references(RecipeTable.id, onDelete = ReferenceOption.CASCADE)
    val position = integer("position")
    val name = varchar("name", 100)
    // Null for "salt to taste"
    val amount = decimal("amount", 12, 3).nullable()
    // Null for a count ("2 eggs")
    val unit = varchar("unit", 16).nullable()
    val note = varchar("note", 255).nullable()
    val section = varchar("section", 100).nullable()
    val optional = bool("optional").default(false)

    override val primaryKey = PrimaryKey(recipeId, position)
}

object RecipeStepTable : Table("recipe_steps") {
    val recipeId = uuid("recipe_id").references(RecipeTable.id, onDelete = ReferenceOption.CASCADE)
    val position = integer("position")
    val text = text("text")

    override val primaryKey = PrimaryKey(recipeId, position)
}

object RecipeTagTable : Table("recipe_tags") {
    val recipeId = uuid("recipe_id").references(RecipeTable.id, onDelete = ReferenceOption.CASCADE)
    val tag = varchar("tag", 50)

    override val primaryKey = PrimaryKey(recipeId, tag)
}
