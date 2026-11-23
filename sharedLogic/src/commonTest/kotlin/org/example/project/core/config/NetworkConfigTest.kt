package org.example.project.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkConfigTest {

    private val appInfo = AppInfo(platform = "android", appVersion = "1.0", languageTag = "en")

    private fun configFor(environment: AppEnvironment) = NetworkConfig(environment = environment, appInfo = appInfo)

    @Test
    fun technicalDetailsAreHiddenOnlyInProd() {
        assertTrue(configFor(AppEnvironment.Dev).showTechnicalDetails)
        assertTrue(configFor(AppEnvironment.Staging).showTechnicalDetails)
        assertFalse(configFor(AppEnvironment.Prod).showTechnicalDetails)
    }

    @Test
    fun logLevelGetsQuieterTowardsProd() {
        assertEquals(NetworkLogLevel.Body, configFor(AppEnvironment.Dev).logLevel)
        assertEquals(NetworkLogLevel.Headers, configFor(AppEnvironment.Staging).logLevel)
        assertEquals(NetworkLogLevel.None, configFor(AppEnvironment.Prod).logLevel)
    }

    @Test
    fun eachEnvironmentHasItsOwnBaseUrlEndingWithSlash() {
        val urls = AppEnvironment.entries.map { configFor(it).baseUrl }

        assertEquals(urls.size, urls.toSet().size, "base URLs must be unique per environment")
        urls.forEach { assertTrue(it.endsWith("/"), "$it must end with / so relative paths resolve") }
    }

    @Test
    fun defaultsMatchThePlan() {
        val config = configFor(AppEnvironment.Dev)

        assertEquals(10_000, config.connectTimeoutMillis)
        assertEquals(15_000, config.socketTimeoutMillis)
        assertEquals(30_000, config.requestTimeoutMillis)
        assertEquals(2, config.maxRetries)
        assertEquals(3_000, config.slowRequestThresholdMillis)
    }
}
