-- Xếp lớp năm học mới (module child): kết quả dự kiến của từng trẻ đang học ở năm ACTIVE cho năm PLANNED.
-- Chỉ áp dụng (đổi enrollments) khi kích hoạt năm học; trước đó sửa thoải mái.
CREATE TABLE class_placements (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    school_year_id    BIGINT      NOT NULL,
    child_id          BIGINT      NOT NULL,
    from_classroom_id BIGINT      NOT NULL,
    action            VARCHAR(20) NOT NULL,
    to_classroom_id   BIGINT,
    updated_by        BIGINT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Bản xếp lớp chỉ thuộc năm học đó: xóa năm PLANNED thì xóa theo
    CONSTRAINT fk_class_placements_school_year_id FOREIGN KEY (school_year_id) REFERENCES school_years (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_class_placements_child_id FOREIGN KEY (child_id) REFERENCES children (id),
    CONSTRAINT fk_class_placements_from_classroom_id FOREIGN KEY (from_classroom_id) REFERENCES classrooms (id),
    CONSTRAINT fk_class_placements_to_classroom_id FOREIGN KEY (to_classroom_id) REFERENCES classrooms (id),
    CONSTRAINT fk_class_placements_updated_by FOREIGN KEY (updated_by) REFERENCES user_accounts (id),
    CONSTRAINT uk_class_placements_school_year_child UNIQUE (school_year_id, child_id),
    CONSTRAINT ck_class_placements_action CHECK (action IN ('PROMOTE', 'STAY', 'LEAVE', 'GRADUATE')),
    -- Lên lớp / ở lại thì có lớp mới; nghỉ học / ra trường thì không
    CONSTRAINT ck_class_placements_to_classroom CHECK (
        (action IN ('PROMOTE', 'STAY')) = (to_classroom_id IS NOT NULL))
);

CREATE INDEX idx_class_placements_to_classroom_id ON class_placements (to_classroom_id);
