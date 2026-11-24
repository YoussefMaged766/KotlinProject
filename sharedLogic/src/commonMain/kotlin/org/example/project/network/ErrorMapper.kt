package org.example.project.network

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.serialization.ContentConvertException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import org.example.project.core.result.DataError
import org.example.project.core.result.ErrorDetails
import org.example.project.core.result.TimeoutKind

/**
 * The single place that turns "something went wrong" into a [DataError].
 * Knows about Ktor exceptions, HTTP statuses and the server's error envelope; nothing above it does.
 *
 * @param isOnline whether the device is online right now (from the ConnectivityMonitor). Used to tell
 *   a dropped connection ([DataError.ConnectionLost]) from being offline ([DataError.NoInternet]).
 */
internal class ErrorMapper(private val isOnline: () -> Boolean) {

    /** For failures that never produced a usable response. Never call it with a CancellationException. */
    fun fromThrowable(
        throwable: Throwable,
        response: HttpResponse? = null,
        sent: ErrorDetails = ErrorDetails(),
    ): DataError {
        val details = (response?.let(::detailsOf) ?: sent).copy(cause = throwable.describe())
        return when (throwable) {
            // Timeouts first: they are IOExceptions too.
            is ConnectTimeoutException -> DataError.Timeout(TimeoutKind.Connect, details)
            is SocketTimeoutException -> DataError.Timeout(TimeoutKind.Socket, details)
            is HttpRequestTimeoutException -> DataError.Timeout(TimeoutKind.Request, details)
            is ContentConvertException, is SerializationException -> DataError.BadResponse(details)
            is IOException -> if (isOnline()) DataError.ConnectionLost(details) else DataError.NoInternet(details)
            else -> DataError.Unknown(details)
        }
    }

    /** For a response with an error status (>= 400). */
    suspend fun fromResponse(response: HttpResponse): DataError {
        val envelope = response.readEnvelopeOrNull()
        val details = detailsOf(response).copy(serverMessage = envelope?.message?.takeIf { it.isNotBlank() })
        val data = envelope?.data as? JsonObject

        return when (val status = response.status.value) {
            400, 422 -> DataError.Validation(fieldErrorsFrom(data), details)
            401 -> DataError.Unauthorized(hadSession = HttpHeaders.Authorization in response.request.headers, details = details)
            403 -> DataError.Forbidden(details)
            404 -> DataError.NotFound(details)
            409 -> DataError.Business(code = data?.stringOrNull(BUSINESS_CODE_FIELD), details = details)
            426 -> DataError.UpgradeRequired(details)
            429 -> DataError.RateLimited(
                retryAfterSeconds = response.headers[HttpHeaders.RetryAfter]?.trim()?.toLongOrNull(),
                details = details,
            )
            503 -> if (data?.booleanOrNull(MAINTENANCE_FIELD) == true) DataError.Maintenance(details)
                   else DataError.Server(status, details)
            else -> DataError.Server(status, details)
        }
    }

    /** For a 2xx whose envelope had no data. */
    fun badResponse(response: HttpResponse): DataError = DataError.BadResponse(detailsOf(response))

    private fun detailsOf(response: HttpResponse) = ErrorDetails(
        status = response.status.value,
        method = response.request.method.value,
        path = response.request.url.encodedPath,
        requestId = response.headers[REQUEST_ID_HEADER],
    )

    private suspend fun HttpResponse.readEnvelopeOrNull(): BaseApiResponse<JsonElement>? = try {
        appJson.decodeFromString<BaseApiResponse<JsonElement>>(bodyAsText())
    } catch (e: SerializationException) {
        null                                              // not JSON at all, e.g. a proxy's 502 page
    } catch (e: IllegalArgumentException) {
        null
    }

    /** Accepts `{"errors": ["field: message", …]}` or `{"errors": {"field": "message"}}`. */
    private fun fieldErrorsFrom(data: JsonObject?): Map<String, String> = when (val errors = data?.get("errors")) {
        is JsonArray -> errors.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.toFieldError() }.toMap()
        is JsonObject -> errors.mapNotNull { (field, message) ->
            (message as? JsonPrimitive)?.contentOrNull?.let { field to it }
        }.toMap()
        else -> emptyMap()
    }

    private fun String.toFieldError(): Pair<String, String>? {
        val separator = indexOf(':')
        if (separator <= 0) return null
        return substring(0, separator).trim() to substring(separator + 1).trim()
    }

    private fun JsonObject.stringOrNull(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.booleanOrNull(key: String) = (get(key) as? JsonPrimitive)?.booleanOrNull

    private fun Throwable.describe() = "${this::class.simpleName}: $message"

    private companion object {
        const val REQUEST_ID_HEADER = "X-Request-Id"
        const val BUSINESS_CODE_FIELD = "errorCode"      // assumption: confirm with the backend
        const val MAINTENANCE_FIELD = "maintenance"      // assumption: confirm with the backend
    }
}
