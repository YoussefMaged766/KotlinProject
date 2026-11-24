package org.example.project.categories.presentation

import kotlinx.collections.immutable.ImmutableList
import org.example.project.categories.presentation.model.CategoryUi
import org.example.project.core.loading.ScreenState

/**
 * Everything the screen needs to render. Stable for Compose through compose_stability.conf.
 * Becomes an @Immutable data class holding the ScreenState once the screen has state of its own.
 */
typealias CategoriesState = ScreenState<ImmutableList<CategoryUi>>
