-- OBS-30 (Cần chốt 27/11/2026): sửa lại đánh giá đã xác nhận trong hạn / khi được mở lại; giữ bản đã thay.
CREATE TABLE evaluation_revisions (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kind           VARCHAR(20) NOT NULL,
    evaluation_id  BIGINT      NOT NULL,
    snapshot       TEXT        NOT NULL,
    replaced_by    BIGINT      NOT NULL,
    replaced_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_evaluation_revisions_kind CHECK (kind IN ('THEME', 'SEMESTER', 'YEAR')),
    CONSTRAINT fk_evaluation_revisions_user FOREIGN KEY (replaced_by) REFERENCES user_accounts (id)
);
CREATE INDEX idx_evaluation_revisions_target ON evaluation_revisions (kind, evaluation_id);
