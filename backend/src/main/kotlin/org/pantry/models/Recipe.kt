package org.pantry.models

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

// The format of docs/recipe-format.md: used to create, update and import recipes
@Serializable
data class RecipeInput(
    val formatVersion: Int = 1,
    val name: String,
    val servings: Int,
    val description: String? = null,
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val tags: List<String> = emptyList(),
    val sourceUrl: String? = null,
    val ingredients: List<RecipeIngredient>,
    val steps: List<String> = emptyList(),
    val notes: String? = null
)

// Defaults make fields optional in requests; @EncodeDefault still sends them in responses
// (the app's Json doesn't encode defaults), so clients always get every field
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RecipeIngredient(
    val name: String,
    // Null for "salt to taste"
    @EncodeDefault val amount: Double? = null,
    // Null for a count ("2 eggs"); otherwise one of RecipeService.UNITS
    @EncodeDefault val unit: String? = null,
    @EncodeDefault val note: String? = null,
    @EncodeDefault val section: String? = null,
    @EncodeDefault val optional: Boolean = false
)

@Serializable
data class Recipe(
    val id: String,
    // Only this user can edit or delete it. Null once they've deleted their account.
    val createdBy: String?,
    val name: String,
    val servings: Int,
    val description: String?,
    val prepMinutes: Int?,
    val cookMinutes: Int?,
    val tags: List<String>,
    val sourceUrl: String?,
    val ingredients: List<RecipeIngredient>,
    val steps: List<String>,
    val notes: String?
)

// For the recipes page: everything but the ingredients and steps
@Serializable
data class RecipeSummary(
    val id: String,
    val createdBy: String?,
    val name: String,
    val servings: Int,
    val description: String?,
    val prepMinutes: Int?,
    val cookMinutes: Int?,
    val tags: List<String>
)

@Serializable
data class RecipeImportResponse(
    val imported: List<RecipeSummary>,
    // Names of recipes the user had already added (same name), so importing a file twice is harmless
    val skipped: List<String>
)
