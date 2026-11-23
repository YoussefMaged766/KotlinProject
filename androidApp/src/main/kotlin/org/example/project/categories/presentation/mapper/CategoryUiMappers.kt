package org.example.project.categories.presentation.mapper

import org.example.project.R
import org.example.project.categories.domain.model.Category
import org.example.project.categories.presentation.model.CategoryUi

/** Domain -> UI. Decides the subtitle here, so the row composable only renders it. */
internal fun Category.toUi(): CategoryUi = CategoryUi(
    id = id,
    name = name,
    subtitleRes = if (isAll) R.string.categories_all_subtitle else null,
)

internal fun List<Category>.toUi(): List<CategoryUi> = map(Category::toUi)
