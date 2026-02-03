package org.pantry.di

import org.koin.dsl.module

import org.pantry.repositories.GroceryListRepository
import org.pantry.repositories.ItemRepository
import org.pantry.repositories.UserRepository
import org.pantry.services.GroceryListService
import org.pantry.services.ItemService
import org.pantry.services.AuthService

val appModule = module {
    single { ItemRepository() }
    single { ItemService(get()) }
    single { GroceryListRepository( ItemRepository() ) }
    single { GroceryListService(get()) }
    single { UserRepository() }
    single {
        AuthService(
            get(),
            getProperty("jwt.secret"),
            getProperty("jwt.issuer"),
            getProperty("jwt.audience")
        )
    }
}