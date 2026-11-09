package org.example.project.categories.domain.model

/** Plain Kotlin: no serialization, Ktor or Android annotations. */
data class Category(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val isAll: Boolean,
)
