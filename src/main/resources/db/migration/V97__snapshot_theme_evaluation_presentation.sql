ALTER TABLE theme_evaluations
    ADD COLUMN presentation_snapshot JSONB;

UPDATE theme_evaluations e
SET presentation_snapshot = jsonb_build_object(
    'origin', 'LEGACY_BASELINE',
    'goals', COALESCE((SELECT jsonb_agg(jsonb_build_object(
        'id', g.id, 'lineage', COALESCE(g.lineage_id, g.id), 'goalNo', g.goal_no, 'text', g.text)
        ORDER BY g.goal_no, g.sort_order, g.id)
        FROM edu_plan_goals g WHERE EXISTS (
            SELECT 1 FROM edu_goal_contents c JOIN edu_content_theme_ticks k ON k.content_id = c.id
            JOIN edu_themes t ON t.id = k.theme_id
            WHERE c.goal_id = g.id AND COALESCE(t.lineage_id, t.id) = e.theme_id
              AND t.year_plan_id = (SELECT t2.year_plan_id FROM edu_themes t2
                JOIN edu_year_plans p ON p.id = t2.year_plan_id
                WHERE COALESCE(t2.lineage_id, t2.id) = e.theme_id AND p.status = 'APPROVED'
                ORDER BY p.version_no DESC, p.id DESC LIMIT 1))), '[]'::jsonb),
    'children', COALESCE((SELECT jsonb_agg(jsonb_build_object(
        'id', c.id, 'fullName', c.full_name, 'dateOfBirth', c.date_of_birth) ORDER BY c.full_name)
        FROM children c WHERE EXISTS (SELECT 1 FROM jsonb_array_elements(e.cells::jsonb) cell
            WHERE (cell->>'c')::bigint = c.id)), '[]'::jsonb))
WHERE e.confirmed_at IS NOT NULL AND e.presentation_snapshot IS NULL;
