package org.example.project.network

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import org.example.project.core.result.ApiResult

/**
 * Runs one request and returns an [ApiResult]. Every API class goes through here, so no feature
 * ever writes its own try/catch. Phase 3 adds the "are we online?" pre-check here.
 */
internal class RequestExecutor(internal val errorMapper: ErrorMapper) {

    /** `reified T` lets Ktor see the full type, e.g. BaseApiResponse<List<CategoryDto>>, at runtime. */
    suspend inline fun <reified T> execute(request: () -> HttpResponse): ApiResult<T> {
        val response = try {
            request()
        } catch (e: CancellationException) {
            throw e                                         // leaving a screen is not an error
        } catch (e: Exception) {
            return ApiResult.Failure(errorMapper.fromThrowable(e))
        }

        if (!response.status.isSuccess()) return ApiResult.Failure(errorMapper.fromResponse(response))

        return try {
            val data = response.body<BaseApiResponse<T>>().data
            if (data != null) ApiResult.Success(data) else ApiResult.Failure(errorMapper.badResponse(response))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResult.Failure(errorMapper.fromThrowable(e, response))
        }
    }
}
