package org.example.project.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import org.example.project.core.config.AppEnvironment
import org.example.project.core.config.AppInfo
import org.example.project.core.config.NetworkConfig
import org.example.project.core.connectivity.FakeConnectivityMonitor
import org.example.project.core.connectivity.NetworkStatus
import org.example.project.core.result.ApiResult
import org.example.project.core.result.DataError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private typealias Reply = suspend MockRequestHandleScope.() -> HttpResponseData

/** R5, R7, R8 through the real client. Backoff runs on virtual time, so nothing really waits. */
@OptIn(ExperimentalCoroutinesApi::class)
class RetryingExecutorTest {

    private val config = NetworkConfig(
        environment = AppEnvironment.Dev,
        appInfo = AppInfo(platform = "android", appVersion = "1.0", languageTag = "en"),
    )

    private val connectivity = FakeConnectivityMonitor(NetworkStatus.Online)

    private class Fixture(val executor: RequestExecutor, private val sentCount: () -> Int) {
        /** Every request that reached the engine. MockEngine.requestHistory leaves out the ones that throw. */
        val sent: Int get() = sentCount()
    }

    /**
     * Answers with [replies] in order; the last one repeats. The engine runs on the test's own dispatcher:
     * on a real thread, virtual time would jump past a backoff while the first request is still being sent.
     */
    private fun TestScope.fixture(vararg replies: Reply): Fixture {
        var index = 0
        val engine = MockEngine.create {
            dispatcher = StandardTestDispatcher(testScheduler)
            reuseHandlers = true
            addHandler { replies[minOf(index++, replies.lastIndex)].invoke(this) }
        }
        return Fixture(testExecutor(engine, config, connectivity, RetryPolicy(config.maxRetries)), sentCount = { index })
    }

    private fun json(status: HttpStatusCode, body: String, retryAfter: String? = null): Reply = {
        val headers = buildList {
            add(HttpHeaders.ContentType to listOf("application/json"))
            if (retryAfter != null) add(HttpHeaders.RetryAfter to listOf(retryAfter))
        }
        respond(body, status, headersOf(*headers.toTypedArray()))
    }

    private val ok = json(HttpStatusCode.OK, """{"code":200,"message":"","data":["a"]}""")
    private val unavailable = json(HttpStatusCode.ServiceUnavailable, """{"code":503,"message":"Overloaded"}""")
    private val broken = json(HttpStatusCode.InternalServerError, """{"code":500,"message":"Oops"}""")

    private fun errorOf(result: ApiResult<*>) = assertIs<ApiResult.Failure>(result).error

    @Test
    fun getRecoversAfterOneServerError() = runTest {
        val fixture = fixture(unavailable, ok)

        val result = fixture.executor.get<List<String>>("v1/things")

        assertEquals(listOf("a"), assertIs<ApiResult.Success<List<String>>>(result).data)
        assertEquals(2, fixture.sent)
    }

    @Test
    fun getGivesUpAfterTheMaximum() = runTest {
        val fixture = fixture(broken)

        val error = errorOf(fixture.executor.get<List<String>>("v1/things"))

        assertEquals(500, assertIs<DataError.Server>(error).status)
        assertEquals(1 + config.maxRetries, fixture.sent)
    }

