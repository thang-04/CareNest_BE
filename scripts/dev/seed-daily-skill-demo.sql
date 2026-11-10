-- CHỈ DÙNG TRÊN DB DEV: giáo án ngày mẫu để kiểm thử đánh giá kiến thức/kỹ năng.
-- Giáo án tuần là hồ sơ chứa các ô hoạt động theo ngày; màn đánh giá chỉ đọc đúng ô của ngày 09/11/2026.
-- Idempotent: chạy lại sẽ cập nhật đúng hồ sơ demo, không tạo bản trùng.
BEGIN;

WITH target AS (
    SELECT c.id AS classroom_id,
           p.id AS year_plan_id,
           th.id AS theme_id,
           teacher.user_id AS teacher_id
    FROM classrooms c
    JOIN LATERAL (
        SELECT yp.id
        FROM edu_year_plans yp
        WHERE yp.school_year_id = c.school_year_id
          AND yp.age_group_id = c.age_group_id
          AND yp.status = 'APPROVED'
        ORDER BY yp.version_no DESC, yp.id DESC
        LIMIT 1
    ) p ON TRUE
    JOIN LATERAL (
        SELECT t.id
        FROM edu_themes t
        WHERE t.year_plan_id = p.id
          AND DATE '2026-11-09' BETWEEN t.start_date AND t.end_date
        ORDER BY t.sort_order, t.id
        LIMIT 1
    ) th ON TRUE
    JOIN LATERAL (
        SELECT sa.user_id
        FROM staff_assignments sa
        WHERE sa.classroom_id = c.id
          AND sa.staff_role = 'TEACHER'
          AND sa.valid_from <= DATE '2026-11-09'
          AND (sa.valid_to IS NULL OR sa.valid_to >= DATE '2026-11-09')
        ORDER BY sa.id
        LIMIT 1
    ) teacher ON TRUE
    WHERE c.id = 2
), payload AS (
    SELECT t.*,
           jsonb_build_object(
               'weekNo', 10,
               'grid', jsonb_build_object(
                   'hoc', jsonb_build_object(
                       'days', jsonb_build_array('Khám phá các bộ phận cơ thể bé', '', '', '', '')
                   ),
                   'ngoai_troi', jsonb_build_object(
                       'days', jsonb_build_array('Quan sát và gọi tên đồ vật trong sân trường', '', '', '', '')
                   ),
                   'goc', jsonb_build_object(
                       'allWeek', true,
                       'common', 'Chơi góc phân vai gia đình và xây dựng'
                   ),
                   'chieu', jsonb_build_object(
                       'days', jsonb_build_array('Ôn kỹ năng chào hỏi và tự phục vụ', '', '', '', '')
                   )
               ),
               'picks', jsonb_build_object(
                   'hoc.0', COALESCE((
                       SELECT jsonb_agg(jsonb_build_object(
                           'id', 'demo-hoc-' || g.id,
                           'goalId', g.id::text
                       ) ORDER BY g.sort_order, g.id)
                       FROM (
                           SELECT pg.id, pg.sort_order
                           FROM edu_plan_goals pg
                           WHERE pg.year_plan_id = t.year_plan_id
                           ORDER BY pg.sort_order, pg.id
                           LIMIT 2
                       ) g
                   ), '[]'::jsonb),
                   'ngoai_troi.0', COALESCE((
                       SELECT jsonb_agg(jsonb_build_object(
                           'id', 'demo-ngoai-troi-' || g.id,
                           'goalId', g.id::text
                       ))
                       FROM (
                           SELECT pg.id
                           FROM edu_plan_goals pg
                           WHERE pg.year_plan_id = t.year_plan_id
                           ORDER BY pg.sort_order, pg.id
                           OFFSET 2 LIMIT 1
                       ) g
                   ), '[]'::jsonb),
                   'goc.all', COALESCE((
                       SELECT jsonb_agg(jsonb_build_object(
                           'id', 'demo-goc-' || g.id,
                           'goalId', g.id::text
                       ))
                       FROM (
                           SELECT pg.id
                           FROM edu_plan_goals pg
                           WHERE pg.year_plan_id = t.year_plan_id
                           ORDER BY pg.sort_order, pg.id
                           OFFSET 3 LIMIT 1
                       ) g
                   ), '[]'::jsonb),
                   'chieu.0', COALESCE((
                       SELECT jsonb_agg(jsonb_build_object(
                           'id', 'demo-chieu-' || g.id,
                           'goalId', g.id::text
                       ))
                       FROM (
                           SELECT pg.id
                           FROM edu_plan_goals pg
                           WHERE pg.year_plan_id = t.year_plan_id
                           ORDER BY pg.sort_order, pg.id
                           OFFSET 4 LIMIT 1
                       ) g
                   ), '[]'::jsonb)
               )
           ) AS content
    FROM target t
)
INSERT INTO edu_class_docs (
    doc_key, kind, classroom_id, year_plan_id, theme_id, week_start,
    status, content, created_by, updated_by, created_at, updated_at
)
SELECT 'wp_' || classroom_id || '_2026-11-09',
       'week', classroom_id, year_plan_id, theme_id, DATE '2026-11-09',
       'APPROVED', content, teacher_id, teacher_id, now(), now()
FROM payload
ON CONFLICT (doc_key) DO UPDATE
SET year_plan_id = EXCLUDED.year_plan_id,
    theme_id = EXCLUDED.theme_id,
    week_start = EXCLUDED.week_start,
    status = EXCLUDED.status,
    content = EXCLUDED.content,
    updated_by = EXCLUDED.updated_by,
    updated_at = now();

COMMIT;
