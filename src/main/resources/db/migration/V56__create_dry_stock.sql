-- NUT-11: kho khô mỗi điểm trường theo lô (số lô, hạn dùng, giá, NCC). Tồn lô = tổng bút toán của lô;
-- bút toán không sửa, không xóa — sai thì ghi bút toán đảo.

CREATE TABLE stock_receipts (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    campus_id     BIGINT       NOT NULL REFERENCES campuses (id),
    supplier_id   BIGINT       NOT NULL REFERENCES suppliers (id),
    received_date DATE         NOT NULL,
    status        VARCHAR(10)  NOT NULL DEFAULT 'DRAFT',
    note          VARCHAR(500) NOT NULL DEFAULT '',
    confirmed_by  BIGINT       REFERENCES user_accounts (id),
    confirmed_at  TIMESTAMPTZ,
    cancelled_by  BIGINT       REFERENCES user_accounts (id),
    cancelled_at  TIMESTAMPTZ,
    cancel_reason VARCHAR(500),
    created_by    BIGINT       REFERENCES user_accounts (id),
    updated_by    BIGINT       REFERENCES user_accounts (id),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_stock_receipts_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'CANCELLED'))
);
CREATE INDEX idx_stock_receipts_campus_date ON stock_receipts (campus_id, received_date);

-- quantity theo đơn vị gốc của thực phẩm (g/ml); unit_price theo đơn vị mua như foods.unit_price
CREATE TABLE stock_receipt_lines (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    receipt_id  BIGINT         NOT NULL REFERENCES stock_receipts (id) ON DELETE CASCADE,
    food_id     BIGINT         NOT NULL REFERENCES foods (id),
    lot_no      VARCHAR(50)    NOT NULL,
    expiry_date DATE           NOT NULL,
    quantity    NUMERIC(14, 2) NOT NULL,
    unit_price  NUMERIC(12, 2) NOT NULL,
    CONSTRAINT ck_stock_receipt_lines_quantity CHECK (quantity > 0),
    CONSTRAINT ck_stock_receipt_lines_price CHECK (unit_price >= 0)
);

CREATE TABLE stock_lots (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    campus_id       BIGINT         NOT NULL REFERENCES campuses (id),
    food_id         BIGINT         NOT NULL REFERENCES foods (id),
    receipt_line_id BIGINT         NOT NULL REFERENCES stock_receipt_lines (id),
    supplier_id     BIGINT         NOT NULL REFERENCES suppliers (id),
    lot_no          VARCHAR(50)    NOT NULL,
    expiry_date     DATE           NOT NULL,
    quantity        NUMERIC(14, 2) NOT NULL,
    unit_price      NUMERIC(12, 2) NOT NULL,
    received_date   DATE           NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uk_stock_lots_receipt_line UNIQUE (receipt_line_id)
);
-- Trừ theo lô hết hạn trước
CREATE INDEX idx_stock_lots_fifo ON stock_lots (campus_id, food_id, expiry_date, id);

-- DEDUCT, ADJUST, SHORTFALL_DEDUCT do trừ kho theo suất ghi (meal_date = ngày ăn)
CREATE TABLE stock_movements (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lot_id        BIGINT         NOT NULL REFERENCES stock_lots (id),
    campus_id     BIGINT         NOT NULL REFERENCES campuses (id),
    food_id       BIGINT         NOT NULL REFERENCES foods (id),
    movement_type VARCHAR(20)    NOT NULL,
    quantity      NUMERIC(14, 2) NOT NULL,
    meal_date     DATE,
    receipt_id    BIGINT         REFERENCES stock_receipts (id),
    reason        VARCHAR(500)   NOT NULL DEFAULT '',
    created_by    BIGINT         REFERENCES user_accounts (id),
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_stock_movements_type CHECK (
        movement_type IN ('RECEIPT', 'DEDUCT', 'ADJUST', 'SHORTFALL_DEDUCT', 'RECEIPT_CANCEL')),
    CONSTRAINT ck_stock_movements_quantity CHECK (quantity <> 0)
);
CREATE INDEX idx_stock_movements_lot ON stock_movements (lot_id);
CREATE INDEX idx_stock_movements_campus_meal_date ON stock_movements (campus_id, meal_date, food_id);
CREATE INDEX idx_stock_movements_campus_created ON stock_movements (campus_id, created_at);