    @Test
    fun unsafeMethodsAreSentExactlyOnceWhateverHappens() = runTest {
        val failures: List<Reply> = listOf(
            unavailable,
            broken,
            json(HttpStatusCode.TooManyRequests, """{"code":429,"message":"Slow down"}""", retryAfter = "1"),
            { throw IOException("connection reset") },
            { throw SocketTimeoutException("read timed out") },
        )
        listOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch, HttpMethod.Delete).forEach { method ->
            failures.forEachIndexed { index, failure ->
                val fixture = fixture(failure, ok)

                val result = fixture.executor.execute<List<String>>(method, "v1/orders")

                assertIs<ApiResult.Failure>(result, "${method.value} failure #$index")
                assertEquals(1, fixture.sent, "${method.value} failure #$index")
            }
        }
    }

    @Test
    fun timeoutsAndDroppedConnectionsOnGetAreRetried() = runTest {
        val timedOut = fixture({ throw SocketTimeoutException("read timed out") }, ok)
        assertIs<ApiResult.Success<*>>(timedOut.executor.get<List<String>>("v1/things"))
        assertEquals(2, timedOut.sent)

        val dropped = fixture({ throw IOException("connection reset") }, ok)
        assertIs<ApiResult.Success<*>>(dropped.executor.get<List<String>>("v1/things"))
        assertEquals(2, dropped.sent)
    }

    @Test
    fun rateLimitWaitsForRetryAfterThenSucceeds() = runTest {
        val fixture = fixture(json(HttpStatusCode.TooManyRequests, """{"code":429,"message":"Slow down"}""", retryAfter = "2"), ok)

        val result = fixture.executor.get<List<String>>("v1/things")

        assertIs<ApiResult.Success<*>>(result)
        assertEquals(2_000, currentTime)
    }

    @Test
    fun aLongRateLimitIsReportedInsteadOfWaitedFor() = runTest {
        val fixture = fixture(json(HttpStatusCode.TooManyRequests, """{"code":429,"message":"Slow down"}""", retryAfter = "60"), ok)

        val error = errorOf(fixture.executor.get<List<String>>("v1/things"))

        assertEquals(60L, assertIs<DataError.RateLimited>(error).retryAfterSeconds)
        assertEquals(1, fixture.sent)
        assertEquals(0, currentTime)
    }

    @Test
    fun maintenanceIsNotRetried() = runTest {
        val fixture = fixture(json(HttpStatusCode.ServiceUnavailable, """{"code":503,"message":"Back soon","data":{"maintenance":true}}"""), ok)

        assertIs<DataError.Maintenance>(errorOf(fixture.executor.get<List<String>>("v1/things")))
        assertEquals(1, fixture.sent)
    }

    @Test
    fun unreadableBodyIsNotRetried() = runTest {
        val fixture = fixture(json(HttpStatusCode.OK, "this is not json"), ok)

        assertIs<DataError.BadResponse>(errorOf(fixture.executor.get<List<String>>("v1/things")))
        assertEquals(1, fixture.sent)
    }

    @Test
    fun goingOfflineMidRequestStopsRetrying() = runTest {
        val fixture = fixture({
            connectivity.set(NetworkStatus.Offline)
            throw IOException("network is unreachable")
        }, ok)

        assertIs<DataError.NoInternet>(errorOf(fixture.executor.get<List<String>>("v1/things")))
        assertEquals(1, fixture.sent)
    }

    @Test
    fun goingOfflineDuringTheBackoffSendsNothingMore() = runTest {
        val fixture = fixture(broken, ok)
        launch { delay(500); connectivity.set(NetworkStatus.Offline) }   // the first backoff is at least 800 ms

        assertIs<DataError.NoInternet>(errorOf(fixture.executor.get<List<String>>("v1/things")))
        assertEquals(1, fixture.sent)
    }

    @Test
    fun leavingTheScreenDuringTheBackoffStopsTheRetries() = runTest {
        val fixture = fixture(broken, ok)
        val load = async(start = CoroutineStart.UNDISPATCHED) { fixture.executor.get<List<String>>("v1/things") }
        launch { delay(500); load.cancel() }

        load.join()

        assertTrue(load.isCancelled)
        assertEquals(1, fixture.sent)
    }

    @Test
    fun failuresWithoutAResponseStillNameTheRequest() = runTest {
        val fixture = fixture({ throw SocketTimeoutException("read timed out") })

        val details = errorOf(fixture.executor.execute<List<String>>(HttpMethod.Post, "v1/orders")).details

        assertEquals("POST", details?.method)
        assertEquals("/v1/orders", details?.path)
    }
}
