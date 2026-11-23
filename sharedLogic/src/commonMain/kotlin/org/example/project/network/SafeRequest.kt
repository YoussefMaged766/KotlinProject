package org.example.project.network

import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import org.example.project.core.result.ApiResult
import org.example.project.core.result.DataError

/**
 * Runs one request and reads the envelope. The ONLY place that knows about Ktor exceptions.
 * `reified T` lets Ktor see the full type BaseApiResponse<List<CategoryDto>> at runtime.
 */
internal suspend inline fun <reified T> safeRequest(request: () -> HttpResponse): ApiResult<T> {
    val response = try {
        request()
    } catch (e: CancellationException) {
        throw e                                         // never swallow cancellation
    } catch (e: HttpRequestTimeoutException) {
        return ApiResult.Failure(DataError.Timeout)
    } catch (e: ConnectTimeoutException) {
        return ApiResult.Failure(DataError.Timeout)
    } catch (e: IOException) {
        return ApiResult.Failure(DataError.NoInternet)  // no network, DNS failure, connection reset
    }

    if (!response.status.isSuccess()) return ApiResult.Failure(response.toServerError())

    return try {
        val data = response.body<BaseApiResponse<T>>().data
        if (data != null) ApiResult.Success(data) else ApiResult.Failure(DataError.BadResponse)
    } catch (e: ContentConvertException) {
        ApiResult.Failure(DataError.BadResponse)
    }
}

/** Error bodies carry a differently-shaped `data`, so decode it loosely as JsonElement. */
internal suspend fun HttpResponse.toServerError(): DataError.Server {
    val message = try {
        appJson.decodeFromString<BaseApiResponse<JsonElement>>(bodyAsText()).message
    } catch (e: SerializationException) {
        status.description                              // not JSON at all, e.g. a proxy's 502 page
    }
    return DataError.Server(status.value, message)
}
