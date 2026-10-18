package org.pantry.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
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
import kotlin.test.assertTrue

class PantryAPIIT : KoinTest {

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
        Database.connect(url = "jdbc:h2:mem:pantry;DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")
        transaction {
            SchemaUtils.create(
                UserTable, HouseholdTable, HouseholdMemberTable, GroceryListTable, ItemTable, RecipeTable,
                RecipeIngredientTable, RecipeStepTable, RecipeTagTable, MealPlanTable, MealPlanEntryTable, PantryItemTable
            )
            listOf(PantryItemTable, MealPlanEntryTable, MealPlanTable, RecipeTagTable, RecipeStepTable, RecipeIngredientTable,
                RecipeTable, ItemTable, GroceryListTable, HouseholdMemberTable, HouseholdTable, UserTable).forEach { it.deleteAll() }
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
                json(Json { ignoreUnknownKeys = true })
            }
            configureStatusPages()
            configureSecurity()
            configureRateLimiting()
            groceriesApi()
            householdsApi()
            recipesApi()
            mealPlansApi()
            pantryApi()
        }
    }

    private val ApplicationTestBuilder.jsonClient
        get() = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

    private suspend fun ApplicationTestBuilder.addPantry(user: UUID, request: AddPantryItemRequest): HttpResponse =
        jsonClient.post("/pantry-items") {
            contentType(ContentType.Application.Json)
            bearerAuth(token(user))
            setBody(request)
        }

    private suspend fun ApplicationTestBuilder.pantry(user: UUID, householdId: String? = null): List<PantryItem> =
        jsonClient.get("/pantry-items" + (householdId?.let { "?householdId=$it" } ?: "")) { bearerAuth(token(user)) }.body()

    private suspend fun ApplicationTestBuilder.createList(user: UUID, householdId: String? = null, defaultItemIds: List<String>? = null): GroceryList =
        jsonClient.post("/lists") {
            contentType(ContentType.Application.Json)
            bearerAuth(token(user))
            setBody(CreateGroceryListRequest("Weekly", householdId, defaultItemIds))
        }.body()

    private suspend fun ApplicationTestBuilder.household(): Household {
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
        return household
    }

    private fun GroceryList.lines() = items.map { "${it.name} ${it.quantity} ${it.unit}" }

    @Test
    fun `personal items belong to the user, household items to every member`() = testApplication {
        setupTestApplication()
        val home = household()
        addPantry(alice, AddPantryItemRequest(name = "milk", kind = "default", quantity = 2.0))
        addPantry(bob, AddPantryItemRequest(home.id, "salt", "staple"))

        assertEquals(listOf("milk"), pantry(alice).map { it.name })
        assertTrue(pantry(bob).isEmpty())
        assertEquals(listOf("salt"), pantry(alice, home.id).map { it.name })

        assertEquals(HttpStatusCode.Forbidden, jsonClient.get("/pantry-items?householdId=${home.id}") { bearerAuth(token(eve)) }.status)
        assertEquals(HttpStatusCode.Forbidden, addPantry(eve, AddPantryItemRequest(home.id, "eggs", "default")).status)
        val salt = pantry(alice, home.id).single()
        assertEquals(HttpStatusCode.NotFound, jsonClient.delete("/pantry-items/${salt.id}") { bearerAuth(token(eve)) }.status)
        val milk = pantry(alice).single()
        assertEquals(HttpStatusCode.NotFound, jsonClient.delete("/pantry-items/${milk.id}") { bearerAuth(token(bob)) }.status)

        assertEquals(HttpStatusCode.NoContent, jsonClient.delete("/pantry-items/${salt.id}") { bearerAuth(token(alice)) }.status)
        assertTrue(pantry(bob, home.id).isEmpty())
    }

    @Test
    fun `adding a name that's there updates it instead of adding a second one`() = testApplication {
        setupTestApplication()
        addPantry(alice, AddPantryItemRequest(name = "Eggs", kind = "default", quantity = 6.0))

        val response = addPantry(alice, AddPantryItemRequest(name = "eggs", kind = "staple"))

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(listOf("eggs staple"), pantry(alice).map { "${it.name} ${it.kind}" })
        assertEquals(HttpStatusCode.BadRequest, addPantry(alice, AddPantryItemRequest(name = "rice", kind = "sometimes")).status)
        assertEquals(HttpStatusCode.BadRequest, addPantry(alice, AddPantryItemRequest(name = " ", kind = "default")).status)
        assertEquals(HttpStatusCode.BadRequest, addPantry(alice, AddPantryItemRequest(name = "rice", kind = "default", unit = "bags")).status)
    }

    @Test
    fun `new lists get the chosen defaults, or all of them when the app doesn't say`() = testApplication {
        setupTestApplication()
        val home = household()
        val milk: PantryItem = addPantry(alice, AddPantryItemRequest(home.id, "milk", "default", 2.0, "l")).body()
        addPantry(alice, AddPantryItemRequest(home.id, "eggs", "default", 6.0))
        addPantry(alice, AddPantryItemRequest(home.id, "salt", "staple"))
        addPantry(alice, AddPantryItemRequest(name = "coffee", kind = "default"))

        assertEquals(listOf("milk 2.0 l", "eggs 6.0 null"), createList(bob, home.id).lines())
        assertEquals(listOf("milk 2.0 l"), createList(bob, home.id, listOf(milk.id)).lines())
        assertTrue(createList(bob, home.id, emptyList()).items.isEmpty())
        // A personal list uses the owner's own defaults
        assertEquals(listOf("coffee 1.0 null"), createList(alice).lines())
        assertTrue(createList(bob).items.isEmpty())
    }

    @Test
    fun `recipes skip staples unless chosen explicitly`() = testApplication {
        setupTestApplication()
        addPantry(alice, AddPantryItemRequest(name = "Salt", kind = "staple"))
        addPantry(alice, AddPantryItemRequest(name = "olive oil", kind = "staple"))
        val recipe: Recipe = jsonClient.post("/recipes") {
            contentType(ContentType.Application.Json)
            bearerAuth(token(alice))
            setBody(RecipeInput(name = "Soup", servings = 2, ingredients = listOf(
                RecipeIngredient("onion", 1.0), RecipeIngredient("salt"), RecipeIngredient("olive oil", 1.0, "tbsp")
            )))
        }.body()
        val list = createList(alice)

        suspend fun add(indexes: List<Int>?) = jsonClient.post("/lists/${list.id}/recipes") {
            contentType(ContentType.Application.Json)
            bearerAuth(token(alice))
            setBody(AddRecipeToListRequest(recipe.id, ingredientIndexes = indexes))
        }.body<AddRecipeToListResponse>().list

        assertEquals(listOf("onion"), add(null).items.map { it.name })
        // Ran out of salt: ticked on purpose
        assertEquals(listOf("onion", "salt"), add(listOf(1)).items.map { it.name })
    }

    @Test
    fun `a confirmed meal plan skips staples and gets the defaults`() = testApplication {
        setupTestApplication()
        addPantry(alice, AddPantryItemRequest(name = "salt", kind = "staple"))
        addPantry(alice, AddPantryItemRequest(name = "milk", kind = "default"))
        addPantry(alice, AddPantryItemRequest(name = "yogurt", kind = "default", quantity = 500.0, unit = "g"))
        val pancakes: Recipe = jsonClient.post("/recipes") {
            contentType(ContentType.Application.Json)
            bearerAuth(token(alice))
            setBody(RecipeInput(name = "Pancakes", servings = 2, ingredients = listOf(
                RecipeIngredient("milk", 0.5, "l"), RecipeIngredient("salt")
            )))
        }.body()
        val plan: MealPlan = jsonClient.post("/meal-plans") {
            contentType(ContentType.Application.Json)
            bearerAuth(token(alice))
            setBody(CreateMealPlanRequest("2026-10-20", 7, null, 2))
        }.body()
        jsonClient.put("/meal-plans/${plan.id}/entries/2026-10-20/lunch") {
            contentType(ContentType.Application.Json)
            bearerAuth(token(alice))
            setBody(SetMealRequest(pancakes.id))
        }

        val result: ConfirmMealPlanResponse = jsonClient.post("/meal-plans/${plan.id}/confirm") { bearerAuth(token(alice)) }.body()

        // Plain "milk" (a reminder) isn't added next to the recipe's 500 ml
        assertEquals(listOf("milk 500.0 ml", "yogurt 500.0 g"), result.list.lines())
    }
}
