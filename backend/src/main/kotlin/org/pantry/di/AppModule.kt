package org.pantry.di

import io.ktor.server.application.*
import org.koin.dsl.module
import org.pantry.repositories.GroceryListRepository
import org.pantry.repositories.HouseholdRepository
import org.pantry.repositories.ItemRepository
import org.pantry.repositories.MealPlanRepository
import org.pantry.repositories.PantryRepository
import org.pantry.repositories.RecipeRepository
import org.pantry.repositories.UserRepository
import org.pantry.services.AuthService
import org.pantry.services.EmailService
import org.pantry.services.GroceryListService
import org.pantry.services.HouseholdService
import org.pantry.services.ItemService
import org.pantry.services.MealPlanService
import org.pantry.services.PantryService
import org.pantry.services.RecipeListService
import org.pantry.services.RecipeService

fun appModule(application: Application) = module {
    single { ItemRepository() }
    single { ItemService(get()) }
    single { GroceryListRepository(get()) }
    single { GroceryListService(get()) }
    single { UserRepository() }
    single { HouseholdRepository() }
    single { HouseholdService(get()) }
    single { RecipeRepository() }
    single { RecipeService(get()) }
    single { RecipeListService(get(), get()) }
    single { MealPlanRepository() }
    single { PantryRepository() }
    single { PantryService(get(), get()) }
    single { MealPlanService(get(), get(), get(), get(), get()) }
    single {
        EmailService(
            application.environment.config.property("resend.apiKey").getString(),
            application.environment.config.property("resend.fromEmail").getString(),
            application.environment.config.property("resend.frontendBaseUrl").getString()
        )
    }
    single {
        AuthService(
            get(),
            get(),
            get(),
            application.environment.config.property("jwt.secret").getString(),
            application.environment.config.property("jwt.issuer").getString(),
            application.environment.config.property("jwt.audience").getString()
        )
    }
}