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

/** One Json configuration, shared by the client and by error decoding. */
internal val appJson = Json {
    ignoreUnknownKeys = true   // the server adds fields without notice; don't crash on them
    coerceInputValues = true   // `null` arriving for a non-null field with a default -> use the default
    explicitNulls = false      // a missing nullable field decodes as null
    encodeDefaults = true      // never silently drop default-valued fields from request bodies
}

internal fun createHttpClient(engine: HttpClientEngine, config: ApiConfig): HttpClient =
    HttpClient(engine) {
        // We read the envelope on 4xx/5xx ourselves (see ApiResult.kt), so Ktor must not throw on them.
        expectSuccess = false

        install(ContentNegotiation) { json(appJson) }

        // Applied to every request: like Retrofit's baseUrl + EZeats' header interceptor.
        defaultRequest {
            url(config.baseUrl)
            header("platform", config.platform)
            header("version", config.appVersion)
            header(HttpHeaders.AcceptLanguage, config.languageTag)
        }

        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.INFO                 // try LogLevel.BODY while studying
            sanitizeHeader { it == HttpHeaders.Authorization }
        }

        // ORDER MATTERS: retry is installed BEFORE timeout so a timed-out attempt can be retried.
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 2)                     // 5xx
            retryOnException(maxRetries = 2, retryOnTimeout = true) // dropped connection, timeout
            exponentialDelay()
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 15_000
        }
    }
