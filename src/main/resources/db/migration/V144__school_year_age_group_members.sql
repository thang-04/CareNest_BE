-- Cần chốt 22/10/2026: mỗi năm học có danh sách nhóm tuổi riêng (thêm, xóa ở năm nào chỉ năm đó đổi); mã nhóm tuổi
-- (age_groups.id) giữ nguyên để kế hoạch giáo dục, thực đơn, sức khỏe... vẫn tham chiếu như cũ.
CREATE TABLE school_year_age_group_members (
    school_year_id BIGINT NOT NULL REFERENCES school_years (id) ON DELETE CASCADE,
    age_group_id   BIGINT NOT NULL REFERENCES age_groups (id) ON DELETE CASCADE,
    PRIMARY KEY (school_year_id, age_group_id)
);
CREATE INDEX idx_school_year_age_group_members_age_group ON school_year_age_group_members (age_group_id);

-- Giữ đúng danh sách mỗi năm đang thấy: năm đã kết thúc theo bản chụp lúc đóng năm, năm còn mở thấy cả danh mục
INSERT INTO school_year_age_group_members (school_year_id, age_group_id)
SELECT s.school_year_id, s.age_group_id
FROM school_year_age_groups s
JOIN school_years y ON y.id = s.school_year_id AND y.status = 'CLOSED'
JOIN age_groups g ON g.id = s.age_group_id;

INSERT INTO school_year_age_group_members (school_year_id, age_group_id)
SELECT y.id, g.id
FROM school_years y
CROSS JOIN age_groups g
WHERE y.status <> 'CLOSED';
