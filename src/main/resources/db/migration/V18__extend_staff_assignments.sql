-- Phân công GV/tổ trưởng theo năm học: chỉ THÊM cột, không đổi cột security đang dùng (valid_from/valid_to, staff_role).
-- is_homeroom: GV chủ nhiệm của lớp (mỗi lớp đúng 2 GV, 1 chủ nhiệm — FE TEACHERS_PER_CLASS).
-- school_year_id: năm học của phân công tổ trưởng (1 tổ trưởng/nhóm tuổi/năm cho cả trường); NULL với phân công cũ.
ALTER TABLE staff_assignments ADD COLUMN is_homeroom BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE staff_assignments ADD COLUMN school_year_id BIGINT;
ALTER TABLE staff_assignments ADD COLUMN assigned_by BIGINT;
ALTER TABLE staff_assignments
    ADD CONSTRAINT fk_staff_assignments_school_year_id FOREIGN KEY (school_year_id) REFERENCES school_years (id);
ALTER TABLE staff_assignments
    ADD CONSTRAINT fk_staff_assignments_assigned_by FOREIGN KEY (assigned_by) REFERENCES user_accounts (id);
ALTER TABLE staff_assignments
    ADD CONSTRAINT ck_staff_assignments_homeroom CHECK (NOT is_homeroom OR staff_role = 'TEACHER');
CREATE INDEX idx_staff_assignments_school_year_id ON staff_assignments (school_year_id);
