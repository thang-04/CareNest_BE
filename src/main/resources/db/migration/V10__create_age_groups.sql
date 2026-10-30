-- Nhóm tuổi (khối) dùng chung toàn trường: lớp thuộc 1 nhóm tuổi, kế hoạch năm học lập theo nhóm tuổi (EDU-01).
CREATE TABLE age_groups (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code       VARCHAR(20)  NOT NULL,
    name       VARCHAR(100) NOT NULL,
    short_name VARCHAR(50)  NOT NULL,
    sort_order SMALLINT     NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_age_groups_code UNIQUE (code)
);

INSERT INTO age_groups (code, name, short_name, sort_order) VALUES
    ('ag-2', 'Nhà trẻ (24–36 tháng)', 'Nhà trẻ', 1),
    ('ag-3', 'Mẫu giáo bé (3–4 tuổi)', 'Mẫu giáo bé', 2),
    ('ag-4', 'Mẫu giáo nhỡ (4–5 tuổi)', 'Mẫu giáo nhỡ', 3),
    ('ag-5', 'Mẫu giáo lớn (5–6 tuổi)', 'Mẫu giáo lớn', 4);

-- Lớp cũ chưa gán nhóm tuổi nên cột cho phép NULL
ALTER TABLE classrooms ADD COLUMN age_group_id BIGINT;
ALTER TABLE classrooms
    ADD CONSTRAINT fk_classrooms_age_group_id FOREIGN KEY (age_group_id) REFERENCES age_groups (id);
CREATE INDEX idx_classrooms_age_group_id ON classrooms (age_group_id);

-- Tổ trưởng khối: giáo viên (role TEACHER ở Keycloak) có thêm phân công AGE_GROUP_TEAM_LEADER theo nhóm tuổi
ALTER TABLE staff_assignments ALTER COLUMN staff_role TYPE VARCHAR(30);
ALTER TABLE staff_assignments ADD COLUMN age_group_id BIGINT;
ALTER TABLE staff_assignments
    ADD CONSTRAINT fk_staff_assignments_age_group_id FOREIGN KEY (age_group_id) REFERENCES age_groups (id);
CREATE INDEX idx_staff_assignments_age_group_id ON staff_assignments (age_group_id);

ALTER TABLE staff_assignments DROP CONSTRAINT ck_staff_assignments_staff_role;
ALTER TABLE staff_assignments
    ADD CONSTRAINT ck_staff_assignments_staff_role
        CHECK (staff_role IN ('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER', 'KITCHEN_STAFF', 'AGE_GROUP_TEAM_LEADER'));

ALTER TABLE staff_assignments DROP CONSTRAINT ck_staff_assignments_scope;
ALTER TABLE staff_assignments
    ADD CONSTRAINT ck_staff_assignments_scope CHECK (
        (staff_role = 'PRINCIPAL' AND campus_id IS NULL AND classroom_id IS NULL AND age_group_id IS NULL)
        OR (staff_role IN ('VICE_PRINCIPAL', 'KITCHEN_STAFF')
            AND campus_id IS NOT NULL AND classroom_id IS NULL AND age_group_id IS NULL)
        OR (staff_role = 'TEACHER' AND classroom_id IS NOT NULL AND campus_id IS NULL AND age_group_id IS NULL)
        OR (staff_role = 'AGE_GROUP_TEAM_LEADER'
            AND age_group_id IS NOT NULL AND campus_id IS NULL AND classroom_id IS NULL)
    );
