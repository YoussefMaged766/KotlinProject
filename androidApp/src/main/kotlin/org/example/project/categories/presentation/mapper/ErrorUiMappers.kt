package org.example.project.categories.presentation.mapper

import androidx.annotation.StringRes
import org.example.project.R
import org.example.project.categories.presentation.model.ErrorUi
import org.example.project.core.error.ErrorDisplay
import org.example.project.core.error.MessageKey

/**
 * Interim until phase 8's ErrorHost: this screen only has room for a full-screen error, so every other
 * display falls back to a generic one instead of leaving the screen empty.
 */
internal fun ErrorDisplay.toErrorUi(): ErrorUi = when (this) {
    is ErrorDisplay.FullScreen -> ErrorUi(
        messageRes = message.key.toStringRes(),
        serverText = message.serverText,
        technicalDetails = message.technicalDetails,
        canRetry = canRetry,
    )
    else -> ErrorUi(messageRes = MessageKey.Generic.toStringRes(), canRetry = true)
}

/** The message table: the only place on Android where error wording is chosen. */
@StringRes
internal fun MessageKey.toStringRes(): Int = when (this) {
    MessageKey.NoInternet -> R.string.error_no_internet
    MessageKey.ConnectionLost -> R.string.error_connection_lost
    MessageKey.Timeout -> R.string.error_timeout
    MessageKey.ServerError -> R.string.error_server
    MessageKey.RateLimited -> R.string.error_rate_limited
    MessageKey.NotAllowed -> R.string.error_not_allowed
    MessageKey.NotFound -> R.string.error_not_found
    MessageKey.InvalidInput -> R.string.error_invalid_input
    MessageKey.OutcomeUnknown -> R.string.error_outcome_unknown
    MessageKey.Generic -> R.string.error_generic
}
