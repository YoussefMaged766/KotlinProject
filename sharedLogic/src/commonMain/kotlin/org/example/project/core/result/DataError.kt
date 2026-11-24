package org.example.project.core.result

/**
 * Every way a request can fail, grouped by cause. Built only by the network layer's ErrorMapper;
 * everything above it (repositories, use cases, UI) just switches on these types.
 */
sealed interface DataError {

    /** Technical info for logs and for dev/staging screens. Never shown to users in Prod. */
    val details: ErrorDetails?

    // ---- The request never got a proper answer --------------------------------------------

    sealed interface Network : DataError

    /** Offline. With [maybeSent] the connection broke while sending, so the server may have the request. */
    data class NoInternet(val maybeSent: Boolean = false, override val details: ErrorDetails? = null) : Network

    /** The device was online but the connection dropped mid-request (reset, closed, unreachable). */
    data class ConnectionLost(override val details: ErrorDetails? = null) : Network

    /** Connect, socket (silence between packets) or whole-request limit reached. */
    data class Timeout(val kind: TimeoutKind, override val details: ErrorDetails? = null) : Network

    // ---- The server answered with an error status ------------------------------------------

    sealed interface Http : DataError

    /** 400 / 422. [fieldErrors] maps a field name to its message, for inline display. */
    data class Validation(val fieldErrors: Map<String, String>, override val details: ErrorDetails? = null) : Http

    /**
     * 401. With [hadSession] (the request carried a token) the session expired: handled app-wide.
     * Without it, e.g. a wrong password at login, it belongs to the screen that asked.
     */
    data class Unauthorized(val hadSession: Boolean, override val details: ErrorDetails? = null) : Http

    /** 403. */
    data class Forbidden(override val details: ErrorDetails? = null) : Http

    /** 404. */
    data class NotFound(override val details: ErrorDetails? = null) : Http

    /** 409 with the server's own code (e.g. "RESTAURANT_CLOSED"), for feature-specific handling. */
    data class Business(val code: String?, override val details: ErrorDetails? = null) : Http

    /** 429. [retryAfterSeconds] from the Retry-After header, when the server sent one. */
    data class RateLimited(val retryAfterSeconds: Long?, override val details: ErrorDetails? = null) : Http

    /** 426: this app version is no longer supported. Handled app-wide. */
    data class UpgradeRequired(override val details: ErrorDetails? = null) : Http

    /** 503 flagged as maintenance by the server. Handled app-wide. */
    data class Maintenance(override val details: ErrorDetails? = null) : Http

    /** Any other 5xx (and unexpected statuses), with the status kept for logs. */
    data class Server(val status: Int, override val details: ErrorDetails? = null) : Http

    // ---- Everything else ------------------------------------------------------------------

    /** A 2xx whose body didn't match our DTOs (or had no data). */
    data class BadResponse(override val details: ErrorDetails? = null) : DataError

    /** Anything not foreseen above. Reported so it can be investigated. */
    data class Unknown(override val details: ErrorDetails? = null) : DataError
}

enum class TimeoutKind { Connect, Socket, Request }

/** What a developer needs to find the failure; shown only where technical details are allowed. */
data class ErrorDetails(
    val status: Int? = null,
    val method: String? = null,
    val path: String? = null,
    val serverMessage: String? = null,
    val requestId: String? = null,
    val cause: String? = null,
)
