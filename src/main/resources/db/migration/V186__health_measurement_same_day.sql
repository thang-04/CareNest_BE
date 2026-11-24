-- HLT-01, HLT-08 (user chốt 2026-11-24, mục 14 tab Test, Q3=A): ngày trẻ đã có số đo kỳ cân đo vẫn lưu được phiếu khám
-- và ngược lại. Mỗi trẻ vẫn tối đa một số đo kỳ cân đo (exam_status null) và một phiếu khám trong một ngày.
ALTER TABLE health_measurements DROP CONSTRAINT uk_health_measurements_child_date;

CREATE UNIQUE INDEX uk_health_measurements_child_date_round ON health_measurements (child_id, date)
    WHERE exam_status IS NULL;
CREATE UNIQUE INDEX uk_health_measurements_child_date_exam ON health_measurements (child_id, date)
    WHERE exam_status IS NOT NULL;
