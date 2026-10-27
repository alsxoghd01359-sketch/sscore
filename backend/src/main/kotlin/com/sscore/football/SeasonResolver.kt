package com.sscore.football

import com.sscore.repository.MatchRepository
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate

// 시즌을 코드에 박아 두지 않고 날짜로 정한다.
// 유럽 리그 시즌은 8월에 시작하므로 7월부터 새 시즌으로 본다. (예: 2026-10 → 2026 시즌, 2026-03 → 2025 시즌)
@Component
class SeasonResolver(
    private val clock: Clock,
    private val matches: MatchRepository
) {
    // 오늘 날짜 기준의 현재 시즌
    fun current(): Int {
        val today = LocalDate.now(clock)
        return if (today.monthValue >= SEASON_START_MONTH) today.year else today.year - 1
    }

    // 요청에 시즌이 없을 때 쓰는 시즌: 현재 시즌의 경기 데이터가 있으면 현재 시즌, 아직 적재 전이면 데이터가 있는 가장 최근 시즌
    fun effective(): Int {
        val current = current()
        if (matches.existsBySeason(current)) return current
        return matches.latestSeasonUpTo(current) ?: current
    }

    fun resolve(requested: Int?): Int = requested ?: effective()

    private companion object {
        const val SEASON_START_MONTH = 7
    }
}
