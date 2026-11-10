-- Bản sao role Keycloak để lọc danh sách tài khoản và kiểm phạm vi quản lý nhân sự (ADR-0017).
-- Keycloak vẫn là nguồn role của token; cột chỉ được ghi khi CareNest tạo tài khoản. NULL = chưa biết (chỉ Admin quản lý).
ALTER TABLE user_accounts ADD COLUMN primary_role VARCHAR(20);
ALTER TABLE user_accounts ADD CONSTRAINT ck_user_accounts_primary_role CHECK (
    primary_role IS NULL
    OR primary_role IN ('ADMIN', 'PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER', 'KITCHEN_STAFF', 'PARENT')
);
CREATE INDEX idx_user_accounts_primary_role ON user_accounts (primary_role);

-- Tài khoản đã có: suy từ phân công (ưu tiên vai trò cao nhất), không có phân công mà có liên kết trẻ ⇒ PARENT
UPDATE user_accounts u
SET primary_role = (
    SELECT a.staff_role FROM staff_assignments a
    WHERE a.user_id = u.id
    ORDER BY CASE a.staff_role
                 WHEN 'PRINCIPAL' THEN 1 WHEN 'VICE_PRINCIPAL' THEN 2 WHEN 'TEACHER' THEN 3 ELSE 4 END
    LIMIT 1)
WHERE u.primary_role IS NULL;

UPDATE user_accounts u SET primary_role = 'PARENT'
WHERE u.primary_role IS NULL AND EXISTS (SELECT 1 FROM guardian_child_links g WHERE g.user_id = u.id);

-- Quyền phân công mới: PHT mặc định được phân công GV/bếp trong campus mình (HT tắt được — AUTH-16)
INSERT INTO role_permissions (role, permission_code) VALUES ('VICE_PRINCIPAL', 'STAFF_ASSIGNMENT_MANAGE');
