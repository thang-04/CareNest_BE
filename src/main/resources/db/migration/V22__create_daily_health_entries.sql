-- Sức khỏe trong ngày: giáo viên lớp ghi nhận khi tiếp nhận trẻ đã có mặt (mỗi lần ghi là một dòng, không sửa).
-- Giáo viên quan sát, không chẩn đoán; thân nhiệt lưu đúng giá trị nhập. symptoms, actions dạng "COUGH,TIRED".
-- request_id: FE sinh cho mỗi lần bấm lưu, gửi lặp (mạng chập chờn) không tạo dòng trùng.
CREATE TABLE daily_health_entries (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    request_id          VARCHAR(64)  NOT NULL,
    child_id            BIGINT       NOT NULL REFERENCES children (id),
    classroom_id        BIGINT       NOT NULL REFERENCES classrooms (id),
    campus_id           BIGINT       NOT NULL REFERENCES campuses (id),
    date                DATE         NOT NULL,
    temperature         NUMERIC(3, 1),
    observation         VARCHAR(20)  NOT NULL,
    symptoms            VARCHAR(100) NOT NULL DEFAULT '',
    note                VARCHAR(500) NOT NULL DEFAULT '',
    actions             VARCHAR(100) NOT NULL DEFAULT '',
    referral_place      VARCHAR(150),
    referral_time       TIME,
    referral_escort     VARCHAR(100),
    notification_status VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    recorded_by         BIGINT       NOT NULL REFERENCES user_accounts (id),
    recorded_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_daily_health_entries_request UNIQUE (recorded_by, request_id),
    CONSTRAINT ck_daily_health_entries_observation CHECK (observation IN ('NO_CONCERN', 'NOTED'))
);

CREATE INDEX idx_daily_health_entries_child_date ON daily_health_entries (child_id, date);
CREATE INDEX idx_daily_health_entries_classroom_date ON daily_health_entries (classroom_id, date);
