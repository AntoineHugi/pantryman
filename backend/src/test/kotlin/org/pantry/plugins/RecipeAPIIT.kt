package org.pantry.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.junit5.KoinTestExtension
import org.pantry.models.Recipe
import org.pantry.models.RecipeImportResponse
import org.pantry.models.RecipeIngredient
import org.pantry.models.RecipeInput
import org.pantry.models.RecipeSummary
import org.pantry.postgres.tables.RecipeIngredientTable
import org.pantry.postgres.tables.RecipeStepTable
import org.pantry.postgres.tables.RecipeTable
import org.pantry.postgres.tables.RecipeTagTable
import org.pantry.postgres.tables.UserTable
import org.pantry.repositories.RecipeRepository
import org.pantry.repositories.UserRepository
import org.pantry.services.RecipeService
import java.util.Date
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecipeAPIIT : KoinTest {

    @JvmField
    @RegisterExtension
    val koinTestExtension = KoinTestExtension.create {
        modules(
            module {
                single { RecipeRepository() }
                single { RecipeService(get()) }
            })
    }

    private val alice = UUID.randomUUID()
    private val bob = UUID.randomUUID()

    @BeforeEach
    fun setupDatabase() {
        Database.connect(
            url = "jdbc:h2:mem:recipes;DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.create(UserTable, RecipeTable, RecipeIngredientTable, RecipeStepTable, RecipeTagTable)
            RecipeTagTable.deleteAll()
            RecipeStepTable.deleteAll()
            RecipeIngredientTable.deleteAll()
            RecipeTable.deleteAll()
            UserTable.deleteAll()
        }
        listOf(alice, bob).forEach { UserRepository().create(it, "$it@example.com", "hash", UUID.randomUUID()) }
    }

    private fun token(userId: UUID): String = JWT.create()
        .withAudience("test-audience")
        .withIssuer("test-issuer")
        .withClaim("userId", userId.toString())
        .withExpiresAt(Date(System.currentTimeMillis() + 60000))
        .sign(Algorithm.HMAC256("test-secret-key-for-testing-only"))

    private fun ApplicationTestBuilder.setupTestApplication() {
        environment {
            config = MapApplicationConfig(
                "jwt.secret" to "test-secret-key-for-testing-only",
                "jwt.issuer" to "test-issuer",
                "jwt.audience" to "test-audience"
            )
        }
        application {
            install(ContentNegotiation) {
                // Like Application.kt: defaults aren't encoded, unlike Ktor's default Json
                json(Json { ignoreUnknownKeys = true })
            }
            configureStatusPages()
            configureSecurity()
            recipesApi()
        }
    }

    private val ApplicationTestBuilder.jsonClient
        get() = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

    private fun recipe(name: String = "Spaghetti Bolognese") = RecipeInput(
        name = name,
        servings = 4,
        tags = listOf("Pasta", "beef"),
        ingredients = listOf(
            RecipeIngredient(name = "minced beef", amount = 500.0, unit = "g", note = "or half pork"),
            RecipeIngredient(name = "Onion", amount = 1.0),
            RecipeIngredient(name = "salt"),
            RecipeIngredient(name = "parmesan", amount = 50.0, unit = "g", section = "To serve", optional = true)
        ),
        steps = listOf("Brown the beef.", "Simmer for an hour.")
    )

    private suspend fun ApplicationTestBuilder.create(user: UUID, input: RecipeInput = recipe()): HttpResponse =
        jsonClient.post("/recipes") {
            contentType(ContentType.Application.Json)
            bearerAuth(token(user))
            setBody(input)
        }

    private suspend fun ApplicationTestBuilder.import(user: UUID, json: String): HttpResponse =
        jsonClient.post("/recipes/import") {
            contentType(ContentType.Application.Json)
            bearerAuth(token(user))
            setBody(json)
        }

    @Test
    fun `create stores the recipe and normalizes it`() = testApplication {
        setupTestApplication()

        val response = create(alice)

        assertEquals(HttpStatusCode.Created, response.status)
        val created: Recipe = response.body()
        assertEquals(alice.toString(), created.createdBy)
        assertEquals(listOf("beef", "pasta"), created.tags)
        assertEquals(listOf("minced beef", "onion", "salt", "parmesan"), created.ingredients.map { it.name })
        assertEquals(500.0, created.ingredients[0].amount)
        assertNull(created.ingredients[2].amount)
        assertEquals("To serve", created.ingredients[3].section)
        assertTrue(created.ingredients[3].optional)
        assertEquals(listOf("Brown the beef.", "Simmer for an hour."), created.steps)

        val fetched: Recipe = jsonClient.get("/recipes/${created.id}") { bearerAuth(token(alice)) }.body()
        assertEquals(created, fetched)
    }

    @Test
    fun `every user sees every recipe, in alphabetical order`() = testApplication {
        setupTestApplication()
        create(alice, recipe("lasagna"))
        create(bob, recipe("Chicken Curry"))

        val all: List<RecipeSummary> = jsonClient.get("/recipes") { bearerAuth(token(bob)) }.body()

        assertEquals(listOf("Chicken Curry", "lasagna"), all.map { it.name })
        assertEquals(listOf("beef", "pasta"), all[0].tags)
    }

    @Test
    fun `responses include empty and null fields`() = testApplication {
        setupTestApplication()
        val created: Recipe = create(alice, recipe().copy(tags = emptyList())).body()

        val summaries = jsonClient.get("/recipes") { bearerAuth(token(alice)) }.bodyAsText()
        assertTrue(summaries.contains("\"tags\":[]") && summaries.contains("\"description\":null"), summaries)
        val detail = jsonClient.get("/recipes/${created.id}") { bearerAuth(token(alice)) }.bodyAsText()
        assertTrue(detail.contains("\"amount\":null") && detail.contains("\"optional\":false"), detail)
    }

    @Test
    fun `another user can read a recipe but not update or delete it`() = testApplication {
        setupTestApplication()
        val created: Recipe = create(alice).body()

        assertEquals(HttpStatusCode.OK, jsonClient.get("/recipes/${created.id}") { bearerAuth(token(bob)) }.status)

        val update = jsonClient.put("/recipes/${created.id}") {
            contentType(ContentType.Application.Json)
            bearerAuth(token(bob))
            setBody(recipe("Hijacked"))
        }
        assertEquals(HttpStatusCode.Forbidden, update.status)
        assertEquals(HttpStatusCode.Forbidden, jsonClient.delete("/recipes/${created.id}") { bearerAuth(token(bob)) }.status)

        val unchanged: Recipe = jsonClient.get("/recipes/${created.id}") { bearerAuth(token(alice)) }.body()
        assertEquals("Spaghetti Bolognese", unchanged.name)
    }

    @Test
    fun `creator can update and delete their recipe`() = testApplication {
        setupTestApplication()
        val created: Recipe = create(alice).body()

        val update = jsonClient.put("/recipes/${created.id}") {
            contentType(ContentType.Application.Json)
            bearerAuth(token(alice))
            setBody(recipe("Bolognese").copy(ingredients = listOf(RecipeIngredient("spaghetti", 400.0, "g")), steps = emptyList()))
        }
        assertEquals(HttpStatusCode.OK, update.status)
        val updated: Recipe = update.body()
        assertEquals("Bolognese", updated.name)
        assertEquals(listOf("spaghetti"), updated.ingredients.map { it.name })
        assertTrue(updated.steps.isEmpty())

        assertEquals(HttpStatusCode.NoContent, jsonClient.delete("/recipes/${created.id}") { bearerAuth(token(alice)) }.status)
        assertEquals(HttpStatusCode.NotFound, jsonClient.get("/recipes/${created.id}") { bearerAuth(token(alice)) }.status)
        // The ingredients went with it
        assertEquals(0L, transaction { RecipeIngredientTable.selectAll().count() })
    }

    @Test
    fun `unknown recipe returns 404 and requests without a token 401`() = testApplication {
        setupTestApplication()

        assertEquals(HttpStatusCode.NotFound, jsonClient.get("/recipes/${UUID.randomUUID()}") { bearerAuth(token(alice)) }.status)
        assertEquals(HttpStatusCode.Unauthorized, jsonClient.get("/recipes").status)
    }

    @Test
    fun `invalid recipe returns 400 with the reason`() = testApplication {
        setupTestApplication()

        val response = create(alice, recipe().copy(ingredients = listOf(RecipeIngredient("flour", 200.0, "grams"))))

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("Ingredient 1: Unknown unit"))
    }

    @Test
    fun `recipe stays when its creator deletes their account`() = testApplication {
        setupTestApplication()
        val created: Recipe = create(alice).body()

        UserRepository().delete(alice)

        val fetched: Recipe = jsonClient.get("/recipes/${created.id}") { bearerAuth(token(bob)) }.body()
        assertNull(fetched.createdBy)
    }

    @Test
    fun `import accepts a single recipe or an array and skips names already added`() = testApplication {
        setupTestApplication()
        val single = """{"formatVersion": 1, "name": "Pancakes", "servings": 2, "unknownField": true,
            "ingredients": [{"amount": 0.5, "unit": "l", "name": "milk"}, {"amount": 2, "unit": null, "name": "egg"}],
            "steps": ["Whisk.", "Fry."]}"""

        val first = import(alice, single)
        assertEquals(HttpStatusCode.OK, first.status)
        val firstResult: RecipeImportResponse = first.body()
        assertEquals(listOf("Pancakes"), firstResult.imported.map { it.name })

        val second: RecipeImportResponse = import(alice, """[$single, ${single.replace("Pancakes", "Waffles")}]""").body()
        assertEquals(listOf("Waffles"), second.imported.map { it.name })
        assertEquals(listOf("Pancakes"), second.skipped)

        // Another user's recipe with the same name isn't a duplicate of theirs
        val bobs: RecipeImportResponse = import(bob, single).body()
        assertEquals(listOf("Pancakes"), bobs.imported.map { it.name })

        val pancakes = transaction { RecipeTable.selectAll().count() }
        assertEquals(3L, pancakes)
    }

    @Test
    fun `import saves nothing when one recipe is invalid`() = testApplication {
        setupTestApplication()
        val good = """{"name": "Pancakes", "servings": 2, "ingredients": [{"amount": 2, "unit": null, "name": "egg"}]}"""
        val bad = """{"name": "Soup", "servings": 0, "ingredients": [{"amount": 1, "unit": "l", "name": "water"}]}"""

        val response = import(alice, "[$good, $bad]")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("Recipe \\\"Soup\\\": servings must be between 1 and 100"), response.bodyAsText())
        assertEquals(0L, transaction { RecipeTable.selectAll().count() })
    }

    @Test
    fun `import reports which recipe doesn't match the format`() = testApplication {
        setupTestApplication()
        val good = """{"name": "Pancakes", "servings": 2, "ingredients": [{"amount": 2, "unit": null, "name": "egg"}]}"""
        val missingServings = """{"name": "Soup", "ingredients": []}"""

        val response = import(alice, "[$good, $missingServings]")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("Recipe number 2 doesn't match the recipe format"), response.bodyAsText())
        assertEquals(HttpStatusCode.BadRequest, import(alice, "not json").status)
        assertEquals(HttpStatusCode.BadRequest, import(alice, "[]").status)
    }
}
