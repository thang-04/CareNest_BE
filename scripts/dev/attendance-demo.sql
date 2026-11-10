-- CHỈ DÙNG TRÊN DB DEV: thử điểm danh khi năm học đang hoạt động được kích hoạt sớm (ngày bắt đầu sau hôm nay).
-- Giáo viên lớp của năm ACTIVE bắt đầu dạy từ hôm nay; mọi trẻ chưa có lớp hôm nay vào lớp đầu tiên của năm ACTIVE.
UPDATE staff_assignments s
SET valid_from = CURRENT_DATE
FROM classrooms c, school_years y
WHERE s.classroom_id = c.id AND c.school_year_id = y.id AND y.status = 'ACTIVE'
  AND s.staff_role = 'TEACHER' AND s.valid_from > CURRENT_DATE;

-- Trẻ còn ghi danh ở lớp năm khác (năm cũ đã kết thúc) ⇒ kết thúc hôm qua
UPDATE enrollments e
SET valid_to = CURRENT_DATE - 1
FROM classrooms c, school_years y
WHERE e.classroom_id = c.id AND c.school_year_id = y.id AND y.status <> 'ACTIVE'
  AND e.valid_from < CURRENT_DATE AND (e.valid_to IS NULL OR e.valid_to >= CURRENT_DATE);

INSERT INTO enrollments (child_id, classroom_id, valid_from)
SELECT ch.id,
       (SELECT c.id FROM classrooms c JOIN school_years y ON y.id = c.school_year_id
        WHERE y.status = 'ACTIVE' ORDER BY c.id LIMIT 1),
       CURRENT_DATE
FROM children ch
WHERE NOT EXISTS (SELECT 1 FROM enrollments e WHERE e.child_id = ch.id
                  AND e.valid_from <= CURRENT_DATE AND (e.valid_to IS NULL OR e.valid_to >= CURRENT_DATE));
