-- Gộp 7 bảng bán trú nhỏ hoặc trùng dữ liệu vào bảng cha (ERD gọn); dữ liệu có sẵn được chuyển trước khi xóa bảng.

-- 1. HLT-06: chất dị ứng là danh mục cố định 9 chất (enum Allergen trong code) ⇒ lưu mã ngay trên thực phẩm,
--    cách nhau dấu phẩy theo thứ tự hiển thị, vd. 'SHRIMP,EGG'. Bỏ allergens và bảng nối food_allergens.
ALTER TABLE foods ADD COLUMN allergen_codes VARCHAR(200) NOT NULL DEFAULT '';
UPDATE foods f
SET allergen_codes = x.codes
FROM (SELECT fa.food_id, string_agg(a.code, ',' ORDER BY a.sort_order) AS codes
      FROM food_allergens fa
      JOIN allergens a ON a.id = fa.allergen_id
      WHERE a.code IN ('SHRIMP', 'CRAB', 'FISH', 'EGG', 'COW_MILK', 'PEANUT', 'SOY', 'WHEAT', 'SESAME')
      GROUP BY fa.food_id) x
WHERE f.id = x.food_id;
DROP TABLE food_allergens;
DROP TABLE allergens;

-- 2. NUT-11: lô kho khô chính là dòng phiếu nhập của phiếu đã xác nhận (stock_lots chép lại 1–1 từng dòng).
--    Bút toán trỏ thẳng vào dòng phiếu; tồn của lô vẫn = tổng stock_movements theo lot_id.
ALTER TABLE stock_movements DROP CONSTRAINT stock_movements_lot_id_fkey;
UPDATE stock_movements m SET lot_id = l.receipt_line_id FROM stock_lots l WHERE l.id = m.lot_id;
ALTER TABLE stock_movements
    ADD CONSTRAINT fk_stock_movements_lot FOREIGN KEY (lot_id) REFERENCES stock_receipt_lines (id);
DROP TABLE stock_lots;
-- Trừ theo lô hết hạn trước (campus lấy qua phiếu nhập)
CREATE INDEX idx_stock_receipt_lines_fifo ON stock_receipt_lines (food_id, expiry_date, id);

-- 3. NUT-08: ngày của thực đơn tuần chỉ có cờ nghỉ + ghi chú ⇒ mảng JSON trên tuần [{date, off, note}];
--    món ghi thẳng tuần + ngày. Bỏ meal_plan_days.
ALTER TABLE meal_plan_weeks ADD COLUMN days JSONB NOT NULL DEFAULT '[]';
UPDATE meal_plan_weeks w
SET days = d.days
FROM (SELECT week_id,
             jsonb_agg(jsonb_build_object('date', menu_date, 'off', off, 'note', note) ORDER BY menu_date) AS days
      FROM meal_plan_days
      GROUP BY week_id) d
WHERE w.id = d.week_id;

ALTER TABLE meal_plan_items ADD COLUMN week_id BIGINT, ADD COLUMN menu_date DATE;
UPDATE meal_plan_items i SET week_id = d.week_id, menu_date = d.menu_date FROM meal_plan_days d WHERE d.id = i.day_id;
ALTER TABLE meal_plan_items DROP CONSTRAINT uk_meal_plan_items;
ALTER TABLE meal_plan_items DROP COLUMN day_id;
ALTER TABLE meal_plan_items
    ALTER COLUMN week_id SET NOT NULL,
    ALTER COLUMN menu_date SET NOT NULL,
    ADD CONSTRAINT fk_meal_plan_items_week FOREIGN KEY (week_id) REFERENCES meal_plan_weeks (id) ON DELETE CASCADE,
    ADD CONSTRAINT uk_meal_plan_items UNIQUE (week_id, menu_date, session, menu_block, dish_id);
DROP TABLE meal_plan_days;

-- 4. NUT-07: sĩ số dự kiến chỉ vài dòng/bản dự trù, không sửa riêng ⇒ mảng JSON
--    [{ageGroupId, enrolledCount, expectedCount}] trên bản dự trù. Bỏ fresh_forecast_counts.
ALTER TABLE fresh_forecasts ADD COLUMN counts JSONB NOT NULL DEFAULT '[]';
UPDATE fresh_forecasts f
SET counts = c.counts
FROM (SELECT forecast_id,
             jsonb_agg(jsonb_build_object('ageGroupId', age_group_id, 'enrolledCount', enrolled_count,
                                          'expectedCount', expected_count) ORDER BY id) AS counts
      FROM fresh_forecast_counts
      GROUP BY forecast_id) c
WHERE f.id = c.forecast_id;
DROP TABLE fresh_forecast_counts;

-- 5. NUT-21: món của mẫu lưu là danh sách id ⇒ mảng bigint (service kiểm món tồn tại). Bỏ food_sample_dishes.
ALTER TABLE food_samples ADD COLUMN dish_ids BIGINT[] NOT NULL DEFAULT '{}';
UPDATE food_samples s
SET dish_ids = d.ids
FROM (SELECT sample_id, array_agg(dish_id ORDER BY dish_id) AS ids FROM food_sample_dishes GROUP BY sample_id) d
WHERE s.id = d.sample_id;
DROP TABLE food_sample_dishes;

-- 6. NUT-16: khung giờ đăng ảnh chỉ 3 dòng, chung toàn trường ⇒ mảng JSON trên dòng cấu hình chung (id = 1)
--    [{session, startTime, endTime, updatedBy, updatedAt}]; chưa có bữa trong mảng ⇒ chưa đăng ảnh được.
ALTER TABLE edu_settings ADD COLUMN meal_photo_windows JSONB NOT NULL DEFAULT '[]';
UPDATE edu_settings
SET meal_photo_windows = COALESCE(
        (SELECT jsonb_agg(jsonb_build_object('session', session,
                                             'startTime', to_char(start_time, 'HH24:MI:SS'),
                                             'endTime', to_char(end_time, 'HH24:MI:SS'),
                                             'updatedBy', updated_by,
                                             'updatedAt', updated_at)
                          ORDER BY CASE session WHEN 'MORNING' THEN 1 WHEN 'LUNCH' THEN 2 ELSE 3 END)
         FROM meal_photo_windows),
        '[]')
WHERE id = 1;
DROP TABLE meal_photo_windows;
