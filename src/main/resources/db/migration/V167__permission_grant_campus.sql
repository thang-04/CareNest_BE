-- AUTH-20 (user chốt 21/11/2026): HT giao việc của PHT tại một điểm trường cụ thể, kể cả khác điểm trường PHT phụ trách.
-- campus_id chỉ đi với GRANT phạm vi CAMPUS; NULL = điểm trường PHT đang được phân công (nghĩa cũ, dữ liệu cũ giữ nguyên).
ALTER TABLE permission_grants ADD COLUMN campus_id BIGINT;
ALTER TABLE permission_grants
    ADD CONSTRAINT fk_permission_grants_campus_id FOREIGN KEY (campus_id) REFERENCES campuses (id);
ALTER TABLE permission_grants
    ADD CONSTRAINT ck_permission_grants_campus CHECK (campus_id IS NULL OR (effect = 'GRANT' AND scope = 'CAMPUS'));

-- Lịch sử ghi được việc đổi nơi giao (Cơ sở 2 → Cơ sở 3) dù hiệu lực và phạm vi không đổi
ALTER TABLE permission_change_log ADD COLUMN before_campus_id BIGINT;
ALTER TABLE permission_change_log ADD COLUMN after_campus_id BIGINT;
