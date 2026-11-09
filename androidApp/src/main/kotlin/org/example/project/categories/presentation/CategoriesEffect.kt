package org.example.project.categories.presentation

/** One-off events that must not replay after rotation (navigation, snackbars). */
sealed interface CategoriesEffect {
    data class OpenCategory(val categoryId: String) : CategoriesEffect
}
