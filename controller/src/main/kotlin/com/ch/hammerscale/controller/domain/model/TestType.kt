package com.ch.hammerscale.controller.domain.model

enum class TestType {
    LOAD, // 일정한 부하를 유지하는 테스트
    STRESS // 점진적으로 부하를 증가시키는 테스트
}

