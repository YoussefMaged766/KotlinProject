package org.example.project.categories.di

import org.example.project.categories.presentation.CategoriesViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Android-only bindings for the categories screen. */
val categoriesPresentationModule = module {
    viewModelOf(::CategoriesViewModel)   // GetCategoriesUseCase comes from sharedLogic's categoriesModule
}
