package org.example.project.categories.presentation

import androidx.compose.runtime.Immutable
import org.example.project.categories.presentation.model.CategoryUi
import org.example.project.categories.presentation.model.ErrorUi

/** Everything the screen needs to render. Immutable; only the ViewModel creates new copies. */
@Immutable
data class CategoriesState(
    val isLoading: Boolean = true,
    val categories: List<CategoryUi> = emptyList(),
    val error: ErrorUi? = null,
)
