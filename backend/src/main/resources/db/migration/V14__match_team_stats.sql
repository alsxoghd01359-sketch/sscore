-- 끝난 경기의 팀별 통계. 경기당 두 줄(홈·원정)이고, 승부예측 학습 데이터로 쓴다.
-- 외부 API가 주지 않은 항목(옛 경기의 xG 등)은 NULL이다. xG(expected_goals)는 23/24 시즌부터 있다.
CREATE TABLE match_team_stats (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    fixture_id       BIGINT        NOT NULL,
    team_id          INT           NOT NULL,
    is_home          TINYINT(1)    NOT NULL,
    possession       INT           NULL,
    shots_total      INT           NULL,
    shots_on_goal    INT           NULL,
    shots_off_goal   INT           NULL,
    shots_blocked    INT           NULL,
    shots_inside_box INT           NULL,
    shots_outside_box INT          NULL,
    corners          INT           NULL,
    fouls            INT           NULL,
    offsides         INT           NULL,
    yellow_cards     INT           NULL,
    red_cards        INT           NULL,
    saves            INT           NULL,
    passes_total     INT           NULL,
    passes_accurate  INT           NULL,
    pass_pct         INT           NULL,
    expected_goals   DECIMAL(5, 2) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_match_team_stats (fixture_id, team_id),
    KEY idx_match_team_stats_team (team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
