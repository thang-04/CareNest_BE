-- AUTH-15 (user chốt 21/11/2026, G94-412): giáo viên có cơ sở công tác ngay khi tạo, chưa cần lớp.
-- Lưu ở module tổ chức để phân công lớp khác cơ sở tự chuyển cơ sở công tác theo lớp.
CREATE TABLE teacher_work_campuses (
    user_id    BIGINT      PRIMARY KEY REFERENCES user_accounts (id),
    campus_id  BIGINT      NOT NULL REFERENCES campuses (id),
    updated_by BIGINT      REFERENCES user_accounts (id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_teacher_work_campuses_campus ON teacher_work_campuses (campus_id);

-- GV hiện có: theo lớp của phân công dạy chính gần nhất (không lấy dạy thay); GV chưa từng có lớp để trống
INSERT INTO teacher_work_campuses (user_id, campus_id)
SELECT DISTINCT ON (a.user_id) a.user_id, c.campus_id
FROM staff_assignments a
JOIN classrooms c ON c.id = a.classroom_id
JOIN user_accounts u ON u.id = a.user_id
WHERE a.staff_role = 'TEACHER' AND NOT a.is_substitute AND u.primary_role = 'TEACHER'
ORDER BY a.user_id, a.valid_from DESC, a.id DESC;
