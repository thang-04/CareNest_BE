-- NUT-29, NUT-30: xuất kho khô ngoài trừ theo suất. Trả NCC (lô còn hạn, đúng NCC đã giao) và xuất hủy (lô hết hạn),
-- mỗi phiếu một lý do. Tồn lô vẫn = tổng stock_movements: phiếu chỉ giữ chứng từ, số lượng nằm ở bút toán âm có disposal_id.

CREATE TABLE stock_disposals (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code          VARCHAR(40)  NOT NULL,
    campus_id     BIGINT       NOT NULL REFERENCES campuses (id),
    kind          VARCHAR(20)  NOT NULL,
    supplier_id   BIGINT       REFERENCES suppliers (id),
    disposal_date DATE         NOT NULL,
    reason        VARCHAR(500) NOT NULL,
    created_by    BIGINT       REFERENCES user_accounts (id),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_stock_disposals_code UNIQUE (code),
    CONSTRAINT ck_stock_disposals_kind CHECK (kind IN ('SUPPLIER_RETURN', 'WRITE_OFF')),
    -- Chỉ phiếu trả NCC có nhà cung cấp
    CONSTRAINT ck_stock_disposals_supplier CHECK ((kind = 'SUPPLIER_RETURN') = (supplier_id IS NOT NULL))
);
CREATE INDEX idx_stock_disposals_campus_date ON stock_disposals (campus_id, disposal_date);

ALTER TABLE stock_movements ADD COLUMN disposal_id BIGINT REFERENCES stock_disposals (id);

ALTER TABLE stock_movements DROP CONSTRAINT ck_stock_movements_type;
ALTER TABLE stock_movements
    ADD CONSTRAINT ck_stock_movements_type CHECK (
        movement_type IN (
            'RECEIPT', 'DEDUCT', 'ADJUST', 'SHORTFALL_DEDUCT', 'RECEIPT_CANCEL', 'SUPPLIER_RETURN', 'WRITE_OFF')),
    -- Bút toán xuất kho khô luôn thuộc một phiếu xuất, các loại khác thì không
    ADD CONSTRAINT ck_stock_movements_disposal CHECK (
        (disposal_id IS NOT NULL) = (movement_type IN ('SUPPLIER_RETURN', 'WRITE_OFF')));

CREATE INDEX idx_stock_movements_disposal ON stock_movements (disposal_id) WHERE disposal_id IS NOT NULL;
