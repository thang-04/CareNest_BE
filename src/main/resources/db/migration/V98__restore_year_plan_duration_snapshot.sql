-- V90 removed historical snapshots. Missing evidence must not become unbounded.
ALTER TABLE edu_year_plans
    ADD COLUMN duration_policy_snapshot jsonb NOT NULL DEFAULT '{"mode":"MISSING_OR_UNKNOWN"}'::jsonb;
