package com.ch.hammerscale.controller.presentation.exception

import com.ch.hammerscale.controller.presentation.dto.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleResourceNotFoundException(
        e: ResourceNotFoundException
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("[Exception] ResourceNotFoundException: ${e.message}")
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiResponse.error(
                message = e.message ?: "Resource not found",
                code = HttpStatus.NOT_FOUND.toString(),
                details = mapOf(
                    "resourceType" to (e.resourceType ?: "Unknown"),
                    "resourceId" to (e.resourceId ?: "Unknown")
                )
            )
        )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(
        e: IllegalArgumentException
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("[Exception] IllegalArgumentException: ${e.message}")
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiResponse.error(
                message = "Invalid request",
                code = HttpStatus.BAD_REQUEST.toString()
            )
        )
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalStateException(
        e: IllegalStateException
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("[Exception] IllegalStateException: ${e.message}")
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiResponse.error(
                message = "Invalid state",
                code = HttpStatus.CONFLICT.toString()
            )
        )
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(
        e: ResponseStatusException
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("[Exception] ResponseStatusException: ${e.reason}")
        val status = e.statusCode
        return ResponseEntity.status(status).body(
            ApiResponse.error(
                message = "Request failed",
                code = status.toString()
            )
        )
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNoSuchElementException(
        e: NoSuchElementException
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("[Exception] NoSuchElementException: ${e.message}")
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiResponse.error(
                message = "Resource not found",
                code = HttpStatus.NOT_FOUND.toString()
            )
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleException(
        e: Exception
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.error("[Exception] Unexpected error: ${e.message}", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ApiResponse.error(
                message = "Internal server error",
                code = HttpStatus.INTERNAL_SERVER_ERROR.toString()
            )
        )
    }
}
