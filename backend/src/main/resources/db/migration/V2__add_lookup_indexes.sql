-- 조회 경로에 쓰이는 컬럼에 인덱스를 추가한다. (기존 DB에는 FK 하나를 제외하고 인덱스가 없었다)
ALTER TABLE matches ADD UNIQUE KEY uq_matches_fixture_id (fixture_id);
CREATE INDEX idx_matches_date_league ON matches (match_date, league_id);
CREATE INDEX idx_matches_league_season ON matches (league_id, season);
CREATE INDEX idx_matches_home_team ON matches (home_team_id, season);
CREATE INDEX idx_matches_away_team ON matches (away_team_id, season);
CREATE INDEX idx_match_events_fixture ON match_events (fixture_id);
CREATE INDEX idx_player_stats_fixture ON player_stats (fixture_id);
CREATE INDEX idx_player_stats_player ON player_stats (player_id);
CREATE INDEX idx_player_season_stats_player ON player_season_stats (player_id, season);
