-- EDU-81 (Tiền chốt và duyệt 12/11/2026): học liệu chỉ thuộc kế hoạch giáo dục ngày, gắn theo ngày + mục hoạt động.
-- Xóa hết học liệu cũ gắn kế hoạch chủ đề (tệp S3 dọn riêng theo key); activity_signature không dùng nữa (luôn NULL).
DELETE FROM edu_learning_resources;
ALTER TABLE edu_learning_resources DROP CONSTRAINT ck_learning_resource_binding;
ALTER TABLE edu_learning_resources ADD CONSTRAINT ck_learning_resource_binding CHECK (
    activity_signature IS NULL
    AND (activity_key IS NULL OR activity_key ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}#(hoc|ngoai_troi|goc|chieu)$'));
