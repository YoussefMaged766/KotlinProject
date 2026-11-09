package org.example.project.categories.presentation

/** Every user action. The ViewModel's single entry point takes one of these. */
sealed interface CategoriesIntent {
    data object LoadCategories : CategoriesIntent
    data object Retry : CategoriesIntent
    data class CategoryClicked(val categoryId: String) : CategoriesIntent
}
