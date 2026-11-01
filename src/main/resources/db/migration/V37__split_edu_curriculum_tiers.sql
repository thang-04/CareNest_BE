-- EDU-28 (user chốt 30/10/2026): danh mục Bộ 3 tầng như kế hoạch — lĩnh vực con > nhóm kết quả mong đợi > mục tiêu;
-- lĩnh vực con và nhóm trong kế hoạch giữ liên kết nguồn như mục tiêu
CREATE TABLE edu_curriculum_sub_domains (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_id    BIGINT       NOT NULL,
    age_group_id BIGINT       NOT NULL,
    domain_id    BIGINT       NOT NULL,
    text         VARCHAR(150) NOT NULL,
    sort_order   INT          NOT NULL,
    CONSTRAINT fk_edu_curriculum_sub_domains_source_id FOREIGN KEY (source_id) REFERENCES edu_curriculum_sources (id),
    CONSTRAINT fk_edu_curriculum_sub_domains_age_group_id FOREIGN KEY (age_group_id) REFERENCES age_groups (id),
    CONSTRAINT fk_edu_curriculum_sub_domains_domain_id FOREIGN KEY (domain_id) REFERENCES edu_domains (id),
    CONSTRAINT uk_edu_curriculum_sub_domains_text UNIQUE (source_id, age_group_id, domain_id, text),
    CONSTRAINT uk_edu_curriculum_sub_domains_order UNIQUE (source_id, age_group_id, sort_order)
);

CREATE TABLE edu_curriculum_expected_results (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_id     BIGINT      NOT NULL,
    age_group_id  BIGINT      NOT NULL,
    domain_id     BIGINT      NOT NULL,
    sub_domain_id BIGINT,
    group_no      VARCHAR(10),
    text          TEXT        NOT NULL,
    sort_order    INT         NOT NULL,
    CONSTRAINT fk_edu_curriculum_expected_results_source_id FOREIGN KEY (source_id) REFERENCES edu_curriculum_sources (id),
    CONSTRAINT fk_edu_curriculum_expected_results_age_group_id FOREIGN KEY (age_group_id) REFERENCES age_groups (id),
    CONSTRAINT fk_edu_curriculum_expected_results_domain_id FOREIGN KEY (domain_id) REFERENCES edu_domains (id),
    CONSTRAINT fk_edu_curriculum_expected_results_sub_domain_id
        FOREIGN KEY (sub_domain_id) REFERENCES edu_curriculum_sub_domains (id),
    CONSTRAINT uk_edu_curriculum_expected_results_order UNIQUE (source_id, age_group_id, sort_order)
);

-- Lĩnh vực con theo thứ tự xuất hiện trong mục D, rồi mục C (dữ liệu V34: mọi lĩnh vực con mục C đều có ở mục D)
INSERT INTO edu_curriculum_sub_domains (source_id, age_group_id, domain_id, text, sort_order)
SELECT source_id, age_group_id, domain_id, sub_domain,
       row_number() OVER (PARTITION BY source_id, age_group_id ORDER BY part, first_order)
FROM (SELECT source_id, age_group_id, domain_id, sub_domain, 0 AS part, min(sort_order) AS first_order
      FROM edu_curriculum_goals
      WHERE sub_domain IS NOT NULL
      GROUP BY source_id, age_group_id, domain_id, sub_domain
      UNION ALL
      SELECT c.source_id, c.age_group_id, c.domain_id, c.sub_domain, 1, min(c.sort_order)
      FROM edu_curriculum_contents c
      WHERE c.sub_domain IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM edu_curriculum_goals g
                        WHERE g.source_id = c.source_id AND g.age_group_id = c.age_group_id
                          AND g.domain_id = c.domain_id AND g.sub_domain = c.sub_domain)
      GROUP BY c.source_id, c.age_group_id, c.domain_id, c.sub_domain) s;

-- Nhóm = dãy mục tiêu liền nhau cùng lĩnh vực con, số nhóm và câu nhóm
INSERT INTO edu_curriculum_expected_results (source_id, age_group_id, domain_id, sub_domain_id, group_no, text, sort_order)
SELECT g.source_id, g.age_group_id, g.domain_id, s.id, g.group_no, g.group_text,
       row_number() OVER (PARTITION BY g.source_id, g.age_group_id ORDER BY min(g.sort_order))
FROM edu_curriculum_goals g
LEFT JOIN edu_curriculum_sub_domains s
       ON s.source_id = g.source_id AND s.age_group_id = g.age_group_id
      AND s.domain_id = g.domain_id AND s.text = g.sub_domain
GROUP BY g.source_id, g.age_group_id, g.domain_id, s.id, g.group_no, g.group_text;

