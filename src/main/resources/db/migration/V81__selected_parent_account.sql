-- V81: select one guardian as the account recipient for each child.
ALTER TABLE child_guardians ADD COLUMN account_recipient BOOLEAN NOT NULL DEFAULT FALSE;
-- Giữ hồ sơ cũ thiếu CCCD; khôi phục đúng ràng buộc NOT VALID sau khi đánh dấu người nhận.
ALTER TABLE child_guardians DROP CONSTRAINT ck_child_guardians_citizen_id_required;
WITH choices AS (
 SELECT child_id, CASE WHEN COUNT(*) = 1 THEN MIN(id)
 WHEN COUNT(*) FILTER (WHERE linked) = 1 THEN MIN(id) FILTER (WHERE linked) END AS chosen
 FROM (SELECT g.*, EXISTS (SELECT 1 FROM guardian_child_links l WHERE l.child_id = g.child_id AND l.user_id = g.user_id) AS linked
       FROM child_guardians g) contacts GROUP BY child_id
)
UPDATE child_guardians g SET account_recipient = TRUE FROM choices c WHERE g.id = c.chosen;
CREATE UNIQUE INDEX uq_child_account_recipient ON child_guardians(child_id) WHERE account_recipient;

ALTER TABLE child_guardians ADD CONSTRAINT ck_child_guardians_citizen_id_required CHECK (citizen_id IS NOT NULL) NOT VALID;
