package com.ch.hammerscale.controller.domain.model

/**
 * Spike Test 설정
 * 
 * 갑작스러운 부하 급증/감소를 시뮬레이션 -> 시스템 탄력성 테스트
 */
data class SpikeTestConfig(
    val baseUsers: Int, // 기본 사용자 수 (평소 트래픽)
    val spikeUsers: Int, // 급증 시 사용자 수 (피크 트래픽)
    val spikeDuration: Int, // 급증 유지 시간 (초)
    val recoveryDuration: Int // 회복 시간 (초)
) {
    init {
        require(baseUsers >= 1) { "baseUsers must be at least 1. input value: $baseUsers" }
        require(spikeUsers > baseUsers) { "spikeUsers must be greater than baseUsers. baseUsers: $baseUsers, spikeUsers: $spikeUsers" }
        require(spikeDuration >= 1) { "spikeDuration must be at least 1. input value: $spikeDuration" }
        require(recoveryDuration >= 0) { "recoveryDuration must be non-negative. input value: $recoveryDuration" }
    }
    
    /**
     * 총 테스트 시간 계산 (초)
     */
    fun getTotalDuration(): Int {
        return recoveryDuration + spikeDuration + recoveryDuration
    }
    
    /**
     * 급증할 사용자 수 계산
     */
    fun getSpikeIncrement(): Int {
        return spikeUsers - baseUsers
    }
}

