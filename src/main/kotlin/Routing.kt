package com.pantry

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.menuApi() {
    routing {
        get(
            "/menu") {
            call.respondText("Hello World!")
        }
        get("/userSettings") {
            call.respondText("Hello World!")
        }
        get("/appSettings") {
            call.respondText("Hello World!")
        }
    }
}

fun Application.groceriesApi() {
    routing {
        route("/lists") {
            post {
                call.respondText("Create a new grocery list")
            }
            get {
                call.respondText("Display all grocery lists")
            }
            route("/{listId}") {
                get {
                    call.respondText("Display specific list by ID")
                }
                put {
                    call.respondText("Rename the list")
                }
                delete {
                    call.respondText("Delete the list")
                }
                post("/duplicate") {
                    call.respondText("Duplicate the list")
                }
                route("/items") {
                    post {
                        call.respondText("Add item to list")
                    }
                    route("/{itemId}") {
                        delete {
                            call.respondText("Delete item from list")
                        }
                        post("/increase") {
                            call.respondText("Increase item quantity")
                        }
                        post("/decrease") {
                            call.respondText("Decrease item quantity")
                        }
                    }
                }
            }
        }
    }
}


