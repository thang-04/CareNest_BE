-- PCK-04 (Cần chốt 27/11/2026): giờ trả ghi theo giờ máy chủ; sửa trong ngày phải có lý do và lưu lịch sử.
CREATE TABLE pickup_time_changes (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pickup_record_id    BIGINT       NOT NULL,
    old_handed_over_at  TIMESTAMPTZ  NOT NULL,
    new_handed_over_at  TIMESTAMPTZ  NOT NULL,
    reason              VARCHAR(500) NOT NULL,
    changed_by          BIGINT       NOT NULL,
    changed_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_pickup_time_changes_record FOREIGN KEY (pickup_record_id) REFERENCES pickup_records (id),
    CONSTRAINT fk_pickup_time_changes_user FOREIGN KEY (changed_by) REFERENCES user_accounts (id)
);
CREATE INDEX idx_pickup_time_changes_record ON pickup_time_changes (pickup_record_id);
