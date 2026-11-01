-- Điểm trường ngừng hoạt động (Cần chốt 23/10/2026): không xóa được vì năm cũ còn dùng; ngừng thì không chọn được
-- khi tạo lớp/phòng mới, năm cũ vẫn xem đủ. Điểm trường chính luôn ACTIVE.
ALTER TABLE campuses ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE campuses ADD COLUMN deactivated_at TIMESTAMPTZ;
ALTER TABLE campuses ADD CONSTRAINT ck_campuses_status CHECK (status IN ('ACTIVE', 'INACTIVE'));
