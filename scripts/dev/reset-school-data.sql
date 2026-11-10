-- CHỈ DÙNG TRÊN DB DEV (Cần chốt 30/10/2026): xóa sạch dữ liệu Cấu hình trường, Điểm danh & suất ăn, Đánh giá trẻ
-- để test lại từ đầu bằng màn Thiết lập năm học. Chạy pg_dump backup TRƯỚC. Không commit file này.
-- GIỮ: tài khoản (user_accounts), điểm trường, nhóm tuổi, phân công Hiệu trưởng / PHT / bếp, hồ sơ trẻ + phụ huynh,
--       danh mục chương trình GDMN (edu_curriculum_*, edu_domains, edu_settings).
-- XÓA kèm do khóa ngoại: trẻ thuộc lớp (enrollments), kế hoạch giáo dục năm + hồ sơ lớp + học liệu.
BEGIN;

-- Đánh giá trẻ
DELETE FROM reward_lists;            -- items, events xóa theo (ON DELETE CASCADE)
DELETE FROM child_evaluations;       -- events xóa theo
DELETE FROM behaviour_tickets;
DELETE FROM class_daily_comments;
DELETE FROM daily_activity_results;
DELETE FROM daily_observations;
DELETE FROM assessment_day_unlocks;

-- Điểm danh, sức khỏe, suất ăn
DELETE FROM meal_counts;             -- lines, history xóa theo
DELETE FROM daily_health_entries;
DELETE FROM attendance_records;      -- history xóa theo
DELETE FROM attendance_day_unlocks;

-- Kế hoạch giáo dục gắn với năm học / lớp (khóa ngoại bắt buộc xóa)
DELETE FROM edu_learning_resources;
DELETE FROM edu_class_docs;
UPDATE edu_year_plans SET source_plan_id = NULL;
DELETE FROM edu_year_plans;          -- goal rows, themes, breaks, events, approvals xóa theo

-- Trẻ thuộc lớp và xếp lớp năm mới (hồ sơ trẻ giữ lại)
DELETE FROM class_placements;
DELETE FROM enrollments;

-- Cấu hình trường: phân công GV / tổ trưởng, giờ chốt, lớp, phòng, năm học
DELETE FROM staff_assignments WHERE staff_role IN ('TEACHER', 'AGE_GROUP_TEAM_LEADER');
UPDATE staff_assignments SET school_year_id = NULL WHERE school_year_id IS NOT NULL;
DELETE FROM attendance_cutoffs;
DELETE FROM classrooms;
DELETE FROM rooms;
DELETE FROM school_years;            -- ảnh chụp school_year_rooms / campuses / age_groups xóa theo

-- Kiểm tra: các bảng đã xóa phải về 0, phần giữ lại vẫn còn
SELECT 'school_years' AS bang, count(*) FROM school_years
UNION ALL SELECT 'classrooms', count(*) FROM classrooms
UNION ALL SELECT 'rooms', count(*) FROM rooms
UNION ALL SELECT 'attendance_records', count(*) FROM attendance_records
UNION ALL SELECT 'daily_observations', count(*) FROM daily_observations
UNION ALL SELECT 'enrollments', count(*) FROM enrollments
UNION ALL SELECT 'GIU campuses', count(*) FROM campuses
UNION ALL SELECT 'GIU age_groups', count(*) FROM age_groups
UNION ALL SELECT 'GIU user_accounts', count(*) FROM user_accounts
UNION ALL SELECT 'GIU phan cong HT/PHT/bep', count(*) FROM staff_assignments
UNION ALL SELECT 'GIU children', count(*) FROM children;

COMMIT;
