package org.example.project.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.example.project.core.connectivity.ConnectivityMonitor
import org.example.project.core.events.GlobalEventBus
import org.example.project.core.events.toGlobalEvent
import org.example.project.core.result.ApiResult
import org.example.project.core.result.DataError
import org.example.project.core.result.ErrorDetails
import kotlin.time.Duration.Companion.milliseconds

/**
 * Runs one request, with its retries, and returns an [ApiResult]. Every API class goes through here,
 * so no feature ever writes its own try/catch, connectivity check or retry loop.
 */
internal class RequestExecutor(
    private val client: HttpClient,
    private val errorMapper: ErrorMapper,
    private val connectivity: ConnectivityMonitor,
    private val retryPolicy: RetryPolicy,
    private val events: GlobalEventBus,
) {

    suspend inline fun <reified T> get(
        path: String,
        waitForNetworkMillis: Long = 0,
        noinline configure: HttpRequestBuilder.() -> Unit = {},
    ): ApiResult<T> = execute(HttpMethod.Get, path, waitForNetworkMillis, configure)

    /**
     * @param path relative to the base URL, without a leading slash.
     * @param waitForNetworkMillis when offline, how long to wait for the connection to come back
     *   before giving up with [DataError.NoInternet]. 0 = fail immediately (the default for screens);
     *   a few seconds suits background syncs.
     * `reified T` lets Ktor see the full type, e.g. BaseApiResponse<List<CategoryDto>>, at runtime.
     */
    suspend inline fun <reified T> execute(
        method: HttpMethod,
        path: String,
        waitForNetworkMillis: Long = 0,
        noinline configure: HttpRequestBuilder.() -> Unit = {},
    ): ApiResult<T> = executeWithRetries(method, path, waitForNetworkMillis, configure) { response ->
        response.body<BaseApiResponse<T>>().data
    }

    suspend fun <T> executeWithRetries(
        method: HttpMethod,
        path: String,
        waitForNetworkMillis: Long,
        configure: HttpRequestBuilder.() -> Unit,
        readData: suspend (HttpResponse) -> T?,
    ): ApiResult<T> {
        var retriesSoFar = 0
        while (true) {
            val result = attempt(method, path, waitForNetworkMillis, configure, readData)
            if (result !is ApiResult.Failure) return result

            val pause = retryPolicy.delayBeforeRetry(result.error, method, retriesSoFar) ?: return result
            delay(pause)
            retriesSoFar++
        }
    }

    private suspend fun <T> attempt(
        method: HttpMethod,
        path: String,
        waitForNetworkMillis: Long,
        configure: HttpRequestBuilder.() -> Unit,
        readData: suspend (HttpResponse) -> T?,
    ): ApiResult<T> {
        val sent = ErrorDetails(method = method.value, path = "/" + path.trimStart('/'))

        // R2: never send a request we already know can't succeed. Checked again before every retry.
        if (!awaitConnection(waitForNetworkMillis)) {
            return ApiResult.Failure(DataError.NoInternet(sent.copy(cause = "Offline before sending")))
        }

        val sentAt = events.now()
        val response = try {
            client.request(path) {
                this.method = method
                configure()
            }
        } catch (e: CancellationException) {
            throw e                                         // leaving a screen is not an error
        } catch (e: Exception) {
            return ApiResult.Failure(errorMapper.fromThrowable(e, sent = sent))
        }

        if (!response.status.isSuccess()) {
            val error = errorMapper.fromResponse(response)
            error.toGlobalEvent()?.let { events.raise(it, sentAt) }     // the caller still gets its error below
            return ApiResult.Failure(error)
        }

        return try {
            val data = readData(response)
            if (data != null) ApiResult.Success(data) else ApiResult.Failure(errorMapper.badResponse(response))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResult.Failure(errorMapper.fromThrowable(e, response))
        }
    }

    /** True if connected now, or the connection returns within [waitMillis]. */
    private suspend fun awaitConnection(waitMillis: Long): Boolean {
        if (connectivity.status.value.isConnected) return true
        if (waitMillis <= 0) return false
        return withTimeoutOrNull(waitMillis.milliseconds) { connectivity.status.first { it.isConnected } } != null
    }
}
