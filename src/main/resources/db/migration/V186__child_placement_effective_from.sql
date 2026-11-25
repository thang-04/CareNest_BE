-- G94-180/CHD-14: ngày trẻ vào lớp mới (xếp / chuyển lớp đặt được ngày tương lai) và lịch chuyển bị hủy hoặc bị thay.
ALTER TABLE child_placements
    ADD COLUMN effective_from DATE,
    ADD COLUMN cancelled_at   TIMESTAMPTZ,
    ADD COLUMN cancelled_by   BIGINT REFERENCES user_accounts (id);

-- Dòng có trước: lớp mới tính từ ngày lưu (giờ Việt Nam). Seed dev chèn sau migration này để trống ⇒ đọc như ngày lưu.
UPDATE child_placements SET effective_from = (placed_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date;

COMMENT ON COLUMN child_placements.effective_from IS 'Ngày vào lớp mới (G94-180/CHD-14); null = ngày của placed_at';
COMMENT ON COLUMN child_placements.cancelled_at IS 'Lịch xếp/chuyển lớp chưa tới ngày bị hủy hoặc bị thay (G94-180/CHD-14)';
