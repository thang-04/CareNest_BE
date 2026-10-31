-- Phiếu khám Phụ lục II là lần đo của đợt khám (HLT-08; plan 2026-10-31-health-exam-follow-ups). Nháp chưa công bố,
-- chưa tính là kết quả, cho phép thiếu chiều cao/cân nặng; kết luận là công bố, sửa là công bố lại (HLT-11, user chốt
-- 2026-10-31). exam = nội dung phiếu (JSON đã chuẩn hóa).
ALTER TABLE health_measurements
    ADD COLUMN exam         JSONB,
    ADD COLUMN exam_status  VARCHAR(20),
    ADD COLUMN concluded_at TIMESTAMPTZ,
    ADD COLUMN published_by BIGINT,
    ALTER COLUMN height_cm DROP NOT NULL,
    ALTER COLUMN weight_kg DROP NOT NULL,
    ALTER COLUMN published_at DROP NOT NULL,
    ADD CONSTRAINT fk_health_measurements_published_by FOREIGN KEY (published_by) REFERENCES user_accounts (id),
    ADD CONSTRAINT ck_health_measurements_exam_status CHECK (exam_status IN ('DRAFT', 'CONCLUDED')),
    ADD CONSTRAINT ck_health_measurements_exam CHECK ((exam IS NULL) = (exam_status IS NULL)),
    -- Chỉ nháp chưa công bố; kết quả khác luôn đủ chiều cao, cân nặng
    ADD CONSTRAINT ck_health_measurements_published
        CHECK ((exam_status IS NOT DISTINCT FROM 'DRAFT') = (published_at IS NULL)),
    ADD CONSTRAINT ck_health_measurements_values
        CHECK (exam_status IS NOT DISTINCT FROM 'DRAFT' OR (height_cm IS NOT NULL AND weight_kg IS NOT NULL)),
    ADD CONSTRAINT ck_health_measurements_concluded
        CHECK ((exam_status IS NOT DISTINCT FROM 'CONCLUDED') = (concluded_at IS NOT NULL));

-- Phiếu theo dõi sức khỏe: mở khi kết luận "theo dõi sát" (nguy cơ lao, chuyển khám chuyên khoa...); GV lớp ghi trao
-- đổi phụ huynh, kết quả chuyên khoa, đóng theo lời bác sĩ (user chốt 2026-10-31). Trạng thái, hạn tính khi đọc từ
-- dòng thời gian, không lưu.
CREATE TABLE health_follow_ups (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    child_id             BIGINT       NOT NULL,
    campus_id            BIGINT       NOT NULL,
    opened_at            TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    closed_at            TIMESTAMPTZ,
    -- null khi hệ thống tự đóng (trẻ ra trường)
    closed_by            BIGINT,
    close_reason         VARCHAR(300),
    close_outcome        VARCHAR(30),
    close_condition_text VARCHAR(100),
    CONSTRAINT fk_health_follow_ups_child_id FOREIGN KEY (child_id) REFERENCES children (id),
    CONSTRAINT fk_health_follow_ups_campus_id FOREIGN KEY (campus_id) REFERENCES campuses (id),
    CONSTRAINT fk_health_follow_ups_closed_by FOREIGN KEY (closed_by) REFERENCES user_accounts (id),
    CONSTRAINT ck_health_follow_ups_closed CHECK ((closed_at IS NULL) = (close_reason IS NULL)),
    CONSTRAINT ck_health_follow_ups_outcome
        CHECK (close_outcome IN ('DOCTOR_NORMAL', 'KNOWN_CONDITION', 'NO_SPECIALIST_RESULT')),
    CONSTRAINT ck_health_follow_ups_outcome_closed CHECK (close_outcome IS NULL OR closed_at IS NOT NULL),
    CONSTRAINT ck_health_follow_ups_condition
        CHECK ((close_outcome IS NOT DISTINCT FROM 'KNOWN_CONDITION') = (close_condition_text IS NOT NULL))
);
-- Mỗi trẻ tối đa một phiếu theo dõi đang mở
CREATE UNIQUE INDEX uk_health_follow_ups_child_open ON health_follow_ups (child_id) WHERE closed_at IS NULL;
CREATE INDEX idx_health_follow_ups_child_id ON health_follow_ups (child_id, opened_at);

