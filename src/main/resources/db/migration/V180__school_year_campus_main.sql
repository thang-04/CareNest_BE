-- Điểm trường chính theo từng năm học (Cần chốt 24/11/2026): đổi điểm chính ở năm sắp diễn ra không đổi năm đang
-- hoạt động. campuses.is_main giữ điểm chính của năm đang hoạt động cho các module khác đọc.
ALTER TABLE school_year_campus_members ADD COLUMN is_main BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE school_year_campus_members m
SET is_main = c.is_main
FROM campuses c
WHERE c.id = m.campus_id;

-- Mỗi năm tối đa một điểm trường chính
CREATE UNIQUE INDEX uk_year_campus_main ON school_year_campus_members (school_year_id) WHERE is_main;
