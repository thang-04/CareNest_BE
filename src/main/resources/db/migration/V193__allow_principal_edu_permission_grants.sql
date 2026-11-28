-- EDU-110 (6): cột Hiệu trưởng tick được 8 mã soạn/duyệt kế hoạch giáo dục.
-- Chỉ nới ràng buộc vai trò của bảng permission_grants để lưu được dòng PRINCIPAL.
-- Không thêm bảng, cột hay dữ liệu.
ALTER TABLE permission_grants DROP CONSTRAINT ck_permission_grants_role;
ALTER TABLE permission_grants
    ADD CONSTRAINT ck_permission_grants_role
        CHECK (role IS NULL OR role IN ('PRINCIPAL', 'VICE_PRINCIPAL', 'TEACHER', 'KITCHEN_STAFF', 'TEAM_LEADER'));
