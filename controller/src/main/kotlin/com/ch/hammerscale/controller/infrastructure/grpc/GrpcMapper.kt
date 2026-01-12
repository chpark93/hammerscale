package com.ch.hammerscale.controller.infrastructure.grpc

import com.ch.hammerscale.controller.domain.model.TestPlan
import com.project.common.proto.TestConfig


fun TestPlan.toProto(): TestConfig {
    val builder = TestConfig.newBuilder()
        .setTestId(this.id)
        .setTargetUrl(this.config.targetUrl)
        .setVirtualUsers(this.config.virtualUsers)
        .setDurationSeconds(this.config.durationSeconds)
        .setHttpMethod(this.config.method.name)
        .putAllHeaders(this.config.headers)
        .putAllQueryParams(this.config.queryParams)
        .setRampUpSeconds(this.config.rampUpSeconds)
    
    // requestBody가 null이 아닐 때만 설정
    this.config.requestBody?.let { builder.setRequestBody(it) }
    
    return builder.build()
}

