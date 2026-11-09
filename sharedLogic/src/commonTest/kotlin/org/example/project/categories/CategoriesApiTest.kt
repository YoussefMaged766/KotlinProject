package org.example.project.categories

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.example.project.categories.data.remote.CategoriesApi
import org.example.project.network.ApiConfig
import org.example.project.network.ApiResult
import org.example.project.network.DataError
import org.example.project.network.createHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals

class CategoriesApiTest {

    private val config = ApiConfig(ApiConfig.DEV_BASE_URL, "android", "1.0", "en")

    private fun apiReturning(
        status: HttpStatusCode,
        body: String,
        onRequest: (HttpRequestData) -> Unit = {},
    ): CategoriesApi {
        val engine = MockEngine { request ->
            onRequest(request)
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return CategoriesApi(createHttpClient(engine, config))
    }

    @Test
    fun successDecodesCategories() = runTest {
        val api = apiReturning(
            HttpStatusCode.OK,
            """{"code":200,"message":"Categories retrieved","data":[{"id":"1","name":"Burger","image":null,"isAll":false,"newField":1}]}""",
        )
        val result = api.getCategories() as ApiResult.Success
        assertEquals("Burger", result.data.single().name)   // also proves ignoreUnknownKeys works
    }

    @Test
    fun sendsPathAndDefaultHeaders() = runTest {
        var seenRequest: HttpRequestData? = null
        apiReturning(HttpStatusCode.OK, """{"code":200,"message":"","data":[]}""") { seenRequest = it }
            .getCategories()

        val request = requireNotNull(seenRequest)
        assertEquals("/v1/client/categories", request.url.encodedPath)
        assertEquals("android", request.headers["platform"])
        assertEquals("1.0", request.headers["version"])
    }

    @Test
    fun validationErrorKeepsServerMessage() = runTest {
        val api = apiReturning(
            HttpStatusCode.BadRequest,
            """{"code":400,"message":"Validation failed","data":{"errors":["longitude: required"]}}""",
        )
        val result = api.getCategories() as ApiResult.Failure
        assertEquals(DataError.Server(400, "Validation failed"), result.error)
    }
}
