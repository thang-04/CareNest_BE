-- AUTH-20 (Cần chốt 10/11/2026): GV/tổ trưởng luôn xem đánh giá; ASSESSMENT_VIEW là quyền cố định, không nằm trong ma trận.
DELETE FROM role_permissions
WHERE permission_code = 'ASSESSMENT_VIEW'
  AND role IN ('TEACHER', 'TEAM_LEADER');
