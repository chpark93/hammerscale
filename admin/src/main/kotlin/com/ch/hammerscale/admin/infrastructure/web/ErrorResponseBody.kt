package com.ch.hammerscale.admin.infrastructure.web

data class ErrorResponseBody(
    val success: Boolean = false,
    val message: String,
    val error: ErrorDetail? = null
) {
    data class ErrorDetail(
        val code: String?,
        val message: String
    )
}
