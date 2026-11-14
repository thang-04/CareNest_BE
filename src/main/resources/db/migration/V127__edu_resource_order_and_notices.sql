-- Spec 900 (Tiền duyệt 13/11/2026): thứ tự học liệu trong mục (EDU-82) + thông báo duyệt/trả lại kho trường (EDU-84).
-- Chạy lại được: DB dev đã nhận các thay đổi này trước khi tách thành migration riêng.
ALTER TABLE edu_resource_attachments ADD COLUMN IF NOT EXISTS sort_order INT NOT NULL DEFAULT 0;
COMMENT ON COLUMN edu_resource_attachments.sort_order IS 'Thứ tự trong mục; trùng số thì xếp theo id';

-- Dòng gắn đã có: thứ tự theo id
UPDATE edu_resource_attachments a SET sort_order = o.n
FROM (SELECT id, row_number() OVER (PARTITION BY class_doc_id, activity_key ORDER BY id) - 1 AS n
      FROM edu_resource_attachments) o
WHERE o.id = a.id AND a.sort_order = 0;

-- Danh sách đủ như V121, thêm 2 loại của kho học liệu
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN (
    'CHILD_PICKED_UP', 'MEAL_COUNT_RECONFIRM', 'CHILD_WITHDRAWN',
    'GUARDIAN_PHONE_CHANGE_REQUESTED', 'GUARDIAN_PHONE_CHANGE_DECIDED',
    'CHILD_TB_CONTACT', 'CHILD_TB_CONTACT_DATE_CHANGED',
    'HEALTH_RESULT_PUBLISHED', 'HEALTH_RESULT_UPDATED', 'HEALTH_CHECK_OPENED',
    'CHILD_PLACED', 'ATTENDANCE_DAY_UNLOCKED',
    'EDU_SUBMITTED', 'EDU_STEP_APPROVED', 'EDU_RELEASED', 'EDU_RETURNED',
    'EDU_DEADLINE_EXTENDED', 'EDU_DEADLINE_NEAR',
    'EDU_RESOURCE_APPROVED', 'EDU_RESOURCE_RETURNED'));
