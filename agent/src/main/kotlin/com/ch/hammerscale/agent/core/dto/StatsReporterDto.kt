package com.ch.hammerscale.agent.core.dto

data class BreakingPointInfo(
    val users: Int,
    val status: String,
    val tpsSaturated: Boolean
)