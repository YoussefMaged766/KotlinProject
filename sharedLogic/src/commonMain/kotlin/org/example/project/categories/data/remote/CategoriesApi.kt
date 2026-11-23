package org.example.project.categories.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import org.example.project.core.result.ApiResult
import org.example.project.network.RequestExecutor

/** The Ktor version of a Retrofit `interface CategoriesApiService`. */
internal class CategoriesApi(
    private val client: HttpClient,
    private val executor: RequestExecutor,
) {

    suspend fun getCategories(): ApiResult<List<CategoryDto>> =
        executor.execute { client.get("v1/client/categories") }   // relative path, no leading slash
}
