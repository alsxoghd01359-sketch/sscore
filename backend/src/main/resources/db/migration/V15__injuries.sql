-- 경기별 부상·결장 명단 (외부 API는 20/21 시즌부터 준다). 승부예측에서 주전 결장 여부를 계산하는 데 쓴다.
-- type: 'Missing Fixture'(결장) 또는 'Questionable'(출전 불투명)
CREATE TABLE injuries (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    fixture_id  BIGINT       NOT NULL,
    league_id   INT          NOT NULL,
    season      INT          NOT NULL,
    team_id     INT          NOT NULL,
    player_id   BIGINT       NOT NULL,
    player_name VARCHAR(100) NOT NULL,
    type        VARCHAR(40)  NULL,
    reason      VARCHAR(100) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_injuries (fixture_id, player_id),
    KEY idx_injuries_league_season (league_id, season),
    KEY idx_injuries_team (team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
