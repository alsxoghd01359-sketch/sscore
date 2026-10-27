-- 비밀번호 재설정 요청: 가입 인증(signup_verifications)과 같은 코드 규칙을 쓴다.
CREATE TABLE password_resets (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    email           VARCHAR(255) NOT NULL,
    code_hash       VARCHAR(64)  NOT NULL,
    expires_at      DATETIME(6)  NOT NULL,
    failed_attempts INT          NOT NULL DEFAULT 0,
    resend_count    INT          NOT NULL DEFAULT 0,
    last_sent_at    DATETIME(6)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_password_resets_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
