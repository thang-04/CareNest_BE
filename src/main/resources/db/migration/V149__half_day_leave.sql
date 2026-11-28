-- ATT-12..14 (Cần chốt 18/11/2026): nghỉ nửa buổi.
-- Đơn nghỉ: buổi nghỉ (FULL_DAY / MORNING / AFTERNOON) và công tắc ăn cho buổi còn học; nửa buổi chỉ đơn 1 ngày.
-- eats NULL = không áp dụng (đơn cả ngày, hoặc buổi còn học không có bữa nào của nhóm tuổi).
ALTER TABLE leave_requests
    ADD COLUMN part VARCHAR(10) NOT NULL DEFAULT 'FULL_DAY',
    ADD COLUMN eats BOOLEAN,
    ADD CONSTRAINT ck_leave_requests_part CHECK (part IN ('FULL_DAY', 'MORNING', 'AFTERNOON')),
    ADD CONSTRAINT ck_leave_requests_half_single_day CHECK (part = 'FULL_DAY' OR from_date = to_date);

-- Điểm danh: buổi vắng có phép theo đơn nửa buổi; trạng thái còn lại là của buổi kia
ALTER TABLE attendance_records
    ADD COLUMN absent_part VARCHAR(10),
    ADD CONSTRAINT ck_attendance_records_absent_part CHECK (absent_part IS NULL OR absent_part IN ('MORNING', 'AFTERNOON'));

ALTER TABLE attendance_record_history
    ADD COLUMN absent_part VARCHAR(10);

-- Giờ đến muộn nhất buổi chiều cho trẻ nghỉ sáng (mặc định 14:00, sau giờ chốt)
ALTER TABLE attendance_cutoffs
    ADD COLUMN latest_afternoon_arrival TIME NOT NULL DEFAULT TIME '14:00';
