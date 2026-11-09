package org.example.project.categories.presentation.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

@Immutable
data class CategoryUi(
    val id: String,
    val name: String,
    @StringRes val subtitleRes: Int?,   // already decided by the mapper; the row just renders it
)
