package org.example.project.categories.data.remote

import kotlinx.serialization.Serializable

/** Wire shape. Property names match the JSON, so no @SerialName is needed. */
@Serializable
internal data class CategoryDto(
    val id: String = "",
    val name: String = "",
    val image: String? = null,   // null on most categories
    val isAll: Boolean = false,
)
