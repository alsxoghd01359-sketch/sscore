# 경기 데이터 수집 범위

외부 API(API-Football 프로 플랜)는 **하루 7,500회, 분당 300회** 호출할 수 있다. 수집은 모두 관리자(ADMIN) 계정으로 호출한다.

## 시즌별로 저장하는 것

시즌 값은 시작 연도이다 (2020 = 20/21).

| 시즌 | 일정·결과 | 라인업 | 타임라인 | 선수 기록 | 팀 통계(xG 포함) | 부상 명단 |
|---|---|---|---|---|---|---|
| 2015~2018 (15/16~18/19) | O | 수집 안 함 | X | X | X | X |
| 2019 (19/20) | O | O | X | X | X | X |
| 2020~2022 (20/21~22/23) | O | O | O | O | O (xG 없음) | O |
| 2023~ (23/24~) | O | O | O | O | O (xG 있음) | O |

- 15/16~18/19의 상세는 승부예측에 최근 경기가 더 중요하다는 판단으로 받지 않는다.
- 다른 대회(챔피언스리그, 컵대회) 경기와 배당률은 지금 수집하지 않는다. 배당률은 API가 지난 경기 값을 주지 않아 앞으로 쌓아야 한다.
- 일정 밀도·최근 폼·Elo·상대전적은 `matches` 테이블로 계산하므로 따로 수집하지 않는다.

## 저장 위치

- 일정·결과: `matches`
- 라인업·경기 요약(JSON): `match_details` (`lineups_json`, `info_json`)
- 타임라인: `match_events`, 선수 기록: `player_stats`
- 팀 통계: `match_team_stats` (경기당 홈·원정 두 줄, 없는 값은 NULL)
- 부상·결장 명단: `injuries`

## 실행

```
POST /api/admin/matches/backfill?from=2019&detailFrom=2020&maxCalls=6500   지난 시즌 상세 채우기
GET  /api/admin/matches/backfill/status                                      진행 상황
POST /api/admin/matches/backfill/cancel                                      멈추기
POST /api/admin/injuries/load?from=2020                                      부상 명단 (리그·시즌당 호출 1번)
```

- 최근 시즌부터 채우고, 이미 저장된 경기는 건너뛰므로 멈췄다가 다시 실행하면 이어서 한다.
- `maxCalls`에 닿으면 멈춘다. 하루 한도를 고려해 정한다.
- 상세는 경기당 호출 1번이다 (응답에 타임라인·통계·선수 기록·라인업이 모두 들어 있다).
