-- 로그인 세션: 기기(로그인) 하나가 한 행이다. 액세스 토큰의 sid가 이 행을 가리킨다.
-- 행을 지우면 그 기기의 액세스 토큰과 갱신 토큰이 즉시 무효가 된다.
CREATE TABLE auth_sessions (
    id                 CHAR(36)    NOT NULL,
    member_id          INT         NOT NULL,
    refresh_hash       CHAR(64)    NOT NULL,
    refresh_expires_at DATETIME(6) NOT NULL,
    created_at         DATETIME(6) NOT NULL,
    last_used_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_auth_sessions_refresh (refresh_hash),
    KEY idx_auth_sessions_member (member_id),
    CONSTRAINT fk_auth_sessions_member FOREIGN KEY (member_id) REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
