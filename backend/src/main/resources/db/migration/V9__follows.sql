-- 회원의 팀/선수 팔로우. 같은 대상은 한 번만 팔로우할 수 있다.
-- 대상 테이블(teams/players)은 데이터 재적재가 있을 수 있어 외래키를 걸지 않는다. 회원이 지워지면 함께 지워진다.
CREATE TABLE member_follows (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    member_id   INT         NOT NULL,
    target_type VARCHAR(10) NOT NULL,
    target_id   BIGINT      NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_member_follows_target (member_id, target_type, target_id),
    CONSTRAINT fk_member_follows_member FOREIGN KEY (member_id) REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
