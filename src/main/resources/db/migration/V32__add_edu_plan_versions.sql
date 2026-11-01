-- Phiên bản kế hoạch năm (Tiền chốt 28/10/2026, specs/004-year-plan-versioning): sửa sau ban hành = phiên bản mới,
-- bản đã ban hành giữ nguyên. Mỗi phiên bản là một dòng kế hoạch đầy đủ; dòng con chép giữ lineage để so sánh.
ALTER TABLE edu_year_plans
    ADD COLUMN version_no INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN source_plan_id BIGINT,
    ADD CONSTRAINT ck_edu_year_plans_version_no CHECK (version_no >= 1),
    ADD CONSTRAINT ck_edu_year_plans_source CHECK ((version_no = 1) = (source_plan_id IS NULL)),
    ADD CONSTRAINT fk_edu_year_plans_source_plan_id FOREIGN KEY (source_plan_id) REFERENCES edu_year_plans (id);

-- EDU-01 thành một chuỗi phiên bản cho mỗi (năm học, nhóm tuổi), tối đa một phiên bản chưa ban hành
ALTER TABLE edu_year_plans DROP CONSTRAINT uk_edu_year_plans_school_year_age_group;
ALTER TABLE edu_year_plans
    ADD CONSTRAINT uk_edu_year_plans_school_year_age_group_version UNIQUE (school_year_id, age_group_id, version_no);
CREATE UNIQUE INDEX uk_edu_year_plans_open_version
    ON edu_year_plans (school_year_id, age_group_id) WHERE status <> 'APPROVED';
CREATE INDEX idx_edu_year_plans_source_plan_id ON edu_year_plans (source_plan_id);

-- NULL = dòng gốc (lineage là chính id của nó); dòng chép mang lineage của dòng nguồn
ALTER TABLE edu_goal_rows ADD COLUMN lineage_id BIGINT;
ALTER TABLE edu_goal_contents ADD COLUMN lineage_id BIGINT;
ALTER TABLE edu_themes ADD COLUMN lineage_id BIGINT;
ALTER TABLE edu_breaks ADD COLUMN lineage_id BIGINT;
CREATE INDEX idx_edu_goal_rows_lineage_id ON edu_goal_rows (lineage_id);
CREATE INDEX idx_edu_goal_contents_lineage_id ON edu_goal_contents (lineage_id);
CREATE INDEX idx_edu_themes_lineage_id ON edu_themes (lineage_id);

-- Tài liệu lớp chép từ phiên bản cũ nhớ bản nguồn; "Kế hoạch điều chỉnh và bổ sung" của kế hoạch ngày lưu riêng
-- để sửa được cả sau khi duyệt mà không đổi nội dung đã duyệt
ALTER TABLE edu_class_docs
    ADD COLUMN source_doc_id BIGINT,
    ADD COLUMN adjustments JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD CONSTRAINT fk_edu_class_docs_source_doc_id
        FOREIGN KEY (source_doc_id) REFERENCES edu_class_docs (id) ON DELETE SET NULL,
    ADD CONSTRAINT ck_edu_class_docs_adjustments CHECK (jsonb_typeof(adjustments) = 'object'),
    ADD CONSTRAINT ck_edu_class_docs_adjustments_week CHECK (kind = 'week' OR adjustments = '{}'::jsonb);
CREATE INDEX idx_edu_class_docs_source_doc_id ON edu_class_docs (source_doc_id);
