-- ============================================================
--  SScore DB 스키마 v5  (모든 FK 완전 연결)
--  LeagueEntity 추가로 leagues 테이블 JPA 관리 가능
-- ============================================================

CREATE DATABASE IF NOT EXISTS sscore_db
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
USE sscore_db;

-- ────────────────────────────────────────────────────────────
-- 1. 리그  (LeagueEntity ✓)
-- ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS leagues (
    league_id   INT          PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    country     VARCHAR(50),
    logo_url    VARCHAR(255),
    league_type VARCHAR(20),
    FULLTEXT KEY ft_leagues_name (name)
);

-- ────────────────────────────────────────────────────────────
-- 2. 팀  (TeamEntity ✓)
-- ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS teams (
    team_id  INT          PRIMARY KEY,
    name     VARCHAR(100) NOT NULL,
    country  VARCHAR(50),
    logo_url VARCHAR(255),
    FULLTEXT KEY ft_teams_name (name)
);

-- ────────────────────────────────────────────────────────────
-- 3. 선수  (PlayerEntity ✓)
-- ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS players (
    player_id   BIGINT       PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    nationality VARCHAR(50),
    photo_url   VARCHAR(255),
    position    VARCHAR(10),
    age         INT,
    team_id     INT,
    FOREIGN KEY (team_id) REFERENCES teams(team_id) ON DELETE SET NULL,
    FULLTEXT KEY ft_players_name (name)
);

-- ────────────────────────────────────────────────────────────
-- 4. 경기  (MatchEntity ✓)
-- ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS matches (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    fixture_id   BIGINT       NOT NULL UNIQUE,
    league_id    INT          NOT NULL,
    season       INT          NOT NULL,
    home_team    VARCHAR(100) NOT NULL,
    away_team    VARCHAR(100) NOT NULL,
    home_team_id INT,
    away_team_id INT,
    match_date   VARCHAR(20)  NOT NULL,
    match_time   VARCHAR(50)  NOT NULL,
    score        VARCHAR(20)  NOT NULL,
    match_round  VARCHAR(50),
    stadium      VARCHAR(200),
    referee      VARCHAR(100),
    FOREIGN KEY (league_id)    REFERENCES leagues(league_id) ON DELETE RESTRICT,
    FOREIGN KEY (home_team_id) REFERENCES teams(team_id)    ON DELETE SET NULL,
    FOREIGN KEY (away_team_id) REFERENCES teams(team_id)    ON DELETE SET NULL,
    KEY idx_matches_date       (match_date),
    KEY idx_matches_league_ssn (league_id, season),
    KEY idx_matches_home       (home_team_id),
    KEY idx_matches_away       (away_team_id)
);

-- ────────────────────────────────────────────────────────────
-- 5. 경기 이벤트  (MatchEventEntity ✓)
-- ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS match_events (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    fixture_id         BIGINT       NOT NULL,
    time               INT,
    extra_time         INT,
    team_id            INT,
    team_name          VARCHAR(100),
    type               VARCHAR(50),
    detail             VARCHAR(100),
    player_name        VARCHAR(100),
    assist_player_name VARCHAR(100),
    FOREIGN KEY (fixture_id) REFERENCES matches(fixture_id) ON DELETE CASCADE,
    FOREIGN KEY (team_id)    REFERENCES teams(team_id)      ON DELETE SET NULL,
    KEY idx_events_fixture (fixture_id)
);

-- ────────────────────────────────────────────────────────────
-- 6. 선수 시즌별 누적 스탯  (PlayerSeasonStatEntity ✓)
--    team_name: 이적 이력 보존 목적으로 FK 없이 문자열로 저장
-- ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS player_season_stats (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_id      BIGINT       NOT NULL,
    season         INT          NOT NULL,
    team_name      VARCHAR(255) NOT NULL,
    league_name    VARCHAR(255) NOT NULL,
    appearances    INT NOT NULL DEFAULT 0,
    goals          INT NOT NULL DEFAULT 0,
    assists        INT NOT NULL DEFAULT 0,
    minutes_played INT NOT NULL DEFAULT 0,
    rating         DOUBLE,
    yellow_cards   INT NOT NULL DEFAULT 0,
    red_cards      INT NOT NULL DEFAULT 0,
    FOREIGN KEY (player_id) REFERENCES players(player_id) ON DELETE CASCADE,
    UNIQUE KEY uq_player_ssn_team_league (player_id, season, team_name, league_name),
    KEY idx_pss_player      (player_id),
    KEY idx_pss_season_team (season, team_name)
);

