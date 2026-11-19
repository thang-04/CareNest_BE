-- Spec 003 (Tiền duyệt DB-A 10/11/2026): mốc duyệt theo thời gian dạy.
-- Thêm 3 loại tài liệu lớp: semester (gốc gói học kỳ), dossier (gốc hồ sơ chủ đề), day (giáo án ngày tách khỏi giáo án tuần).
-- Chuyển dữ liệu cũ (FR-021..023): mỗi kế hoạch chủ đề có hồ sơ chủ đề cùng trạng thái; phần ngày của giáo án tuần sang
-- dòng day; mỗi lớp + kế hoạch tổ đang hiệu lực + học kỳ đã cấu hình có gói học kỳ.

ALTER TABLE edu_class_docs ADD COLUMN semester_no SMALLINT;

ALTER TABLE edu_class_docs DROP CONSTRAINT ck_edu_class_docs_kind;
ALTER TABLE edu_class_docs ADD CONSTRAINT ck_edu_class_docs_kind
    CHECK (kind IN ('branch', 'theme', 'week', 'semester', 'dossier', 'day'));

ALTER TABLE edu_class_docs DROP CONSTRAINT ck_edu_class_docs_shape;
ALTER TABLE edu_class_docs ADD CONSTRAINT ck_edu_class_docs_shape CHECK (
    (kind = 'branch' AND theme_id IS NULL AND week_start IS NULL AND semester_no IS NULL)
    OR (kind = 'semester' AND theme_id IS NULL AND week_start IS NULL AND semester_no IN (1, 2))
    OR (kind IN ('theme', 'dossier') AND theme_id IS NOT NULL AND week_start IS NULL AND semester_no IS NULL)
    OR (kind IN ('week', 'day') AND theme_id IS NOT NULL AND week_start IS NOT NULL AND semester_no IS NULL
        AND EXTRACT(ISODOW FROM week_start) = 1));

DROP INDEX uq_edu_class_docs_natural_team;
CREATE UNIQUE INDEX uq_edu_class_docs_natural_team ON edu_class_docs
    (classroom_id, team_plan_id, kind, COALESCE(theme_id, 0), COALESCE(week_start, DATE '1970-01-01'),
     COALESCE(semester_no, 0));
DROP INDEX uq_edu_class_docs_natural_sy;
CREATE UNIQUE INDEX uq_edu_class_docs_natural_sy ON edu_class_docs
    (classroom_id, school_year_plan_id, kind, COALESCE(theme_id, 0), COALESCE(week_start, DATE '1970-01-01'),
     COALESCE(semester_no, 0));

-- 1. Hồ sơ chủ đề: trạng thái, chuỗi duyệt, lịch sử lấy từ kế hoạch chủ đề (gốc cũ); cách gửi "theo chủ đề"
INSERT INTO edu_class_docs (doc_key, kind, classroom_id, team_plan_id, theme_id, status, content, approvals, history,
                            created_by, updated_by, created_at, updated_at, version, sign_chain, review_round,
                            applied_policy_revision, completion_policy, print_snapshot, completion_mode, completed_at,
                            author_tracking_since, author_coverage)
SELECT 'ds_' || t.classroom_id || '_' || t.theme_id, 'dossier', t.classroom_id, t.team_plan_id, t.theme_id, t.status,
       '{}'::jsonb, t.approvals, t.history, t.created_by, t.updated_by, t.created_at, t.updated_at, t.version,
       t.sign_chain, t.review_round, t.applied_policy_revision, t.completion_policy, t.print_snapshot,
       t.completion_mode, t.completed_at, t.author_tracking_since, t.author_coverage
FROM edu_class_docs t
WHERE t.kind = 'theme';

UPDATE edu_class_doc_releases r
SET class_doc_id = d.id
FROM edu_class_docs t
JOIN edu_class_docs d ON d.doc_key = 'ds_' || t.classroom_id || '_' || t.theme_id
WHERE r.class_doc_id = t.id AND t.kind = 'theme';

ALTER TABLE edu_class_docs DROP CONSTRAINT ck_edu_class_docs_adjustments_week;

-- 2. Giáo án ngày: days, dayGoals và "điều chỉnh sau dạy" (EDU-27) chuyển sang dòng day cùng trạng thái
INSERT INTO edu_class_docs (doc_key, kind, classroom_id, team_plan_id, theme_id, week_start, status, content, approvals,
                            history, adjustments, created_by, updated_by, created_at, updated_at, version, sign_chain,
                            review_round, applied_policy_revision, completion_policy, completion_mode, completed_at,
                            author_tracking_since, author_coverage, source_doc_id)
SELECT 'dd_' || substring(w.doc_key FROM 4), 'day', w.classroom_id, w.team_plan_id, w.theme_id, w.week_start, w.status,
       jsonb_strip_nulls(jsonb_build_object('days', w.content -> 'days', 'dayGoals', w.content -> 'dayGoals')),
       w.approvals, w.history, w.adjustments, w.created_by, w.updated_by, w.created_at, w.updated_at, w.version,
       w.sign_chain, w.review_round, w.applied_policy_revision, w.completion_policy, w.completion_mode,
       w.completed_at, w.author_tracking_since, w.author_coverage, NULL
