-- Điểm danh và tham gia ăn theo trẻ/ngày (ATT-01, ATT-01b: một trẻ một bản ghi mỗi ngày, lưu lặp = cập nhật).
-- meals: các bữa trẻ ăn, dạng "LUNCH,AFTERNOON"; vắng thì rỗng.
CREATE TABLE attendance_records (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    child_id      BIGINT      NOT NULL REFERENCES children (id),
    classroom_id  BIGINT      NOT NULL REFERENCES classrooms (id),
    campus_id     BIGINT      NOT NULL REFERENCES campuses (id),
    date          DATE        NOT NULL,
    status        VARCHAR(20) NOT NULL,
    meals         VARCHAR(40) NOT NULL DEFAULT '',
    recorded_by   BIGINT      NOT NULL REFERENCES user_accounts (id),
    recorded_at   TIMESTAMPTZ NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_attendance_records_child_date UNIQUE (child_id, date),
    CONSTRAINT ck_attendance_records_status CHECK (status IN ('PRESENT', 'EXCUSED', 'UNEXCUSED'))
);

CREATE INDEX idx_attendance_records_classroom_date ON attendance_records (classroom_id, date);

-- Mở lại một lớp/ngày đã khóa (HT, PHT điểm trường). Thu hồi = đặt hạn về lúc thu hồi, giữ bản ghi.
CREATE TABLE attendance_day_unlocks (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    classroom_id BIGINT       NOT NULL REFERENCES classrooms (id),
    campus_id    BIGINT       NOT NULL REFERENCES campuses (id),
    date         DATE         NOT NULL,
    valid_until  TIMESTAMPTZ  NOT NULL,
    reason       VARCHAR(300) NOT NULL,
    unlocked_by  BIGINT       NOT NULL REFERENCES user_accounts (id),
    unlocked_at  TIMESTAMPTZ  NOT NULL,
    revoked_by   BIGINT REFERENCES user_accounts (id),
    revoked_at   TIMESTAMPTZ
);

CREATE INDEX idx_attendance_day_unlocks_classroom_date ON attendance_day_unlocks (classroom_id, date);

-- Lịch sử ghi/sửa từng bản ghi điểm danh; unlock_id khi lưu trong lần mở lại.
CREATE TABLE attendance_record_history (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    record_id  BIGINT      NOT NULL REFERENCES attendance_records (id) ON DELETE CASCADE,
    action     VARCHAR(20) NOT NULL,
    status     VARCHAR(20) NOT NULL,
    meals      VARCHAR(40) NOT NULL DEFAULT '',
    user_id    BIGINT      NOT NULL REFERENCES user_accounts (id),
    at         TIMESTAMPTZ NOT NULL,
    unlock_id  BIGINT REFERENCES attendance_day_unlocks (id),
    CONSTRAINT ck_attendance_record_history_action CHECK (action IN ('RECORDED', 'UPDATED'))
);

CREATE INDEX idx_attendance_record_history_record ON attendance_record_history (record_id);
