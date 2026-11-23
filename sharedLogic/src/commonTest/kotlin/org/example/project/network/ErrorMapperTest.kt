package org.example.project.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import org.example.project.core.config.AppEnvironment
import org.example.project.core.config.AppInfo
import org.example.project.core.config.NetworkConfig
import org.example.project.core.connectivity.FakeConnectivityMonitor
import org.example.project.core.connectivity.NetworkStatus
import org.example.project.core.result.ApiResult
import org.example.project.core.result.DataError
import org.example.project.core.result.TimeoutKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Black-box: real HttpClient + RequestExecutor + ErrorMapper, only the engine is fake.
 * Retries are off (maxRetries = 0) so each case is one request and no real delays.
 */
class ErrorMapperTest {

    private val config = NetworkConfig(
        environment = AppEnvironment.Dev,
        appInfo = AppInfo(platform = "android", appVersion = "1.0", languageTag = "en"),
        maxRetries = 0,
    )

    private suspend fun errorFor(isOnline: Boolean = true, handler: MockRequestHandler): DataError {
        val client = createHttpClient(MockEngine(handler), config)
        val executor = RequestExecutor(ErrorMapper(isOnline = { isOnline }), FakeConnectivityMonitor(NetworkStatus.Online))
        val result = executor.execute<List<String>> { client.get("v1/things") }
        return assertIs<ApiResult.Failure>(result).error
    }

    private fun json(status: HttpStatusCode, body: String, vararg extraHeaders: Pair<String, String>): MockRequestHandler = {
        respond(
            content = body,
            status = status,
            headers = headersOf(HttpHeaders.ContentType to listOf("application/json"), *extraHeaders.map { it.first to listOf(it.second) }.toTypedArray()),
        )
    }

    // ---- HTTP statuses ---------------------------------------------------------------------

    @Test
    fun badRequestWithFieldErrorsBecomesValidation() = runTest {
        val error = errorFor(handler = json(
            HttpStatusCode.BadRequest,
            """{"code":400,"message":"Validation failed","data":{"errors":["longitude: must be a number","latitude: required"]}}""",
        ))

        val validation = assertIs<DataError.Validation>(error)
        assertEquals(mapOf("longitude" to "must be a number", "latitude" to "required"), validation.fieldErrors)
        assertEquals(400, validation.details?.status)
        assertEquals("Validation failed", validation.details?.serverMessage)
    }

    @Test
    fun unprocessableEntityIsAlsoValidation() = runTest {
        assertIs<DataError.Validation>(errorFor(handler = json(HttpStatusCode.UnprocessableEntity, """{"code":422,"message":"Invalid","data":null}""")))
    }

    @Test
    fun simpleStatusesMapToTheirType() = runTest {
        assertIs<DataError.Unauthorized>(errorFor(handler = json(HttpStatusCode.Unauthorized, """{"code":401,"message":"Token expired"}""")))
        assertIs<DataError.Forbidden>(errorFor(handler = json(HttpStatusCode.Forbidden, """{"code":403,"message":"No"}""")))
        assertIs<DataError.NotFound>(errorFor(handler = json(HttpStatusCode.NotFound, """{"code":404,"message":"Cannot GET /v1/things"}""")))
        assertIs<DataError.UpgradeRequired>(errorFor(handler = json(HttpStatusCode.UpgradeRequired, """{"code":426,"message":"Update"}""")))
    }

    @Test
    fun conflictCarriesTheServersBusinessCode() = runTest {
        val error = errorFor(handler = json(HttpStatusCode.Conflict, """{"code":409,"message":"Restaurant closed","data":{"errorCode":"RESTAURANT_CLOSED"}}"""))

        val business = assertIs<DataError.Business>(error)
        assertEquals("RESTAURANT_CLOSED", business.code)
        assertEquals("Restaurant closed", business.details?.serverMessage)
    }

