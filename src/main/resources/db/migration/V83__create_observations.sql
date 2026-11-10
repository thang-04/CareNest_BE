-- Quan sát hằng ngày có cấu trúc (learning-observation). Tiêu chí là cấu hình, không hard-code enum (OBS-02);
-- bộ tiêu chí chính thức chờ chốt P-11, profile dev seed bộ mẫu.
CREATE TABLE observation_criteria (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code       VARCHAR(50)  NOT NULL,
    name       VARCHAR(150) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order INTEGER      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_observation_criteria_code UNIQUE (code)
);

-- Lựa chọn của một tiêu chí; score càng cao càng tốt (dùng để tính bằng code, không đưa cho AI tự suy luận thang đo).
CREATE TABLE criteria_options (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    criteria_id BIGINT       NOT NULL,
    code        VARCHAR(50)  NOT NULL,
    label       VARCHAR(100) NOT NULL,
    score       INTEGER      NOT NULL,
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT fk_criteria_options_criteria_id FOREIGN KEY (criteria_id) REFERENCES observation_criteria (id),
    CONSTRAINT uk_criteria_options_criteria_code UNIQUE (criteria_id, code)
);

-- Mỗi trẻ tối đa một bản quan sát mỗi ngày; GV nhập theo batch cả lớp.
CREATE TABLE observations (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    child_id     BIGINT        NOT NULL,
    classroom_id BIGINT        NOT NULL,
    observed_on  DATE          NOT NULL,
    note         VARCHAR(1000),
    recorded_by  BIGINT        NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT fk_observations_child_id FOREIGN KEY (child_id) REFERENCES children (id),
    CONSTRAINT fk_observations_classroom_id FOREIGN KEY (classroom_id) REFERENCES classrooms (id),
    CONSTRAINT fk_observations_recorded_by FOREIGN KEY (recorded_by) REFERENCES user_accounts (id),
    CONSTRAINT uk_observations_child_observed_on UNIQUE (child_id, observed_on)
);
CREATE INDEX idx_observations_classroom_observed_on ON observations (classroom_id, observed_on);

CREATE TABLE observation_values (
    observation_id BIGINT NOT NULL,
    criteria_id    BIGINT NOT NULL,
    option_id      BIGINT NOT NULL,
    CONSTRAINT pk_observation_values PRIMARY KEY (observation_id, criteria_id),
    CONSTRAINT fk_observation_values_observation_id FOREIGN KEY (observation_id) REFERENCES observations (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_observation_values_criteria_id FOREIGN KEY (criteria_id) REFERENCES observation_criteria (id),
    CONSTRAINT fk_observation_values_option_id FOREIGN KEY (option_id) REFERENCES criteria_options (id)
);
