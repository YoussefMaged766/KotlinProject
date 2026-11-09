package org.example.project.categories

import kotlinx.coroutines.test.runTest
import org.example.project.categories.domain.model.Category
import org.example.project.categories.domain.repository.CategoriesRepository
import org.example.project.categories.domain.usecase.GetCategoriesUseCase
import org.example.project.network.ApiResult
import org.example.project.network.DataError
import kotlin.test.Test
import kotlin.test.assertEquals

class GetCategoriesUseCaseTest {

    /** Satisfies the same contract as CategoriesRepositoryImpl, with no HTTP (Liskov). */
    private class FakeCategoriesRepository(
        private val result: ApiResult<List<Category>>,
    ) : CategoriesRepository {
        override suspend fun getCategories() = result
    }

    @Test
    fun allCategoryComesFirst() = runTest {
        val burger = Category(id = "1", name = "Burger", imageUrl = null, isAll = false)
        val all = Category(id = "2", name = "All", imageUrl = null, isAll = true)
        val useCase = GetCategoriesUseCase(FakeCategoriesRepository(ApiResult.Success(listOf(burger, all))))

        assertEquals(listOf(all, burger), (useCase() as ApiResult.Success).data)
    }

    @Test
    fun failurePassesThroughUnchanged() = runTest {
        val useCase = GetCategoriesUseCase(FakeCategoriesRepository(ApiResult.Failure(DataError.Timeout)))

        assertEquals(ApiResult.Failure(DataError.Timeout), useCase())
    }
}