FROM edu_class_docs w
WHERE w.kind = 'week';

UPDATE edu_class_docs
SET content = content - 'days' - 'dayGoals',
    adjustments = '{}'::jsonb
WHERE kind = 'week';

ALTER TABLE edu_class_docs ADD CONSTRAINT ck_edu_class_docs_adjustments_day
    CHECK (kind = 'day' OR adjustments = '{}'::jsonb);

-- 3. Gói học kỳ cho lớp + kế hoạch tổ đang hiệu lực (không có bản duyệt mới hơn cùng năm + nhóm tuổi)
WITH active_plans AS (
    SELECT p.id, p.school_year_id
    FROM edu_team_plans p
    WHERE NOT EXISTS (SELECT 1 FROM edu_team_plans n
                      WHERE n.school_year_id = p.school_year_id AND n.age_group_id = p.age_group_id
                        AND n.status = 'APPROVED' AND n.version_no > p.version_no)
),
semesters AS (
    SELECT y.id AS school_year_id, 1 AS no, y.semester1_start AS s, y.semester1_end AS e FROM school_years y
    WHERE y.semester1_start IS NOT NULL
    UNION ALL
    SELECT y.id, 2, y.semester2_start, y.semester2_end FROM school_years y WHERE y.semester2_start IS NOT NULL
),
targets AS (
    SELECT DISTINCT d.classroom_id, d.team_plan_id, sm.no, sm.s, sm.e
    FROM edu_class_docs d
    JOIN active_plans ap ON ap.id = d.team_plan_id
    JOIN semesters sm ON sm.school_year_id = ap.school_year_id
),
picked AS (
    SELECT tg.*,
           (SELECT t.id FROM edu_class_docs t JOIN edu_themes th ON th.id = t.theme_id
            WHERE t.kind = 'theme' AND t.status = 'APPROVED' AND t.classroom_id = tg.classroom_id
              AND t.team_plan_id = tg.team_plan_id AND th.start_date BETWEEN tg.s AND tg.e
            ORDER BY t.completed_at NULLS LAST, t.id LIMIT 1) AS approved_theme_id,
           (SELECT min(d.id) FROM edu_class_docs d
            WHERE d.classroom_id = tg.classroom_id AND d.team_plan_id = tg.team_plan_id) AS first_doc_id
    FROM targets tg
)
INSERT INTO edu_class_docs (doc_key, kind, classroom_id, team_plan_id, semester_no, status, content, created_by,
                            updated_by, completed_at, completion_mode)
SELECT 'sp_' || pk.classroom_id || '_' || pk.team_plan_id || '_' || pk.no, 'semester', pk.classroom_id,
       pk.team_plan_id, pk.no,
       CASE WHEN pk.approved_theme_id IS NULL THEN 'DRAFT' ELSE 'APPROVED' END, '{}'::jsonb,
       f.created_by, COALESCE(a.updated_by, f.updated_by),
       a.completed_at, CASE WHEN pk.approved_theme_id IS NULL THEN NULL ELSE 'MIGRATED_V96' END
FROM picked pk
JOIN edu_class_docs f ON f.id = pk.first_doc_id
LEFT JOIN edu_class_docs a ON a.id = pk.approved_theme_id;

-- Bản ban hành của gói học kỳ đã coi là duyệt: tên nhánh các tuần thuộc kỳ (người chỉ xem đọc từ đây, FR-015)
INSERT INTO edu_class_doc_releases (class_doc_id, dossier_version, print_snapshot, approved_by, released_at)
SELECT sp.id, 0,
       jsonb_build_object('signatures', NULL, 'members', jsonb_build_array(jsonb_build_object(
           'kind', 'branch',
           'names', COALESCE((SELECT jsonb_object_agg(k, v) FROM jsonb_each(b.content -> 'names') AS x(k, v)
                              WHERE k ~ '^\d{4}-\d{2}-\d{2}$' AND k::date BETWEEN sm.s AND sm.e), '{}'::jsonb)))),
       sp.updated_by, COALESCE(sp.completed_at, now())
FROM edu_class_docs sp
JOIN edu_team_plans p ON p.id = sp.team_plan_id
JOIN LATERAL (SELECT CASE sp.semester_no WHEN 1 THEN y.semester1_start ELSE y.semester2_start END AS s,
                     CASE sp.semester_no WHEN 1 THEN y.semester1_end ELSE y.semester2_end END AS e
              FROM school_years y WHERE y.id = p.school_year_id) sm ON TRUE
LEFT JOIN edu_class_docs b ON b.kind = 'branch' AND b.classroom_id = sp.classroom_id AND b.team_plan_id = sp.team_plan_id
WHERE sp.kind = 'semester' AND sp.status = 'APPROVED';
