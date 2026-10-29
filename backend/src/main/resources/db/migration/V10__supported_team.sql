-- 회원이 고른 응원 팀 (선택). 팀 데이터 재적재가 있을 수 있어 외래키는 걸지 않는다.
ALTER TABLE users ADD COLUMN supported_team_id INT NULL;
