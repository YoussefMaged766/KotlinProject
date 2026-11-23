package org.example.project.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.example.project.core.config.NetworkConfig
import org.example.project.core.config.NetworkLogLevel

/** One Json configuration, shared by the client and by error decoding. */
internal val appJson = Json {
    ignoreUnknownKeys = true   // the server adds fields without notice; don't crash on them
    coerceInputValues = true   // `null` arriving for a non-null field with a default -> use the default
    explicitNulls = false      // a missing nullable field decodes as null
    encodeDefaults = true      // never silently drop default-valued fields from request bodies
}

/** Every number and switch comes from [config]; nothing is hard-coded here. */
internal fun createHttpClient(engine: HttpClientEngine, config: NetworkConfig): HttpClient =
    HttpClient(engine) {
        // We read the envelope on 4xx/5xx ourselves (see SafeRequest.kt), so Ktor must not throw on them.
        expectSuccess = false

        install(ContentNegotiation) { json(appJson) }

        // Applied to every request: base URL of the environment + who is calling.
        defaultRequest {
            url(config.baseUrl)
            header("platform", config.appInfo.platform)
            header("version", config.appInfo.appVersion)
            header(HttpHeaders.AcceptLanguage, config.appInfo.languageTag)
        }

        install(Logging) {
            logger = Logger.SIMPLE
            level = config.logLevel.toKtor()              // Dev: BODY, Staging: HEADERS, Prod: NONE
            sanitizeHeader { it == HttpHeaders.Authorization }
        }

        // ORDER MATTERS: retry is installed BEFORE timeout so a timed-out attempt can be retried.
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = config.maxRetries)                     // 5xx
            retryOnException(maxRetries = config.maxRetries, retryOnTimeout = true) // dropped connection, timeout
            exponentialDelay()
        }
        install(HttpTimeout) {
            connectTimeoutMillis = config.connectTimeoutMillis
            socketTimeoutMillis = config.socketTimeoutMillis
            requestTimeoutMillis = config.requestTimeoutMillis
        }
    }

internal fun NetworkLogLevel.toKtor(): LogLevel = when (this) {
    NetworkLogLevel.Body -> LogLevel.BODY
    NetworkLogLevel.Headers -> LogLevel.HEADERS
    NetworkLogLevel.None -> LogLevel.NONE
}
