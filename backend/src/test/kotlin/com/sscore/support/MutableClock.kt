package com.sscore.support

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

// 테스트에서 시간을 앞으로 돌릴 수 있는 시계
class MutableClock(
    private var now: Instant = START,
    private val zone: ZoneId = ZoneId.of("Asia/Seoul")
) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
    override fun instant(): Instant = now
    fun advance(duration: Duration) { now = now.plus(duration) }
    fun set(instant: Instant) { now = instant }

    companion object {
        val START: Instant = Instant.parse("2026-01-01T00:00:00Z")
    }
}
