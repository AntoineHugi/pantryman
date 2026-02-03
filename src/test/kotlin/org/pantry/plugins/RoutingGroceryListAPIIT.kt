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
import org.pantry.postgres.tables.GroceryListTable

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
            SchemaUtils.create(GroceryListTable, ItemTable)
        }
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
            configureSecurity()
            groceriesApi()
        }
    }

    @Test
    fun `GET lists without token returns 401`() = testApplication {
        setupTestApplication()

        val response = client.get("/lists")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST grocery list returns 201 and creates list`() = testApplication {
        setupTestApplication()

        val newList = CreateGroceryListRequest(name = "Weekly Groceries", testUserId.toString())
        val response = client.post("/lists") {
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

        val newList = CreateGroceryListRequest(name = "Weekly Groceries", testUserId.toString())
        val postResponse = client.post("/lists") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $testToken")
            setBody(newList)
        }
        assertEquals(HttpStatusCode.Created, postResponse.status)

        val getResponse = client.get("/lists") {
            header("Authorization", "Bearer $testToken")
        }
        assertEquals(HttpStatusCode.OK, getResponse.status)
        val groceryLists: List<GroceryList> = getResponse.body()
        assertTrue(groceryLists.isNotEmpty())
    }

    @Test
    fun `GET grocery list ID returns 200 and list`() = testApplication {
        setupTestApplication()

        val newList = CreateGroceryListRequest(name = "Weekly Groceries", testUserId.toString())
        val postResponse = client.post("/lists") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $testToken")
            setBody(newList)
        }
        assertEquals(HttpStatusCode.Created, postResponse.status)
        val createdList: GroceryList = postResponse.body()
        assertNotNull(createdList)
        assertEquals(newList.name, createdList.name)

        val getResponse = client.get("/lists/${createdList.id}") {
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

        val newList = CreateGroceryListRequest(name = "Weekly Groceries", testUserId.toString())
        val postResponse = client.post("/lists") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $testToken")
            setBody(newList)
        }
        assertEquals(HttpStatusCode.Created, postResponse.status)
        val createdList: GroceryList = postResponse.body()
        assertNotNull(createdList)
        assertEquals(newList.name, createdList.name)

        val newName = "Daily Groceries"
        val patchResponse = client.patch("/lists/${createdList.id}") {
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

        val newList = CreateGroceryListRequest(name = "Weekly Groceries", testUserId.toString())
        val postResponse = client.post("/lists") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $testToken")
            setBody(newList)
        }
        assertEquals(HttpStatusCode.Created, postResponse.status)
        val createdList: GroceryList = postResponse.body()

        val response = client.delete("/lists/${createdList.id}") {
            header("Authorization", "Bearer $testToken")
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
        val deletedList = groceryListRepo.getById(UUID.fromString(createdList.id), testUserId)
        assertNull(deletedList)
    }

    @Test
    fun `Post item returns 201 and creates item`() = testApplication {
        setupTestApplication()

        val newList = CreateGroceryListRequest(name = "Weekly Groceries", testUserId.toString())
        val postListResponse = client.post("/lists") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $testToken")
            setBody(newList)
        }
        assertEquals(HttpStatusCode.Created, postListResponse.status)
        val createdList: GroceryList = postListResponse.body()
        assertNotNull(createdList)
        assertEquals(newList.name, createdList.name)

        val newItem = CreateItemRequest(listId = createdList.id, name = "milk", quantity = 1)
        val postItemResponse = client.post("/lists/${createdList.id}/items") {
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

        val newList = CreateGroceryListRequest(name = "Weekly Groceries", testUserId.toString())
        val postListResponse = client.post("/lists") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $testToken")
            setBody(newList)
        }
        assertEquals(HttpStatusCode.Created, postListResponse.status)
        val createdList: GroceryList = postListResponse.body()
        assertNotNull(createdList)
        assertEquals(newList.name, createdList.name)

        val newItem = CreateItemRequest(listId = createdList.id, name = "milk", quantity = 1)
        val postItemResponse = client.post("/lists/${createdList.id}/items") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $testToken")
            setBody(newItem)
        }
        assertEquals(HttpStatusCode.Created, postItemResponse.status)

        val getResponse = client.get("/lists/${createdList.id}/items") {
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

        val newList = CreateGroceryListRequest(name = "Weekly Groceries", testUserId.toString())
        val postListResponse = client.post("/lists") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $testToken")
            setBody(newList)
        }
        assertEquals(HttpStatusCode.Created, postListResponse.status)
        val createdList: GroceryList = postListResponse.body()
        assertNotNull(createdList)
        assertEquals(newList.name, createdList.name)

        val newItem = CreateItemRequest(listId = createdList.id, name = "milk", quantity = 1)
        val postItemResponse = client.post("/lists/${createdList.id}/items") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $testToken")
            setBody(newItem)
        }
        assertEquals(HttpStatusCode.Created, postItemResponse.status)
        val createdItem: Item = postItemResponse.body()
        assertNotNull(createdItem)
        assertEquals(newItem.name, createdItem.name)

        val response = client.delete("/lists/${createdList.id}/items/${createdItem.id}") {
            header("Authorization", "Bearer $testToken")
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
        val deletedItem = itemRepo.getById(UUID.fromString(createdItem.id))
        assertNull(deletedItem)
    }

    @Test
    fun `PATCH item ID returns 200 and updates item`() = testApplication {
        setupTestApplication()

        val newList = CreateGroceryListRequest(name = "Weekly Groceries", testUserId.toString())
        val postListResponse = client.post("/lists") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $testToken")
            setBody(newList)
        }
        assertEquals(HttpStatusCode.Created, postListResponse.status)
        val createdList: GroceryList = postListResponse.body()
        assertNotNull(createdList)
        assertEquals(newList.name, createdList.name)

        val newItem = CreateItemRequest(listId = createdList.id, name = "milk", quantity = 1)
        val postItemResponse = client.post("/lists/${createdList.id}/items") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $testToken")
            setBody(newItem)
        }
        assertEquals(HttpStatusCode.Created, postItemResponse.status)
        val createdItem: Item = postItemResponse.body()
        assertNotNull(createdItem)
        assertEquals(newItem.name, createdItem.name)

        val newName = "eggs"
        val patchResponse = client.patch("/lists/${createdList.id}/items/${createdItem.id}") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $testToken")
            setBody(mapOf("name" to newName))
        }
        assertEquals(HttpStatusCode.OK, patchResponse.status)
        val updatedItemName = itemRepo.getById(UUID.fromString(createdItem.id))
        assertNotNull(updatedItemName)
        assertEquals(newName, updatedItemName.name)
    }

    @Test
    fun `GET lists with invalid token returns 401`() = testApplication {
        setupTestApplication()

        val response = client.get("/lists") {
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

        val response = client.get("/lists") {
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

        val response = client.get("/lists") {
            header("Authorization", "Bearer $wrongIssuerToken")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}