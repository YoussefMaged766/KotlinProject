package org.example.project.categories.presentation

import androidx.compose.runtime.Immutable
import org.example.project.core.error.ErrorDisplay

/** One-off events that must not replay after rotation (navigation, snackbars). */
@Immutable
sealed interface CategoriesEffect {
    data class OpenCategory(val categoryId: String) : CategoriesEffect

    /** A toast or a dialog; full-screen errors are state instead. */
    data class ShowError(val display: ErrorDisplay) : CategoriesEffect

    /** Tells the user why the screen changed by itself. */
    data object ShowBackOnline : CategoriesEffect
}
