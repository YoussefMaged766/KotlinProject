package org.example.project.categories.presentation.mapper

import org.example.project.R
import org.example.project.categories.presentation.model.ErrorUi
import org.example.project.core.result.DataError

/**
 * DataError -> the string resource (and arguments) the error section shows.
 * Interim mapping for phase 2; phase 6 replaces it with the shared ErrorPresenter + MessageKey.
 */
internal fun DataError.toErrorUi(): ErrorUi = when (this) {
    is DataError.NoInternet -> ErrorUi(R.string.error_no_internet)
    is DataError.ConnectionLost -> ErrorUi(R.string.error_connection_lost)
    is DataError.Timeout -> ErrorUi(R.string.error_timeout)
    is DataError.Server -> ErrorUi(
        messageRes = R.string.error_server,
        formatArgs = listOf(status.toString(), details?.serverMessage.orEmpty()),
    )
    is DataError.BadResponse -> ErrorUi(R.string.error_bad_response)
    is DataError.Http, is DataError.Unknown -> ErrorUi(R.string.error_generic)
}