ALTER TABLE edu_curriculum_goals ADD COLUMN expected_result_id BIGINT;
UPDATE edu_curriculum_goals g
SET expected_result_id = r.id
FROM edu_curriculum_expected_results r
LEFT JOIN edu_curriculum_sub_domains s ON s.id = r.sub_domain_id
WHERE r.source_id = g.source_id AND r.age_group_id = g.age_group_id AND r.domain_id = g.domain_id
  AND s.text IS NOT DISTINCT FROM g.sub_domain
  AND r.group_no IS NOT DISTINCT FROM g.group_no
  AND r.text = g.group_text;
ALTER TABLE edu_curriculum_goals
    ALTER COLUMN expected_result_id SET NOT NULL,
    ADD CONSTRAINT fk_edu_curriculum_goals_expected_result_id
        FOREIGN KEY (expected_result_id) REFERENCES edu_curriculum_expected_results (id),
    DROP COLUMN sub_domain,
    DROP COLUMN group_no,
    DROP COLUMN group_text;
CREATE INDEX idx_edu_curriculum_goals_expected_result_id ON edu_curriculum_goals (expected_result_id);

ALTER TABLE edu_curriculum_contents ADD COLUMN sub_domain_id BIGINT;
UPDATE edu_curriculum_contents c
SET sub_domain_id = s.id
FROM edu_curriculum_sub_domains s
WHERE s.source_id = c.source_id AND s.age_group_id = c.age_group_id
  AND s.domain_id = c.domain_id AND s.text = c.sub_domain;
ALTER TABLE edu_curriculum_contents
    ADD CONSTRAINT fk_edu_curriculum_contents_sub_domain_id
        FOREIGN KEY (sub_domain_id) REFERENCES edu_curriculum_sub_domains (id),
    DROP COLUMN sub_domain;

-- Kế hoạch: liên kết nguồn của lĩnh vực con/nhóm; câu trong kế hoạch vẫn là bản chép độc lập
ALTER TABLE edu_plan_sub_domains ADD COLUMN curriculum_sub_domain_id BIGINT;
ALTER TABLE edu_plan_sub_domains
    ADD CONSTRAINT fk_edu_plan_sub_domains_curriculum_sub_domain_id
        FOREIGN KEY (curriculum_sub_domain_id) REFERENCES edu_curriculum_sub_domains (id) ON DELETE SET NULL;
CREATE INDEX idx_edu_plan_sub_domains_curriculum_sub_domain_id ON edu_plan_sub_domains (curriculum_sub_domain_id);

ALTER TABLE edu_plan_expected_results ADD COLUMN curriculum_expected_result_id BIGINT;
ALTER TABLE edu_plan_expected_results
    ADD CONSTRAINT fk_edu_plan_expected_results_curriculum_expected_result_id
        FOREIGN KEY (curriculum_expected_result_id) REFERENCES edu_curriculum_expected_results (id) ON DELETE SET NULL;
CREATE INDEX idx_edu_plan_expected_results_curriculum_expected_result_id
    ON edu_plan_expected_results (curriculum_expected_result_id);

-- Dữ liệu cũ: chỉ gắn khi tiêu đề có mục tiêu con lấy từ Bộ và chữ trùng câu của Bộ, tránh gắn nhầm tiêu đề tự gõ
UPDATE edu_plan_expected_results p
SET curriculum_expected_result_id = x.id
FROM (SELECT DISTINCT ON (pg.expected_result_id) pg.expected_result_id AS plan_group_id, r.id, r.text
      FROM edu_plan_goals pg
      JOIN edu_curriculum_goals cg ON cg.id = pg.curriculum_goal_id
      JOIN edu_curriculum_expected_results r ON r.id = cg.expected_result_id
      WHERE pg.expected_result_id IS NOT NULL
      ORDER BY pg.expected_result_id, pg.sort_order) x
WHERE x.plan_group_id = p.id
  AND regexp_replace(btrim(p.text), '\s+', ' ', 'g') = regexp_replace(btrim(x.text), '\s+', ' ', 'g');

UPDATE edu_plan_sub_domains p
SET curriculum_sub_domain_id = x.id
FROM (SELECT DISTINCT ON (pg.sub_domain_id) pg.sub_domain_id AS plan_sub_id, s.id, s.text
      FROM edu_plan_goals pg
      JOIN edu_curriculum_goals cg ON cg.id = pg.curriculum_goal_id
      JOIN edu_curriculum_expected_results r ON r.id = cg.expected_result_id
      JOIN edu_curriculum_sub_domains s ON s.id = r.sub_domain_id
      WHERE pg.sub_domain_id IS NOT NULL
      ORDER BY pg.sub_domain_id, pg.sort_order) x
WHERE x.plan_sub_id = p.id
  AND regexp_replace(btrim(p.text), '\s+', ' ', 'g') = regexp_replace(btrim(x.text), '\s+', ' ', 'g');
