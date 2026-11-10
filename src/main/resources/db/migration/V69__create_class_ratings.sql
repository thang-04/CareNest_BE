-- Xếp loại nhóm lớp học kỳ I, II, cuối năm theo 7 tiêu chí của sổ chỉ đạo chuyên môn (mục 7); HT, PHT chấm.
CREATE TABLE class_ratings (
    id              BIGSERIAL PRIMARY KEY,
    classroom_id    BIGINT       NOT NULL REFERENCES classrooms (id),
    campus_id       BIGINT       NOT NULL REFERENCES campuses (id),
    school_year_id  BIGINT       NOT NULL REFERENCES school_years (id),
    period          VARCHAR(12)  NOT NULL,
    records         VARCHAR(10),
    program         VARCHAR(10),
    theme           VARCHAR(10),
    environment     VARCHAR(10),
    routine         VARCHAR(10),
    health          VARCHAR(10),
    parents         VARCHAR(10),
    overall         VARCHAR(10),
    note            VARCHAR(500),
    rated_by        BIGINT       NOT NULL,
    rated_at        TIMESTAMPTZ  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_class_ratings_class_period UNIQUE (classroom_id, period),
    CONSTRAINT ck_class_ratings_period CHECK (period IN ('SEMESTER_1', 'SEMESTER_2', 'YEAR')),
    CONSTRAINT ck_class_ratings_levels CHECK (
        (records IS NULL OR records IN ('TOT', 'KHA', 'DAT', 'CHUA_DAT'))
        AND (program IS NULL OR program IN ('TOT', 'KHA', 'DAT', 'CHUA_DAT'))
        AND (theme IS NULL OR theme IN ('TOT', 'KHA', 'DAT', 'CHUA_DAT'))
        AND (environment IS NULL OR environment IN ('TOT', 'KHA', 'DAT', 'CHUA_DAT'))
        AND (routine IS NULL OR routine IN ('TOT', 'KHA', 'DAT', 'CHUA_DAT'))
        AND (health IS NULL OR health IN ('TOT', 'KHA', 'DAT', 'CHUA_DAT'))
        AND (parents IS NULL OR parents IN ('TOT', 'KHA', 'DAT', 'CHUA_DAT'))
        AND (overall IS NULL OR overall IN ('TOT', 'KHA', 'DAT', 'CHUA_DAT')))
);

CREATE INDEX idx_class_ratings_year_period ON class_ratings (school_year_id, period);
