-- G94-180 (user chốt 2026-11-10): Sửa hồ sơ trẻ đổi SĐT của phụ huynh đã có tài khoản không áp dụng ngay mà thành yêu
-- cầu; PHT có quyền quản lý tài khoản phụ huynh ở điểm trường của trẻ (hoặc HT) duyệt thì mới đổi tên đăng nhập, cấp mật
-- khẩu tạm mới và gửi SMS tới số mới. Mỗi tài khoản tối đa một yêu cầu đang chờ.
CREATE TABLE guardian_phone_change_requests (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES user_accounts (id),
    child_id       BIGINT       NOT NULL REFERENCES children (id),
    guardian_id    BIGINT       NOT NULL REFERENCES child_guardians (id),
    old_phone      VARCHAR(10)  NOT NULL,
    new_phone      VARCHAR(10)  NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    reason         VARCHAR(300) NOT NULL,
    requested_by   BIGINT       NOT NULL REFERENCES user_accounts (id),
    requested_at   TIMESTAMPTZ  NOT NULL,
    decided_by     BIGINT REFERENCES user_accounts (id),
    decided_at     TIMESTAMPTZ,
    decision_note  VARCHAR(300),
    sms_status     VARCHAR(10),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_phone_change_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_phone_change_numbers CHECK (old_phone <> new_phone),
    -- Đã quyết định (duyệt/từ chối) thì có người và lúc quyết định; hủy do yêu cầu mới thay thế thì không
    CONSTRAINT ck_phone_change_decision CHECK (
        (status IN ('APPROVED', 'REJECTED')) = (decided_by IS NOT NULL AND decided_at IS NOT NULL)),
    CONSTRAINT ck_phone_change_sms CHECK (sms_status IS NULL OR sms_status IN ('SENT', 'FAILED'))
);

CREATE UNIQUE INDEX uq_phone_change_pending_user ON guardian_phone_change_requests (user_id) WHERE status = 'PENDING';
CREATE INDEX idx_phone_change_child ON guardian_phone_change_requests (child_id);
CREATE INDEX idx_phone_change_status ON guardian_phone_change_requests (status);

-- Thông báo: trẻ nghỉ học (giáo viên lớp), yêu cầu đổi SĐT (người duyệt), kết quả duyệt (người gửi yêu cầu)
ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type CHECK (type IN (
    'CHILD_PICKED_UP', 'MEAL_COUNT_RECONFIRM', 'CHILD_WITHDRAWN',
    'GUARDIAN_PHONE_CHANGE_REQUESTED', 'GUARDIAN_PHONE_CHANGE_DECIDED'));
