ALTER TABLE edu_themes ADD CONSTRAINT uq_edu_theme_plan_id UNIQUE(year_plan_id,id);
CREATE TABLE edu_plan_branch_templates (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 year_plan_id BIGINT NOT NULL REFERENCES edu_year_plans(id) ON DELETE CASCADE,
 theme_id BIGINT NOT NULL,
 source_template_id BIGINT REFERENCES edu_plan_branch_templates(id) ON DELETE RESTRICT,
 lineage_id BIGINT,
 name VARCHAR(150) NOT NULL,
 start_date DATE,
 end_date DATE,
 sort_order INTEGER NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CONSTRAINT fk_edu_template_theme FOREIGN KEY(year_plan_id,theme_id) REFERENCES edu_themes(year_plan_id,id) ON DELETE CASCADE,
 CONSTRAINT ck_edu_template_dates CHECK(start_date IS NULL OR end_date IS NULL OR start_date <= end_date)
);
CREATE INDEX idx_edu_templates_plan_theme ON edu_plan_branch_templates(year_plan_id,theme_id,sort_order);
CREATE INDEX idx_edu_templates_source ON edu_plan_branch_templates(source_template_id);
