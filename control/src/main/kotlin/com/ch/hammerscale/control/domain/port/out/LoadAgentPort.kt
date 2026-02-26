package com.ch.hammerscale.control.domain.port.out

import com.ch.hammerscale.control.domain.model.TestPlan

interface LoadAgentPort {
    fun runTest(
        plan: TestPlan
    )

    fun stopTest(
        planId: String
    )

    fun ping(): Boolean
}
