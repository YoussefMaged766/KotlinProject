package org.example.project.core.config

/**
 * Where the app talks to, and how much it reveals while doing so.
 * Each environment carries all of its settings, so adding one is a single new entry,
 * not new if/when branches spread through the code.
 */
enum class AppEnvironment(
    val baseUrl: String,
    val logLevel: NetworkLogLevel,
    /** Technical details (status, endpoint, server text) under user messages. Never in Prod. */
    val showTechnicalDetails: Boolean,
) {
    Dev(
        baseUrl = "https://api-dev.ezeats.app/",
        logLevel = NetworkLogLevel.Body,
        showTechnicalDetails = true,
    ),
    Staging(
        baseUrl = "https://api-staging.ezeats.app/",
        logLevel = NetworkLogLevel.Headers,
        showTechnicalDetails = true,
    ),
    Prod(
        baseUrl = "https://api.ezeats.app/",
        logLevel = NetworkLogLevel.None,
        showTechnicalDetails = false,
    ),
}

/** Our own log levels, so core/config stays free of Ktor types. Mapped in the network layer. */
enum class NetworkLogLevel { Body, Headers, None }
