package org.example.project.core.error

import org.example.project.core.events.toGlobalEvent
import org.example.project.core.result.DataError
import org.example.project.core.result.ErrorDetails
import org.example.project.core.result.TimeoutKind

/**
 * The only place that decides how an error is shown (R4). Pure: the same error and kind always give the
 * same display, and [showTechnicalDetails] never changes anything but the developers' line (R9).
 *
 * A feature that knows a [DataError.Business] code handles it first and passes the rest here.
 */
class ErrorPresenter(private val showTechnicalDetails: Boolean) {

    fun present(error: DataError, kind: RequestKind): ErrorDisplay {
        error.toGlobalEvent()?.let { return ErrorDisplay.AppAction(it) }
        return when (kind) {
            RequestKind.InitialLoad -> ErrorDisplay.FullScreen(messageFor(error), error.canRetry)
            RequestKind.Refresh -> presentRefresh(error)
            RequestKind.UserAction -> presentUserAction(error)
            RequestKind.Background -> ErrorDisplay.Silent
        }
    }

    private fun presentRefresh(error: DataError): ErrorDisplay = when (error) {
        is DataError.NoInternet -> ErrorDisplay.Silent          // the banner already says it
        is DataError.Business -> ErrorDisplay.Dialog(messageFor(error), canRetry = false)
        else -> ErrorDisplay.Toast(messageFor(error))
    }

    private fun presentUserAction(error: DataError): ErrorDisplay = when {
        error.mayHaveChangedTheServer -> ErrorDisplay.Dialog(
            message = messageFor(error, MessageKey.OutcomeUnknown),
            canRetry = error !is DataError.BadResponse,         // the server said yes: retrying would do it twice
        )
        error is DataError.NoInternet || error is DataError.RateLimited -> ErrorDisplay.Toast(messageFor(error))
        error is DataError.Validation && error.fieldErrors.isNotEmpty() -> ErrorDisplay.Inline(error.fieldErrors)
        else -> ErrorDisplay.Dialog(messageFor(error), error.canRetry)
    }

    private fun messageFor(error: DataError, key: MessageKey = error.messageKey) = ErrorMessage(
        key = key,
        // Every other server text can be technical or untranslated, so it only goes into the details.
        serverText = if (error is DataError.Business) error.details?.serverMessage else null,
        technicalDetails = if (showTechnicalDetails) error.technicalDetails() else null,
    )
}

private val DataError.messageKey: MessageKey
    get() = when (this) {
        is DataError.NoInternet -> MessageKey.NoInternet
        is DataError.ConnectionLost -> MessageKey.ConnectionLost
        is DataError.Timeout -> MessageKey.Timeout
        is DataError.Validation -> MessageKey.InvalidInput
        is DataError.Unauthorized, is DataError.Forbidden -> MessageKey.NotAllowed
        is DataError.NotFound -> MessageKey.NotFound
        is DataError.RateLimited -> MessageKey.RateLimited
        is DataError.Server, is DataError.BadResponse -> MessageKey.ServerError
        is DataError.Business, is DataError.UpgradeRequired, is DataError.Maintenance, is DataError.Unknown -> MessageKey.Generic
    }

/** Whether the same request could succeed later. */
private val DataError.canRetry: Boolean
    get() = when (this) {
        is DataError.Network, is DataError.RateLimited, is DataError.Server, is DataError.BadResponse, is DataError.Unknown -> true
        is DataError.Validation, is DataError.Unauthorized, is DataError.Forbidden, is DataError.NotFound,
        is DataError.Business, is DataError.UpgradeRequired, is DataError.Maintenance -> false
    }

/** A request that changes data was sent, and nothing tells us whether the server acted on it. */
private val DataError.mayHaveChangedTheServer: Boolean
    get() = details?.isRead != true && when (this) {
        is DataError.NoInternet -> maybeSent
        is DataError.ConnectionLost, is DataError.BadResponse -> true
        is DataError.Timeout -> kind != TimeoutKind.Connect     // never connected, so never sent
        is DataError.Server -> status == BAD_GATEWAY || status == GATEWAY_TIMEOUT   // a proxy lost the real answer
        else -> false
    }

private val ErrorDetails.isRead: Boolean get() = method == "GET" || method == "HEAD"

/** E.g. `TIMEOUT·REQUEST · GET /v1/client/categories · req 8f2c`. */
private fun DataError.technicalDetails(): String = listOfNotNull(
    technicalName,
    listOfNotNull(details?.method, details?.path).joinToString(" ").ifEmpty { null },
    details?.requestId?.let { "req $it" },
    details?.serverMessage,
    details?.cause,
).joinToString(" · ")

/** Written out because class names are shortened in release builds. */
private val DataError.technicalName: String
    get() = when (this) {
        is DataError.NoInternet -> "NO_INTERNET"
        is DataError.ConnectionLost -> "CONNECTION_LOST"
        is DataError.Timeout -> "TIMEOUT·${kind.name.uppercase()}"
        is DataError.BadResponse -> "BAD_RESPONSE"
        is DataError.Unknown -> "UNKNOWN"
        is DataError.Server -> "HTTP $status"
        is DataError.Http -> "HTTP ${details?.status ?: "?"}"
    }

private const val BAD_GATEWAY = 502
private const val GATEWAY_TIMEOUT = 504
