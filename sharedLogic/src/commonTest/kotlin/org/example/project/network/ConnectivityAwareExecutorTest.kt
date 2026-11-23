package org.example.project.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

/** R2 + R5: the executor checks connectivity before sending, and the mapper uses it after a failure. */
class ConnectivityAwareExecutorTest {

    private val config = NetworkConfig(
        environment = AppEnvironment.Dev,
        appInfo = AppInfo(platform = "android", appVersion = "1.0", languageTag = "en"),
        maxRetries = 0,
    )

    private val okBody: MockRequestHandler = {
        respond(
            """{"code":200,"message":"","data":["a"]}""",
            HttpStatusCode.OK,
            headersOf(HttpHeaders.ContentType, "application/json")
        )
    }

    private class Setup(
        val engine: MockEngine,
        val executor: RequestExecutor,
        val run: suspend (Long) -> ApiResult<List<String>>
    )

    private fun setup(
        connectivity: FakeConnectivityMonitor,
        handler: MockRequestHandler = okBody
    ): Setup {
        val engine = MockEngine(handler)
        val client = createHttpClient(engine, config)
        val executor = RequestExecutor(
            ErrorMapper(isOnline = { connectivity.status.value.isConnected }),
            connectivity
        )
        return Setup(
            engine,
            executor
        ) { wait -> executor.execute(waitForNetworkMillis = wait) { client.get("v1/things") } }
    }

    @Test
    fun offlineReturnsNoInternetWithoutSendingAnything() = runTest {
        val connectivity = FakeConnectivityMonitor(NetworkStatus.Offline)
        val setup = setup(connectivity)

        val result = setup.run(0)

        assertIs<DataError.NoInternet>(assertIs<ApiResult.Failure>(result).error)
        assertTrue(
            setup.engine.requestHistory.isEmpty(),
            "no request may reach the network while offline"
        )
    }

    @Test
    fun onlineSendsTheRequest() = runTest {
        val setup = setup(FakeConnectivityMonitor(NetworkStatus.Online))

        assertEquals(listOf("a"), assertIs<ApiResult.Success<List<String>>>(setup.run(0)).data)
        assertEquals(1, setup.engine.requestHistory.size)
    }

    @Test
    fun weakConnectionStillSendsTheRequest() = runTest {
        val setup = setup(FakeConnectivityMonitor(NetworkStatus.Weak))

        assertIs<ApiResult.Success<List<String>>>(setup.run(0))
    }

    @Test
    fun comingBackOnlineWithinTheWaitWindowSucceeds() = runTest {
        val connectivity = FakeConnectivityMonitor(NetworkStatus.Offline)
        val setup = setup(connectivity)
        launch { delay(500); connectivity.set(NetworkStatus.Online) }

        val result = setup.run(2_000)

        assertIs<ApiResult.Success<List<String>>>(result)
        assertEquals(1, setup.engine.requestHistory.size)
    }

    @Test
    fun stayingOfflineLongerThanTheWaitWindowIsNoInternet() = runTest {
        val connectivity = FakeConnectivityMonitor(NetworkStatus.Offline)
        val setup = setup(connectivity)
        launch { delay(3_000); connectivity.set(NetworkStatus.Online) }

        val result = setup.run(1_000)

        assertIs<DataError.NoInternet>(assertIs<ApiResult.Failure>(result).error)
        assertTrue(setup.engine.requestHistory.isEmpty())
    }

    @Test
    fun connectionDroppingBecauseTheDeviceWentOfflineIsNoInternet() = runTest {
        val connectivity = FakeConnectivityMonitor(NetworkStatus.Online)
        val setup = setup(connectivity) {
            connectivity.set(NetworkStatus.Offline)     // e.g. airplane mode switched on mid-request
            throw IOException("network is unreachable")
        }

        assertIs<DataError.NoInternet>(assertIs<ApiResult.Failure>(setup.run(0)).error)
    }

    @Test
    fun connectionDroppingWhileStillOnlineIsConnectionLost() = runTest {
        val setup =
            setup(FakeConnectivityMonitor(NetworkStatus.Online)) { throw IOException("connection reset by peer") }

        assertIs<DataError.ConnectionLost>(assertIs<ApiResult.Failure>(setup.run(0)).error)
    }

    @Test
    fun onlyOfflineCountsAsDisconnected() {
        assertTrue(NetworkStatus.Online.isConnected)
        assertTrue(NetworkStatus.Weak.isConnected)
        assertEquals(false, NetworkStatus.Offline.isConnected)
    }
}
