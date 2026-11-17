-- PAR-10 (Tiền 17/11/2026): lịch có nội dung riêng; mẫu chỉ là nguồn điền nhanh.
ALTER TABLE announcement_schedules ALTER COLUMN template_id DROP NOT NULL;
ALTER TABLE announcement_schedules ADD COLUMN title VARCHAR(150), ADD COLUMN body VARCHAR(4000);
UPDATE announcement_schedules s SET title = t.title, body = t.body
FROM announcement_templates t WHERE t.id = s.template_id;
ALTER TABLE announcement_schedules ADD CONSTRAINT ck_schedule_content CHECK (
    (title IS NOT NULL AND body IS NOT NULL AND length(trim(title)) > 0 AND length(trim(body)) > 0)
    OR (template_id IS NOT NULL AND title IS NULL AND body IS NULL)
);

-- Một tệp thuộc lịch HOẶC một thông báo. Mỗi lần gửi tạo metadata mới, giữ cùng file_key.
ALTER TABLE announcement_attachments ALTER COLUMN announcement_id DROP NOT NULL;
ALTER TABLE announcement_attachments ADD COLUMN schedule_id BIGINT
    REFERENCES announcement_schedules(id) ON DELETE CASCADE;
ALTER TABLE announcement_attachments ADD CONSTRAINT ck_announcement_attachment_owner
    CHECK ((announcement_id IS NOT NULL) <> (schedule_id IS NOT NULL));
CREATE INDEX idx_announcement_attachments_schedule ON announcement_attachments(schedule_id);
