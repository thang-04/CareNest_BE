-- AUTH-24: phân công dạy thay đánh dấu rõ (không suy từ ngày kết thúc); dạy thay chỉ cho GV và bắt buộc ngày kết thúc.
-- Dòng cũ giữ FALSE = dạy chính.
ALTER TABLE staff_assignments ADD COLUMN is_substitute BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE staff_assignments
    ADD CONSTRAINT ck_staff_assignments_substitute
        CHECK (NOT is_substitute OR (staff_role = 'TEACHER' AND valid_to IS NOT NULL));
