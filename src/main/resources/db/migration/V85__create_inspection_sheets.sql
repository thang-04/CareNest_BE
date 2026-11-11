-- INS-01..04: biên bản kiểm thực ba bước (M1 tươi/khô, M2, M3) và soát sổ lưu mẫu (M5) của một điểm trường–ngày.
-- Một bảng, dòng và lịch sử nằm trong jsonb (ADR-0024); chưa có dòng = chưa nhập gì (khung dựng từ dữ liệu nguồn).
CREATE TABLE inspection_sheets (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    campus_id  BIGINT      NOT NULL REFERENCES campuses (id),
    sheet_date DATE        NOT NULL,
    tab        VARCHAR(10) NOT NULL,
    status     VARCHAR(10) NOT NULL DEFAULT 'DRAFT',
    version    INT         NOT NULL DEFAULT 0,
    header     JSONB       NOT NULL DEFAULT '{}'::jsonb,
    rows       JSONB       NOT NULL DEFAULT '[]'::jsonb,
    signature  JSONB,
    snapshot   JSONB,
    history    JSONB       NOT NULL DEFAULT '[]'::jsonb,
    updated_by BIGINT      REFERENCES user_accounts (id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_inspection_sheets UNIQUE (campus_id, sheet_date, tab),
    CONSTRAINT ck_inspection_sheets_tab CHECK (tab IN ('FRESH', 'DRY', 'PROCESS', 'SERVE', 'SAMPLE')),
    CONSTRAINT ck_inspection_sheets_status CHECK (status IN ('DRAFT', 'SIGNED')),
    CONSTRAINT ck_inspection_sheets_signed CHECK ((status = 'SIGNED') = (signature IS NOT NULL))
);
CREATE INDEX idx_inspection_sheets_day ON inspection_sheets (campus_id, sheet_date);

-- Nhà cung cấp của lần giao hàng tươi: M1 tươi điền sẵn từ kiểm nhận; null với dữ liệu cũ
ALTER TABLE fresh_checks ADD COLUMN supplier_id BIGINT REFERENCES suppliers (id);
