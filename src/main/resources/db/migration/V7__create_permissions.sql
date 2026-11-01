-- Phân quyền cấu hình được (ADR-0017). Danh mục quyền nằm trong code (enum Permission); bảng chỉ lưu cấu hình.
-- Quyền của ADMIN/PRINCIPAL/PARENT cố định trong code nên ma trận chỉ nhận role cấu hình được (AUTH-11).

-- Ma trận: có dòng = role có quyền đó.
CREATE TABLE role_permissions (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    role            VARCHAR(20) NOT NULL,
    permission_code VARCHAR(64) NOT NULL,
    updated_by      BIGINT,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_role_permissions_updated_by FOREIGN KEY (updated_by) REFERENCES user_accounts (id),
    CONSTRAINT uk_role_permissions_role_permission UNIQUE (role, permission_code),
    CONSTRAINT ck_role_permissions_role CHECK (role IN ('VICE_PRINCIPAL', 'TEACHER', 'KITCHEN_STAFF'))
);

-- Ngoại lệ từng người: GRANT cộng thêm, REVOKE thu hồi phần ma trận. Không ai tự tạo ngoại lệ cho mình (AUTH-12).
CREATE TABLE user_permission_overrides (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    permission_code VARCHAR(64)  NOT NULL,
    effect          VARCHAR(10)  NOT NULL,
    reason          VARCHAR(500) NOT NULL,
    created_by      BIGINT       NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_user_permission_overrides_user_id FOREIGN KEY (user_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_user_permission_overrides_created_by FOREIGN KEY (created_by) REFERENCES user_accounts (id),
    CONSTRAINT uk_user_permission_overrides_user_permission UNIQUE (user_id, permission_code),
    CONSTRAINT ck_user_permission_overrides_effect CHECK (effect IN ('GRANT', 'REVOKE')),
    CONSTRAINT ck_user_permission_overrides_not_self CHECK (created_by <> user_id)
);

-- Lịch sử mọi thay đổi (AUTH-14): mỗi quyền đổi 1 dòng, cùng một lần lưu chung batch_id.
CREATE TABLE permission_change_log (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_id        UUID         NOT NULL,
    actor_user_id   BIGINT       NOT NULL,
    target_role     VARCHAR(20),
    target_user_id  BIGINT,
    permission_code VARCHAR(64)  NOT NULL,
    before_state    VARCHAR(10)  NOT NULL,
    after_state     VARCHAR(10)  NOT NULL,
    reason          VARCHAR(500) NOT NULL,
    changed_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_permission_change_log_actor FOREIGN KEY (actor_user_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_permission_change_log_target_user FOREIGN KEY (target_user_id) REFERENCES user_accounts (id),
    CONSTRAINT ck_permission_change_log_one_target CHECK ((target_role IS NULL) <> (target_user_id IS NULL)),
    CONSTRAINT ck_permission_change_log_before CHECK (before_state IN ('ON', 'OFF', 'GRANT', 'REVOKE', 'INHERIT')),
    CONSTRAINT ck_permission_change_log_after CHECK (after_state IN ('ON', 'OFF', 'GRANT', 'REVOKE', 'INHERIT'))
);
CREATE INDEX idx_permission_change_log_changed_at ON permission_change_log (changed_at DESC);
CREATE INDEX idx_permission_change_log_target_role ON permission_change_log (target_role, changed_at DESC);
CREATE INDEX idx_permission_change_log_target_user ON permission_change_log (target_user_id, changed_at DESC);

-- Seed = đúng hành vi hasRole trước đây: GET /children/{id} mở cho TEACHER, VICE_PRINCIPAL (+ PRINCIPAL, PARENT cố định)
INSERT INTO role_permissions (role, permission_code) VALUES
    ('VICE_PRINCIPAL', 'CHILD_PROFILE_VIEW'),
    ('TEACHER', 'CHILD_PROFILE_VIEW');
