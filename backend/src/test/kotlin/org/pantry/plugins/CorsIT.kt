package org.pantry.plugins

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CorsIT {

    private fun corsTest(block: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            configureCors()
            routing { get("/ping") { call.respondText("pong") } }
        }
        block()
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.allowedOriginFor(origin: String): String? =
        client.options("/ping") {
            header(HttpHeaders.Origin, origin)
            header(HttpHeaders.AccessControlRequestMethod, "POST")
            header(HttpHeaders.AccessControlRequestHeaders, "authorization,content-type")
        }.headers[HttpHeaders.AccessControlAllowOrigin]

    @Test
    fun `allows the website and the Android app`() = corsTest {
        assertEquals("https://pantryman.de", allowedOriginFor("https://pantryman.de"))
        assertEquals("https://www.pantryman.de", allowedOriginFor("https://www.pantryman.de"))
        assertEquals("https://localhost", allowedOriginFor("https://localhost"))
        assertEquals("http://localhost:5173", allowedOriginFor("http://localhost:5173"))
    }

    @Test
    fun `rejects other origins`() = corsTest {
        assertNull(allowedOriginFor("https://evil.example.com"))
        assertNull(allowedOriginFor("http://pantryman.de"))
        assertNull(allowedOriginFor("http://localhost"))
    }
}
