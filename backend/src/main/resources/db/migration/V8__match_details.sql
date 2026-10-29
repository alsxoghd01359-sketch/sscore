-- 끝난 경기의 확정본. 경기가 끝나면 타임라인·통계·평점(info)과 라인업을 JSON으로 한 번 저장하고,
-- 이후 조회는 외부 API를 부르지 않고 이 행에서 준다. info/lineups는 각각 준비되는 대로 채워진다.
CREATE TABLE match_details (
    fixture_id   BIGINT      NOT NULL,
    info_json    MEDIUMTEXT  NULL,
    lineups_json MEDIUMTEXT  NULL,
    finalized_at DATETIME(6) NOT NULL,
    PRIMARY KEY (fixture_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
