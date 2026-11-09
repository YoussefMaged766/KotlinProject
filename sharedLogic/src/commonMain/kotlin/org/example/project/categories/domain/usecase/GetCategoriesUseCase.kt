package org.example.project.categories.domain.usecase

import org.example.project.categories.domain.model.Category
import org.example.project.categories.domain.repository.CategoriesRepository
import org.example.project.network.ApiResult
import org.example.project.network.map

/** One use case, one public `invoke()`. Business rules live here, not in the UI. */
class GetCategoriesUseCase(private val repository: CategoriesRepository) {

    suspend operator fun invoke(): ApiResult<List<Category>> =
        repository.getCategories().map(::putAllCategoryFirst)

    /** Product rule: the "All" category always leads the list, whatever order the server sends. */
    private fun putAllCategoryFirst(categories: List<Category>): List<Category> =
        categories.sortedByDescending(Category::isAll)
}
