package org.example.project.categories

import org.example.project.categories.data.mapper.CategoryDtoListMapper
import org.example.project.categories.data.mapper.CategoryDtoMapper
import org.example.project.categories.data.remote.CategoryDto
import org.example.project.categories.domain.model.Category
import kotlin.test.Test
import kotlin.test.assertEquals

class CategoryDtoMapperTest {

    @Test
    fun mapsImageToImageUrl() {
        val dto = CategoryDto(id = "1", name = "Burger", image = "https://cdn/burger.png", isAll = false)

        assertEquals(
            Category(id = "1", name = "Burger", imageUrl = "https://cdn/burger.png", isAll = false),
            CategoryDtoMapper().map(dto),
        )
    }

    @Test
    fun listMapperKeepsOrder() {
        val listMapper = CategoryDtoListMapper(itemMapper = CategoryDtoMapper())
        val dtos = listOf(CategoryDto(id = "a", name = "A"), CategoryDto(id = "b", name = "B"))

        assertEquals(listOf("a", "b"), listMapper.map(dtos).map(Category::id))
    }
}
