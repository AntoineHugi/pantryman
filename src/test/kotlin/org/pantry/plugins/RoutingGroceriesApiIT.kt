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
import io.ktor.client.statement.bodyAsText
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

import org.pantry.models.GroceryList
import org.pantry.models.CreateGroceryListRequest
import org.pantry.models.Item
import org.pantry.repositories.GroceryListRepository
import org.pantry.repositories.ItemRepository
import org.pantry.groceriesApi
import org.pantry.services.GroceryListService
import org.pantry.module
import org.pantry.postgres.tables.ItemTable
import org.pantry.postgres.tables.GroceryListTable

class RoutingGroceriesApiIT: KoinTest {

    @JvmField
    @RegisterExtension
    val koinTestExtension = KoinTestExtension.create {
        modules(
            module {
                single { ItemRepository() }
                single { GroceryListRepository(get()) }
                single { GroceryListService(get()) }
            }
        )
    }
    private val groceryListRepo: GroceryListRepository by inject()

    @Test
    fun `POST list returns 201 and creates list`() = testApplication {
        Database.connect(
            url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.create(GroceryListTable)
            SchemaUtils.create(ItemTable)
        }
        application {
            install(ContentNegotiation) {
                json()
            }
            install(org.koin.ktor.plugin.Koin) {
                modules(
                    module {
                        single { ItemRepository() }
                        single { GroceryListRepository(get()) }
                        single { GroceryListService(get()) }
                    }
                )
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

        val savedList = groceryListRepo.getById(UUID.fromString(createdList.id))
        assertNotNull(savedList)
        assertEquals(createdList.name,savedList.name)
        assertEquals(createdList.items,savedList.items)
    }

    @Test
    fun `GET grocery List returns 200 and grocery lists`() = testApplication {
        Database.connect(
            url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.create(GroceryListTable)
            SchemaUtils.create(ItemTable)
        }
        application {
            install(ContentNegotiation) {
                json()
            }
            install(org.koin.ktor.plugin.Koin) {
                modules(
                    module {
                        single { ItemRepository() }
                        single { GroceryListRepository(get()) }
                        single { GroceryListService(get()) }
                    }
                )
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
        if (postResponse.status == HttpStatusCode.Created) {
            val getResponse = client.get("/lists")

            assertEquals(HttpStatusCode.OK, getResponse.status)
            val groceryLists: List<GroceryList> = getResponse.body()
            assertTrue(groceryLists.isNotEmpty())
        }
    }

    @Test
    fun `PUT list ID returns 200 and updates list`() = testApplication {
        Database.connect(
            url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.create(GroceryListTable)
            SchemaUtils.create(ItemTable)
        }
        application {
            install(ContentNegotiation) {
                json()
            }
            install(org.koin.ktor.plugin.Koin) {
                modules(
                    module {
                        single { ItemRepository() }
                        single { GroceryListRepository(get()) }
                        single { GroceryListService(get()) }
                    }
                )
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
        if (postResponse.status == HttpStatusCode.Created) {
            val createdList: GroceryList = postResponse.body()
            val newName = "Daily Groceries"
            val putResponse = client.put("/lists/${createdList.id}") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("name" to newName))
            }
            assertEquals(HttpStatusCode.OK, putResponse.status)
            val savedList = groceryListRepo.getById(UUID.fromString(createdList.id))
            assertNotNull(savedList)
            assertEquals(newName, savedList.name)
        }
    }

    @Test
    fun `GET list ID returns 200 and list`() = testApplication {
        Database.connect(
            url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.create(GroceryListTable)
            SchemaUtils.create(ItemTable)
        }
        application {
            install(ContentNegotiation) {
                json()
            }
            install(org.koin.ktor.plugin.Koin) {
                modules(
                    module {
                        single { ItemRepository() }
                        single { GroceryListRepository(get()) }
                        single { GroceryListService(get()) }
                    }
                )
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
        if (postResponse.status == HttpStatusCode.Created) {
            val createdList: GroceryList = postResponse.body()
            val getResponse = client.get("/lists/${createdList.id}")

            assertEquals(HttpStatusCode.OK, getResponse.status)
            val groceryList: GroceryList = getResponse.body()
            assertEquals(createdList.id, groceryList.id)
            assertEquals(createdList.name, groceryList.name)
            assertEquals(createdList.items, groceryList.items)
        }
        else
            print("error with post request")
    }

    @Test
    fun `DELETE list ID returns 200 and deletes list`() = testApplication {
        application {
            Database.connect(
                url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;",
                driver = "org.h2.Driver"
            )
            transaction {
                SchemaUtils.create(GroceryListTable)
                SchemaUtils.create(ItemTable)
            }
            install(org.koin.ktor.plugin.Koin) {
                modules(
                    module {
                        single { ItemRepository() }
                        single { GroceryListRepository(get()) }
                        single { GroceryListService(get()) }
                    }
                )
            }
            groceriesApi()
        }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }
        val groceryListRepo: GroceryListRepository by inject<GroceryListRepository>()

        val newList = groceryListRepo.create(name = "Weekly Groceries")
        val postResponse = client.post("/${newList.id}") {
            contentType(ContentType.Application.Json)
            setBody(newList)
        }
        if (postResponse.status == HttpStatusCode.Created) {
            val response = client.delete("/${newList.id}")

            assertEquals(HttpStatusCode.NoContent, response.status)
            val savedList = groceryListRepo.getById(UUID.fromString(newList.id))
            assertNull(savedList)
        }
        else
            print("error with post request")
    }

    @Test
    fun `Post item returns 201 and creates item`() = testApplication {
        application {
            Database.connect(
                url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;",
                driver = "org.h2.Driver"
            )
            transaction {
                SchemaUtils.create(GroceryListTable)
                SchemaUtils.create(ItemTable)
            }
            install(org.koin.ktor.plugin.Koin) {
                modules(
                    module {
                        single { ItemRepository() }
                        single { GroceryListRepository(get()) }
                        single { GroceryListService(get()) }
                    }
                )
            }
            groceriesApi()
        }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }
        val groceryListRepo: GroceryListRepository by inject<GroceryListRepository>()
        val itemRepo: ItemRepository by inject()

        val newList = groceryListRepo.create(name = "Weekly Groceries")
        val postListResponse = client.post("/${newList.id}") {
            contentType(ContentType.Application.Json)
            setBody(newList)
        }
        if (postListResponse.status == HttpStatusCode.Created) {
            val newItem = itemRepo.create(name = "milk", quantity = 1, isChecked = false, isFavorite = true)
            val response = client.post("/${newList.id}/items") {
                contentType(ContentType.Application.Json)
                setBody(newItem)
            }
            assertEquals(HttpStatusCode.Created, response.status)
            val savedList = groceryListRepo.getById(UUID.fromString(newList.id))
            assertNotNull(savedList)
            val savedItem = itemRepo.getById(UUID.fromString(newItem.id))
            assertNotNull(savedItem)
            assertTrue(savedList.items.contains(savedItem))
            assertEquals(newItem.id, savedItem.id)
            assertEquals(newItem.name, savedItem.name)
            assertEquals(newItem.quantity, savedItem.quantity)
            assertEquals(newItem.isChecked, savedItem.isChecked)
            assertEquals(newItem.isFavorite, savedItem.isFavorite)
        }
        else
            print("error with list post request")
    }

    @Test
    fun `GET item returns 200 and item list`() = testApplication {
        application {
            Database.connect(
                url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;",
                driver = "org.h2.Driver"
            )
            transaction {
                SchemaUtils.create(GroceryListTable)
                SchemaUtils.create(ItemTable)
            }
            install(org.koin.ktor.plugin.Koin) {
                modules(
                    module {
                        single { ItemRepository() }
                        single { GroceryListRepository(get()) }
                        single { GroceryListService(get()) }
                    }
                )
            }
            groceriesApi()
        }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }
        val groceryListRepo: GroceryListRepository by inject<GroceryListRepository>()
        val itemRepo: ItemRepository by inject()

        val newList = groceryListRepo.create(name = "Weekly Groceries")
        val postListResponse = client.post("/${newList.id}") {
            contentType(ContentType.Application.Json)
            setBody(newList)
        }
        if (postListResponse.status == HttpStatusCode.Created) {
            val newItem = itemRepo.create(name = "milk", quantity = 1, isChecked = false, isFavorite = true)
            val postResponse = client.post("/${newList.id}/items") {
                contentType(ContentType.Application.Json)
                setBody(newItem)
            }
            if (postResponse.status == HttpStatusCode.Created) {
                val response = client.get("/${newList.id}/items")
                assertEquals(HttpStatusCode.OK, response.status)
                val savedItemList: List<Item> = response.body()
                assertNotNull(savedItemList)
                assertEquals(newList.items, savedItemList)
            }
        }
        else
            print("error with list post request")
    }
}