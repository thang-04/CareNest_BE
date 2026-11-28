-- EDU-108: quyền kế hoạch giáo dục vào ma trận quyền động, mặc định giữ hành vi hiện tại.
-- Chỉ thêm dòng role vào permission_grants (bảng ma trận từ V122); HT là role cố định nên không có dòng.
INSERT INTO permission_grants (role, permission_code) VALUES
    ('VICE_PRINCIPAL', 'EDU_SCHOOL_YEAR_PLAN_EDIT'),
    ('VICE_PRINCIPAL', 'EDU_SCHOOL_YEAR_PLAN_APPROVE'),
    ('VICE_PRINCIPAL', 'EDU_TEAM_PLAN_EDIT'),
    ('VICE_PRINCIPAL', 'EDU_TEAM_PLAN_APPROVE'),
    ('VICE_PRINCIPAL', 'EDU_SEMESTER_PACKAGE_EDIT'),
    ('VICE_PRINCIPAL', 'EDU_SEMESTER_PACKAGE_APPROVE'),
    ('VICE_PRINCIPAL', 'EDU_THEME_PLAN_EDIT'),
    ('VICE_PRINCIPAL', 'EDU_THEME_PLAN_APPROVE'),
    ('TEACHER', 'EDU_SEMESTER_PACKAGE_EDIT'),
    ('TEACHER', 'EDU_THEME_PLAN_EDIT'),
    ('TEAM_LEADER', 'EDU_TEAM_PLAN_EDIT'),
    ('TEAM_LEADER', 'EDU_SEMESTER_PACKAGE_EDIT'),
    ('TEAM_LEADER', 'EDU_SEMESTER_PACKAGE_APPROVE'),
    ('TEAM_LEADER', 'EDU_THEME_PLAN_EDIT'),
    ('TEAM_LEADER', 'EDU_THEME_PLAN_APPROVE')
ON CONFLICT (role, permission_code) WHERE role IS NOT NULL DO NOTHING;
