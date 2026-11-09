package org.example.project.network

/** Everything the network layer needs from the host app, supplied once at startup. */
data class ApiConfig(
    val baseUrl: String,
    val platform: String,      // "android" or "ios", sent as the `platform` header
    val appVersion: String,    // sent as the `version` header
    val languageTag: String,   // sent as Accept-Language, e.g. "en"
) {
    companion object {
        /** The only environment this study project talks to for now. */
        const val DEV_BASE_URL = "https://api-dev.ezeats.app/"
    }
}
