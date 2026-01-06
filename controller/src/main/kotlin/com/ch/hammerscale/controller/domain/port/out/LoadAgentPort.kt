package com.ch.hammerscale.controller.domain.port.out

import com.ch.hammerscale.controller.domain.model.TestPlan

interface LoadAgentPort {
    fun runTest(
        plan: TestPlan
    )

    fun stopTest(
        planId: String
    )
}

