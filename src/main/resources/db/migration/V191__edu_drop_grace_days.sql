-- V168__edu_drop_grace_days.sql (EDU-109)
ALTER TABLE edu_settings
  DROP CONSTRAINT IF EXISTS ck_edu_settings_late_return_grace_days,
  DROP CONSTRAINT IF EXISTS ck_edu_settings_late_approval_grace_days,
  DROP COLUMN IF EXISTS late_return_grace_days,
  DROP COLUMN IF EXISTS late_approval_grace_days;
