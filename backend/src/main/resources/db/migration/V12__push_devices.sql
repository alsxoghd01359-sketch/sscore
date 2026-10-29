-- 푸시를 받을 기기. 로그인 세션 하나에 기기(FCM 토큰) 하나가 붙는다.
-- 세션이 지워지면(로그아웃, 모든 기기 로그아웃, 탈퇴) 그 기기 토큰도 함께 지워져 알림이 가지 않는다.
CREATE TABLE push_devices (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    member_id  INT          NOT NULL,
    session_id CHAR(36)     NOT NULL,
    token      VARCHAR(512) NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_push_devices_token (token(255)),
    UNIQUE KEY uq_push_devices_session (session_id),
    KEY idx_push_devices_member (member_id),
    CONSTRAINT fk_push_devices_member FOREIGN KEY (member_id) REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_push_devices_session FOREIGN KEY (session_id) REFERENCES auth_sessions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
