package org.example.project.categories

import org.example.project.categories.data.mapper.toDomain
import org.example.project.categories.data.remote.CategoryDto
import org.example.project.categories.domain.model.Category
import kotlin.test.Test
import kotlin.test.assertEquals

class CategoryMapperTest {

    @Test
    fun mapsImageToImageUrl() {
        val dto = CategoryDto(id = "1", name = "Burger", image = "https://cdn/burger.png", isAll = false)

        assertEquals(
            Category(id = "1", name = "Burger", imageUrl = "https://cdn/burger.png", isAll = false),
            dto.toDomain(),
        )
    }

    @Test
    fun aRepeatedIdIsKeptOnce() {
        // Ids are list keys on both platforms; Compose crashes on a duplicate.
        val dtos = listOf(CategoryDto(id = "a", name = "A"), CategoryDto(id = "a", name = "A again"), CategoryDto(id = "b", name = "B"))

        assertEquals(listOf("A", "B"), dtos.toDomain().map(Category::name))
    }

    @Test
    fun listMappingKeepsOrder() {
        val dtos = listOf(CategoryDto(id = "a", name = "A"), CategoryDto(id = "b", name = "B"))

        assertEquals(listOf("a", "b"), dtos.toDomain().map(Category::id))
    }
}
