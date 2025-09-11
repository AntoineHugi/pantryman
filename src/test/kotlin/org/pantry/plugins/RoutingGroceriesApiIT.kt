package org.pantry.plugins

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*

import org.pantry.models.GroceryList

class RoutingGroceriesApiIT {

    @Test
    fun `GET grocery List returns 200 and JSON body`() = testApplication {
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }

        val response = client.get("/lists")

        assertEquals(HttpStatusCode.OK, response.status)
        val groceryLists: List<GroceryList> = response.body()
        assertTrue(groceryLists.isNotEmpty())
    }
}