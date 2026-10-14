package org.pantry.di

import io.ktor.server.application.*
import org.koin.dsl.module
import org.pantry.repositories.GroceryListRepository
import org.pantry.repositories.ItemRepository
import org.pantry.repositories.UserRepository
import org.pantry.services.AuthService
import org.pantry.services.EmailService
import org.pantry.services.GroceryListService
import org.pantry.services.ItemService

fun appModule(application: Application) = module {
    single { ItemRepository() }
    single { ItemService(get()) }
    single { GroceryListRepository(get()) }
    single { GroceryListService(get()) }
    single { UserRepository() }
    single {
        EmailService(
            application.environment.config.property("resend.apiKey").getString(),
            application.environment.config.property("resend.fromEmail").getString(),
            application.environment.config.property("resend.verificationBaseUrl").getString()
        )
    }
    single {
        AuthService(
            get(),
            get(),
            application.environment.config.property("jwt.secret").getString(),
            application.environment.config.property("jwt.issuer").getString(),
            application.environment.config.property("jwt.audience").getString()
        )
    }
}