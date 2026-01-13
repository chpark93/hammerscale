package com.ch.hammerscale.controller.infrastructure.grpc

import com.ch.hammerscale.controller.domain.model.TestPlan
import com.ch.hammerscale.controller.domain.model.TestType
import com.project.common.proto.StressTestConfig
import com.project.common.proto.TestConfig


fun TestPlan.toProto(): TestConfig {
    val builder = TestConfig.newBuilder()
        .setTestId(this.id)
        .setTargetUrl(this.config.targetUrl)
        .setHttpMethod(this.config.method.name)
        .putAllHeaders(this.config.headers)
        .putAllQueryParams(this.config.queryParams)
        .setTestType(this.config.testType.name)
    
    this.config.requestBody?.let {
        builder.setRequestBody(it)
    }
    
    when (this.config.testType) {
        TestType.LOAD -> {
            builder
                .setVirtualUsers(this.config.virtualUsers)
                .setDurationSeconds(this.config.durationSeconds)
                .setRampUpSeconds(this.config.rampUpSeconds)
        }
        TestType.STRESS -> {
            val stressConfig = this.config.stressTestConfig!!
            builder.setStressTestConfig(
                StressTestConfig.newBuilder()
                    .setStartUsers(stressConfig.startUsers)
                    .setMaxUsers(stressConfig.maxUsers)
                    .setStepDuration(stressConfig.stepDuration)
                    .setStepIncrement(stressConfig.stepIncrement)
                    .build()
            )
        }
    }
    
    return builder.build()
}

