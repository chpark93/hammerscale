package com.ch.hammerscale.controller.presentation.dto

data class TestPlanResponse(
    val id: String,
    val title: String,
    val targetUrl: String,
    val virtualUsers: Int,
    val durationSeconds: Int,
    val method: String,
    val status: String,
    val createdAt: String
)
