-- Chỉ chạy trên DB dev/test, với 2 lớp và 2 tài khoản GV thử nghiệm đã được phân công.
-- psql <connection> -v class_a=... -v class_b=... -v teacher_a=... -v teacher_b=... -f <file này>
-- Không tạo tài khoản/mật khẩu; không sửa dữ liệu trẻ hiện có. Mỗi lần chạy tạo một trẻ TEST mới.
\set ON_ERROR_STOP on
\if :{?as_of}
\else
SELECT CURRENT_DATE AS as_of \gset
\endif
BEGIN;
SELECT id, campus_id, school_year_id FROM classrooms WHERE id = :'class_a'::bigint \gset a_
SELECT id, campus_id, school_year_id FROM classrooms WHERE id = :'class_b'::bigint \gset b_
SELECT (:'a_id'::bigint <> :'b_id'::bigint
    AND :'a_school_year_id'::bigint = :'b_school_year_id'::bigint
    AND EXISTS (SELECT 1 FROM school_years WHERE id = :'a_school_year_id'::bigint
        AND status = 'ACTIVE' AND start_date <= '2026-10-26' AND end_date >= :'as_of'::date)
    AND :'as_of'::date > '2026-10-30'
    AND EXISTS (SELECT 1 FROM staff_assignments WHERE user_id = :'teacher_a'::bigint
        AND staff_role = 'TEACHER' AND classroom_id = :'class_a'::bigint
        AND valid_from <= :'as_of'::date AND (valid_to IS NULL OR valid_to >= :'as_of'::date))
    AND EXISTS (SELECT 1 FROM staff_assignments WHERE user_id = :'teacher_b'::bigint
        AND staff_role = 'TEACHER' AND classroom_id = :'class_b'::bigint
        AND valid_from <= :'as_of'::date AND (valid_to IS NULL OR valid_to >= :'as_of'::date))) AS fixture_valid \gset
\if :fixture_valid
\else
    ROLLBACK;
    \echo 'Fixture requires two distinct classes in the same active school year covering October 2026, correctly assigned test teachers, and a date after 30/10/2026.'
    DO $$ BEGIN RAISE EXCEPTION 'Invalid assessment-continuity fixture parameters'; END $$;
\endif

INSERT INTO children (full_name, date_of_birth)
VALUES ('TEST Lịch sử ' || to_char(clock_timestamp(), 'YYYYMMDD-HH24MISS-MS'), '2023-01-01') RETURNING id \gset test_child_
INSERT INTO enrollments (child_id, classroom_id, valid_from, valid_to) VALUES
    (:'test_child_id', :'class_a', '2026-10-01', '2026-10-28'),
    (:'test_child_id', :'class_b', '2026-10-29', NULL);
INSERT INTO attendance_records (child_id, classroom_id, campus_id, date, status, meals, recorded_by, recorded_at)
SELECT :'test_child_id',
    CASE WHEN day::date < '2026-10-29' THEN :'class_a'::bigint ELSE :'class_b'::bigint END,
    CASE WHEN day::date < '2026-10-29' THEN :'a_campus_id'::bigint ELSE :'b_campus_id'::bigint END,
    day::date, 'PRESENT', 'LUNCH',
    CASE WHEN day::date < '2026-10-29' THEN :'teacher_a'::bigint ELSE :'teacher_b'::bigint END, now()
FROM generate_series('2026-10-26'::date, '2026-10-30'::date, '1 day') day;
INSERT INTO daily_observations (child_id, classroom_id, campus_id, date, meals, behaviors, flag, comment, recorded_by, recorded_at)
SELECT child_id, classroom_id, campus_id, date, '{}'::jsonb, '',
    date IN ('2026-10-26', '2026-10-27', '2026-10-29'),
    'TEST - quan sát tại lớp gốc ngày ' || date::text, recorded_by, now()
FROM attendance_records WHERE child_id = :'test_child_id'::bigint;
INSERT INTO child_evaluations (child_id, classroom_id, campus_id, school_year_id, period_type, period_start, period_end,
    status, draft_content, draft_at, final_content, confirmed_by, confirmed_at, created_at, updated_at)
VALUES (:'test_child_id', :'class_a', :'a_campus_id', :'a_school_year_id', 'MONTH', '2026-10-01', '2026-10-31',
    'CONFIRMED', 'TEST bản nháp riêng của giáo viên lớp A', now(),
    'TEST nhận xét đã xác nhận của lớp A: giữ nguyên nội dung khi trẻ chuyển sang lớp B.', :'teacher_a', now(), now(), now())
RETURNING id \gset test_evaluation_
COMMIT;
SELECT :'test_child_id' AS child_id, :'test_evaluation_id' AS old_confirmed_evaluation_id,
    '2026-10-26' AS week_start, '2026-10-01' AS month_start,
    5 AS weekly_present, 3 AS weekly_flags, 2 AS class_a_flags, 1 AS class_b_flags;
