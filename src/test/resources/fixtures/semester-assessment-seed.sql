-- Fixture test OBS-25: đoạn tạo bảng học kỳ nháp của script dữ liệu dev (scripts/dev chỉ có ở máy dev)
INSERT INTO semester_assessments (classroom_id, campus_id, school_year_id, semester_no,
                                  period_start, period_end, note, version)
SELECT c.id, c.campus_id, c.school_year_id, 1,
       DATE '2026-09-01', DATE '2027-01-15', 'Bảng học kỳ I (dữ liệu dev)', 0
FROM classrooms c
WHERE c.school_year_id = 1
  AND NOT EXISTS (
    SELECT 1 FROM semester_assessments s WHERE s.classroom_id = c.id AND s.semester_no = 1);

INSERT INTO semester_assessments (classroom_id, campus_id, school_year_id, semester_no,
                                  period_start, period_end, note, version)
SELECT c.id, c.campus_id, c.school_year_id, 2,
       DATE '2027-01-16', DATE '2027-05-31', 'Bảng học kỳ II (dữ liệu dev)', 0
FROM classrooms c
WHERE c.school_year_id = 1
  AND NOT EXISTS (
    SELECT 1 FROM semester_assessments s WHERE s.classroom_id = c.id AND s.semester_no = 2);

-- ============ 4. hết đoạn học kỳ
