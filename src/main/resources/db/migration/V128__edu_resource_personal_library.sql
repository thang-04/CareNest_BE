-- Spec 901 (Tiền duyệt 14/11/2026): kho học liệu chỉ cá nhân (EDU-84), bỏ Ẩn (EDU-85),
-- nhãn chủ đề kèm năm học, nhãn tự thêm gắn với dòng gắn — gỡ dòng gắn là nhãn tự bỏ (EDU-87).

-- 1. Bỏ phạm vi, đề nghị lên kho trường, ẩn: học liệu thuộc người tạo (owner_id giữ nguyên)
ALTER TABLE edu_resource_items DROP CONSTRAINT IF EXISTS ck_resource_item_class;
DROP INDEX IF EXISTS ix_resource_items_scope;
DROP INDEX IF EXISTS ix_resource_items_class;
ALTER TABLE edu_resource_items
    DROP COLUMN scope,
    DROP COLUMN classroom_id,
    DROP COLUMN share_status,
    DROP COLUMN share_reason,
    DROP COLUMN share_by,
    DROP COLUMN share_at,
    DROP COLUMN hidden;

-- 2. Nhãn: attachment_id NULL = thêm tay; có giá trị = tự thêm, xóa theo dòng gắn
ALTER TABLE edu_resource_item_tags
    ADD COLUMN attachment_id BIGINT REFERENCES edu_resource_attachments(id) ON DELETE CASCADE,
    ADD COLUMN school_year_id BIGINT REFERENCES school_years(id);
ALTER TABLE edu_resource_item_tags DROP CONSTRAINT uq_resource_item_tag;
CREATE UNIQUE INDEX uq_resource_tag_manual ON edu_resource_item_tags(item_id, kind, ref_id) WHERE attachment_id IS NULL;
CREATE UNIQUE INDEX uq_resource_tag_auto ON edu_resource_item_tags(attachment_id, kind, ref_id) WHERE attachment_id IS NOT NULL;
CREATE INDEX ix_resource_item_tags_attachment ON edu_resource_item_tags(attachment_id);

-- 3. Chủ đề của mỗi dòng gắn thành nhãn tự thêm (tên từ edu_themes, năm học của lớp)
INSERT INTO edu_resource_item_tags (item_id, kind, ref_id, name, attachment_id, school_year_id)
SELECT a.item_id, 'THEME', d.theme_id, th.name, a.id, c.school_year_id
FROM edu_resource_attachments a
JOIN edu_class_docs d ON d.id = a.class_doc_id
LEFT JOIN classrooms c ON c.id = d.classroom_id
LEFT JOIN edu_themes th ON th.id = d.theme_id
WHERE d.theme_id IS NOT NULL;

-- Nhãn THEME cũ đã có nhãn tự thêm cùng chủ đề ⇒ bỏ; còn lại giữ làm nhãn tay, điền tên nếu thiếu
DELETE FROM edu_resource_item_tags t
WHERE t.attachment_id IS NULL AND t.kind = 'THEME'
  AND EXISTS (SELECT 1 FROM edu_resource_item_tags a
              WHERE a.attachment_id IS NOT NULL AND a.item_id = t.item_id AND a.kind = 'THEME' AND a.ref_id = t.ref_id);
UPDATE edu_resource_item_tags t SET name = th.name
FROM edu_themes th
WHERE t.kind = 'THEME' AND t.name IS NULL AND th.id = t.ref_id;

-- 4. Bỏ thông báo duyệt/trả lại học liệu; danh sách loại như V121
DELETE FROM notifications WHERE type IN ('EDU_RESOURCE_APPROVED', 'EDU_RESOURCE_RETURNED');
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN (
    'CHILD_PICKED_UP', 'MEAL_COUNT_RECONFIRM', 'CHILD_WITHDRAWN',
    'GUARDIAN_PHONE_CHANGE_REQUESTED', 'GUARDIAN_PHONE_CHANGE_DECIDED',
    'CHILD_TB_CONTACT', 'CHILD_TB_CONTACT_DATE_CHANGED',
    'HEALTH_RESULT_PUBLISHED', 'HEALTH_RESULT_UPDATED', 'HEALTH_CHECK_OPENED',
    'CHILD_PLACED', 'ATTENDANCE_DAY_UNLOCKED',
    'EDU_SUBMITTED', 'EDU_STEP_APPROVED', 'EDU_RELEASED', 'EDU_RETURNED',
    'EDU_DEADLINE_EXTENDED', 'EDU_DEADLINE_NEAR'));

-- 5. Bảng học liệu cũ đã chuyển hết sang kho ở V126
DROP TABLE edu_learning_resources;
