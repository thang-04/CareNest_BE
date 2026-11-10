-- AUTH-20 (chốt 31/10): GRANT mã quyền sửa theo mảng có phạm vi CAMPUS (điểm trường phân công) hoặc SCHOOL (mọi điểm trường).
-- REVOKE không có phạm vi; GRANT có sẵn trước đây giữ hành vi cũ = CAMPUS.
ALTER TABLE user_permission_overrides ADD COLUMN scope VARCHAR(10);
UPDATE user_permission_overrides SET scope = 'CAMPUS' WHERE effect = 'GRANT';
ALTER TABLE user_permission_overrides
    ADD CONSTRAINT ck_user_permission_overrides_scope
        CHECK ((effect = 'GRANT' AND scope IN ('CAMPUS', 'SCHOOL')) OR (effect = 'REVOKE' AND scope IS NULL));

-- Lịch sử ghi cả đổi phạm vi (AUTH-14); NULL = không có ngoại lệ GRANT ở phía đó.
ALTER TABLE permission_change_log ADD COLUMN before_scope VARCHAR(10);
ALTER TABLE permission_change_log ADD COLUMN after_scope VARCHAR(10);
ALTER TABLE permission_change_log
    ADD CONSTRAINT ck_permission_change_log_scopes
        CHECK ((before_scope IS NULL OR before_scope IN ('CAMPUS', 'SCHOOL'))
            AND (after_scope IS NULL OR after_scope IN ('CAMPUS', 'SCHOOL')));

-- NUT-02: PHT có phân công vẫn xác nhận/điều chỉnh số suất cơ sở mình như trước; HT thu hồi hoặc nâng phạm vi.
INSERT INTO role_permissions (role, permission_code) VALUES ('VICE_PRINCIPAL', 'MEAL_COUNT_CONFIRM');
