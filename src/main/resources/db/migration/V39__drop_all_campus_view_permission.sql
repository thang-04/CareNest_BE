-- AUTH-20 (chốt 31/10): PHT còn phân công xem dữ liệu trường mọi cơ sở mặc định, mã ALL_CAMPUS_VIEW bị bỏ khỏi enum.
-- Xóa dòng ma trận/ngoại lệ đang trỏ tới mã cũ; permission_change_log giữ nguyên để truy vết.
DELETE FROM role_permissions WHERE permission_code = 'ALL_CAMPUS_VIEW';
DELETE FROM user_permission_overrides WHERE permission_code = 'ALL_CAMPUS_VIEW';
