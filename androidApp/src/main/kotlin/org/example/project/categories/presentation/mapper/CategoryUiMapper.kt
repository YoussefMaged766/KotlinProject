package org.example.project.categories.presentation.mapper

import org.example.project.R
import org.example.project.categories.domain.model.Category
import org.example.project.categories.presentation.model.CategoryUi
import org.example.project.core.mapper.ListMapper
import org.example.project.core.mapper.Mapper

class CategoryUiMapper : Mapper<Category, CategoryUi> {
    override fun map(input: Category): CategoryUi = CategoryUi(
        id = input.id,
        name = input.name,
        subtitleRes = if (input.isAll) R.string.categories_all_subtitle else null,
    )
}

class CategoryUiListMapper(
    private val itemMapper: Mapper<Category, CategoryUi>,
) : ListMapper<Category, CategoryUi> {
    override fun map(input: List<Category>): List<CategoryUi> = input.map(itemMapper::map)
}
