package org.example.project.core.error

import org.example.project.core.events.GlobalEvent

/** What the user was doing when the request failed. The same error deserves a different reaction in each. */
enum class RequestKind {
    /** The screen has nothing to show yet. */
    InitialLoad,

    /** The screen already shows data and is updating it. */
    Refresh,

    /** The user pressed something and is waiting for the result. */
    UserAction,

    /** Nobody asked for it, so nobody should be interrupted. */
    Background,
}

/** Shared code picks the key; each platform turns it into translated text. The same in every environment. */
enum class MessageKey {
    NoInternet,
    ConnectionLost,
    Timeout,
    ServerError,
    RateLimited,
    NotAllowed,
    NotFound,
    InvalidInput,

    /** A change was sent but its result is unknown: the user should check before doing it again (R5). */
    OutcomeUnknown,
    Generic,
}

/**
 * @param serverText shown instead of [key]'s text. Only for business errors, whose text is meant for users.
 * @param technicalDetails a line for developers under the message; always null where the environment hides it.
 */
data class ErrorMessage(
    val key: MessageKey,
    val serverText: String? = null,
    val technicalDetails: String? = null,
)

/** How to show an error. Each platform renders these in one place, so features never draw their own error UI. */
sealed interface ErrorDisplay {
    data class FullScreen(val message: ErrorMessage, val canRetry: Boolean) : ErrorDisplay
    data class Toast(val message: ErrorMessage, val canRetry: Boolean) : ErrorDisplay
    data class Dialog(val message: ErrorMessage, val canRetry: Boolean) : ErrorDisplay

    /** Field name to the server's message for it, shown under that field. Never empty. */
    data class Inline(val fieldErrors: Map<String, String>) : ErrorDisplay

    /** Nothing to show: the offline banner covers it, or nobody is waiting. */
    data object Silent : ErrorDisplay

    /** The app root already got [event] from the GlobalEventBus and reacts to it; the screen does not. */
    data class AppAction(val event: GlobalEvent) : ErrorDisplay
}
