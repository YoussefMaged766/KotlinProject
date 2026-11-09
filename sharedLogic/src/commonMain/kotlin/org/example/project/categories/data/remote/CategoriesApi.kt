package org.example.project.categories.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import org.example.project.network.ApiResult
import org.example.project.network.safeRequest

/** The Ktor version of a Retrofit `interface CategoriesApiService`. */
internal class CategoriesApi(private val client: HttpClient) {

    suspend fun getCategories(): ApiResult<List<CategoryDto>> =
        safeRequest { client.get("v1/client/categories") }   // relative path, no leading slash
}
