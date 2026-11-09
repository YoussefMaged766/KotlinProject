package org.example.project.categories.data.mapper

import org.example.project.categories.data.remote.CategoryDto
import org.example.project.categories.domain.model.Category
import org.example.project.core.mapper.ListMapper
import org.example.project.core.mapper.Mapper

internal class CategoryDtoMapper : Mapper<CategoryDto, Category> {
    override fun map(input: CategoryDto): Category = Category(
        id = input.id,
        name = input.name,
        imageUrl = input.image,      // the JSON calls it `image`; the domain calls it imageUrl
        isAll = input.isAll,
    )
}

internal class CategoryDtoListMapper(
    private val itemMapper: Mapper<CategoryDto, Category>,
) : ListMapper<CategoryDto, Category> {
    override fun map(input: List<CategoryDto>): List<Category> = input.map(itemMapper::map)
}
