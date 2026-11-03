-- NUT-21, NUT-16: lưu mẫu thức ăn (lưu, hủy sau 24 giờ, PHT/HT duyệt) và ảnh bữa ăn trong khung giờ HT cấu hình.
-- Mỗi bản lưu mẫu thuộc một điểm trường–ngày–bữa; hủy mẫu là bước sau của cùng bản ghi (disposal_*).
CREATE TABLE food_samples (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    campus_id            BIGINT       NOT NULL REFERENCES campuses (id),
    sample_date          DATE         NOT NULL,
    session              VARCHAR(20)  NOT NULL,
    sampled_at           TIMESTAMPTZ  NOT NULL,
    recorded_by          BIGINT       REFERENCES user_accounts (id),
    status               VARCHAR(10)  NOT NULL DEFAULT 'RECORDED',
    approved_by          BIGINT       REFERENCES user_accounts (id),
    approved_at          TIMESTAMPTZ,
    disposed_at          TIMESTAMPTZ,
    disposal_recorded_by BIGINT       REFERENCES user_accounts (id),
    disposal_status      VARCHAR(10)  NOT NULL DEFAULT 'NONE',
    disposal_approved_by BIGINT       REFERENCES user_accounts (id),
    disposal_approved_at TIMESTAMPTZ,
    note                 VARCHAR(500) NOT NULL DEFAULT '',
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_food_samples_session CHECK (session IN ('MORNING', 'LUNCH', 'AFTERNOON')),
    CONSTRAINT ck_food_samples_status CHECK (status IN ('RECORDED', 'APPROVED')),
    CONSTRAINT ck_food_samples_disposal_status CHECK (disposal_status IN ('NONE', 'RECORDED', 'APPROVED')),
    CONSTRAINT ck_food_samples_disposal_time CHECK ((disposal_status = 'NONE') = (disposed_at IS NULL))
);
CREATE INDEX idx_food_samples_campus_date ON food_samples (campus_id, sample_date);
-- Hàng chờ duyệt của điểm trường (trang Bán trú hôm nay đếm theo đây)
CREATE INDEX idx_food_samples_pending ON food_samples (campus_id)
    WHERE status = 'RECORDED' OR disposal_status = 'RECORDED';

CREATE TABLE food_sample_dishes (
    sample_id BIGINT NOT NULL REFERENCES food_samples (id) ON DELETE CASCADE,
    dish_id   BIGINT NOT NULL REFERENCES dishes (id),
    PRIMARY KEY (sample_id, dish_id)
);

-- Khung giờ đăng ảnh theo bữa, chung toàn trường (HT cấu hình); chưa có dòng của bữa ⇒ chưa đăng ảnh được
CREATE TABLE meal_photo_windows (
    session    VARCHAR(20) PRIMARY KEY,
    start_time TIME        NOT NULL,
    end_time   TIME        NOT NULL,
    updated_by BIGINT      REFERENCES user_accounts (id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_meal_photo_windows_session CHECK (session IN ('MORNING', 'LUNCH', 'AFTERNOON')),
    CONSTRAINT ck_meal_photo_windows_range CHECK (start_time < end_time)
);

-- Một ảnh mỗi điểm trường–ngày–bữa; đăng lại thì thay dòng và tệp cũ. storage_key là khóa tệp trong kho ảnh (UUID).
CREATE TABLE meal_photos (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    campus_id    BIGINT       NOT NULL REFERENCES campuses (id),
    photo_date   DATE         NOT NULL,
    session      VARCHAR(20)  NOT NULL,
    storage_key  VARCHAR(64)  NOT NULL,
    content_type VARCHAR(50)  NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    uploaded_by  BIGINT       REFERENCES user_accounts (id),
    uploaded_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_meal_photos UNIQUE (campus_id, photo_date, session),
    CONSTRAINT ck_meal_photos_session CHECK (session IN ('MORNING', 'LUNCH', 'AFTERNOON')),
    CONSTRAINT ck_meal_photos_size CHECK (size_bytes > 0)
);

-- AUTH-21, AUTH-22: tài khoản bếp mặc định được ghi lưu mẫu, hủy mẫu, đăng ảnh; HT thu hồi được ở ma trận quyền.
INSERT INTO role_permissions (role, permission_code) VALUES ('KITCHEN_STAFF', 'KITCHEN_RECORD');
