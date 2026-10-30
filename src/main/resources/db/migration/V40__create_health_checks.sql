-- Đợt khám sức khỏe (EXAM, bác sĩ khám, thường 1 lần/năm học) và kỳ cân đo (MEASUREMENT, khoảng 3 tháng/lần) theo điểm trường
-- (HLT-01, HLT-08 user chốt 2026-10-31; plan 2026-10-31-health-check-rounds). Đợt khám cũng là một kỳ cân đo.
CREATE TABLE health_check_campaigns (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kind         VARCHAR(20)  NOT NULL,
    campus_id    BIGINT       NOT NULL,
    name         VARCHAR(150) NOT NULL,
    start_date   DATE         NOT NULL,
    end_date     DATE         NOT NULL,
    examiner     VARCHAR(150) NOT NULL DEFAULT '',
    note         VARCHAR(500) NOT NULL DEFAULT '',
    created_by   BIGINT       NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    closed_at    TIMESTAMPTZ,
    closed_by    BIGINT,
    cancelled_at TIMESTAMPTZ,
    CONSTRAINT fk_health_check_campaigns_campus_id FOREIGN KEY (campus_id) REFERENCES campuses (id),
    CONSTRAINT fk_health_check_campaigns_created_by FOREIGN KEY (created_by) REFERENCES user_accounts (id),
    CONSTRAINT fk_health_check_campaigns_closed_by FOREIGN KEY (closed_by) REFERENCES user_accounts (id),
    CONSTRAINT ck_health_check_campaigns_kind CHECK (kind IN ('EXAM', 'MEASUREMENT')),
    CONSTRAINT ck_health_check_campaigns_dates CHECK (end_date >= start_date),
    -- Kỳ cân đo do giáo viên cân đo, không có đơn vị khám
    CONSTRAINT ck_health_check_campaigns_examiner CHECK (kind = 'EXAM' OR examiner = ''),
    CONSTRAINT ck_health_check_campaigns_closed CHECK ((closed_at IS NULL) = (closed_by IS NULL)),
    CONSTRAINT ck_health_check_campaigns_ended CHECK (closed_at IS NULL OR cancelled_at IS NULL)
);
-- Mỗi điểm trường tối đa một đợt/kỳ chưa đóng, tính chung hai loại: giáo viên luôn biết đang nhập cho đợt/kỳ nào
CREATE UNIQUE INDEX uk_health_check_campaigns_campus_open ON health_check_campaigns (campus_id)
    WHERE closed_at IS NULL AND cancelled_at IS NULL;
CREATE INDEX idx_health_check_campaigns_campus_start ON health_check_campaigns (campus_id, start_date);

-- Lịch sử thao tác của đợt/kỳ, chỉ thêm
CREATE TABLE health_check_campaign_history (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    campaign_id BIGINT      NOT NULL,
    action      VARCHAR(20) NOT NULL,
    user_id     BIGINT      NOT NULL,
    reason      VARCHAR(300),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_health_check_campaign_history_campaign_id FOREIGN KEY (campaign_id)
        REFERENCES health_check_campaigns (id),
    CONSTRAINT fk_health_check_campaign_history_user_id FOREIGN KEY (user_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_health_check_campaign_history_action
        CHECK (action IN ('CREATED', 'UPDATED', 'CLOSED', 'REOPENED', 'CANCELLED'))
);
CREATE INDEX idx_health_check_campaign_history_campaign_id ON health_check_campaign_history (campaign_id, created_at);

-- Trẻ chưa có kết quả khi đóng đợt/kỳ ("Vắng"); mở lại thì xóa
CREATE TABLE health_check_absences (
    campaign_id  BIGINT NOT NULL,
    child_id     BIGINT NOT NULL,
    classroom_id BIGINT NOT NULL,
    CONSTRAINT pk_health_check_absences PRIMARY KEY (campaign_id, child_id),
    CONSTRAINT fk_health_check_absences_campaign_id FOREIGN KEY (campaign_id) REFERENCES health_check_campaigns (id),
    CONSTRAINT fk_health_check_absences_child_id FOREIGN KEY (child_id) REFERENCES children (id),
    CONSTRAINT fk_health_check_absences_classroom_id FOREIGN KEY (classroom_id) REFERENCES classrooms (id)
);

-- Một lần đo của trẻ trong đợt/kỳ (HLT-13 lưu từng lần đo). Lưu là công bố cho phụ huynh (user chốt 2026-10-23).
CREATE TABLE health_measurements (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    child_id              BIGINT       NOT NULL,
    campaign_id           BIGINT       NOT NULL,
    classroom_id          BIGINT       NOT NULL,
    date                  DATE         NOT NULL,
    height_cm             NUMERIC(4, 1) NOT NULL,
    weight_kg             NUMERIC(4, 1) NOT NULL,
    head_circumference_cm NUMERIC(3, 1),
    note                  VARCHAR(500) NOT NULL DEFAULT '',
    measured_by           BIGINT       NOT NULL,
    published_at          TIMESTAMPTZ  NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_health_measurements_child_id FOREIGN KEY (child_id) REFERENCES children (id),
    CONSTRAINT fk_health_measurements_campaign_id FOREIGN KEY (campaign_id) REFERENCES health_check_campaigns (id),
    CONSTRAINT fk_health_measurements_classroom_id FOREIGN KEY (classroom_id) REFERENCES classrooms (id),
    CONSTRAINT fk_health_measurements_measured_by FOREIGN KEY (measured_by) REFERENCES user_accounts (id),
    CONSTRAINT ck_health_measurements_height CHECK (height_cm BETWEEN 40 AND 150),
    CONSTRAINT ck_health_measurements_weight CHECK (weight_kg BETWEEN 3 AND 60),
    CONSTRAINT ck_health_measurements_head CHECK (head_circumference_cm IS NULL OR head_circumference_cm BETWEEN 30 AND 60),
    -- Mỗi trẻ một kết quả trong một đợt/kỳ, một lần đo trong một ngày
    CONSTRAINT uk_health_measurements_child_campaign UNIQUE (child_id, campaign_id),
    CONSTRAINT uk_health_measurements_child_date UNIQUE (child_id, date)
);
CREATE INDEX idx_health_measurements_campaign_id ON health_measurements (campaign_id, classroom_id);

-- Lịch sử sửa số đo (HLT-13), chỉ thêm: changes = [{ field, from, to }]
CREATE TABLE health_measurement_edits (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    measurement_id BIGINT       NOT NULL,
    user_id        BIGINT       NOT NULL,
    reason         VARCHAR(300) NOT NULL,
    changes        JSONB        NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_health_measurement_edits_measurement_id FOREIGN KEY (measurement_id)
        REFERENCES health_measurements (id),
    CONSTRAINT fk_health_measurement_edits_user_id FOREIGN KEY (user_id) REFERENCES user_accounts (id)
);
CREATE INDEX idx_health_measurement_edits_measurement_id ON health_measurement_edits (measurement_id, created_at);
