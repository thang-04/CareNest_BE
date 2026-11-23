-- Populate nutrition settings for demo age groups without overwriting Principal changes.
INSERT INTO nutrition_group_settings (age_group_id, enabled)
SELECT id, TRUE FROM age_groups
ON CONFLICT (age_group_id) DO NOTHING;
