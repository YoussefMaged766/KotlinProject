package org.example.project.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import org.example.project.core.config.AppEnvironment
import org.example.project.core.config.AppInfo
import org.example.project.core.config.NetworkConfig
import org.example.project.core.config.NetworkLogLevel
import kotlin.test.Test
import kotlin.test.assertEquals

/** Proves the client reads its settings from NetworkConfig, per environment. */
class HttpClientConfigTest {

    private val appInfo = AppInfo(platform = "ios", appVersion = "2.3.0", languageTag = "ar")

    private suspend fun firstRequestFor(environment: AppEnvironment): HttpRequestData {
        var seen: HttpRequestData? = null
        val engine = MockEngine { request -> seen = request; respond("{}") }
        createHttpClient(engine, NetworkConfig(environment, appInfo)).get("v1/ping")
        return requireNotNull(seen)
    }

    @Test
    fun requestsGoToTheEnvironmentsBaseUrl() = runTest {
        AppEnvironment.entries.forEach { environment ->
            val request = firstRequestFor(environment)
            assertEquals(environment.baseUrl + "v1/ping", request.url.toString())
        }
    }

    @Test
    fun appInfoIsSentAsHeaders() = runTest {
        val request = firstRequestFor(AppEnvironment.Dev)

        assertEquals("ios", request.headers["platform"])
        assertEquals("2.3.0", request.headers["version"])
        assertEquals("ar", request.headers[HttpHeaders.AcceptLanguage])
    }

    @Test
    fun ourLogLevelMapsToKtors() {
        assertEquals(LogLevel.BODY, NetworkLogLevel.Body.toKtor())
        assertEquals(LogLevel.HEADERS, NetworkLogLevel.Headers.toKtor())
        assertEquals(LogLevel.NONE, NetworkLogLevel.None.toKtor())
    }
}
