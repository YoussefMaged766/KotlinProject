package org.example.project.categories.presentation.mapper

import org.example.project.R
import org.example.project.categories.presentation.model.ErrorUi
import org.example.project.core.mapper.Mapper
import org.example.project.network.DataError

class ErrorUiMapper : Mapper<DataError, ErrorUi> {
    override fun map(input: DataError): ErrorUi = when (input) {
        DataError.NoInternet -> ErrorUi(R.string.error_no_internet)
        DataError.Timeout -> ErrorUi(R.string.error_timeout)
        DataError.BadResponse -> ErrorUi(R.string.error_bad_response)
        is DataError.Server -> ErrorUi(
            messageRes = R.string.error_server,
            formatArgs = listOf(input.code.toString(), input.message),
        )
    }
}
