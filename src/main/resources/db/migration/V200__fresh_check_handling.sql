-- Kiểm nhận lần 2 theo cân đối: cách xử lý chênh lệch so với hàng đã nhận lần 1 và lượng gọi thêm / trả lại
ALTER TABLE fresh_check_lines
    ADD COLUMN handling_action VARCHAR(20),
    ADD COLUMN handling_qty    NUMERIC(14, 2);

ALTER TABLE fresh_check_lines
    ADD CONSTRAINT ck_fresh_check_lines_handling
        CHECK ((handling_action IS NULL OR handling_action IN ('ORDER_MORE', 'RETURN', 'KEEP'))
            AND (handling_qty IS NULL OR handling_qty >= 0));
