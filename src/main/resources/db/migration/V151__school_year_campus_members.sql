-- Cần chốt 22/10/2026: mỗi năm học có danh sách điểm trường riêng (thêm, xóa ở năm nào chỉ năm đó đổi), như nhóm tuổi
-- (V148). Mã điểm trường (campuses.id) giữ nguyên để lớp, phòng, nhân sự, suất ăn... vẫn tham chiếu như cũ.
CREATE TABLE school_year_campus_members (
    school_year_id BIGINT NOT NULL REFERENCES school_years (id) ON DELETE CASCADE,
    campus_id      BIGINT NOT NULL REFERENCES campuses (id) ON DELETE CASCADE,
    PRIMARY KEY (school_year_id, campus_id)
);
CREATE INDEX idx_school_year_campus_members_campus ON school_year_campus_members (campus_id);

-- Giữ đúng danh sách mỗi năm đang thấy: năm đã kết thúc theo bản chụp lúc đóng năm, năm còn mở thấy cả danh mục
INSERT INTO school_year_campus_members (school_year_id, campus_id)
SELECT s.school_year_id, s.campus_id
FROM school_year_campuses s
JOIN school_years y ON y.id = s.school_year_id AND y.status = 'CLOSED'
JOIN campuses c ON c.id = s.campus_id;

INSERT INTO school_year_campus_members (school_year_id, campus_id)
SELECT y.id, c.id
FROM school_years y
CROSS JOIN campuses c
WHERE y.status <> 'CLOSED';
