package org.example.project.categories.presentation.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

@Immutable
data class ErrorUi(
    @StringRes val messageRes: Int,
    val serverText: String? = null,         // shown instead of messageRes (business errors only)
    val technicalDetails: String? = null,   // always null in Prod
    val canRetry: Boolean,
)
