-- 알림 종류별 설정(회원당 한 행, 없으면 모두 켜짐으로 본다)과 팔로우 대상별 알림 여부
CREATE TABLE notification_settings (
    member_id    INT        NOT NULL,
    match_start  TINYINT(1) NOT NULL DEFAULT 1,
    goals_events TINYINT(1) NOT NULL DEFAULT 1,
    match_end    TINYINT(1) NOT NULL DEFAULT 1,
    lineup       TINYINT(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (member_id),
    CONSTRAINT fk_notification_settings_member FOREIGN KEY (member_id) REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE member_follows ADD COLUMN notify TINYINT(1) NOT NULL DEFAULT 1;
