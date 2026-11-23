package org.example.project.core.config

/** Who is calling: sent as the `platform`, `version` and `Accept-Language` headers. */
data class AppInfo(
    val platform: String,      // "android" or "ios"
    val appVersion: String,
    val languageTag: String,   // e.g. "en"
)

/**
 * Every setting the network layer needs, supplied once at startup by the app.
 * Numbers have plan defaults; a test or a special build can override any of them.
 */
data class NetworkConfig(
    val environment: AppEnvironment,
    val appInfo: AppInfo,
    val connectTimeoutMillis: Long = 10_000,      // opening the connection
    val socketTimeoutMillis: Long = 15_000,       // max silence between data packets (stalled weak networks)
    val requestTimeoutMillis: Long = 30_000,      // the whole request, retries not included
    val maxRetries: Int = 2,                      // automatic retries for safe (GET) requests
    val slowRequestThresholdMillis: Long = 3_000, // when "Slow connection, still trying…" appears
) {
    val baseUrl: String get() = environment.baseUrl
    val logLevel: NetworkLogLevel get() = environment.logLevel
    val showTechnicalDetails: Boolean get() = environment.showTechnicalDetails
}
