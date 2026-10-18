package org.pantry.plugins

import io.ktor.server.application.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import io.ktor.server.testing.ApplicationTestBuilder
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import io.ktor.serialization.kotlinx.json.*
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import java.util.UUID
import org.koin.core.component.inject
import org.koin.dsl.module
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SchemaUtils
import org.koin.test.KoinTest
import org.koin.test.junit5.KoinTestExtension
import org.junit.jupiter.api.extension.RegisterExtension
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.config.MapApplicationConfig
import org.junit.jupiter.api.BeforeEach
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

import org.pantry.models.GroceryList
import org.pantry.models.CreateGroceryListRequest
import org.pantry.models.Item
import org.pantry.repositories.GroceryListRepository
import org.pantry.repositories.ItemRepository
import org.pantry.models.CreateItemRequest
import org.pantry.services.GroceryListService
import org.pantry.services.ItemService
import org.pantry.postgres.tables.ItemTable
import org.pantry.postgres.tables.MealPlanEntryTable
import org.pantry.postgres.tables.MealPlanTable
import org.pantry.postgres.tables.PantryItemTable
import org.pantry.postgres.tables.RecipeIngredientTable
import org.pantry.postgres.tables.RecipeStepTable
import org.pantry.postgres.tables.RecipeTable
import org.pantry.postgres.tables.RecipeTagTable
import org.pantry.postgres.tables.GroceryListTable
import org.pantry.postgres.tables.HouseholdMemberTable
import org.pantry.postgres.tables.HouseholdTable
import org.pantry.postgres.tables.UserTable
import org.pantry.repositories.UserRepository
import org.pantry.repositories.HouseholdRepository
import org.pantry.repositories.PantryRepository
import org.pantry.services.HouseholdService
import org.pantry.services.PantryService

