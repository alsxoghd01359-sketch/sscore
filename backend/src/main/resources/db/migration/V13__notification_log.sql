-- 이미 보낸 알림 기록. (회원, 이벤트)당 한 번만 보내기 위한 것이라 서버를 다시 켜도 중복되지 않는다.
CREATE TABLE notification_log (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    member_id  INT          NOT NULL,
    event_key  VARCHAR(190) NOT NULL,
    sent_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_notification_log_event (member_id, event_key),
    KEY idx_notification_log_key (event_key),
    CONSTRAINT fk_notification_log_member FOREIGN KEY (member_id) REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
