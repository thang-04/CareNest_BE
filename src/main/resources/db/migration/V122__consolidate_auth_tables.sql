-- Gộp bảng phân quyền và bỏ bảng liên kết phụ huynh–trẻ trùng dữ liệu (ERD gọn); dữ liệu có sẵn được chuyển trước.

-- 1. ADR-0017: một bảng cho cả ma trận quyền theo role và ngoại lệ từng người.
--    Dòng role (role có giá trị, user_id NULL): có dòng = role có quyền; effect, reason, scope NULL.
--    Dòng người (user_id có giá trị, role NULL): GRANT cộng thêm / REVOKE thu hồi phần ma trận; bắt buộc lý do và người
--    cấp, không ai tự tạo ngoại lệ cho mình (AUTH-12); chỉ GRANT có phạm vi CAMPUS/SCHOOL (AUTH-20).
--    granted_by = người bật ô ma trận (NULL với dòng seed) / người sửa ngoại lệ gần nhất.
CREATE TABLE permission_grants (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    role            VARCHAR(20),
    user_id         BIGINT,
    permission_code VARCHAR(64)  NOT NULL,
    effect          VARCHAR(10),
    scope           VARCHAR(10),
    reason          VARCHAR(500),
    granted_by      BIGINT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_permission_grants_user_id FOREIGN KEY (user_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_permission_grants_granted_by FOREIGN KEY (granted_by) REFERENCES user_accounts (id),
    CONSTRAINT ck_permission_grants_subject CHECK ((role IS NULL) <> (user_id IS NULL)),
    CONSTRAINT ck_permission_grants_role
        CHECK (role IS NULL OR role IN ('VICE_PRINCIPAL', 'TEACHER', 'KITCHEN_STAFF', 'TEAM_LEADER')),
    CONSTRAINT ck_permission_grants_role_row CHECK (role IS NULL OR (effect IS NULL AND scope IS NULL AND reason IS NULL)),
    CONSTRAINT ck_permission_grants_user_row CHECK (
        user_id IS NULL
        OR (effect IN ('GRANT', 'REVOKE') AND reason IS NOT NULL AND granted_by IS NOT NULL AND granted_by <> user_id)),
    CONSTRAINT ck_permission_grants_scope CHECK (
        (effect IS NULL AND scope IS NULL)
        OR (effect = 'GRANT' AND scope IN ('CAMPUS', 'SCHOOL'))
        OR (effect = 'REVOKE' AND scope IS NULL))
);
CREATE UNIQUE INDEX uk_permission_grants_role_permission ON permission_grants (role, permission_code)
    WHERE role IS NOT NULL;
CREATE UNIQUE INDEX uk_permission_grants_user_permission ON permission_grants (user_id, permission_code)
    WHERE user_id IS NOT NULL;

INSERT INTO permission_grants (role, permission_code, granted_by, created_at, updated_at)
SELECT role, permission_code, updated_by, updated_at, updated_at FROM role_permissions;
INSERT INTO permission_grants (user_id, permission_code, effect, scope, reason, granted_by, created_at, updated_at)
SELECT user_id, permission_code, effect, scope, reason, created_by, created_at, updated_at FROM user_permission_overrides;
DROP TABLE role_permissions;
DROP TABLE user_permission_overrides;

-- 2. AUTH-04: liên kết tài khoản phụ huynh–trẻ (nguồn scope phụ huynh) = phụ huynh trên hồ sơ có user_id, kèm thời điểm
--    liên kết. Mọi liên kết luôn được tạo cùng dòng hồ sơ (V63 đã chuyển liên kết cũ), nên chỉ cần lấy thời điểm.
--    Dòng còn user_id mà không còn liên kết (đã gỡ phụ huynh trước đây, AUTH-19) ⇒ gỡ luôn user_id cho nhất quán.
ALTER TABLE child_guardians ADD COLUMN linked_at TIMESTAMPTZ;
-- Hồ sơ cũ có thể thiếu CCCD: bỏ tạm ràng buộc NOT VALID để cập nhật được, gắn lại NOT VALID như V81
ALTER TABLE child_guardians DROP CONSTRAINT ck_child_guardians_citizen_id_required;
UPDATE child_guardians g
SET linked_at = l.created_at
FROM guardian_child_links l
WHERE l.child_id = g.child_id AND l.user_id = g.user_id;
UPDATE child_guardians SET user_id = NULL WHERE user_id IS NOT NULL AND linked_at IS NULL;
ALTER TABLE child_guardians
    ADD CONSTRAINT ck_child_guardians_linked CHECK ((user_id IS NULL) = (linked_at IS NULL));
ALTER TABLE child_guardians
    ADD CONSTRAINT ck_child_guardians_citizen_id_required CHECK (citizen_id IS NOT NULL) NOT VALID;
DROP TABLE guardian_child_links;
