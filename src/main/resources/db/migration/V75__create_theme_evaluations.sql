-- Đánh giá cuối chủ đề của lớp mẫu giáo (OBS-27, Cần chốt 04/11/2026): bảng trẻ × MT của chủ đề, hệ thống tính từ Kiến
-- thức kỹ năng hằng ngày, GV sửa ô rồi xác nhận. Chủ đề và MT đọc từ kế hoạch năm học (module kế hoạch giáo dục), không
-- khóa ngoại sang bảng của module đó: lưu id chủ đề kèm tên, ngày để bảng đã xác nhận không đổi khi kế hoạch có bản mới.
CREATE TABLE theme_evaluations (
    id             BIGSERIAL PRIMARY KEY,
    classroom_id   BIGINT        NOT NULL REFERENCES classrooms (id),
    campus_id      BIGINT        NOT NULL REFERENCES campuses (id),
    school_year_id BIGINT        NOT NULL REFERENCES school_years (id),
    theme_id       BIGINT        NOT NULL,
    theme_name     VARCHAR(255)  NOT NULL,
    start_date     DATE          NOT NULL,
    end_date       DATE          NOT NULL,
    -- Ô GV sửa tay (trước khi xác nhận) hoặc toàn bộ bảng khi đã xác nhận: [{"c":childId,"g":goalLineage,"m":true,"x":manual}]
    cells          TEXT          NOT NULL DEFAULT '[]',
    note           VARCHAR(1000),
    confirmed_by   BIGINT REFERENCES user_accounts (id),
    confirmed_at   TIMESTAMPTZ,
    updated_by     BIGINT REFERENCES user_accounts (id),
    updated_at     TIMESTAMPTZ   NOT NULL,
    version        BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_theme_evaluations_class_theme UNIQUE (classroom_id, theme_id),
    CONSTRAINT ck_theme_evaluations_dates CHECK (end_date >= start_date),
    CONSTRAINT ck_theme_evaluations_confirmed CHECK ((confirmed_at IS NULL) = (confirmed_by IS NULL))
);
