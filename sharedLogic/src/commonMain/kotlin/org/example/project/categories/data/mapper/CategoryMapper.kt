package org.example.project.categories.data.mapper

import org.example.project.categories.data.remote.CategoryDto
import org.example.project.categories.domain.model.Category

/** DTO -> domain. The JSON calls it `image`; the domain calls it imageUrl. */
internal fun CategoryDto.toDomain(): Category = Category(
    id = id,
    name = name,
    imageUrl = image,
    isAll = isAll,
)

/** The first of each id wins: an id identifies a category everywhere above, e.g. as a list key. */
internal fun List<CategoryDto>.toDomain(): List<Category> = distinctBy(CategoryDto::id).map(CategoryDto::toDomain)
