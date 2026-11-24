package org.example.project.categories.presentation

import androidx.compose.runtime.Immutable

/** Every user action. The ViewModel's single entry point takes one of these. */
@Immutable
sealed interface CategoriesIntent {
    data object LoadCategories : CategoriesIntent

    /** A Retry button: full screen, dialog or snackbar. */
    data object Retry : CategoriesIntent

    /** Pull to refresh. */
    data object Refresh : CategoriesIntent

    data class CategoryClicked(val categoryId: String) : CategoriesIntent
}
