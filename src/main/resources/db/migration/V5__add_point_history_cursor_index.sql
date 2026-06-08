-- Replace the older member/created_at index with the keyset-pagination index.
-- Create the new member_id-prefixed index first so MySQL can keep the member_id FK indexed
-- while the older index is dropped afterward.
-- The new index matches ORDER BY created_at DESC, id DESC used by GET /api/points/history.
CREATE INDEX idx_ph_member_created_id_desc
    ON point_history (member_id, created_at DESC, id DESC);

ALTER TABLE point_history DROP INDEX idx_ph_member_created_at;
