-- EDU-09: ba bảng cha–con, giữ định danh cũ để giáo án/học liệu không đổi tham chiếu.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM edu_goal_rows GROUP BY year_plan_id, domain_id, sort_order HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'ambiguous goal row order; resolve duplicate sort_order before upgrading';
    END IF;
    IF EXISTS (SELECT 1 FROM edu_goal_contents c JOIN edu_goal_rows r ON r.id=c.goal_row_id WHERE r.kind <> 'goal') THEN
        RAISE EXCEPTION 'goal content references a heading; resolve before upgrading';
    END IF;
END $$;

CREATE SEQUENCE edu_plan_rows_seq;
DO $$
DECLARE previous_value BIGINT;
BEGIN
    EXECUTE format('SELECT last_value FROM %s', pg_get_serial_sequence('edu_goal_rows','id')) INTO previous_value;
    PERFORM setval('edu_plan_rows_seq', GREATEST(previous_value, COALESCE((SELECT max(id) FROM edu_goal_rows),0),1), true);
END $$;

CREATE TABLE edu_plan_sub_domains (
    id BIGINT PRIMARY KEY DEFAULT nextval('edu_plan_rows_seq'),
    year_plan_id BIGINT NOT NULL REFERENCES edu_year_plans(id) ON DELETE CASCADE,
    domain_id BIGINT NOT NULL REFERENCES edu_domains(id),
    sort_order SMALLINT NOT NULL,
    text TEXT NOT NULL,
    lineage_id BIGINT,
    UNIQUE (id, year_plan_id, domain_id)
);
CREATE TABLE edu_plan_expected_results (
    id BIGINT PRIMARY KEY DEFAULT nextval('edu_plan_rows_seq'),
    year_plan_id BIGINT NOT NULL REFERENCES edu_year_plans(id) ON DELETE CASCADE,
    domain_id BIGINT NOT NULL REFERENCES edu_domains(id),
    sort_order SMALLINT NOT NULL,
    text TEXT NOT NULL,
    lineage_id BIGINT,
    sub_domain_id BIGINT,
    sub_domain_key BIGINT GENERATED ALWAYS AS (COALESCE(sub_domain_id,0)) STORED,
    UNIQUE (id, year_plan_id, domain_id, sub_domain_key),
    FOREIGN KEY (sub_domain_id,year_plan_id,domain_id)
        REFERENCES edu_plan_sub_domains(id,year_plan_id,domain_id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED
);
CREATE TABLE edu_plan_goals (
    id BIGINT PRIMARY KEY DEFAULT nextval('edu_plan_rows_seq'),
    year_plan_id BIGINT NOT NULL REFERENCES edu_year_plans(id) ON DELETE CASCADE,
    domain_id BIGINT NOT NULL REFERENCES edu_domains(id),
    sort_order SMALLINT NOT NULL,
    text TEXT NOT NULL,
    lineage_id BIGINT,
    goal_no SMALLINT NOT NULL,
    curriculum_goal_id BIGINT REFERENCES edu_curriculum_goals(id) ON DELETE SET NULL,
    sub_domain_id BIGINT,
    expected_result_id BIGINT,
    sub_domain_key BIGINT GENERATED ALWAYS AS (COALESCE(sub_domain_id,0)) STORED,
    FOREIGN KEY (sub_domain_id,year_plan_id,domain_id)
        REFERENCES edu_plan_sub_domains(id,year_plan_id,domain_id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,
    FOREIGN KEY (expected_result_id,year_plan_id,domain_id,sub_domain_key)
        REFERENCES edu_plan_expected_results(id,year_plan_id,domain_id,sub_domain_key) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED
);

CREATE TEMP TABLE edu_row_parents ON COMMIT DROP AS
WITH subs AS (
    SELECT r.*, max(sort_order) FILTER (WHERE kind='sub') OVER (
        PARTITION BY year_plan_id,domain_id ORDER BY sort_order ROWS UNBOUNDED PRECEDING) AS sub_order
    FROM edu_goal_rows r
), groups AS (
    SELECT s.*, max(sort_order) FILTER (WHERE kind='group') OVER (
        PARTITION BY year_plan_id,domain_id,sub_order ORDER BY sort_order ROWS UNBOUNDED PRECEDING) AS group_order
    FROM subs s
)
SELECT r.*, s.id AS sub_domain_id, g.id AS expected_result_id
FROM groups r
LEFT JOIN edu_goal_rows s ON s.year_plan_id=r.year_plan_id AND s.domain_id=r.domain_id AND s.sort_order=r.sub_order
LEFT JOIN edu_goal_rows g ON g.year_plan_id=r.year_plan_id AND g.domain_id=r.domain_id AND g.sort_order=r.group_order;

INSERT INTO edu_plan_sub_domains(id,year_plan_id,domain_id,sort_order,text,lineage_id)
SELECT id,year_plan_id,domain_id,sort_order,text,lineage_id FROM edu_row_parents WHERE kind='sub';
INSERT INTO edu_plan_expected_results(id,year_plan_id,domain_id,sort_order,text,lineage_id,sub_domain_id)
SELECT id,year_plan_id,domain_id,sort_order,text,lineage_id,sub_domain_id FROM edu_row_parents WHERE kind='group';
INSERT INTO edu_plan_goals(id,year_plan_id,domain_id,sort_order,text,lineage_id,goal_no,curriculum_goal_id,sub_domain_id,expected_result_id)
SELECT id,year_plan_id,domain_id,sort_order,text,lineage_id,goal_no,curriculum_goal_id,sub_domain_id,expected_result_id
FROM edu_row_parents WHERE kind='goal';

-- Kiểm FK backfill trước DDL tiếp theo để PostgreSQL không còn deferred trigger đang chờ.
SET CONSTRAINTS ALL IMMEDIATE;

ALTER TABLE edu_goal_contents DROP CONSTRAINT fk_edu_goal_contents_goal_row_id;
ALTER TABLE edu_goal_contents RENAME COLUMN goal_row_id TO goal_id;
ALTER TABLE edu_goal_contents ADD CONSTRAINT fk_edu_goal_contents_goal_id
    FOREIGN KEY (goal_id) REFERENCES edu_plan_goals(id) ON DELETE CASCADE;
ALTER INDEX idx_edu_goal_contents_goal_row_id RENAME TO idx_edu_goal_contents_goal_id;
DROP TABLE edu_goal_rows;
CREATE INDEX idx_edu_plan_sub_domains_plan ON edu_plan_sub_domains(year_plan_id,domain_id,sort_order);
CREATE INDEX idx_edu_plan_expected_results_plan ON edu_plan_expected_results(year_plan_id,domain_id,sort_order);
CREATE INDEX idx_edu_plan_expected_results_sub ON edu_plan_expected_results(sub_domain_id);
CREATE INDEX idx_edu_plan_goals_plan ON edu_plan_goals(year_plan_id,domain_id,sort_order);
CREATE INDEX idx_edu_plan_goals_sub ON edu_plan_goals(sub_domain_id);
CREATE INDEX idx_edu_plan_goals_group ON edu_plan_goals(expected_result_id);
CREATE INDEX idx_edu_plan_goals_curriculum ON edu_plan_goals(curriculum_goal_id);
CREATE INDEX idx_edu_plan_sub_domains_lineage ON edu_plan_sub_domains(lineage_id);
CREATE INDEX idx_edu_plan_expected_results_lineage ON edu_plan_expected_results(lineage_id);
CREATE INDEX idx_edu_plan_goals_lineage ON edu_plan_goals(lineage_id);
