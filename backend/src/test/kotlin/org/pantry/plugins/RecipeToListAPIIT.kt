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
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.junit5.KoinTestExtension
import org.pantry.models.AddRecipeToListRequest
import org.pantry.models.AddRecipeToListResponse
import org.pantry.models.CreateGroceryListRequest
import org.pantry.models.GroceryList
import org.pantry.models.Item
import org.pantry.models.RecipeIngredient
import org.pantry.models.RecipeInput
import org.pantry.postgres.tables.*
import org.pantry.repositories.GroceryListRepository
import org.pantry.repositories.HouseholdRepository
import org.pantry.repositories.ItemRepository
import org.pantry.repositories.RecipeRepository
import org.pantry.repositories.UserRepository
import org.pantry.services.GroceryListService
import org.pantry.services.HouseholdService
import org.pantry.services.ItemService
import org.pantry.services.RecipeListService
import org.pantry.services.RecipeService
import java.util.Date
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecipeToListAPIIT : KoinTest {

    @JvmField
    @RegisterExtension
    val koinTestExtension = KoinTestExtension.create {
        modules(
            module {
                single { ItemRepository() }
                single { ItemService(get()) }
                single { GroceryListRepository(get()) }
                single { GroceryListService(get()) }
                single { HouseholdRepository() }
                single { HouseholdService(get()) }
                single { RecipeRepository() }
                single { RecipeService(get()) }
                single { RecipeListService(get(), get()) }
            })
    }

    private val alice = UUID.randomUUID()
    private val bob = UUID.randomUUID()

    @BeforeEach
    fun setupDatabase() {
        Database.connect(url = "jdbc:h2:mem:recipelists;DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")
        transaction {
            SchemaUtils.create(
                UserTable, HouseholdTable, HouseholdMemberTable, GroceryListTable, ItemTable,
                RecipeTable, RecipeIngredientTable, RecipeStepTable, RecipeTagTable, MealPlanTable, MealPlanEntryTable
            )
            listOf(RecipeTagTable, RecipeStepTable, RecipeIngredientTable, RecipeTable, ItemTable, GroceryListTable,
                HouseholdMemberTable, HouseholdTable, UserTable).forEach { it.deleteAll() }
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
                // Like Application.kt: defaults aren't encoded
                json(Json { ignoreUnknownKeys = true })
            }
            configureStatusPages()
            configureSecurity()
            groceriesApi()
            recipesApi()
        }
    }

    private val ApplicationTestBuilder.jsonClient
        get() = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

    private val bolognese = RecipeInput(
        name = "Bolognese",
        servings = 4,
        ingredients = listOf(
            RecipeIngredient("minced beef", 500.0, "g"),
            RecipeIngredient("onion", 1.0),
            RecipeIngredient("salt"),
            RecipeIngredient("red wine", 150.0, "ml", optional = true)
        )
    )

    private suspend fun ApplicationTestBuilder.createRecipe(user: UUID): String =
        jsonClient.post("/recipes") {
            contentType(ContentType.Application.Json)
            bearerAuth(token(user))
            setBody(bolognese)
        }.body<org.pantry.models.Recipe>().id

    private suspend fun ApplicationTestBuilder.createList(user: UUID): GroceryList =
        jsonClient.post("/lists") {
            contentType(ContentType.Application.Json)
            bearerAuth(token(user))
            setBody(CreateGroceryListRequest("Weekly"))
        }.body()

    private suspend fun ApplicationTestBuilder.addRecipe(user: UUID, listId: String, request: AddRecipeToListRequest): HttpResponse =
        jsonClient.post("/lists/$listId/recipes") {
            contentType(ContentType.Application.Json)
            bearerAuth(token(user))
            setBody(request)
        }

    @Test
    fun `adds the recipe's ingredients, scaled, without optional ones by default`() = testApplication {
        setupTestApplication()
        val recipeId = createRecipe(bob)
        val list = createList(alice)

        val response = addRecipe(alice, list.id, AddRecipeToListRequest(recipeId, servings = 6))

        assertEquals(HttpStatusCode.OK, response.status)
        val result: AddRecipeToListResponse = response.body()
        assertEquals(3, result.added)
        assertEquals(0, result.merged)
        assertEquals(
            listOf(Triple("minced beef", 750.0, "g"), Triple("onion", 2.0, null), Triple("salt", 1.0, null)),
            result.list.items.map { Triple(it.name, it.quantity, it.unit) }
        )
        assertTrue(result.list.items.all { it.fromRecipes == "Bolognese" })
    }

    @Test
    fun `adding twice merges into the same items`() = testApplication {
        setupTestApplication()
        val recipeId = createRecipe(alice)
        val list = createList(alice)
        addRecipe(alice, list.id, AddRecipeToListRequest(recipeId))

        val result: AddRecipeToListResponse = addRecipe(alice, list.id, AddRecipeToListRequest(recipeId)).body()

        assertEquals(0, result.added)
        assertEquals(3, result.merged)
        assertEquals(listOf(1.0 to "kg", 2.0 to null, 1.0 to null), result.list.items.map { it.quantity to it.unit })
    }

    @Test
    fun `chosen ingredients only, including an optional one`() = testApplication {
        setupTestApplication()
        val recipeId = createRecipe(alice)
        val list = createList(alice)

        val result: AddRecipeToListResponse =
            addRecipe(alice, list.id, AddRecipeToListRequest(recipeId, ingredientIndexes = listOf(0, 3))).body()

        assertEquals(listOf("minced beef", "red wine"), result.list.items.map { it.name })
    }

    @Test
    fun `bad requests return 400 or 404`() = testApplication {
        setupTestApplication()
        val recipeId = createRecipe(alice)
        val list = createList(alice)

        assertEquals(HttpStatusCode.NotFound, addRecipe(alice, list.id, AddRecipeToListRequest(UUID.randomUUID().toString())).status)
        assertEquals(HttpStatusCode.BadRequest, addRecipe(alice, list.id, AddRecipeToListRequest("nope")).status)
        assertEquals(HttpStatusCode.BadRequest, addRecipe(alice, list.id, AddRecipeToListRequest(recipeId, servings = 0)).status)
        assertEquals(HttpStatusCode.BadRequest, addRecipe(alice, list.id, AddRecipeToListRequest(recipeId, ingredientIndexes = listOf(9))).status)
        assertEquals(HttpStatusCode.BadRequest, addRecipe(alice, list.id, AddRecipeToListRequest(recipeId, ingredientIndexes = emptyList())).status)
    }

    @Test
    fun `can't add to someone else's list`() = testApplication {
        setupTestApplication()
        val recipeId = createRecipe(bob)
        val alicesList = createList(alice)

        assertEquals(HttpStatusCode.Forbidden, addRecipe(bob, alicesList.id, AddRecipeToListRequest(recipeId)).status)
        val unchanged: GroceryList = jsonClient.get("/lists/${alicesList.id}") { bearerAuth(token(alice)) }.body()
        assertTrue(unchanged.items.isEmpty())
    }

    @Test
    fun `items take decimal quantities and units, and old clients' requests still work`() = testApplication {
        setupTestApplication()
        val list = createList(alice)

        // An older app sends an integer quantity and no unit
        val old = jsonClient.post("/lists/${list.id}/items") {
            contentType(ContentType.Application.Json)
            bearerAuth(token(alice))
            setBody("""{"name": "milk", "quantity": 2}""")
        }
        assertEquals(HttpStatusCode.Created, old.status)
        val milk: Item = old.body()
        assertEquals(2.0, milk.quantity)

        val flour = jsonClient.post("/lists/${list.id}/items") {
            contentType(ContentType.Application.Json)
            bearerAuth(token(alice))
            setBody("""{"name": "flour", "quantity": 0.5, "unit": "KG"}""")
        }.body<Item>()
        assertEquals(0.5 to "kg", flour.quantity to flour.unit)

        // Missing unit leaves it; "" removes it
        jsonClient.patch("/lists/${list.id}/items/${flour.id}") {
            contentType(ContentType.Application.Json)
            bearerAuth(token(alice))
            setBody("""{"quantity": 1}""")
        }
        val raw = jsonClient.get("/lists/${list.id}") { bearerAuth(token(alice)) }.bodyAsText()
        assertTrue(raw.contains("\"unit\":\"kg\"") && raw.contains("\"unit\":null") && raw.contains("\"fromRecipes\":null"), raw)
        jsonClient.patch("/lists/${list.id}/items/${flour.id}") {
            contentType(ContentType.Application.Json)
            bearerAuth(token(alice))
            setBody("""{"unit": ""}""")
        }
        val items = jsonClient.get("/lists/${list.id}") { bearerAuth(token(alice)) }.body<GroceryList>().items
        assertEquals(listOf(2.0 to null, 1.0 to null), items.map { it.quantity to it.unit })

        val badUnit = jsonClient.post("/lists/${list.id}/items") {
            contentType(ContentType.Application.Json)
            bearerAuth(token(alice))
            setBody("""{"name": "rice", "quantity": 1, "unit": "bags"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, badUnit.status)
    }
}
