-- AUTH-16, SCH-04 (Cần chốt 10/11/2026): phân công dạy thay ghi rõ dạy thay cho giáo viên đứng lớp nào.
-- Dòng dạy thay cũ để trống; chỉ phân công dạy thay mới bắt buộc (kiểm ở service).
ALTER TABLE staff_assignments ADD COLUMN replaced_user_id BIGINT REFERENCES user_accounts (id);
ALTER TABLE staff_assignments
    ADD CONSTRAINT ck_staff_assignments_replaced
        CHECK (replaced_user_id IS NULL OR (is_substitute AND replaced_user_id <> user_id));
