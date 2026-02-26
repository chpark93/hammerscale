package com.ch.hammerscale.control.domain.port.out

import com.ch.hammerscale.control.domain.model.TestPlan

interface TestPlanRepository {
    fun save(
        testPlan: TestPlan
    ): TestPlan

    fun findById(
        id: String
    ): TestPlan?
}
