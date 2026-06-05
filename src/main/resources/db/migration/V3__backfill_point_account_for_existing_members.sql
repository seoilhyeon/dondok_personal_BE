-- 기존 회원 데이터에 누락된 포인트 계좌를 0원으로 채움
-- 목적: 과거 데이터/수동 회원 삽입에 의해 point_account가 없는 회원에 대한 백필 보장
-- 멱등성: 동일 회원에 대해 중복 실행되어도 재삽입되지 않음
INSERT INTO point_account (member_id, available_balance, reserved_balance, locked_balance)
SELECT m.id, 0, 0, 0
FROM member m
ON DUPLICATE KEY UPDATE
  member_id = member_id;
