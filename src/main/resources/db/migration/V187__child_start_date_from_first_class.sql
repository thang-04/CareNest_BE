-- Mục 2 (user chốt 25/11/2026): ngày bắt đầu đi học = ngày vào lớp. Điền cho trẻ chưa có ngày bắt đầu mà đã từng vào lớp:
-- lấy ngày vào lớp sớm nhất; trẻ tiếp nhận lại (readmitted_on) chỉ xét các lớp của lần học hiện tại.
UPDATE children c
SET start_date = (SELECT MIN(e.valid_from)
                  FROM enrollments e
                  WHERE e.child_id = c.id
                    AND (c.readmitted_on IS NULL OR e.valid_from >= c.readmitted_on))
WHERE c.start_date IS NULL
  -- bỏ qua hồ sơ cũ vi phạm ck_children_complete (NOT VALID): UPDATE sẽ bị chặn
  AND c.gender IS NOT NULL
  AND EXISTS (SELECT 1 FROM enrollments e
              WHERE e.child_id = c.id
                AND (c.readmitted_on IS NULL OR e.valid_from >= c.readmitted_on));
