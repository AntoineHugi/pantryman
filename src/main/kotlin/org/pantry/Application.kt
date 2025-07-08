package org.pantry

import com.pantry.org.pantry.groceriesApi
import com.pantry.org.pantry.menuApi
import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    embeddedServer(
        Netty,
        port = 8080,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    menuApi()
    groceriesApi()
}
