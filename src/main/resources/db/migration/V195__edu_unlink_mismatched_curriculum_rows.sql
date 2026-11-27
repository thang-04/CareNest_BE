-- EDU-98 + V176 (Tiền chốt 25/11/2026): nhóm chuẩn 25–36 tháng không ghép nguồn nhà trẻ 24–36 của Bộ.
-- Kế hoạch tạo trước V176 còn dòng trỏ danh mục Bộ khác khoảng tháng của nhóm tuổi ⇒ không lưu được (7030).
-- Gỡ liên kết danh mục ở các dòng đó, giữ nguyên chữ: dòng trở thành dòng tự soạn của kế hoạch.
CREATE TEMP TABLE edu_plan_age ON COMMIT DROP AS
SELECT p.id, 'y' AS k, a.age_from_months, a.age_to_months
FROM edu_year_plans p JOIN age_groups a ON a.id = p.age_group_id
UNION ALL
SELECT p.id, 't', a.age_from_months, a.age_to_months
FROM edu_team_plans p JOIN age_groups a ON a.id = p.age_group_id;

UPDATE edu_plan_goals g
SET curriculum_goal_id = NULL
FROM edu_curriculum_goals c, edu_plan_age pa
WHERE c.id = g.curriculum_goal_id
  AND ((pa.k = 'y' AND pa.id = g.school_year_plan_id) OR (pa.k = 't' AND pa.id = g.team_plan_id))
  AND (c.age_from_months, c.age_to_months) <> (pa.age_from_months, pa.age_to_months);

UPDATE edu_plan_expected_results g
SET curriculum_expected_result_id = NULL
FROM edu_curriculum_expected_results c, edu_plan_age pa
WHERE c.id = g.curriculum_expected_result_id
  AND ((pa.k = 'y' AND pa.id = g.school_year_plan_id) OR (pa.k = 't' AND pa.id = g.team_plan_id))
  AND (c.age_from_months, c.age_to_months) <> (pa.age_from_months, pa.age_to_months);

UPDATE edu_plan_sub_domains g
SET curriculum_sub_domain_id = NULL
FROM edu_curriculum_sub_domains c, edu_plan_age pa
WHERE c.id = g.curriculum_sub_domain_id
  AND ((pa.k = 'y' AND pa.id = g.school_year_plan_id) OR (pa.k = 't' AND pa.id = g.team_plan_id))
  AND (c.age_from_months, c.age_to_months) <> (pa.age_from_months, pa.age_to_months);
