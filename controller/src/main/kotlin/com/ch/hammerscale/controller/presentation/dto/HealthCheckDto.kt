package com.ch.hammerscale.controller.presentation.dto

data class HealthStatus(
    val overall: String,
    val controller: String,
    val agent: String,
    val message: String
)
