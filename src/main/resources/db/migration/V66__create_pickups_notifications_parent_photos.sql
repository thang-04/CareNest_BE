-- Ảnh phụ huynh (PCK-02, đóng P-33 — user chốt 2026-10-31): phụ huynh tự tải ảnh chân dung khi đăng nhập App lần đầu
-- (bắt buộc), GV đối chiếu người đón ở màn đón trẻ. Tệp private ở S3, đọc qua API có kiểm phạm vi. Plan 2026-10-31-child-pickup.
ALTER TABLE user_accounts
    ADD COLUMN photo_key          VARCHAR(36),
    ADD COLUMN photo_content_type VARCHAR(30),
    ADD COLUMN photo_size_bytes   INTEGER,
    ADD COLUMN photo_updated_at   TIMESTAMPTZ,
    ADD CONSTRAINT uk_user_accounts_photo_key UNIQUE (photo_key),
    ADD CONSTRAINT ck_user_accounts_photo CHECK (
        (photo_key IS NULL) = (photo_content_type IS NULL)
        AND (photo_key IS NULL) = (photo_size_bytes IS NULL)
        AND (photo_key IS NULL) = (photo_updated_at IS NULL)),
    ADD CONSTRAINT ck_user_accounts_photo_type CHECK (photo_content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    ADD CONSTRAINT ck_user_accounts_photo_size CHECK (photo_size_bytes BETWEEN 1 AND 2097152);

-- Đón trẻ (PCK-01..04, ATT-10): mỗi lần GV xác minh người đón. Khớp ảnh phụ huynh hoặc phụ huynh xác nhận qua điện
-- thoại mới trả trẻ; lần không trả được vẫn lưu. Không phân biệt về sớm/đón muộn (user chốt 2026-10-31).
CREATE TABLE pickup_records (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    child_id              BIGINT       NOT NULL,
    classroom_id          BIGINT       NOT NULL,
    campus_id             BIGINT       NOT NULL,
    date                  DATE         NOT NULL,
    outcome               VARCHAR(20)  NOT NULL,
    verification          VARCHAR(20)  NOT NULL,
    -- Phụ huynh khớp ảnh (vị trí trên hồ sơ trẻ); người đón chụp lại từ hồ sơ lúc trả
    guardian_position     SMALLINT,
    pickup_person_name    VARCHAR(150) NOT NULL DEFAULT '',
    pickup_person_relationship VARCHAR(30),
    pickup_person_phone   VARCHAR(10)  NOT NULL DEFAULT '',
    handed_over_at        TIMESTAMPTZ,
    note                  VARCHAR(500) NOT NULL DEFAULT '',
    -- Số tài khoản phụ huynh đã được báo trẻ được đón (PCK-04)
    notified_parents      SMALLINT     NOT NULL DEFAULT 0,
    recorded_by           BIGINT       NOT NULL,
    recorded_at           TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_pickup_records_child_id FOREIGN KEY (child_id) REFERENCES children (id),
    CONSTRAINT fk_pickup_records_classroom_id FOREIGN KEY (classroom_id) REFERENCES classrooms (id),
    CONSTRAINT fk_pickup_records_campus_id FOREIGN KEY (campus_id) REFERENCES campuses (id),
    CONSTRAINT fk_pickup_records_recorded_by FOREIGN KEY (recorded_by) REFERENCES user_accounts (id),
    CONSTRAINT ck_pickup_records_outcome CHECK (outcome IN ('HANDED_OVER', 'NOT_HANDED_OVER')),
    CONSTRAINT ck_pickup_records_verification
        CHECK (verification IN ('PHOTO_MATCH', 'PHONE_CONFIRMED', 'PHONE_DECLINED')),
    CONSTRAINT ck_pickup_records_outcome_verification
        CHECK ((outcome = 'HANDED_OVER') = (verification IN ('PHOTO_MATCH', 'PHONE_CONFIRMED'))),
    CONSTRAINT ck_pickup_records_handed_over_at CHECK ((outcome = 'HANDED_OVER') = (handed_over_at IS NOT NULL)),
    CONSTRAINT ck_pickup_records_guardian CHECK ((verification = 'PHOTO_MATCH') = (guardian_position IS NOT NULL)),
    CONSTRAINT ck_pickup_records_relationship CHECK (pickup_person_relationship IN (
        'FATHER', 'MOTHER', 'GRANDFATHER', 'GRANDMOTHER', 'SIBLING', 'GUARDIAN', 'OTHER'))
);
-- Mỗi trẻ trả thành công tối đa một lần/ngày
CREATE UNIQUE INDEX uk_pickup_records_child_date_handed_over ON pickup_records (child_id, date)
    WHERE outcome = 'HANDED_OVER';
CREATE INDEX idx_pickup_records_classroom_date ON pickup_records (classroom_id, date);
CREATE INDEX idx_pickup_records_child_date ON pickup_records (child_id, date);

-- Thông báo trong App theo người dùng: App đọc danh sách, đánh dấu đã đọc; đẩy tới thiết bị đã đăng ký qua cổng đẩy
CREATE TABLE notifications (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    type       VARCHAR(40)  NOT NULL,
    title      VARCHAR(150) NOT NULL,
    body       VARCHAR(500) NOT NULL,
    child_id   BIGINT,
    created_at TIMESTAMPTZ  NOT NULL,
    read_at    TIMESTAMPTZ,
    CONSTRAINT fk_notifications_user_id FOREIGN KEY (user_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_notifications_child_id FOREIGN KEY (child_id) REFERENCES children (id),
    CONSTRAINT ck_notifications_type CHECK (type IN ('CHILD_PICKED_UP'))
);
CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC, id DESC);

-- Thiết bị nhận thông báo đẩy (token của dịch vụ đẩy); một token thuộc một người dùng, đăng nhập người khác thì chuyển
CREATE TABLE device_tokens (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    token      VARCHAR(512) NOT NULL,
    platform   VARCHAR(20)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_device_tokens_user_id FOREIGN KEY (user_id) REFERENCES user_accounts (id),
    CONSTRAINT uk_device_tokens_token UNIQUE (token),
    CONSTRAINT ck_device_tokens_platform CHECK (platform IN ('ANDROID', 'IOS'))
);
CREATE INDEX idx_device_tokens_user_id ON device_tokens (user_id);
