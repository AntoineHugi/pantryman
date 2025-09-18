package org.pantry.di

import org.koin.dsl.module

import org.pantry.repositories.GroceryListRepository
import org.pantry.repositories.ItemRepository
import org.pantry.services.GroceryListService
import org.pantry.services.ItemService

val appModule = module {
    single { ItemRepository() }
    single { ItemService(get()) }
    single { GroceryListRepository( ItemRepository() ) }
    single { GroceryListService(get()) }
}