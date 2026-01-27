package com.ch.hammerscale.controller.presentation.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class TriggerRequest(
    val title: String? = null,
    @field:NotBlank
    val targetUrl: String,
    
    val testType: String = "LOAD",

    @field:Min(0)
    @field:Max(200_000)
    val virtualUsers: Int? = null,
    
    @field:Min(0)
    @field:Max(86400)
    val durationSeconds: Int? = null,
    
    @field:Min(0)
    val rampUpSeconds: Int? = null,

    val stressConfig: StressConfigRequest? = null,
    val spikeConfig: SpikeConfigRequest? = null,

    val method: String = "GET",
    val headers: Map<String, String>? = null,
    val queryParams: Map<String, String>? = null,
    val requestBody: String? = null
)

data class StressConfigRequest(
    @field:Min(1)
    val startUsers: Int,
    
    @field:Min(2)
    @field:Max(200_000)
    val maxUsers: Int,
    
    @field:Min(1)
    val stepDuration: Int,
    
    @field:Min(1)
    val stepIncrement: Int
)

data class SpikeConfigRequest(
    @field:Min(1)
    val baseUsers: Int,
    
    @field:Min(2)
    @field:Max(200_000)
    val spikeUsers: Int,
    
    @field:Min(1)
    val spikeDuration: Int,
    
    @field:Min(0)
    val recoveryDuration: Int
)
