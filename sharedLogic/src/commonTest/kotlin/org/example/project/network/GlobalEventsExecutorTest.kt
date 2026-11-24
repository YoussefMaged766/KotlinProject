package org.example.project.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import org.example.project.core.config.AppEnvironment
import org.example.project.core.config.AppInfo
import org.example.project.core.config.NetworkConfig
import org.example.project.core.events.GlobalEvent
import org.example.project.core.events.GlobalEventBus
import org.example.project.core.result.ApiResult
import org.example.project.core.result.DataError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** M1, M2, M3 through the real client: which responses become app-wide events, and how many. */
@OptIn(ExperimentalCoroutinesApi::class)
class GlobalEventsExecutorTest {

    private val config = NetworkConfig(
        environment = AppEnvironment.Dev,
        appInfo = AppInfo(platform = "android", appVersion = "1.0", languageTag = "en"),
    )

    private val events = GlobalEventBus()

    private suspend fun pending() = events.pending.first()

    /** Answers everything with [status]. A path ending in digits, like `v1/300`, is answered after that many ms. */
    private fun TestScope.executor(status: HttpStatusCode, body: String = """{"code":0,"message":"x"}"""): RequestExecutor {
        val engine = MockEngine.create {
            dispatcher = StandardTestDispatcher(testScheduler)
            reuseHandlers = true
            addHandler { request ->
                request.url.encodedPath.substringAfterLast('/').toLongOrNull()?.let { delay(it) }
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        return testExecutor(engine, config, events = events)
    }

    private suspend fun RequestExecutor.sendWithToken(path: String, method: HttpMethod = HttpMethod.Get) =
        execute<List<String>>(method, path) { header(HttpHeaders.Authorization, "Bearer test") }

    private fun errorOf(result: ApiResult<*>) = assertIs<ApiResult.Failure>(result).error

    /** Like the app root: reacts to each session expiry at once, and counts them. */
    private fun TestScope.countLogouts(): () -> Int {
        var logouts = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            events.pending.collect { pending ->
                if (GlobalEvent.SessionExpired in pending) {
                    logouts++
                    events.handled(GlobalEvent.SessionExpired)
                }
            }
        }
        return { logouts }
    }

    @Test
    fun parallel401sLogOutOnceEvenWhenTheyArriveAfterTheLogout() = runTest {
        val executor = executor(HttpStatusCode.Unauthorized)
        val logouts = countLogouts()

        val results = listOf(100, 200, 300).map { async { executor.sendWithToken("v1/$it") } }.awaitAll()

        assertEquals(1, logouts())
        results.forEach { assertIs<DataError.Unauthorized>(errorOf(it)) }   // every caller still gets its own answer
    }

    @Test
    fun aLater401InANewSessionLogsOutAgain() = runTest {
        val executor = executor(HttpStatusCode.Unauthorized)
        val logouts = countLogouts()

        executor.sendWithToken("v1/things")
        executor.sendWithToken("v1/things")

        assertEquals(2, logouts())
    }

    @Test
    fun a401WithoutATokenIsNotAnExpiredSession() = runTest {
        val result = executor(HttpStatusCode.Unauthorized).execute<List<String>>(HttpMethod.Post, "v1/login")

        assertEquals(false, assertIs<DataError.Unauthorized>(errorOf(result)).hadSession)
        assertEquals(emptySet(), pending())
    }

    @Test
    fun a401OnAnyMethodExpiresTheSession() = runTest {
        val result = executor(HttpStatusCode.Unauthorized).sendWithToken("v1/orders", HttpMethod.Post)

        assertTrue(assertIs<DataError.Unauthorized>(errorOf(result)).hadSession)
        assertEquals(setOf<GlobalEvent>(GlobalEvent.SessionExpired), pending())
    }

    @Test
    fun status426AsksForAnUpgrade() = runTest {
        assertIs<DataError.UpgradeRequired>(errorOf(executor(HttpStatusCode.UpgradeRequired).get<List<String>>("v1/things")))
        assertEquals(setOf<GlobalEvent>(GlobalEvent.UpgradeRequired), pending())
    }

    @Test
    fun onlyAFlagged503IsMaintenance() = runTest {
        executor(HttpStatusCode.ServiceUnavailable).get<List<String>>("v1/things")
        assertEquals(emptySet(), pending())

        executor(HttpStatusCode.ServiceUnavailable, """{"code":503,"message":"x","data":{"maintenance":true}}""").get<List<String>>("v1/things")
        assertEquals(setOf<GlobalEvent>(GlobalEvent.Maintenance), pending())
    }

    @Test
    fun ordinaryResultsRaiseNothing() = runTest {
        listOf(400, 403, 404, 409, 422, 429, 500, 502).forEach { status ->
            executor(HttpStatusCode.fromValue(status)).sendWithToken("v1/things")
            assertEquals(emptySet(), pending(), "status $status")
        }
        executor(HttpStatusCode.OK, """{"code":200,"message":"","data":[]}""").sendWithToken("v1/things")
        listOf(IOException("reset"), SocketTimeoutException("slow"), IllegalStateException("bug")).forEach { failure ->
            testExecutor(MockEngine { throw failure }, config, events = events).sendWithToken("v1/things")
        }
        assertEquals(emptySet(), pending())
    }
}
