-- Hồ sơ gốc của trẻ (khung tối thiểu cho phân quyền; cột nghiệp vụ thêm bằng migration sau).
CREATE TABLE children (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    full_name     VARCHAR(150) NOT NULL,
    date_of_birth DATE         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Trẻ học lớp nào theo thời gian; campus của trẻ suy từ lớp tại ngày cần xét (ADR-0002).
CREATE TABLE enrollments (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    child_id     BIGINT      NOT NULL,
    classroom_id BIGINT      NOT NULL,
    valid_from   DATE        NOT NULL,
    valid_to     DATE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_enrollments_child_id FOREIGN KEY (child_id) REFERENCES children (id),
    CONSTRAINT fk_enrollments_classroom_id FOREIGN KEY (classroom_id) REFERENCES classrooms (id),
    CONSTRAINT ck_enrollments_valid_range CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
CREATE INDEX idx_enrollments_child_id ON enrollments (child_id);
CREATE INDEX idx_enrollments_classroom_id ON enrollments (classroom_id);

-- Nguồn scope của phụ huynh (AUTH-04): chỉ truy cập trẻ có liên kết với chính tài khoản đó.
CREATE TABLE guardian_child_links (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT      NOT NULL,
    child_id     BIGINT      NOT NULL,
    relationship VARCHAR(30) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_guardian_child_links_user_id FOREIGN KEY (user_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_guardian_child_links_child_id FOREIGN KEY (child_id) REFERENCES children (id),
    CONSTRAINT uk_guardian_child_links_user_child UNIQUE (user_id, child_id)
);
CREATE INDEX idx_guardian_child_links_child_id ON guardian_child_links (child_id);
