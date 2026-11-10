-- OBS-19: nhận xét chung độc lập với kết quả từng trẻ, một bản cho mỗi lớp/ngày.
CREATE TABLE class_daily_comments (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    classroom_id BIGINT NOT NULL REFERENCES classrooms (id),
    campus_id    BIGINT NOT NULL REFERENCES campuses (id),
    date         DATE NOT NULL,
    text         VARCHAR(500) NOT NULL,
    recorded_by  BIGINT NOT NULL REFERENCES user_accounts (id),
    updated_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_class_daily_comments UNIQUE (classroom_id, date)
);
