-- Point History Cursor Pagination — Query Plan Check
-- Purpose:
--   - Verify /api/points/history keyset pagination query plan in one place.
--   - Confirm ordering/predicate alignment with cursor (created_at + id descending).
--   - Confirm query is bounded by LIMIT (for stable cursor-based infinite scroll).
--
-- How to run:
--   1) Connect to MySQL-compatible local/dev DB after migrations.
--   2) Replace placeholders below with real values.
--   3) Compare EXPLAIN trees between first page and next-page query.
--      and ensure only expected key/index access path is used.
--
-- Placeholders:
--   - :member_uuid            : 조회 대상 member.uuid
--   - :cursor_created_at      : next_cursor에서 분해한 created_at (yyyy-MM-dd HH:mm:ss.SSSSSS)
--   - :cursor_point_history_id : next_cursor에서 분해한 point_history.id
--
-- Expected:
--   - ORDER BY remains: point_history.created_at DESC, point_history.id DESC
--   - Next-page predicate uses lexicographic keyset condition.
--   - LIMIT remains 21 (20개 조회 + 다음 페이지 유무 판별 기준)

SET @member_uuid := '00000000-0000-0000-0000-000000000000';

-- First page: cursor 미사용
EXPLAIN FORMAT=TREE
SELECT
    ph.id,
    ph.amount,
    ph.available_after,
    ph.transaction_type,
    ph.reference_type,
    ph.reference_id,
    ph.created_at
FROM point_history ph
JOIN member m ON m.id = ph.member_id
WHERE m.uuid = @member_uuid
ORDER BY ph.created_at DESC, ph.id DESC
LIMIT 21;

-- Next page: next_cursor 기반 keyset cursor
-- next_cursor = ${created_at}_${id}
SET @cursor_created_at := TIMESTAMP '2026-06-08 12:00:00.000000';
SET @cursor_point_history_id := 1000000;

EXPLAIN FORMAT=TREE
SELECT
    ph.id,
    ph.amount,
    ph.available_after,
    ph.transaction_type,
    ph.reference_type,
    ph.reference_id,
    ph.created_at
FROM point_history ph
JOIN member m ON m.id = ph.member_id
WHERE m.uuid = @member_uuid
  AND (
    ph.created_at < @cursor_created_at
    OR (ph.created_at = @cursor_created_at AND ph.id < @cursor_point_history_id)
  )
ORDER BY ph.created_at DESC, ph.id DESC
LIMIT 21;
