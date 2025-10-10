package org.pantry.plugins

import io.ktor.server.application.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
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
import org.junit.jupiter.api.BeforeEach

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
                single { ItemService( get() ) }
                single { GroceryListRepository(get() ) }
                single { GroceryListService(get() ) }
            })
    }

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

    private val groceryListRepo: GroceryListRepository by inject()
    private val itemRepo: ItemRepository by inject()

    @Test
    fun `POST grocery list returns 201 and creates list`() = testApplication {
        application {
            install(ContentNegotiation) {
                json()
            }
            groceriesApi()
        }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }
        val newList = CreateGroceryListRequest(name = "Weekly Groceries")
        val response = client.post("/lists") {
            contentType(ContentType.Application.Json)
            setBody(newList)
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val createdList: GroceryList = response.body()
        assertNotNull(createdList)
        assertEquals(newList.name, createdList.name)
        val savedList = groceryListRepo.getById(UUID.fromString(createdList.id))
        assertNotNull(savedList)
        assertEquals(createdList.name, savedList.name)
        assertEquals(createdList.items, savedList.items)
    }

    @Test
    fun `GET grocery List returns 200 and grocery lists`() = testApplication {
        application {
            install(ContentNegotiation) {
                json()
            }
            groceriesApi()
        }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }
        val newList = CreateGroceryListRequest(name = "Weekly Groceries")
        val postResponse = client.post("/lists") {
            contentType(ContentType.Application.Json)
            setBody(newList)
        }
        assertEquals(HttpStatusCode.Created, postResponse.status)
        val getResponse = client.get("/lists")
        assertEquals(HttpStatusCode.OK, getResponse.status)
        val groceryLists: List<GroceryList> = getResponse.body()
        assertTrue(groceryLists.isNotEmpty())
    }

    @Test
    fun `GET grocery list ID returns 200 and list`() = testApplication {
        application {
            install(ContentNegotiation) {
                json()
            }
            groceriesApi()
        }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }
        val newList = CreateGroceryListRequest(name = "Weekly Groceries")
        val postResponse = client.post("/lists") {
            contentType(ContentType.Application.Json)
            setBody(newList)
        }
        assertEquals(HttpStatusCode.Created, postResponse.status)
        val createdList: GroceryList = postResponse.body()
        assertNotNull(createdList)
        assertEquals(newList.name, createdList.name)
        val getResponse = client.get("/lists/${createdList.id}")
        assertEquals(HttpStatusCode.OK, getResponse.status)
        val groceryList: GroceryList = getResponse.body()
        assertNotNull(groceryList)
        assertEquals(createdList.id, groceryList.id)
        assertEquals(createdList.name, groceryList.name)
        assertEquals(createdList.items, groceryList.items)
    }

    @Test
    fun `PATCH grocery list ID returns 200 and updates list`() = testApplication {
        application {
            install(ContentNegotiation) {
                json()
            }
            groceriesApi()
        }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }
        val newList = CreateGroceryListRequest(name = "Weekly Groceries")
        val postResponse = client.post("/lists") {
            contentType(ContentType.Application.Json)
            setBody(newList)
        }
        assertEquals(HttpStatusCode.Created, postResponse.status)
        val createdList: GroceryList = postResponse.body()
        assertNotNull(createdList)
        assertEquals(newList.name, createdList.name)
        val newName = "Daily Groceries"
        val patchResponse = client.patch("/lists/${createdList.id}") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("name" to newName))
        }
        assertEquals(HttpStatusCode.OK, patchResponse.status)
        val savedList = groceryListRepo.getById(UUID.fromString(createdList.id))
        assertNotNull(savedList)
        assertEquals(newName, savedList.name)
    }

    @Test
    fun `DELETE grocery list ID returns 200 and deletes list`() = testApplication {
        application {
            install(ContentNegotiation) {
                json()
            }
            groceriesApi()
        }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }
        val newList = CreateGroceryListRequest(name = "Weekly Groceries")
        val postResponse = client.post("/lists") {
            contentType(ContentType.Application.Json)
            setBody(newList)
        }
        assertEquals(HttpStatusCode.Created, postResponse.status)
        val createdList: GroceryList = postResponse.body()
        val response = client.delete("/lists/${createdList.id}")
        assertEquals(HttpStatusCode.NoContent, response.status)
        val deletedList = groceryListRepo.getById(UUID.fromString(createdList.id))
        assertNull(deletedList)
    }

    @Test
    fun `Post item returns 201 and creates item`() = testApplication {
        application {
            install(ContentNegotiation) {
                json()
            }
            groceriesApi()
        }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }

        val newList = CreateGroceryListRequest(name = "Weekly Groceries")
        val postListResponse = client.post("/lists") {
            contentType(ContentType.Application.Json)
            setBody(newList)
        }
        assertEquals(HttpStatusCode.Created, postListResponse.status)
        val createdList: GroceryList = postListResponse.body()
        assertNotNull(createdList)
        assertEquals(newList.name, createdList.name)
        val newItem = CreateItemRequest(listId = createdList.id, name = "milk", quantity = 1)
        val postItemResponse = client.post("/lists/${createdList.id}/items") {
            contentType(ContentType.Application.Json)
            setBody(newItem)
        }
        assertEquals(HttpStatusCode.Created, postItemResponse.status)
        val createdItem: Item = postItemResponse.body()
        assertNotNull(createdItem)
        val savedList = groceryListRepo.getById(UUID.fromString(createdList.id))
        assertNotNull(savedList)
        assertTrue(savedList.items.contains(createdItem))
        assertEquals(newItem.name, createdItem.name)
        assertEquals(newItem.quantity, createdItem.quantity)
    }

    @Test
    fun `GET items returns 200 and item list`() = testApplication {
        application {
            install(ContentNegotiation) {
                json()
            }
            groceriesApi()
        }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }

        val newList = CreateGroceryListRequest(name = "Weekly Groceries")
        val postListResponse = client.post("/lists") {
            contentType(ContentType.Application.Json)
            setBody(newList)
        }
        assertEquals(HttpStatusCode.Created, postListResponse.status)
        val createdList: GroceryList = postListResponse.body()
        assertNotNull(createdList)
        assertEquals(newList.name, createdList.name)
        val newItem = CreateItemRequest(listId = createdList.id, name = "milk", quantity = 1)
        val postItemResponse = client.post("/lists/${createdList.id}/items") {
            contentType(ContentType.Application.Json)
            setBody(newItem)
        }
        assertEquals(HttpStatusCode.Created, postItemResponse.status)
        val getResponse = client.get("/lists/${createdList.id}/items")
        {
            contentType(ContentType.Application.Json)
            setBody(createdList.id)
        }
        assertEquals(HttpStatusCode.OK, getResponse.status)
        val savedItemList: List<Item> = getResponse.body()
        assertNotNull(savedItemList)
        val savedList = groceryListRepo.getById(UUID.fromString(createdList.id))
        assertNotNull(savedList)
        assertEquals(savedList.items, savedItemList)
    }

    @Test
    fun `DELETE item ID returns 200 and deletes item`() = testApplication {
        application {
            install(ContentNegotiation) {
                json()
            }
            groceriesApi()
        }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }
        val newList = CreateGroceryListRequest(name = "Weekly Groceries")
        val postListResponse = client.post("/lists") {
            contentType(ContentType.Application.Json)
            setBody(newList)
        }
        assertEquals(HttpStatusCode.Created, postListResponse.status)
        val createdList: GroceryList = postListResponse.body()
        assertNotNull(createdList)
        assertEquals(newList.name, createdList.name)
        val newItem = CreateItemRequest(listId = createdList.id, name = "milk", quantity = 1)
        val postItemResponse = client.post("/lists/${createdList.id}/items") {
            contentType(ContentType.Application.Json)
            setBody(newItem)
        }
        assertEquals(HttpStatusCode.Created, postItemResponse.status)
        val createdItem : Item = postItemResponse.body()
        assertNotNull(createdItem)
        assertEquals(newItem.name, createdItem.name)
        val response = client.delete("/lists/${createdList.id}/items/${createdItem.id}")
        assertEquals(HttpStatusCode.NoContent, response.status)
        val deletedItem = itemRepo.getById(UUID.fromString(createdItem.id))
        assertNull(deletedItem)
    }

    @Test
    fun `PATCH item ID returns 200 and updates item`() = testApplication {
        application {
            install(ContentNegotiation) {
                json()
            }
            groceriesApi()
        }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }
        val newList = CreateGroceryListRequest(name = "Weekly Groceries")
        val postListResponse = client.post("/lists") {
            contentType(ContentType.Application.Json)
            setBody(newList)
        }
        assertEquals(HttpStatusCode.Created, postListResponse.status)
        val createdList: GroceryList = postListResponse.body()
        assertNotNull(createdList)
        assertEquals(newList.name, createdList.name)
        val newItem = CreateItemRequest(listId = createdList.id, name = "milk", quantity = 1)
        val postItemResponse = client.post("/lists/${createdList.id}/items") {
            contentType(ContentType.Application.Json)
            setBody(newItem)
        }
        assertEquals(HttpStatusCode.Created, postItemResponse.status)
        val createdItem : Item = postItemResponse.body()
        assertNotNull(createdItem)
        assertEquals(newItem.name, createdItem.name)
        val newName = "eggs"
        val patchResponse = client.patch("/lists/${createdList.id}/items/${createdItem.id}") {
            contentType(ContentType.Application.Json)
            setBody(mapOf(
                "newName" to newName
            ))
        }
        assertEquals(HttpStatusCode.OK, patchResponse.status)
        val updatedItemName = itemRepo.getById(UUID.fromString(createdItem.id))
        assertNotNull(updatedItemName)
        assertEquals(newName, updatedItemName.name)
    }
}
