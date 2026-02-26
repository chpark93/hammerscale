package com.ch.hammerscale.control.presentation.health.dto

data class HealthStatus(
    val overall: String,
    val controller: String,
    val agent: String,
    val message: String
)