-- ────────────────────────────────────────────────────────────
-- 7. 선수 경기별 상세 스탯  (PlayerStatEntity ✓)
-- ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS player_stats (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    fixture_id                  BIGINT NOT NULL,
    team_id                     INT,
    player_id                   BIGINT,
    player_name                 VARCHAR(100),
    rating                      DOUBLE,
    position                    VARCHAR(10),
    minutes_played              INT,            -- Entity: Int? = null
    goals                       INT NOT NULL DEFAULT 0,
    assists                     INT NOT NULL DEFAULT 0,
    passes_total                INT NOT NULL DEFAULT 0,
    passes_accuracy             INT NOT NULL DEFAULT 0,
    passes_key                  INT NOT NULL DEFAULT 0,
    shots_total                 INT NOT NULL DEFAULT 0,
    shots_on_target             INT NOT NULL DEFAULT 0,
    shots_off_target            INT NOT NULL DEFAULT 0,
    ball_touches                INT NOT NULL DEFAULT 0,
    turnovers                   INT NOT NULL DEFAULT 0,
    tackles_total               INT NOT NULL DEFAULT 0,
    tackles_blocks              INT NOT NULL DEFAULT 0,
    tackles_interceptions       INT NOT NULL DEFAULT 0,
    duels_total                 INT NOT NULL DEFAULT 0,
    duels_won                   INT NOT NULL DEFAULT 0,
    fouls_drawn                 INT NOT NULL DEFAULT 0,
    fouls_committed             INT NOT NULL DEFAULT 0,
    goalkeeper_saves            INT NOT NULL DEFAULT 0,
    goalkeeper_conceded         INT NOT NULL DEFAULT 0,
    goalkeeper_saves_inside_box INT NOT NULL DEFAULT 0,
    goalkeeper_punches          INT NOT NULL DEFAULT 0,
    FOREIGN KEY (fixture_id) REFERENCES matches(fixture_id) ON DELETE CASCADE,
    FOREIGN KEY (team_id)    REFERENCES teams(team_id)      ON DELETE SET NULL,
    FOREIGN KEY (player_id)  REFERENCES players(player_id)  ON DELETE SET NULL,
    KEY idx_ps_fixture (fixture_id),
    KEY idx_ps_player  (player_id)
);

-- ────────────────────────────────────────────────────────────
-- 8. 리그 우승 기록  (ChampionEntity ✓)
-- ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS champions (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    league_id INT          NOT NULL,
    season    INT          NOT NULL,
    team_id   INT          NOT NULL,
    team_name VARCHAR(100) NOT NULL,
    country   VARCHAR(50),
    FOREIGN KEY (league_id) REFERENCES leagues(league_id) ON DELETE RESTRICT,
    FOREIGN KEY (team_id)   REFERENCES teams(team_id)     ON DELETE CASCADE,
    UNIQUE KEY uq_league_season (league_id, season),
    KEY idx_champ_team (team_id)
);

-- ────────────────────────────────────────────────────────────
-- 9. 사용자  (Entity 없음)
-- ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    user_id    INT AUTO_INCREMENT PRIMARY KEY,
    email      VARCHAR(100) UNIQUE NOT NULL,
    password   VARCHAR(255) NOT NULL,
    nickname   VARCHAR(50),
    fcm_token  VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ────────────────────────────────────────────────────────────
-- 10. 팀 팔로우  (Entity 없음)
-- ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS follows (
    follow_id  INT AUTO_INCREMENT PRIMARY KEY,
    user_id    INT NOT NULL,
    team_id    INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (team_id) REFERENCES teams(team_id) ON DELETE CASCADE,
    UNIQUE KEY uq_follow (user_id, team_id)
);

-- ────────────────────────────────────────────────────────────
-- 11. 이적 기록  (Entity 없음)
-- ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS transfers (
    transfer_id   INT AUTO_INCREMENT PRIMARY KEY,
    player_id     BIGINT,
    player_name   VARCHAR(100),
    from_team_id  INT,
    to_team_id    INT,
    transfer_date DATE,
    market_value  VARCHAR(50),
    transfer_type VARCHAR(50),
    FOREIGN KEY (player_id)    REFERENCES players(player_id) ON DELETE SET NULL,
    FOREIGN KEY (from_team_id) REFERENCES teams(team_id)     ON DELETE SET NULL,
    FOREIGN KEY (to_team_id)   REFERENCES teams(team_id)     ON DELETE SET NULL,
    KEY idx_transfers_player (player_id)
);

-- ============================================================
--  빅5 리그 기본 데이터 (leagues 테이블 초기값)
--  matches, champions FK 오류 방지를 위해 먼저 삽입
-- ============================================================
INSERT IGNORE INTO leagues (league_id, name, country, league_type) VALUES
    (39,  'Premier League',   'England', 'League'),
    (140, 'La Liga',          'Spain',   'League'),
    (78,  'Bundesliga',       'Germany', 'League'),
    (135, 'Serie A',          'Italy',   'League'),
    (61,  'Ligue 1',          'France',  'League');