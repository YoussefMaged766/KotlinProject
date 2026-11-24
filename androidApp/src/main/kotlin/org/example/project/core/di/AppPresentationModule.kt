package org.example.project.core.di

import org.example.project.core.presentation.root.AppViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Android-only bindings for what every screen shares. */
val appPresentationModule = module {
    viewModelOf(::AppViewModel)
}
