-- Bút toán trừ/trả kho phát sinh sau khi M1 kho khô của ngày ăn đã ký: ghi vào ngày thực hiện (meal_date),
-- correction_for = ngày ăn được điều chỉnh. Biên bản và sổ kho của ngày ăn đọc theo ngày hiệu lực.
ALTER TABLE stock_movements
    ADD COLUMN correction_for DATE,
    ADD CONSTRAINT ck_stock_movements_correction CHECK (
        correction_for IS NULL
        OR (meal_date IS NOT NULL AND movement_type IN ('DEDUCT', 'ADJUST', 'SHORTFALL_DEDUCT')));

CREATE INDEX idx_stock_movements_correction_for
    ON stock_movements (campus_id, correction_for, food_id)
    WHERE correction_for IS NOT NULL;
