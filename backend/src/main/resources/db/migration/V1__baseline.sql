-- Baseline: schema of the existing local DB (created by Hibernate ddl-auto=update).
-- Existing DBs are baselined at V1 and skip this file; new DBs create the schema from it.

CREATE TABLE `leagues` (
  `league_id` int NOT NULL,
  `country` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `league_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `logo_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`league_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `teams` (
  `team_id` int NOT NULL,
  `country` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `logo_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`team_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `champions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `country` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `league_id` int NOT NULL,
  `season` int NOT NULL,
  `team_id` int NOT NULL,
  `team_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKqrsciowax2j37av2ntol4rvqf` (`league_id`,`season`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `match_events` (
  `extra_time` int DEFAULT NULL,
  `team_id` int DEFAULT NULL,
  `time` int DEFAULT NULL,
  `fixture_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `assist_player_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `detail` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `player_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `team_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `matches` (
  `away_team_id` int DEFAULT NULL,
  `home_team_id` int DEFAULT NULL,
  `league_id` int DEFAULT NULL,
  `season` int DEFAULT NULL,
  `fixture_id` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `away_team` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `home_team` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `match_date` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `match_round` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `match_time` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `score` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `stadium` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `referee` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `player_season_stats` (
  `appearances` int NOT NULL,
  `assists` int NOT NULL,
  `goals` int NOT NULL,
  `minutes_played` int NOT NULL,
  `rating` double DEFAULT NULL,
  `red_cards` int NOT NULL,
  `season` int NOT NULL,
  `yellow_cards` int NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `player_id` bigint NOT NULL,
  `league_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `team_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `player_stats` (
  `assists` int DEFAULT NULL,
  `ball_touches` int DEFAULT NULL,
  `duels_total` int DEFAULT NULL,
  `duels_won` int DEFAULT NULL,
  `fouls_committed` int DEFAULT NULL,
  `fouls_drawn` int DEFAULT NULL,
  `goalkeeper_conceded` int DEFAULT NULL,
  `goalkeeper_punches` int DEFAULT NULL,
  `goalkeeper_saves` int DEFAULT NULL,
  `goalkeeper_saves_inside_box` int DEFAULT NULL,
  `goals` int DEFAULT NULL,
  `minutes_played` int DEFAULT NULL,
  `passes_accuracy` int DEFAULT NULL,
  `passes_key` int DEFAULT NULL,
  `passes_total` int DEFAULT NULL,
  `rating` double DEFAULT NULL,
  `shots_off_target` int DEFAULT NULL,
  `shots_on_target` int DEFAULT NULL,
  `shots_total` int DEFAULT NULL,
  `tackles_blocks` int DEFAULT NULL,
  `tackles_interceptions` int DEFAULT NULL,
  `tackles_total` int DEFAULT NULL,
  `team_id` int DEFAULT NULL,
  `turnovers` int DEFAULT NULL,
  `fixture_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `player_id` bigint DEFAULT NULL,
  `player_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `position` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `xg` double DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `players` (
  `team_id` int DEFAULT NULL,
  `player_id` bigint NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `nationality` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `photo_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `position` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `age` int DEFAULT NULL,
  PRIMARY KEY (`player_id`),
  KEY `FK5nglidr00c4dyybl171v6kask` (`team_id`),
  CONSTRAINT `FK5nglidr00c4dyybl171v6kask` FOREIGN KEY (`team_id`) REFERENCES `teams` (`team_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `users` (
  `user_id` int NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
