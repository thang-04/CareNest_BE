-- NUT-11, NUT-12 (user chốt 19/11/2026): kiểm nhận hàng hôm nay gồm cả hàng khô.
-- Bản dự trù chụp lượng hàng khô đặt cho kỳ lúc lưu: [{foodId, quantity}] theo đơn vị gốc
ALTER TABLE fresh_forecasts ADD COLUMN dry_order JSONB NOT NULL DEFAULT '[]';

-- Đợt hàng khô kiểm nhận sinh phiếu nhập: gắn bản dự trù để cộng đã nhận theo kỳ, dòng không đạt không thành lô
ALTER TABLE stock_receipts
    ADD COLUMN forecast_id    BIGINT REFERENCES fresh_forecasts (id) ON DELETE SET NULL,
    ADD COLUMN from_intake    BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN rejected_lines JSONB   NOT NULL DEFAULT '[]';

CREATE INDEX idx_stock_receipts_forecast ON stock_receipts (forecast_id) WHERE forecast_id IS NOT NULL;
