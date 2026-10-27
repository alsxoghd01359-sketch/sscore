-- 회원 탈퇴: 탈퇴를 요청한 시각. status가 WITHDRAWN인 회원은 이 시각부터 30일 동안만 복구할 수 있다.
ALTER TABLE users ADD COLUMN withdrawn_at DATETIME(6) NULL;
