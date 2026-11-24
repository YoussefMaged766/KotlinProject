package org.example.project.categories.presentation.mapper

import org.example.project.categories.presentation.CategoriesEffect
import org.example.project.core.loading.LoadEffect

internal fun LoadEffect.toEffect(): CategoriesEffect = when (this) {
    is LoadEffect.ShowError -> CategoriesEffect.ShowError(display)
    LoadEffect.ShowBackOnline -> CategoriesEffect.ShowBackOnline
}
