package com.ch.hammerscale.auth.infrastructure.security

data class UnauthorizedBody(
    val success: Boolean = false,
    val message: String,
    val error: ErrorDetail? = null
) {
    data class ErrorDetail(
        val code: String?,
        val message: String
    )
}
