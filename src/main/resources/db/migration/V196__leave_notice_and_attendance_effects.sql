-- ATT-04, ATT-05b (user chốt 25/11/2026): báo nghỉ chiều sau giờ chốt chỉ là thông báo.
ALTER TABLE leave_requests
    ADD COLUMN notice_only BOOLEAN NOT NULL DEFAULT FALSE;

-- Lưu đúng trạng thái trước/sau khi đơn nghỉ làm thay đổi điểm danh để hủy đơn không ghi đè thao tác của giáo viên.
CREATE TABLE leave_attendance_effects (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    leave_request_id    BIGINT NOT NULL REFERENCES leave_requests (id) ON DELETE CASCADE,
    attendance_record_id BIGINT NOT NULL REFERENCES attendance_records (id) ON DELETE CASCADE,
    record_created      BOOLEAN NOT NULL,
    before_status       VARCHAR(20),
    before_meals        VARCHAR(40),
    before_absent_part  VARCHAR(10),
    before_recorded_by  BIGINT,
    before_recorded_at  TIMESTAMPTZ,
    after_recorded_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_leave_attendance_effect_request_record UNIQUE (leave_request_id, attendance_record_id),
    CONSTRAINT ck_leave_attendance_effect_snapshot CHECK (
        (record_created = TRUE AND before_status IS NULL AND before_meals IS NULL
            AND before_absent_part IS NULL AND before_recorded_by IS NULL AND before_recorded_at IS NULL)
        OR
        (record_created = FALSE AND before_status IS NOT NULL AND before_meals IS NOT NULL
            AND before_recorded_by IS NOT NULL AND before_recorded_at IS NOT NULL)
    )
);

CREATE INDEX idx_leave_attendance_effects_request ON leave_attendance_effects (leave_request_id);
