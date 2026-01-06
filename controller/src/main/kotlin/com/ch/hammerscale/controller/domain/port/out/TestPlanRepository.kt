package com.ch.hammerscale.controller.domain.port.out

import com.ch.hammerscale.controller.domain.model.TestPlan

interface TestPlanRepository {
    fun save(
        testPlan: TestPlan
    ): TestPlan

    fun findById(
        id: String
    ): TestPlan?
}

