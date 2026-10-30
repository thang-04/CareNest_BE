-- Tài khoản phụ huynh gắn trẻ (AUTH-18, AUTH-19). PHT mặc định được cấp tài khoản phụ huynh cho trẻ campus mình.
INSERT INTO role_permissions (role, permission_code) VALUES ('VICE_PRINCIPAL', 'PARENT_ACCOUNT_MANAGE');

-- Quan hệ phụ huynh–trẻ là danh sách cố định (giá trị seed cũ MOTHER/FATHER thuộc danh sách)
ALTER TABLE guardian_child_links ADD CONSTRAINT ck_guardian_child_links_relationship
    CHECK (relationship IN ('MOTHER', 'FATHER', 'GUARDIAN', 'OTHER'));
