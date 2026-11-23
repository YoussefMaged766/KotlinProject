package org.example.project.network

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.example.project.core.connectivity.ConnectivityMonitor
import org.example.project.core.result.ApiResult
import org.example.project.core.result.DataError
import org.example.project.core.result.ErrorDetails
import kotlin.time.Duration.Companion.milliseconds

/**
 * Runs one request and returns an [ApiResult]. Every API class goes through here, so no feature
 * ever writes its own try/catch or connectivity check.
 */
internal class RequestExecutor(
    internal val errorMapper: ErrorMapper,
    internal val connectivity: ConnectivityMonitor,
) {

    /**
     * @param waitForNetworkMillis when offline, how long to wait for the connection to come back
     *   before giving up with [DataError.NoInternet]. 0 = fail immediately (the default for screens);
     *   a few seconds suits background syncs.
     * `reified T` lets Ktor see the full type, e.g. BaseApiResponse<List<CategoryDto>>, at runtime.
     */
    suspend inline fun <reified T> execute(
        waitForNetworkMillis: Long = 0,
        request: () -> HttpResponse,
    ): ApiResult<T> {
        // R2: never send a request we already know can't succeed.
        if (!awaitConnection(waitForNetworkMillis)) {
            return ApiResult.Failure(DataError.NoInternet(ErrorDetails(cause = "Offline before sending")))
        }

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

    /** True if connected now, or the connection returns within [waitMillis]. */
    internal suspend fun awaitConnection(waitMillis: Long): Boolean {
        if (connectivity.status.value.isConnected) return true
        if (waitMillis <= 0) return false
        return withTimeoutOrNull(waitMillis.milliseconds) { connectivity.status.first { it.isConnected } } != null
    }
}
