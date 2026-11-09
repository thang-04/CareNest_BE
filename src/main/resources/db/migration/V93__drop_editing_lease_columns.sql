-- V93: BÃ£i bá»  hoÃ n toÃ n cÆ¡n cháº¿ lease/editing phiÃªn sá»±a 90s cÅ© vÃ  cÃ¡c cá»™t liÃªn quan trÃªn edu_year_plans vÃ  edu_team_plans

-- 1. Drop cÃ¡c index phá»¥c vá»¥ session/editing
DROP INDEX IF EXISTS idx_edu_year_plan_active_session;
DROP INDEX IF EXISTS idx_edu_team_plans_active_session;

-- 2. Drop cÃ¡c rÃ ng buá»™c check & FK liÃªn quan Ä‘áº¿n editing lease
ALTER TABLE edu_year_plans DROP CONSTRAINT IF EXISTS ck_edu_year_plan_edit_session;
ALTER TABLE edu_year_plans DROP CONSTRAINT IF EXISTS ck_edu_year_plans_edit_session;
ALTER TABLE edu_year_plans DROP CONSTRAINT IF EXISTS fk_edu_year_plans_editing_by;

ALTER TABLE edu_team_plans DROP CONSTRAINT IF EXISTS ck_edu_team_plans_edit_session;
ALTER TABLE edu_team_plans DROP CONSTRAINT IF EXISTS fk_edu_team_plans_editing_by;

-- 3. Drop 5 cá»™t lease trÃªn edu_year_plans
ALTER TABLE edu_year_plans
    DROP COLUMN IF EXISTS edit_session_id,
    DROP COLUMN IF EXISTS editing_by,
    DROP COLUMN IF EXISTS editing_since,
    DROP COLUMN IF EXISTS editing_at,
    DROP COLUMN IF EXISTS editing_from;

-- 4. Drop 5 cá»™t lease trÃªn edu_team_plans
ALTER TABLE edu_team_plans
    DROP COLUMN IF EXISTS edit_session_id,
    DROP COLUMN IF EXISTS editing_by,
    DROP COLUMN IF EXISTS editing_since,
    DROP COLUMN IF EXISTS editing_at,
    DROP COLUMN IF EXISTS editing_from;
