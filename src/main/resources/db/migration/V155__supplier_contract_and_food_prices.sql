-- NUT-27: hợp đồng hiện hành của nhà cung cấp; gia hạn = cập nhật ngày
ALTER TABLE suppliers
    ADD COLUMN contract_no    VARCHAR(50),
    ADD COLUMN contract_start DATE,
    ADD COLUMN contract_end   DATE,
    ADD CONSTRAINT ck_suppliers_contract_range
        CHECK (contract_start IS NULL OR contract_end IS NULL OR contract_start <= contract_end);

-- NUT-27: lịch sử giá và NCC của thực phẩm; giá tại ngày D = mức có effective_from gần nhất <= D
CREATE TABLE food_prices (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    food_id        BIGINT         NOT NULL REFERENCES foods (id),
    supplier_id    BIGINT         REFERENCES suppliers (id),
    price          NUMERIC(12, 2) NOT NULL,
    effective_from DATE           NOT NULL,
    reason         VARCHAR(300)   NOT NULL DEFAULT '',
    created_by     BIGINT         REFERENCES user_accounts (id),
    updated_by     BIGINT         REFERENCES user_accounts (id),
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uk_food_prices UNIQUE (food_id, effective_from),
    CONSTRAINT ck_food_prices_price CHECK (price >= 0)
);
CREATE INDEX idx_food_prices_supplier ON food_prices (supplier_id);

-- Mức giá đầu tiên lấy từ giá danh mục hiện có; foods.unit_price giữ lại nhưng không còn là nguồn giá
INSERT INTO food_prices (food_id, price, effective_from, reason, created_by, updated_by)
SELECT f.id, f.unit_price, (f.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date, 'Giá ban đầu', f.created_by, f.created_by
FROM foods f;

-- NUT-12: kiểm nhận ghi NCC theo từng dòng; dòng cũ lấy NCC của lần kiểm
ALTER TABLE fresh_check_lines
    ADD COLUMN supplier_id BIGINT REFERENCES suppliers (id);

UPDATE fresh_check_lines l
SET supplier_id = c.supplier_id
FROM fresh_checks c
WHERE c.id = l.check_id
  AND c.supplier_id IS NOT NULL;
