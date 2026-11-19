-- G94-180 (2026-11-11, user chốt): Sửa hồ sơ không đổi SĐT phụ huynh đã lưu; mọi phụ huynh (có hay chưa có tài khoản)
-- đổi số qua yêu cầu, HT hoặc PHT có quyền ở điểm trường của trẻ duyệt (người gửi có quyền thì tự duyệt được).
-- Phụ huynh chưa có tài khoản ⇒ user_id NULL. Mỗi phụ huynh trên hồ sơ tối đa một yêu cầu đang chờ.
-- Bỏ phụ huynh ở Sửa hồ sơ vẫn được: yêu cầu giữ lịch sử, guardian_id về NULL (duyệt sau đó ⇒ 409).
ALTER TABLE guardian_phone_change_requests ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE guardian_phone_change_requests ALTER COLUMN guardian_id DROP NOT NULL;
ALTER TABLE guardian_phone_change_requests DROP CONSTRAINT guardian_phone_change_requests_guardian_id_fkey;
ALTER TABLE guardian_phone_change_requests ADD CONSTRAINT fk_phone_change_guardian
    FOREIGN KEY (guardian_id) REFERENCES child_guardians (id) ON DELETE SET NULL;

CREATE UNIQUE INDEX uq_phone_change_pending_guardian
    ON guardian_phone_change_requests (guardian_id) WHERE status = 'PENDING';
