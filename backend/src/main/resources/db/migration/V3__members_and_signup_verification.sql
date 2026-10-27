-- 회원: 닉네임·역할·상태 추가 (기존 users는 비어 있었다)
ALTER TABLE users
    ADD COLUMN nickname VARCHAR(12) NOT NULL,
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER',
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD UNIQUE KEY uq_users_nickname (nickname);

-- 이메일 인증을 마치기 전의 가입 요청. 인증이 끝나면 users로 옮기고 삭제한다.
CREATE TABLE signup_verifications (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    email           VARCHAR(255) NOT NULL,
    nickname        VARCHAR(12)  NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    code_hash       VARCHAR(64)  NOT NULL,
    expires_at      DATETIME(6)  NOT NULL,
    failed_attempts INT          NOT NULL DEFAULT 0,
    resend_count    INT          NOT NULL DEFAULT 0,
    last_sent_at    DATETIME(6)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_signup_verifications_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
