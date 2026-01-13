package com.ch.hammerscale.controller.domain.model

enum class TestType {
    LOAD, // 일정한 부하를 유지하는 테스트
    STRESS, // 점진적으로 부하를 증가시키는 테스트
    SPIKE, // 갑작스러운 부하 급증/감소 테스트
    SOAK // 장시간 일정 부하 유지 -> 안정성/메모리 누수 테스트
}

