-- Giờ chốt điểm danh/báo ăn theo năm học (ATT-06, P-03 để cấu hình). Mỗi lần lưu thêm một dòng:
-- giờ chốt của một ngày = dòng có effective_from <= ngày đó, lưu muộn nhất. Dòng cũ giữ làm lịch sử.
CREATE TABLE attendance_cutoffs (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_year_id BIGINT       NOT NULL,
    cutoff_time    TIME         NOT NULL,
    effective_from DATE         NOT NULL,
    note           VARCHAR(500) NOT NULL DEFAULT '',
    created_by     BIGINT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_attendance_cutoffs_school_year_id FOREIGN KEY (school_year_id) REFERENCES school_years (id),
    CONSTRAINT fk_attendance_cutoffs_created_by FOREIGN KEY (created_by) REFERENCES user_accounts (id)
);

CREATE INDEX idx_attendance_cutoffs_school_year_id ON attendance_cutoffs (school_year_id, effective_from);