-- Dòng thời gian của phiếu theo dõi; thứ tự id là thứ tự thời gian. event_date: ngày khám (OPENED, MEASUREMENT_ADDED),
-- ngày trao đổi (PARENT_CONTACTED), ngày khám chuyên khoa (EXTERNAL_RESULT).
CREATE TABLE health_follow_up_events (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    follow_up_id   BIGINT       NOT NULL,
    action         VARCHAR(30)  NOT NULL,
    -- null khi hệ thống ghi (tự đóng khi trẻ ra trường)
    user_id        BIGINT,
    created_at     TIMESTAMPTZ  NOT NULL,
    measurement_id BIGINT,
    event_date     DATE,
    -- Lần khám thêm vào phiếu theo mẫu FOLLOW_UP: có bất thường mới (mở vòng trao đổi mới) hay không
    new_finding    BOOLEAN,
    -- Trao đổi do bác sĩ thực hiện với người đi cùng tại buổi khám
    at_exam        BOOLEAN      NOT NULL DEFAULT false,
    note           VARCHAR(500),
    facility       VARCHAR(150),
    next_visit_on  DATE,
    outcome        VARCHAR(30),
    condition_text VARCHAR(100),
    auto_reason    VARCHAR(30),
    CONSTRAINT fk_health_follow_up_events_follow_up_id FOREIGN KEY (follow_up_id) REFERENCES health_follow_ups (id),
    CONSTRAINT fk_health_follow_up_events_user_id FOREIGN KEY (user_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_health_follow_up_events_measurement_id FOREIGN KEY (measurement_id)
        REFERENCES health_measurements (id),
    CONSTRAINT ck_health_follow_up_events_action
        CHECK (action IN ('OPENED', 'MEASUREMENT_ADDED', 'PARENT_CONTACTED', 'EXTERNAL_RESULT', 'CLOSED')),
    CONSTRAINT ck_health_follow_up_events_measurement
        CHECK (action NOT IN ('OPENED', 'MEASUREMENT_ADDED') OR (measurement_id IS NOT NULL AND event_date IS NOT NULL)),
    CONSTRAINT ck_health_follow_up_events_outcome
        CHECK (outcome IN ('DOCTOR_NORMAL', 'KNOWN_CONDITION', 'NO_SPECIALIST_RESULT')),
    CONSTRAINT ck_health_follow_up_events_auto CHECK (auto_reason IN ('CHILD_LEFT_SCHOOL'))
);
CREATE INDEX idx_health_follow_up_events_follow_up_id ON health_follow_up_events (follow_up_id, id);
-- Một lần khám thuộc tối đa một phiếu theo dõi
CREATE UNIQUE INDEX uk_health_follow_up_events_measurement ON health_follow_up_events (measurement_id)
    WHERE action IN ('OPENED', 'MEASUREMENT_ADDED');

-- Ảnh kết quả khám chuyên khoa (tối đa 3 ảnh mỗi lần ghi), tệp ở S3 theo storage_key; chỉ GV/BGH trong phạm vi xem
CREATE TABLE health_follow_up_images (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id     BIGINT      NOT NULL,
    position     SMALLINT    NOT NULL,
    storage_key  VARCHAR(36) NOT NULL,
    content_type VARCHAR(30) NOT NULL,
    size_bytes   INTEGER     NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_health_follow_up_images_event_id FOREIGN KEY (event_id) REFERENCES health_follow_up_events (id),
    CONSTRAINT uk_health_follow_up_images_storage_key UNIQUE (storage_key),
    CONSTRAINT uk_health_follow_up_images_event_position UNIQUE (event_id, position),
    CONSTRAINT ck_health_follow_up_images_position CHECK (position BETWEEN 0 AND 2),
    CONSTRAINT ck_health_follow_up_images_type CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT ck_health_follow_up_images_size CHECK (size_bytes BETWEEN 1 AND 1048576)
);

-- Kết luận phiếu khám ghi tiền sử ngược về khai báo sức khỏe (HLT-06, HLT-08)
ALTER TABLE child_health_declaration_history DROP CONSTRAINT ck_child_health_declaration_history_action;
ALTER TABLE child_health_declaration_history ADD CONSTRAINT ck_child_health_declaration_history_action
    CHECK (action IN ('DECLARED', 'UPDATED', 'UPDATED_FROM_FOLLOW_UP', 'UPDATED_FROM_EXAM'));
