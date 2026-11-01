-- Cấu trúc trường (1 trường, nhiều campus — ADR-0002) và phân công nhân sự làm nguồn scope (ADR-0003).
CREATE TABLE campuses (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code       VARCHAR(30)  NOT NULL,
    name       VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_campuses_code UNIQUE (code)
);

CREATE TABLE school_years (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(20) NOT NULL,
    start_date DATE        NOT NULL,
    end_date   DATE        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_school_years_name UNIQUE (name),
    CONSTRAINT ck_school_years_date_range CHECK (end_date > start_date)
);

-- Lớp gắn với 1 năm học: năm sau là lớp khác, nên phân công cũ không tự áp sang lớp mới
CREATE TABLE classrooms (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    campus_id      BIGINT       NOT NULL,
    school_year_id BIGINT       NOT NULL,
    name           VARCHAR(100) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_classrooms_campus_id FOREIGN KEY (campus_id) REFERENCES campuses (id),
    CONSTRAINT fk_classrooms_school_year_id FOREIGN KEY (school_year_id) REFERENCES school_years (id),
    CONSTRAINT uk_classrooms_school_year_campus_name UNIQUE (school_year_id, campus_id, name)
);
CREATE INDEX idx_classrooms_campus_id ON classrooms (campus_id);

-- staff_role cho biết phân công thuộc vai trò nào (1 campus có thể vừa có HP vừa có bếp).
-- Phạm vi theo vai trò: PRINCIPAL toàn trường; VICE_PRINCIPAL/KITCHEN_STAFF theo campus; TEACHER theo lớp.
-- Hiệu lực [valid_from, valid_to], valid_to NULL = đang hiệu lực; kết thúc phân công bằng cách đặt valid_to.
CREATE TABLE staff_assignments (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT      NOT NULL,
    staff_role   VARCHAR(20) NOT NULL,
    campus_id    BIGINT,
    classroom_id BIGINT,
    valid_from   DATE        NOT NULL,
    valid_to     DATE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_staff_assignments_user_id FOREIGN KEY (user_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_staff_assignments_campus_id FOREIGN KEY (campus_id) REFERENCES campuses (id),
    CONSTRAINT fk_staff_assignments_classroom_id FOREIGN KEY (classroom_id) REFERENCES classrooms (id),
    CONSTRAINT ck_staff_assignments_staff_role
        CHECK (staff_role IN ('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER', 'KITCHEN_STAFF')),
    CONSTRAINT ck_staff_assignments_scope CHECK (
        (staff_role = 'PRINCIPAL' AND campus_id IS NULL AND classroom_id IS NULL)
        OR (staff_role IN ('VICE_PRINCIPAL', 'KITCHEN_STAFF') AND campus_id IS NOT NULL AND classroom_id IS NULL)
        OR (staff_role = 'TEACHER' AND classroom_id IS NOT NULL AND campus_id IS NULL)
    ),
    CONSTRAINT ck_staff_assignments_valid_range CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
CREATE INDEX idx_staff_assignments_user_id ON staff_assignments (user_id);
CREATE INDEX idx_staff_assignments_campus_id ON staff_assignments (campus_id);
CREATE INDEX idx_staff_assignments_classroom_id ON staff_assignments (classroom_id);
