package org.example.project.network

import kotlinx.serialization.Serializable

/** The server's standard envelope. `data` is null (or a different shape) on errors. */
@Serializable
internal data class BaseApiResponse<T>(
    val code: Int = 0,
    val message: String = "",
    val data: T? = null,
)
