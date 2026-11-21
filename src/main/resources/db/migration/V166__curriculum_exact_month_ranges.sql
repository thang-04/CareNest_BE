ALTER TABLE edu_curriculum_goals ADD COLUMN age_from_months smallint, ADD COLUMN age_to_months smallint;
UPDATE edu_curriculum_goals c SET
    age_from_months = CASE g.code WHEN 'ag-2' THEN 24 WHEN 'ag-3' THEN 36 WHEN 'ag-4' THEN 48 WHEN 'ag-5' THEN 60 ELSE g.age_from_months END,
    age_to_months = CASE g.code WHEN 'ag-2' THEN 36 WHEN 'ag-3' THEN 48 WHEN 'ag-4' THEN 60 WHEN 'ag-5' THEN 72 ELSE g.age_to_months END
FROM age_groups g WHERE g.id = c.age_group_id;
ALTER TABLE edu_curriculum_goals ALTER COLUMN age_from_months SET NOT NULL, ALTER COLUMN age_to_months SET NOT NULL,
    ADD CHECK (age_from_months >= 0 AND age_from_months < age_to_months),
    DROP CONSTRAINT fk_edu_curriculum_goals_age_group_id, ALTER COLUMN age_group_id DROP NOT NULL,
    DROP CONSTRAINT uk_edu_curriculum_goals_order,
    ADD UNIQUE (source_id, age_from_months, age_to_months, sort_order);
CREATE INDEX idx_edu_curriculum_goals_months ON edu_curriculum_goals (age_from_months, age_to_months);

ALTER TABLE edu_curriculum_contents ADD COLUMN age_from_months smallint, ADD COLUMN age_to_months smallint;
UPDATE edu_curriculum_contents c SET
    age_from_months = CASE g.code WHEN 'ag-2' THEN 24 WHEN 'ag-3' THEN 36 WHEN 'ag-4' THEN 48 WHEN 'ag-5' THEN 60 ELSE g.age_from_months END,
    age_to_months = CASE g.code WHEN 'ag-2' THEN 36 WHEN 'ag-3' THEN 48 WHEN 'ag-4' THEN 60 WHEN 'ag-5' THEN 72 ELSE g.age_to_months END
FROM age_groups g WHERE g.id = c.age_group_id;
ALTER TABLE edu_curriculum_contents ALTER COLUMN age_from_months SET NOT NULL, ALTER COLUMN age_to_months SET NOT NULL,
    ADD CHECK (age_from_months >= 0 AND age_from_months < age_to_months),
    DROP CONSTRAINT fk_edu_curriculum_contents_age_group_id, ALTER COLUMN age_group_id DROP NOT NULL,
    DROP CONSTRAINT uk_edu_curriculum_contents_order,
    ADD UNIQUE (source_id, age_from_months, age_to_months, sort_order);
CREATE INDEX idx_edu_curriculum_contents_months ON edu_curriculum_contents (age_from_months, age_to_months);

ALTER TABLE edu_curriculum_sub_domains ADD COLUMN age_from_months smallint, ADD COLUMN age_to_months smallint;
UPDATE edu_curriculum_sub_domains c SET
    age_from_months = CASE g.code WHEN 'ag-2' THEN 24 WHEN 'ag-3' THEN 36 WHEN 'ag-4' THEN 48 WHEN 'ag-5' THEN 60 ELSE g.age_from_months END,
    age_to_months = CASE g.code WHEN 'ag-2' THEN 36 WHEN 'ag-3' THEN 48 WHEN 'ag-4' THEN 60 WHEN 'ag-5' THEN 72 ELSE g.age_to_months END
FROM age_groups g WHERE g.id = c.age_group_id;
ALTER TABLE edu_curriculum_sub_domains ALTER COLUMN age_from_months SET NOT NULL, ALTER COLUMN age_to_months SET NOT NULL,
    ADD CHECK (age_from_months >= 0 AND age_from_months < age_to_months),
    DROP CONSTRAINT fk_edu_curriculum_sub_domains_age_group_id, ALTER COLUMN age_group_id DROP NOT NULL,
    DROP CONSTRAINT uk_edu_curriculum_sub_domains_order,
    ADD UNIQUE (source_id, age_from_months, age_to_months, sort_order);
CREATE INDEX idx_edu_curriculum_sub_domains_months ON edu_curriculum_sub_domains (age_from_months, age_to_months);

ALTER TABLE edu_curriculum_expected_results ADD COLUMN age_from_months smallint, ADD COLUMN age_to_months smallint;
UPDATE edu_curriculum_expected_results c SET
    age_from_months = CASE g.code WHEN 'ag-2' THEN 24 WHEN 'ag-3' THEN 36 WHEN 'ag-4' THEN 48 WHEN 'ag-5' THEN 60 ELSE g.age_from_months END,
    age_to_months = CASE g.code WHEN 'ag-2' THEN 36 WHEN 'ag-3' THEN 48 WHEN 'ag-4' THEN 60 WHEN 'ag-5' THEN 72 ELSE g.age_to_months END
FROM age_groups g WHERE g.id = c.age_group_id;
ALTER TABLE edu_curriculum_expected_results ALTER COLUMN age_from_months SET NOT NULL, ALTER COLUMN age_to_months SET NOT NULL,
    ADD CHECK (age_from_months >= 0 AND age_from_months < age_to_months),
    DROP CONSTRAINT fk_edu_curriculum_expected_results_age_group_id, ALTER COLUMN age_group_id DROP NOT NULL,
    DROP CONSTRAINT uk_edu_curriculum_expected_results_order,
    ADD UNIQUE (source_id, age_from_months, age_to_months, sort_order);
CREATE INDEX idx_edu_curriculum_expected_results_months ON edu_curriculum_expected_results (age_from_months, age_to_months);

ALTER TABLE edu_curriculum_sub_domains DROP CONSTRAINT uk_edu_curriculum_sub_domains_text,
    ADD UNIQUE (source_id, age_from_months, age_to_months, domain_id, text);
