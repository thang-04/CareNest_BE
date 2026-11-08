ALTER TABLE edu_year_plans
    ADD COLUMN plan_level VARCHAR(20) NOT NULL DEFAULT 'TEAM',
    ADD COLUMN curriculum_source_plan_id BIGINT REFERENCES edu_year_plans(id),
    ADD COLUMN legacy_team_without_source BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN duration_policy_snapshot JSONB NOT NULL DEFAULT '{"mode":"LEGACY_UNBOUNDED"}';
UPDATE edu_year_plans SET legacy_team_without_source = true;
ALTER TABLE edu_year_plans ADD CONSTRAINT ck_edu_plan_level CHECK (plan_level IN ('TEAM','SCHOOL_YEAR'));
ALTER TABLE edu_year_plans ADD CONSTRAINT ck_edu_plan_source CHECK (
    (plan_level = 'SCHOOL_YEAR' AND curriculum_source_plan_id IS NULL AND NOT legacy_team_without_source)
    OR (plan_level = 'TEAM' AND ((curriculum_source_plan_id IS NOT NULL AND NOT legacy_team_without_source)
        OR (curriculum_source_plan_id IS NULL AND legacy_team_without_source)))
);
-- Chuỗi phiên bản tách theo cấp, không thay ID hoặc predecessor cũ.
DO $$ DECLARE item RECORD; BEGIN
 FOR item IN SELECT conname FROM pg_constraint WHERE conrelid = ('"' || current_schema() || '".edu_year_plans')::regclass AND contype='u'
 AND pg_get_constraintdef(oid) LIKE '%school_year_id%' AND pg_get_constraintdef(oid) LIKE '%age_group_id%'
 LOOP EXECUTE format('ALTER TABLE edu_year_plans DROP CONSTRAINT IF EXISTS %I',item.conname); END LOOP;
 FOR item IN SELECT indexname FROM pg_indexes WHERE schemaname = current_schema() AND tablename='edu_year_plans'
 AND indexdef LIKE 'CREATE UNIQUE INDEX%' AND indexdef LIKE '%school_year_id%' AND indexdef LIKE '%age_group_id%'
 LOOP EXECUTE format('DROP INDEX IF EXISTS %I',item.indexname); END LOOP;
END $$;
CREATE UNIQUE INDEX uq_edu_plan_level_version ON edu_year_plans(plan_level,school_year_id,age_group_id,version_no);
CREATE UNIQUE INDEX uq_edu_plan_level_open ON edu_year_plans(plan_level,school_year_id,age_group_id) WHERE status <> 'APPROVED';
CREATE INDEX idx_edu_plan_curriculum_source ON edu_year_plans(curriculum_source_plan_id);
ALTER TABLE edu_settings ADD COLUMN duration_policy JSONB NOT NULL DEFAULT '{"theme":{"minWeeks":2,"maxWeeks":4},"branch":{"minWeeks":1,"maxWeeks":2}}',
    ADD COLUMN duration_revision BIGINT NOT NULL DEFAULT 1 CHECK (duration_revision > 0);
UPDATE edu_settings SET workflow_policy = workflow_policy || '{"schoolYear":{"creators":["VICE_PRINCIPAL"],"reviewers":["PRINCIPAL"]}}'::jsonb
WHERE NOT workflow_policy ? 'schoolYear';
ALTER TABLE edu_class_docs ADD COLUMN duration_policy_snapshot JSONB;
UPDATE edu_class_docs SET duration_policy_snapshot = '{"mode":"LEGACY_UNBOUNDED"}'::jsonb WHERE kind = 'branch';
