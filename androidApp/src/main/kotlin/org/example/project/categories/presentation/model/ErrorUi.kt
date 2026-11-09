package org.example.project.categories.presentation.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

@Immutable
data class ErrorUi(
    @StringRes val messageRes: Int,
    val formatArgs: List<String> = emptyList(),
)
