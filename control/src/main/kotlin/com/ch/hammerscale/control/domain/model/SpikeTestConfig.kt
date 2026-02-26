package com.ch.hammerscale.control.domain.model

/**
 * Spike Test 설정
 *
 * 갑작스러운 부하 급증/감소를 시뮬레이션 -> 시스템 탄력성 테스트
 */
data class SpikeTestConfig(
    val baseUsers: Int,
    val spikeUsers: Int,
    val spikeDuration: Int,
    val recoveryDuration: Int
) {
    init {
        require(baseUsers >= 1) { "baseUsers must be at least 1. input value: $baseUsers" }
        require(spikeUsers > baseUsers) { "spikeUsers must be greater than baseUsers. baseUsers: $baseUsers, spikeUsers: $spikeUsers" }
        require(spikeDuration >= 1) { "spikeDuration must be at least 1. input value: $spikeDuration" }
        require(recoveryDuration >= 0) { "recoveryDuration must be non-negative. input value: $recoveryDuration" }
    }

    fun getTotalDuration(): Int {
        return recoveryDuration + spikeDuration + recoveryDuration
    }

    fun getSpikeIncrement(): Int {
        return spikeUsers - baseUsers
    }
}
