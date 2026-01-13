package com.ch.hammerscale.controller.domain.model

/**
 * Stress Test 설정
 * 
 * 점진적으로 부하를 증가시켜 시스템의 한계를 찾는 테스트
 */
data class StressTestConfig(
    val startUsers: Int, // 시작 가상 사용자 수
    val maxUsers: Int, // 최대 가상 사용자 수
    val stepDuration: Int, // 각 단계의 지속 시간 (초)
    val stepIncrement: Int // 각 단계마다 증가할 사용자 수
) {
    init {
        require(startUsers >= 1) { "startUsers must be at least 1. input value: $startUsers" }
        require(maxUsers > startUsers) { "maxUsers must be greater than startUsers. startUsers: $startUsers, maxUsers: $maxUsers" }
        require(stepDuration >= 1) { "stepDuration must be at least 1. input value: $stepDuration" }
        require(stepIncrement >= 1) { "stepIncrement must be at least 1. input value: $stepIncrement" }
    }
    
    /**
     * 총 단계 수 계산
     */
    fun getTotalSteps(): Int {
        return ((maxUsers - startUsers) / stepIncrement) + 1
    }
    
    /**
     * 총 테스트 시간 계산 (초)
     */
    fun getTotalDuration(): Int {
        return getTotalSteps() * stepDuration
    }
    
    /**
     * 특정 단계의 사용자 수 계산
     */
    fun getUsersAtStep(
        step: Int
    ): Int {
        return startUsers + (step * stepIncrement)
    }
}