class RoutingGroceriesApiIT : KoinTest {

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
            })
    }

    private val groceryListRepo: GroceryListRepository by inject()
    private val itemRepo: ItemRepository by inject()

    private val testUserId = UUID.randomUUID()
    private val testToken = generateTestToken(testUserId)

    @BeforeEach
    fun setupDatabase() {
        Database.connect(
            url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.create(
                UserTable, HouseholdTable, HouseholdMemberTable, GroceryListTable, ItemTable,
                RecipeTable, RecipeIngredientTable, RecipeStepTable, RecipeTagTable, MealPlanTable, MealPlanEntryTable, PantryItemTable
            )
        }
        createUser(testUserId)
    }

    // Lists reference users, so every list owner needs a real user row.
    private fun createUser(id: UUID) {
        UserRepository().create(id, "$id@example.com", "hash", UUID.randomUUID())
    }

    private fun generateTestToken(userId: UUID): String {
        return JWT.create()
            .withAudience("test-audience")
            .withIssuer("test-issuer")
            .withClaim("userId", userId.toString())
            .withExpiresAt(Date(System.currentTimeMillis() + 60000))
            .sign(Algorithm.HMAC256("test-secret-key-for-testing-only"))
    }

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
                json()
            }
            configureStatusPages()
            configureSecurity()
            groceriesApi()
        }
    }

    // The default test client has no JSON serializer, so request bodies need this one.
    private val ApplicationTestBuilder.jsonClient
        get() = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

    @Test
    fun `GET lists without token returns 401`() = testApplication {
        setupTestApplication()

        val response = jsonClient.get("/lists")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST grocery list returns 201 and creates list`() = testApplication {
        setupTestApplication()

        val newList = CreateGroceryListRequest(name = "Weekly Groceries")
        val response = jsonClient.post("/lists") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $testToken")
            setBody(newList)
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val createdList: GroceryList = response.body()
        assertNotNull(createdList)
        assertEquals(newList.name, createdList.name)
        assertEquals(testUserId.toString(), createdList.userId)

        val savedList = groceryListRepo.getById(UUID.fromString(createdList.id), UUID.fromString(createdList.userId))
        assertNotNull(savedList)
        assertEquals(createdList.name, savedList.name)
        assertEquals(createdList.items, savedList.items)
    }

    @Test
    fun `GET grocery List returns 200 and grocery lists`() = testApplication {
        setupTestApplication()

        val newList = CreateGroceryListRequest(name = "Weekly Groceries")
        val postResponse = jsonClient.post("/lists") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $testToken")
            setBody(newList)
        }
        assertEquals(HttpStatusCode.Created, postResponse.status)

        val getResponse = jsonClient.get("/lists") {
            header("Authorization", "Bearer $testToken")
        }
        assertEquals(HttpStatusCode.OK, getResponse.status)
        val groceryLists: List<GroceryList> = getResponse.body()
        assertTrue(groceryLists.isNotEmpty())
    }

    @Test
    fun `GET grocery list ID returns 200 and list`() = testApplication {
        setupTestApplication()

        val newList = CreateGroceryListRequest(name = "Weekly Groceries")
        val postResponse = jsonClient.post("/lists") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $testToken")
            setBody(newList)
        }
        assertEquals(HttpStatusCode.Created, postResponse.status)
        val createdList: GroceryList = postResponse.body()
        assertNotNull(createdList)
        assertEquals(newList.name, createdList.name)

        val getResponse = jsonClient.get("/lists/${createdList.id}") {
            header("Authorization", "Bearer $testToken")
        }
        assertEquals(HttpStatusCode.OK, getResponse.status)
        val groceryList: GroceryList = getResponse.body()
        assertNotNull(groceryList)
        assertEquals(createdList.id, groceryList.id)
        assertEquals(createdList.name, groceryList.name)
        assertEquals(createdList.items, groceryList.items)
    }

    @Test
    fun `PATCH grocery list ID returns 200 and updates list`() = testApplication {
        setupTestApplication()

        val newList = CreateGroceryListRequest(name = "Weekly Groceries")
        val postResponse = jsonClient.post("/lists") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $testToken")
            setBody(newList)
        }
        assertEquals(HttpStatusCode.Created, postResponse.status)
        val createdList: GroceryList = postResponse.body()
        assertNotNull(createdList)
        assertEquals(newList.name, createdList.name)

        val newName = "Daily Groceries"
        val patchResponse = jsonClient.patch("/lists/${createdList.id}") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $testToken")
            setBody(mapOf("name" to newName))
        }
        assertEquals(HttpStatusCode.OK, patchResponse.status)
        val savedList = groceryListRepo.getById(UUID.fromString(createdList.id), testUserId)
        assertNotNull(savedList)
        assertEquals(newName, savedList.name)
    }

    @Test
    fun `DELETE grocery list ID returns 200 and deletes list`() = testApplication {
        setupTestApplication()

        val newList = CreateGroceryListRequest(name = "Weekly Groceries")
        val postResponse = jsonClient.post("/lists") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $testToken")
            setBody(newList)
        }
        assertEquals(HttpStatusCode.Created, postResponse.status)
        val createdList: GroceryList = postResponse.body()

        val response = jsonClient.delete("/lists/${createdList.id}") {
            header("Authorization", "Bearer $testToken")
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
        val deletedList = groceryListRepo.getById(UUID.fromString(createdList.id), testUserId)
        assertNull(deletedList)
    }

    @Test
    fun `Post item returns 201 and creates item`() = testApplication {
        setupTestApplication()

        val newList = CreateGroceryListRequest(name = "Weekly Groceries")
        val postListResponse = jsonClient.post("/lists") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $testToken")
            setBody(newList)
        }
        assertEquals(HttpStatusCode.Created, postListResponse.status)
        val createdList: GroceryList = postListResponse.body()
        assertNotNull(createdList)
        assertEquals(newList.name, createdList.name)

        val newItem = CreateItemRequest(name = "milk", quantity = 1.0)
        val postItemResponse = jsonClient.post("/lists/${createdList.id}/items") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $testToken")
            setBody(newItem)
        }
        assertEquals(HttpStatusCode.Created, postItemResponse.status)
        val createdItem: Item = postItemResponse.body()
        assertNotNull(createdItem)
        val savedList = groceryListRepo.getById(UUID.fromString(createdList.id), testUserId)
        assertNotNull(savedList)
        assertTrue(savedList.items.contains(createdItem))
        assertEquals(newItem.name, createdItem.name)
        assertEquals(newItem.quantity, createdItem.quantity)
    }

    @Test
    fun `GET items returns 200 and item list`() = testApplication {
        setupTestApplication()

        val newList = CreateGroceryListRequest(name = "Weekly Groceries")
        val postListResponse = jsonClient.post("/lists") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $testToken")
            setBody(newList)
        }
        assertEquals(HttpStatusCode.Created, postListResponse.status)
        val createdList: GroceryList = postListResponse.body()
        assertNotNull(createdList)
        assertEquals(newList.name, createdList.name)

        val newItem = CreateItemRequest(name = "milk", quantity = 1.0)
        val postItemResponse = jsonClient.post("/lists/${createdList.id}/items") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $testToken")
            setBody(newItem)
        }
        assertEquals(HttpStatusCode.Created, postItemResponse.status)

        val getResponse = jsonClient.get("/lists/${createdList.id}/items") {
            header("Authorization", "Bearer $testToken")
        }
        assertEquals(HttpStatusCode.OK, getResponse.status)
        val savedItemList: List<Item> = getResponse.body()
        assertNotNull(savedItemList)
        val savedList = groceryListRepo.getById(UUID.fromString(createdList.id), testUserId)
        assertNotNull(savedList)
        assertEquals(savedList.items, savedItemList)
    }

    @Test
    fun `DELETE item ID returns 200 and deletes item`() = testApplication {
        setupTestApplication()

        val newList = CreateGroceryListRequest(name = "Weekly Groceries")
        val postListResponse = jsonClient.post("/lists") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $testToken")
            setBody(newList)
        }
        assertEquals(HttpStatusCode.Created, postListResponse.status)
        val createdList: GroceryList = postListResponse.body()
        assertNotNull(createdList)
        assertEquals(newList.name, createdList.name)

        val newItem = CreateItemRequest(name = "milk", quantity = 1.0)
        val postItemResponse = jsonClient.post("/lists/${createdList.id}/items") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $testToken")
            setBody(newItem)
        }
        assertEquals(HttpStatusCode.Created, postItemResponse.status)
        val createdItem: Item = postItemResponse.body()
        assertNotNull(createdItem)
        assertEquals(newItem.name, createdItem.name)

        val response = jsonClient.delete("/lists/${createdList.id}/items/${createdItem.id}") {
            header("Authorization", "Bearer $testToken")
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
        val deletedItem = itemRepo.getById(UUID.fromString(createdList.id), UUID.fromString(createdItem.id))
        assertNull(deletedItem)
    }

    @Test
    fun `PATCH item ID returns 200 and updates item`() = testApplication {
        setupTestApplication()

        val newList = CreateGroceryListRequest(name = "Weekly Groceries")
        val postListResponse = jsonClient.post("/lists") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $testToken")
            setBody(newList)
        }
        assertEquals(HttpStatusCode.Created, postListResponse.status)
        val createdList: GroceryList = postListResponse.body()
        assertNotNull(createdList)
        assertEquals(newList.name, createdList.name)

        val newItem = CreateItemRequest(name = "milk", quantity = 1.0)
        val postItemResponse = jsonClient.post("/lists/${createdList.id}/items") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $testToken")
            setBody(newItem)
        }
        assertEquals(HttpStatusCode.Created, postItemResponse.status)
        val createdItem: Item = postItemResponse.body()
        assertNotNull(createdItem)
        assertEquals(newItem.name, createdItem.name)

        val newName = "eggs"
        val patchResponse = jsonClient.patch("/lists/${createdList.id}/items/${createdItem.id}") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $testToken")
            setBody(mapOf("name" to newName))
        }
        assertEquals(HttpStatusCode.OK, patchResponse.status)
        val updatedItemName = itemRepo.getById(UUID.fromString(createdList.id), UUID.fromString(createdItem.id))
        assertNotNull(updatedItemName)
        assertEquals(newName, updatedItemName.name)
    }

    @Test
    fun `GET lists with invalid token returns 401`() = testApplication {
        setupTestApplication()

        val response = jsonClient.get("/lists") {
            header("Authorization", "Bearer invalid-token-xyz")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET lists with expired token returns 401`() = testApplication {
        setupTestApplication()

        val expiredToken = JWT.create()
            .withAudience("test-audience")
            .withIssuer("test-issuer")
            .withClaim("userId", UUID.randomUUID().toString())
            .withExpiresAt(Date(System.currentTimeMillis() - 10000))
            .sign(Algorithm.HMAC256("test-secret-key-for-testing-only"))

        val response = jsonClient.get("/lists") {
            header("Authorization", "Bearer $expiredToken")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET lists with token from wrong issuer returns 401`() = testApplication {
        setupTestApplication()

        val wrongIssuerToken = JWT.create()
            .withAudience("test-audience")
            .withIssuer("wrong-issuer")
            .withClaim("userId", UUID.randomUUID().toString())
            .withExpiresAt(Date(System.currentTimeMillis() + 60000))
            .sign(Algorithm.HMAC256("test-secret-key-for-testing-only"))

        val response = jsonClient.get("/lists") {
            header("Authorization", "Bearer $wrongIssuerToken")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `cannot modify another user's item through own list`() = testApplication {
        setupTestApplication()

        val ownList = groceryListRepo.create(UUID.randomUUID(), "Mine", testUserId)
        val otherUserId = UUID.randomUUID().also { createUser(it) }
        val otherList = groceryListRepo.create(UUID.randomUUID(), "Theirs", otherUserId)
        val otherItem = itemRepo.create(UUID.randomUUID(), UUID.fromString(otherList.id), "their milk", 1.0, false)

        val patchResponse = jsonClient.patch("/lists/${ownList.id}/items/${otherItem.id}") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $testToken")
            setBody(mapOf("name" to "hacked"))
        }
        assertEquals(HttpStatusCode.NotFound, patchResponse.status)

        val deleteViaOwnList = jsonClient.delete("/lists/${ownList.id}/items/${otherItem.id}") {
            header("Authorization", "Bearer $testToken")
        }
        assertEquals(HttpStatusCode.NotFound, deleteViaOwnList.status)

        val deleteViaTheirList = jsonClient.delete("/lists/${otherList.id}/items/${otherItem.id}") {
            header("Authorization", "Bearer $testToken")
        }
        assertEquals(HttpStatusCode.Forbidden, deleteViaTheirList.status)

        val untouched = itemRepo.getById(UUID.fromString(otherList.id), UUID.fromString(otherItem.id))
        assertNotNull(untouched)
        assertEquals("their milk", untouched.name)
    }

    @Test
    fun `malformed list ID returns 400 with JSON error`() = testApplication {
        setupTestApplication()

        val response = jsonClient.get("/lists/not-a-uuid") {
            header("Authorization", "Bearer $testToken")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error: Map<String, String> = response.body()
        assertEquals("Invalid listId", error["error"])

        val itemResponse = jsonClient.delete("/lists/${UUID.randomUUID()}/items/not-a-uuid") {
            header("Authorization", "Bearer $testToken")
        }
        // Ownership is checked before the item ID is parsed
        assertEquals(HttpStatusCode.Forbidden, itemResponse.status)
    }

    @Test
    fun `unknown list returns 404 with JSON error`() = testApplication {
        setupTestApplication()

        val response = jsonClient.get("/lists/${UUID.randomUUID()}") {
            header("Authorization", "Bearer $testToken")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
        val error: Map<String, String> = response.body()
        assertEquals("List not found", error["error"])
    }

    @Test
    fun `invalid item body returns 400 with JSON error`() = testApplication {
        setupTestApplication()

        val list = groceryListRepo.create(UUID.randomUUID(), "Mine", testUserId)
        val response = jsonClient.post("/lists/${list.id}/items") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $testToken")
            setBody("""{"quantity": 1}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error: Map<String, String> = response.body()
        assertNotNull(error["error"])
    }

    @Test
    fun `blank item name on update returns 400 with service message`() = testApplication {
        setupTestApplication()

        val list = groceryListRepo.create(UUID.randomUUID(), "Mine", testUserId)
        val item = itemRepo.create(UUID.randomUUID(), UUID.fromString(list.id), "milk", 1.0, false)
        val response = jsonClient.patch("/lists/${list.id}/items/${item.id}") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $testToken")
            setBody(mapOf("name" to " "))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error: Map<String, String> = response.body()
        assertEquals("Name cannot be blank", error["error"])
    }

    @Test
    fun `POST bulk-delete deletes the given items of the list`() = testApplication {
        setupTestApplication()

        val list = groceryListRepo.create(UUID.randomUUID(), "Mine", testUserId)
        val listId = UUID.fromString(list.id)
        val milk = itemRepo.create(UUID.randomUUID(), listId, "milk", 1.0, false)
        val eggs = itemRepo.create(UUID.randomUUID(), listId, "eggs", 1.0, false)
        itemRepo.create(UUID.randomUUID(), listId, "bread", 1.0, false)

        val response = jsonClient.post("/lists/${list.id}/items/bulk-delete") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $testToken")
            // An ID already deleted (e.g. by another member) is skipped
            setBody(mapOf("itemIds" to listOf(milk.id, eggs.id, UUID.randomUUID().toString())))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body: Map<String, Int> = response.body()
        assertEquals(2, body["deleted"])
        assertEquals(listOf("bread"), itemRepo.getByListId(listId).map { it.name })
    }

    @Test
    fun `bulk-delete cannot reach another user's items`() = testApplication {
        setupTestApplication()

        val ownList = groceryListRepo.create(UUID.randomUUID(), "Mine", testUserId)
        val otherUserId = UUID.randomUUID().also { createUser(it) }
        val otherList = groceryListRepo.create(UUID.randomUUID(), "Theirs", otherUserId)
        val otherItem = itemRepo.create(UUID.randomUUID(), UUID.fromString(otherList.id), "their milk", 1.0, false)

        // Their item through my list: not on my list, so nothing is deleted
        val viaOwnList = jsonClient.post("/lists/${ownList.id}/items/bulk-delete") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $testToken")
            setBody(mapOf("itemIds" to listOf(otherItem.id)))
        }
        assertEquals(HttpStatusCode.OK, viaOwnList.status)
        val body: Map<String, Int> = viaOwnList.body()
        assertEquals(0, body["deleted"])

        val viaTheirList = jsonClient.post("/lists/${otherList.id}/items/bulk-delete") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $testToken")
            setBody(mapOf("itemIds" to listOf(otherItem.id)))
        }
        assertEquals(HttpStatusCode.Forbidden, viaTheirList.status)

        assertNotNull(itemRepo.getById(UUID.fromString(otherList.id), UUID.fromString(otherItem.id)))
    }

    @Test
    fun `bulk-delete with no or malformed IDs returns 400`() = testApplication {
        setupTestApplication()

        val list = groceryListRepo.create(UUID.randomUUID(), "Mine", testUserId)
        val empty = jsonClient.post("/lists/${list.id}/items/bulk-delete") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $testToken")
            setBody(mapOf("itemIds" to emptyList<String>()))
        }
        assertEquals(HttpStatusCode.BadRequest, empty.status)
        val emptyError: Map<String, String> = empty.body()
        assertEquals("itemIds cannot be empty", emptyError["error"])

        val malformed = jsonClient.post("/lists/${list.id}/items/bulk-delete") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $testToken")
            setBody(mapOf("itemIds" to listOf("not-a-uuid")))
        }
        assertEquals(HttpStatusCode.BadRequest, malformed.status)
        val malformedError: Map<String, String> = malformed.body()
        assertEquals("Invalid item ID", malformedError["error"])
    }
}
