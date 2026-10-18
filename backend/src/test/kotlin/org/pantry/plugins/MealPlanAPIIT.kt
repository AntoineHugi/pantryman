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
import org.pantry.models.*
import org.pantry.postgres.tables.*
import org.pantry.repositories.*
import org.pantry.services.*
import java.util.Date
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MealPlanAPIIT : KoinTest {

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
                single { PantryRepository() }
                single { PantryService(get(), get()) }
                single { RecipeRepository() }
                single { RecipeService(get()) }
                single { RecipeListService(get(), get()) }
                single { MealPlanRepository() }
                single { MealPlanService(get(), get(), get(), get(), get()) }
            })
    }

    private val alice = UUID.randomUUID()
    private val bob = UUID.randomUUID()
    private val eve = UUID.randomUUID()

    @BeforeEach
    fun setupDatabase() {
        Database.connect(url = "jdbc:h2:mem:mealplans;DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")
        transaction {
            SchemaUtils.create(
                UserTable, HouseholdTable, HouseholdMemberTable, GroceryListTable, ItemTable,
                RecipeTable, RecipeIngredientTable, RecipeStepTable, RecipeTagTable, MealPlanTable, MealPlanEntryTable, PantryItemTable
            )
            listOf(MealPlanEntryTable, MealPlanTable, RecipeTagTable, RecipeStepTable, RecipeIngredientTable, RecipeTable,
                ItemTable, GroceryListTable, HouseholdMemberTable, HouseholdTable, UserTable).forEach { it.deleteAll() }
        }
        listOf(alice, bob, eve).forEach { UserRepository().create(it, "$it@example.com", "hash", UUID.randomUUID()) }
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
            configureRateLimiting()
            groceriesApi()
            householdsApi()
            recipesApi()
            mealPlansApi()
        }
    }

    private val ApplicationTestBuilder.jsonClient
        get() = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

    private suspend fun ApplicationTestBuilder.recipe(name: String, vararg ingredients: RecipeIngredient): String =
        jsonClient.post("/recipes") {
            contentType(ContentType.Application.Json)
            bearerAuth(token(alice))
            setBody(RecipeInput(name = name, servings = 2, ingredients = ingredients.toList()))
        }.body<Recipe>().id

    private suspend fun ApplicationTestBuilder.createPlan(user: UUID, householdId: String? = null, people: Int = 4): MealPlan {
        val response = jsonClient.post("/meal-plans") {
            contentType(ContentType.Application.Json)
            bearerAuth(token(user))
            setBody(CreateMealPlanRequest("2026-10-20", 7, householdId, people))
        }
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        return response.body()
    }

    private suspend fun ApplicationTestBuilder.setMeal(user: UUID, planId: String, date: String, meal: String, recipeId: String, servings: Int? = null): HttpResponse =
        jsonClient.put("/meal-plans/$planId/entries/$date/$meal") {
            contentType(ContentType.Application.Json)
            bearerAuth(token(user))
            setBody(SetMealRequest(recipeId, servings))
        }

    private suspend fun ApplicationTestBuilder.plan(user: UUID, planId: String): HttpResponse =
        jsonClient.get("/meal-plans/$planId") { bearerAuth(token(user)) }

    private fun MealPlan.slots() = entries.map { "${it.date} ${it.meal} ${it.recipeName} ${it.servings}" }

    @Test
    fun `create a plan and fill slots, servings defaulting to the plan's people`() = testApplication {
        setupTestApplication()
        val soup = recipe("Soup", RecipeIngredient("water", 1.0, "l"))
        val created = createPlan(alice)
        assertEquals("2026-10-26", created.endDate)

        setMeal(alice, created.id, "2026-10-21", "dinner", soup)
        val response = setMeal(alice, created.id, "2026-10-21", "lunch", soup, servings = 2)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(listOf("2026-10-21 lunch Soup 2", "2026-10-21 dinner Soup 4"), response.body<MealPlan>().slots())
        val all: List<MealPlan> = jsonClient.get("/meal-plans") { bearerAuth(token(alice)) }.body()
        assertEquals(listOf(created.id), all.map { it.id })
    }

    @Test
    fun `slots outside the plan or with an unknown meal are rejected`() = testApplication {
        setupTestApplication()
        val soup = recipe("Soup", RecipeIngredient("water", 1.0, "l"))
        val plan = createPlan(alice)

        assertEquals(HttpStatusCode.BadRequest, setMeal(alice, plan.id, "2026-10-27", "lunch", soup).status)
        assertEquals(HttpStatusCode.BadRequest, setMeal(alice, plan.id, "2026-10-20", "breakfast", soup).status)
        assertEquals(HttpStatusCode.BadRequest, setMeal(alice, plan.id, "20-10-2026", "lunch", soup).status)
        assertEquals(HttpStatusCode.BadRequest, setMeal(alice, plan.id, "2026-10-20", "lunch", UUID.randomUUID().toString()).status)
        assertEquals(HttpStatusCode.BadRequest, setMeal(alice, plan.id, "2026-10-20", "lunch", soup, servings = 0).status)
    }

    @Test
    fun `moving a meal to an empty slot moves it, to a full one swaps them`() = testApplication {
        setupTestApplication()
        val soup = recipe("Soup", RecipeIngredient("water", 1.0, "l"))
        val pasta = recipe("Pasta", RecipeIngredient("spaghetti", 200.0, "g"))
        val plan = createPlan(alice)
        setMeal(alice, plan.id, "2026-10-20", "lunch", soup)
        setMeal(alice, plan.id, "2026-10-20", "dinner", pasta, servings = 3)

        suspend fun move(from: MealSlot, to: MealSlot) = jsonClient.post("/meal-plans/${plan.id}/move") {
            contentType(ContentType.Application.Json)
            bearerAuth(token(alice))
            setBody(MoveMealRequest(from, to))
        }

        val swapped: MealPlan = move(MealSlot("2026-10-20", "lunch"), MealSlot("2026-10-20", "dinner")).body()
        assertEquals(listOf("2026-10-20 lunch Pasta 3", "2026-10-20 dinner Soup 4"), swapped.slots())

        val moved: MealPlan = move(MealSlot("2026-10-20", "lunch"), MealSlot("2026-10-23", "dinner")).body()
        assertEquals(listOf("2026-10-20 dinner Soup 4", "2026-10-23 dinner Pasta 3"), moved.slots())

        assertEquals(HttpStatusCode.NotFound, move(MealSlot("2026-10-21", "lunch"), MealSlot("2026-10-22", "lunch")).status)

        val removed = jsonClient.delete("/meal-plans/${plan.id}/entries/2026-10-20/dinner") { bearerAuth(token(alice)) }
        assertEquals(listOf("2026-10-23 dinner Pasta 3"), removed.body<MealPlan>().slots())
    }

    @Test
    fun `confirming creates the list from every meal, merged and scaled`() = testApplication {
        setupTestApplication()
        val soup = recipe("Soup", RecipeIngredient("onion", 1.0), RecipeIngredient("stock", 500.0, "ml"), RecipeIngredient("cream", 100.0, "ml", optional = true))
        val pasta = recipe("Pasta", RecipeIngredient("spaghetti", 200.0, "g"), RecipeIngredient("onion", 1.0))
        val plan = createPlan(alice, people = 4)
        setMeal(alice, plan.id, "2026-10-20", "dinner", soup)
        setMeal(alice, plan.id, "2026-10-22", "lunch", pasta, servings = 2)
        setMeal(alice, plan.id, "2026-10-24", "dinner", soup)

        val response = jsonClient.post("/meal-plans/${plan.id}/confirm") { bearerAuth(token(alice)) }

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        val result: ConfirmMealPlanResponse = response.body()
        assertEquals("Meal plan - 20.10/26.10", result.list.name)
        assertEquals(result.list.id, result.plan.listId)
        // Soup for 4, twice: 2 onions + 1 l stock each. Pasta for 2: 1 onion.
        assertEquals(
            listOf("onion 5.0 null Soup, Pasta", "stock 2.0 l Soup", "spaghetti 200.0 g Pasta"),
            result.list.items.map { "${it.name} ${it.quantity} ${it.unit} ${it.fromRecipes}" }
        )

        val again = jsonClient.post("/meal-plans/${plan.id}/confirm") { bearerAuth(token(alice)) }
        assertEquals(HttpStatusCode.BadRequest, again.status)

        // Deleting the list lets the plan make a new one
        jsonClient.delete("/lists/${result.list.id}") { bearerAuth(token(alice)) }
        assertNull(plan(alice, plan.id).body<MealPlan>().listId)
    }

    @Test
    fun `an empty plan can't be confirmed`() = testApplication {
        setupTestApplication()
        val plan = createPlan(alice)

        val response = jsonClient.post("/meal-plans/${plan.id}/confirm") { bearerAuth(token(alice)) }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `a shared plan is open to household members only, and its list is shared too`() = testApplication {
        setupTestApplication()
        val soup = recipe("Soup", RecipeIngredient("water", 1.0, "l"))
        val household: Household = jsonClient.post("/households") {
            contentType(ContentType.Application.Json)
            bearerAuth(token(alice))
            setBody(CreateHouseholdRequest("Home"))
        }.body()
        jsonClient.post("/households/join") {
            contentType(ContentType.Application.Json)
            bearerAuth(token(bob))
            setBody(JoinHouseholdRequest(household.inviteCode))
        }
        val plan = createPlan(alice, household.id)

        assertEquals(HttpStatusCode.OK, setMeal(bob, plan.id, "2026-10-20", "lunch", soup).status)
        assertEquals(HttpStatusCode.NotFound, plan(eve, plan.id).status)
        assertEquals(HttpStatusCode.NotFound, setMeal(eve, plan.id, "2026-10-20", "dinner", soup).status)
        assertEquals(HttpStatusCode.NotFound, jsonClient.delete("/meal-plans/${plan.id}") { bearerAuth(token(eve)) }.status)
        assertTrue(jsonClient.get("/meal-plans") { bearerAuth(token(eve)) }.body<List<MealPlan>>().isEmpty())

        val result: ConfirmMealPlanResponse = jsonClient.post("/meal-plans/${plan.id}/confirm") { bearerAuth(token(bob)) }.body()
        assertEquals(household.id, result.list.householdId)

        // Eve can't make a plan in a household she isn't in
        val forbidden = jsonClient.post("/meal-plans") {
            contentType(ContentType.Application.Json)
            bearerAuth(token(eve))
            setBody(CreateMealPlanRequest("2026-10-20", householdId = household.id))
        }
        assertEquals(HttpStatusCode.Forbidden, forbidden.status)

        // Alice leaves: the plan stays with the household, now owned by Bob
        jsonClient.post("/households/${household.id}/leave") { bearerAuth(token(alice)) }
        val kept: MealPlan = plan(bob, plan.id).body()
        assertEquals(bob.toString(), kept.userId)
        assertEquals(HttpStatusCode.NotFound, plan(alice, plan.id).status)
    }

    @Test
    fun `deleting a recipe removes its meals`() = testApplication {
        setupTestApplication()
        val soup = recipe("Soup", RecipeIngredient("water", 1.0, "l"))
        val plan = createPlan(alice)
        setMeal(alice, plan.id, "2026-10-20", "lunch", soup)

        jsonClient.delete("/recipes/$soup") { bearerAuth(token(alice)) }

        val after: MealPlan = plan(alice, plan.id).body()
        assertTrue(after.entries.isEmpty())
        assertNotNull(after.id)
    }

    @Test
    fun `invalid plans are rejected`() = testApplication {
        setupTestApplication()
        suspend fun create(request: CreateMealPlanRequest) = jsonClient.post("/meal-plans") {
            contentType(ContentType.Application.Json)
            bearerAuth(token(alice))
            setBody(request)
        }.status

        assertEquals(HttpStatusCode.BadRequest, create(CreateMealPlanRequest("tomorrow")))
        assertEquals(HttpStatusCode.BadRequest, create(CreateMealPlanRequest("2026-10-20", days = 15)))
        assertEquals(HttpStatusCode.BadRequest, create(CreateMealPlanRequest("2026-10-20", people = 0)))
    }
}
