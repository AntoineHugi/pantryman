package org.pantry.di

import io.ktor.server.application.*
import org.koin.dsl.module
import org.pantry.repositories.GroceryListRepository
import org.pantry.repositories.ItemRepository
import org.pantry.repositories.UserRepository
import org.pantry.services.AuthService
import org.pantry.services.GroceryListService
import org.pantry.services.ItemService

fun appModule(application: Application) = module {  // ✅ Accept Application parameter
    single { ItemRepository() }
    single { ItemService(get()) }
    single { GroceryListRepository(get()) }
    single { GroceryListService(get()) }
    single { UserRepository() }
    single {
        AuthService(
            get(),
            application.environment.config.property("jwt.secret").getString(),
            application.environment.config.property("jwt.issuer").getString(),
            application.environment.config.property("jwt.audience").getString()
        )
    }
}