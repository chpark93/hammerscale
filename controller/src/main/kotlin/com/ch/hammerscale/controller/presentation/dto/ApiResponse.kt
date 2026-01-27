package com.ch.hammerscale.controller.presentation.dto

data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T? = null,
    val error: ApiError? = null
) {
    companion object {
        fun <T> success(
            data: T,
            message: String = "Success"
        ): ApiResponse<T> {
            return ApiResponse(
                success = true,
                message = message,
                data = data,
                error = null
            )
        }

        fun <T> success(
            message: String = "Success"
        ): ApiResponse<T> {
            return ApiResponse(
                success = true,
                message = message,
                data = null,
                error = null
            )
        }

        fun <T> error(
            message: String,
            code: String? = null,
            details: Map<String, Any>? = null
        ): ApiResponse<T> {
            return ApiResponse(
                success = false,
                message = message,
                data = null,
                error = ApiError(
                    code = code,
                    message = message,
                    details = details
                )
            )
        }
    }
}

data class ApiError(
    val code: String?,
    val message: String,
    val details: Map<String, Any>? = null
)
