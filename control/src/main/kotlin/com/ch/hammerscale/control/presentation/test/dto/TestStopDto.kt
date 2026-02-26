package com.ch.hammerscale.control.presentation.test.dto

data class StopTestResponse(
    val success: Boolean,
    val message: String,
    val testId: String,
    val previousStatus: String
)
