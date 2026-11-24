package org.example.project.categories.presentation

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import org.example.project.categories.presentation.model.CategoryUi
import org.example.project.core.loading.ScreenState

/** Everything the screen needs to render. Immutable; only the ViewModel creates new copies. */
@Immutable
data class CategoriesState(
    val screen: ScreenState<ImmutableList<CategoryUi>> = ScreenState(),
)
