-- Ma trận quyền có thêm cột TEAM_LEADER: quyền cộng thêm cho GV đang có phân công tổ trưởng (AUTH-23).
-- TEAM_LEADER không phải realm role Keycloak; chưa có quyền nào bật mặc định nên không seed.
ALTER TABLE role_permissions DROP CONSTRAINT ck_role_permissions_role;
ALTER TABLE role_permissions
    ADD CONSTRAINT ck_role_permissions_role
        CHECK (role IN ('VICE_PRINCIPAL', 'TEACHER', 'KITCHEN_STAFF', 'TEAM_LEADER'));
