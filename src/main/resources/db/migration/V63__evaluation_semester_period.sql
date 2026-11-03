-- OBS-06: thêm đánh giá học kỳ (khoảng ngày theo học kỳ của năm học ở Cấu hình thời gian).
ALTER TABLE child_evaluations DROP CONSTRAINT ck_child_evaluations_period_type;
ALTER TABLE child_evaluations ADD CONSTRAINT ck_child_evaluations_period_type
    CHECK (period_type IN ('MONTH', 'SEMESTER', 'YEAR'));
