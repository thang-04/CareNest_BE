-- G94-180/CHD-14: ngày trẻ bắt đầu đi học, chọn khi tiếp nhận / mở lại hồ sơ (khác enrolled_on là ngày tiếp nhận).
-- Null = hồ sơ có trước quy tắc này.
ALTER TABLE children ADD COLUMN start_date DATE;

COMMENT ON COLUMN children.start_date IS 'Ngày bắt đầu đi học (G94-180/CHD-14); null = hồ sơ cũ';
