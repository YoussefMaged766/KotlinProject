package org.example.project.core.presentation.error

import androidx.annotation.StringRes
import org.example.project.R
import org.example.project.core.error.MessageKey

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
