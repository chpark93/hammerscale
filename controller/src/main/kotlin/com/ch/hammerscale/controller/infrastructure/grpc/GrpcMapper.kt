package com.ch.hammerscale.controller.infrastructure.grpc

import com.ch.hammerscale.controller.domain.model.TestPlan
import com.project.common.proto.TestConfig


fun TestPlan.toProto(): TestConfig {
    return TestConfig.newBuilder()
        .setTestId(this.id)
        .setTargetUrl(this.config.targetUrl)
        .setVirtualUsers(this.config.virtualUsers)
        .setDurationSeconds(this.config.durationSeconds)
        .setHttpMethod(this.config.method.name)
        .build()
}

