-- Dữ liệu giả cho màn thông báo; không tạo token thiết bị hoặc tệp S3 giả.
-- Lịch mẫu tắt và thông báo đã hủy, không tự gửi push khi khởi động dev.
INSERT INTO announcement_templates (name, title, body, created_by, created_at, updated_at)
SELECT 'Mẫu thông báo sinh hoạt', 'Thông báo sinh hoạt', 'Nội dung thông báo mẫu.', u.id, now(), now()
FROM user_accounts u
WHERE u.primary_role = 'PRINCIPAL'
  AND NOT EXISTS (SELECT 1 FROM announcement_templates t WHERE t.name = 'Mẫu thông báo sinh hoạt')
ORDER BY u.id LIMIT 1;

INSERT INTO school_announcements (sender_id, title, body, scope_type, roles, status, send_at, recipient_count, created_at, updated_at)
SELECT u.id, 'Thông báo sinh hoạt mẫu', 'Nội dung thông báo mẫu.', 'SCHOOL', 'PARENT', 'CANCELLED',
       '2099-01-01T00:00:00Z'::timestamptz, 0, now(), now()
FROM user_accounts u
WHERE u.primary_role = 'PRINCIPAL'
  AND NOT EXISTS (SELECT 1 FROM school_announcements a WHERE a.title = 'Thông báo sinh hoạt mẫu')
ORDER BY u.id LIMIT 1;

INSERT INTO announcement_schedules (template_id, scope_type, roles, frequency, day_of_week, send_time, next_run_at, active, created_by, created_at, updated_at)
SELECT t.id, 'SCHOOL', 'PARENT', 'WEEKLY', 1, '08:00'::time, '2099-01-05T01:00:00Z'::timestamptz, false, t.created_by, now(), now()
FROM announcement_templates t
WHERE t.name = 'Mẫu thông báo sinh hoạt'
  AND NOT EXISTS (SELECT 1 FROM announcement_schedules s WHERE s.template_id = t.id)
ORDER BY t.id LIMIT 1;

-- Bản ghi rule minh họa dùng đúng cấu hình mặc định; không tắt sự kiện.
INSERT INTO notification_rules (type, enabled, title_template, body_template, updated_by, updated_at)
SELECT 'MENU_PUBLISHED', true, NULL, NULL, u.id, now()
FROM user_accounts u
WHERE u.primary_role = 'PRINCIPAL'
  AND NOT EXISTS (SELECT 1 FROM notification_rules r WHERE r.type = 'MENU_PUBLISHED')
ORDER BY u.id LIMIT 1;
