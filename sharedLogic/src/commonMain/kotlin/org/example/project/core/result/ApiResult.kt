package org.example.project.core.result

/**
 * The result contract shared by data, domain and presentation.
 * No networking imports here: domain and UI depend on this package, never on `network`.
 */
sealed interface DataError {
    data object NoInternet : DataError
    data object Timeout : DataError
    data object BadResponse : DataError                              // body didn't match our DTOs
    data class Server(val code: Int, val message: String) : DataError // the server's own error envelope
}

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(val error: DataError) : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(data))
    is ApiResult.Failure -> this
}
