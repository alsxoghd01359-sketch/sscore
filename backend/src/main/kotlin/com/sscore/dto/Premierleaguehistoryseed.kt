package com.sscore.service

import com.sscore.entity.ChampionEntity
import com.sscore.repository.ChampionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 2025/26 프리미어리그 20개 팀의 잉글랜드 1부 리그 우승 이력 시드 데이터.
 * 풋볼 리그 1부(1888~1992) + 프리미어리그(1992~) 를 leagueId=39 로 통합.
 * season = 시즌 시작 연도 (예: 1992/93 → 1992)
 */
@Service
class PremierLeagueHistorySeed(
    private val championRepository: ChampionRepository
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private data class Seed(val teamId: Int, val name: String, val seasons: List<Int>)

    private val seeds = listOf(

        // ── 맨체스터 유나이티드 (33) ── 20회 ──────────────────────
        // 풋볼리그 7 + PL 13
        Seed(33, "Manchester United", listOf(
            1907, 1910, 1951, 1955, 1956, 1964, 1966,
            1992, 1993, 1995, 1996, 1998, 1999, 2000,
            2002, 2006, 2007, 2008, 2010, 2012
        )),

        // ── 리버풀 (40) ── 20회 ────────────────────────────────────
        // 풋볼리그 18 + PL 2 (2019/20, 2024/25)
        Seed(40, "Liverpool", listOf(
            1900, 1905, 1921, 1922, 1946, 1963, 1965,
            1972, 1975, 1976, 1978, 1979, 1981, 1982,
            1983, 1985, 1987, 1989,
            2019, 2024
        )),

        // ── 아스날 (42) ── 14회 ────────────────────────────────────
        // 풋볼리그 10 + PL 4 (1997/98, 2001/02, 2003/04, 2024/25)
        Seed(42, "Arsenal", listOf(
            1930, 1932, 1933, 1934, 1937, 1947, 1952, 1970, 1988, 1990,
            1997, 2001, 2003, 2025  // 14번째: 2025/26 시즌 우승
        )),

        // ── 에버턴 (45) ── 9회 ─────────────────────────────────────
        // 풋볼리그 9 + PL 0
        Seed(45, "Everton", listOf(
            1890, 1914, 1927, 1931, 1938, 1962, 1969, 1984, 1986
        )),

        // ── 맨체스터 시티 (50) ── 10회 ─────────────────────────────
        // 풋볼리그 2 + PL 8
        Seed(50, "Manchester City", listOf(
            1936, 1967,
            2011, 2013, 2017, 2018, 2020, 2021, 2022, 2023
        )),

        // ── 첼시 (49) ── 6회 ───────────────────────────────────────
        // 풋볼리그 1 + PL 5
        Seed(49, "Chelsea", listOf(
            1954,
            2004, 2005, 2009, 2014, 2016
        )),

        // ── 선덜랜드 (746) ── 6회 ──────────────────────────────────
        // 풋볼리그 6 + PL 0
        Seed(746, "Sunderland", listOf(
            1891, 1892, 1894, 1901, 1912, 1935
        )),

        // ── 애스턴 빌라 (66) ── 7회 ────────────────────────────────
        // 풋볼리그 7 + PL 0
        Seed(66, "Aston Villa", listOf(
            1893, 1895, 1896, 1898, 1899, 1909, 1980
        )),

        // ── 뉴캐슬 유나이티드 (34) ── 4회 ──────────────────────────
        // 풋볼리그 4 + PL 0
        Seed(34, "Newcastle United", listOf(
            1904, 1906, 1908, 1926
        )),

        // ── 리즈 유나이티드 (63) ── 3회 ────────────────────────────
        // 풋볼리그 3 + PL 0
        Seed(63, "Leeds United", listOf(
            1968, 1973, 1991
        )),

        // ── 울버햄프턴 (39) ── 3회 ─────────────────────────────────
        // 풋볼리그 3 + PL 0
        Seed(39, "Wolverhampton", listOf(
            1953, 1957, 1958
        )),

        // ── 토트넘 (47) ── 2회 ─────────────────────────────────────
        // 풋볼리그 2 + PL 0
        Seed(47, "Tottenham Hotspur", listOf(
            1950, 1960
        )),

        // ── 번리 (44) ── 2회 ───────────────────────────────────────
        // 풋볼리그 2 + PL 0
        Seed(44, "Burnley", listOf(
            1920, 1959
        )),

        // ── 노팅엄 포레스트 (65) ── 1회 ────────────────────────────
        Seed(65, "Nottingham Forest", listOf(1977)),

        // ── 우승 이력 없는 팀 (0회) ─────────────────────────────────
        // 브라이튼(51), 브렌트포드(55), 크리스탈 팰리스(52),
        // 풀럼(36), 본머스(35), 웨스트햄(48) → 데이터 없음
    )

    @Transactional
    fun loadAll(): Map<String, Any> {
        var inserted = 0
        var skipped  = 0

        for (seed in seeds) {
            for (season in seed.seasons) {
                if (championRepository.existsByLeagueIdAndSeasonAndTeamId(39, season, seed.teamId)) {
                    skipped++
                } else {
                    championRepository.save(ChampionEntity(
                        leagueId = 39,
                        season   = season,
                        teamId   = seed.teamId,
                        teamName = seed.name
                    ))
                    inserted++
                }
            }
        }

        log.info("✅ [시드] 잉글랜드 1부 우승 이력 — 신규: {}건, 스킵: {}건", inserted, skipped)
        return mapOf(
            "신규_삽입"       to inserted,
            "이미_존재_스킵"   to skipped,
            "팀_수"           to seeds.filter { it.seasons.isNotEmpty() }.size
        )
    }
}