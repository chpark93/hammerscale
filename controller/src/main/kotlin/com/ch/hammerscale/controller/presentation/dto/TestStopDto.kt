package com.ch.hammerscale.controller.presentation.dto

data class StopTestResponse(
    val success: Boolean,
    val message: String,
    val testId: String,
    val previousStatus: String
)
