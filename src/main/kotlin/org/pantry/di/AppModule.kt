package org.pantry.di

import org.koin.dsl.module

import org.pantry.repositories.GroceryListRepository
import org.pantry.repositories.ItemRepository
import org.pantry.services.GroceryListService
import org.pantry.services.ItemService

val appModule = module {
    single { GroceryListRepository() }
    single { GroceryListService(get()) }
    single { ItemRepository() }
    single { ItemService(get()) }
}