    @Test
    fun tooManyRequestsReadsRetryAfter() = runTest {
        val error = errorFor(handler = json(HttpStatusCode.TooManyRequests, """{"code":429,"message":"Slow down"}""", HttpHeaders.RetryAfter to "7"))

        assertEquals(7L, assertIs<DataError.RateLimited>(error).retryAfterSeconds)
    }

    @Test
    fun serviceUnavailableIsMaintenanceOnlyWhenFlagged() = runTest {
        assertIs<DataError.Maintenance>(errorFor(handler = json(HttpStatusCode.ServiceUnavailable, """{"code":503,"message":"Back soon","data":{"maintenance":true}}""")))

        val plain = errorFor(handler = json(HttpStatusCode.ServiceUnavailable, """{"code":503,"message":"Overloaded"}"""))
        assertEquals(503, assertIs<DataError.Server>(plain).status)
    }

    @Test
    fun otherServerErrorsKeepTheirStatus() = runTest {
        assertEquals(500, assertIs<DataError.Server>(errorFor(handler = json(HttpStatusCode.InternalServerError, """{"code":500,"message":"Oops"}"""))).status)
    }

    @Test
    fun nonJsonErrorPageStillMapsByStatus() = runTest {
        val error = errorFor(handler = { respond("<html>Bad gateway</html>", HttpStatusCode.BadGateway, headersOf(HttpHeaders.ContentType, "text/html")) })

        val server = assertIs<DataError.Server>(error)
        assertEquals(502, server.status)
        assertNull(server.details?.serverMessage)
    }

    @Test
    fun detailsCarryMethodPathAndRequestId() = runTest {
        val error = errorFor(handler = json(HttpStatusCode.NotFound, """{"code":404,"message":"Nope"}""", "X-Request-Id" to "req-8f2c"))

        val details = requireNotNull(error.details)
        assertEquals("GET", details.method)
        assertEquals("/v1/things", details.path)
        assertEquals("req-8f2c", details.requestId)
    }

    // ---- Exceptions ------------------------------------------------------------------------

    @Test
    fun eachTimeoutKeepsItsKind() = runTest {
        assertEquals(TimeoutKind.Connect, assertIs<DataError.Timeout>(errorFor { throw ConnectTimeoutException("connect timed out") }).kind)
        assertEquals(TimeoutKind.Socket, assertIs<DataError.Timeout>(errorFor { throw SocketTimeoutException("read timed out") }).kind)
        assertEquals(TimeoutKind.Request, assertIs<DataError.Timeout>(errorFor { throw HttpRequestTimeoutException("https://x/v1/things", 30_000) }).kind)
    }

    @Test
    fun ioFailureWhileOnlineIsConnectionLost() = runTest {
        assertIs<DataError.ConnectionLost>(errorFor(isOnline = true) { throw IOException("connection reset") })
    }

    @Test
    fun ioFailureWhileOfflineIsNoInternet() = runTest {
        assertIs<DataError.NoInternet>(errorFor(isOnline = false) { throw IOException("unreachable") })
    }

    @Test
    fun malformedJsonIsBadResponse() = runTest {
        assertIs<DataError.BadResponse>(errorFor(handler = json(HttpStatusCode.OK, """this is not json""")))
    }

    @Test
    fun successWithoutDataIsBadResponse() = runTest {
        assertIs<DataError.BadResponse>(errorFor(handler = json(HttpStatusCode.OK, """{"code":200,"message":"ok","data":null}""")))
    }

    @Test
    fun unexpectedExceptionIsUnknownWithItsCause() = runTest {
        val error = errorFor { throw IllegalStateException("boom") }

        assertIs<DataError.Unknown>(error)
        assertTrue(error.details?.cause.orEmpty().contains("IllegalStateException"))
    }

    @Test
    fun cancellationIsRethrownNeverAnError() = runTest {
        val client = createHttpClient(MockEngine { throw CancellationException("screen closed") }, config)
        val executor = RequestExecutor(ErrorMapper(isOnline = { true }), FakeConnectivityMonitor(NetworkStatus.Online))

        assertFailsWith<CancellationException> {
            executor.execute<List<String>> { client.post("v1/things") }
        }
    }
}
