CREATE TABLE planned_admissions (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    child_id      BIGINT      NOT NULL,
    school_year_id BIGINT     NOT NULL,
    campus_id     BIGINT      NOT NULL,
    classroom_id  BIGINT,
    start_date    DATE        NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_by    BIGINT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_planned_admissions_child FOREIGN KEY (child_id) REFERENCES children (id),
    CONSTRAINT fk_planned_admissions_school_year FOREIGN KEY (school_year_id) REFERENCES school_years (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_planned_admissions_campus FOREIGN KEY (campus_id) REFERENCES campuses (id),
    CONSTRAINT fk_planned_admissions_classroom FOREIGN KEY (classroom_id) REFERENCES classrooms (id),
    CONSTRAINT fk_planned_admissions_created_by FOREIGN KEY (created_by) REFERENCES user_accounts (id),
    CONSTRAINT uk_planned_admissions_child_year UNIQUE (child_id, school_year_id),
    CONSTRAINT ck_planned_admissions_status CHECK (status IN ('PENDING', 'PLACED', 'ACTIVATED', 'CANCELLED'))
);

CREATE INDEX idx_planned_admissions_year_status
    ON planned_admissions (school_year_id, status);
CREATE INDEX idx_planned_admissions_classroom
    ON planned_admissions (classroom_id) WHERE classroom_id IS NOT NULL;
