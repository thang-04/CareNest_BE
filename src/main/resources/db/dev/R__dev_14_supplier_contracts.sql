-- Bộ dữ liệu dev (profile "dev"): hợp đồng nhà cung cấp, nhà cung cấp theo thực phẩm (NUT-27), nhà cung cấp theo dòng
-- kiểm nhận hàng tươi (NUT-12), lịch sử thực phẩm. Chạy sau R__dev_03_kitchen.sql (cần các lần kiểm nhận đã seed).
-- Idempotent: chỉ điền chỗ còn trống / chỉ thêm khi chưa có theo khóa tự nhiên.

-- 1. Hợp đồng hiện hành của từng nhà cung cấp (năm học 2026-2027). Ngọc Lâm sắp hết hạn để màn hình có ca cần gia hạn.
UPDATE suppliers s
SET contract_no = v.contract_no, contract_start = v.contract_start, contract_end = v.contract_end,
    updated_at = TIMESTAMPTZ '2026-08-28 09:00+07'
FROM (VALUES
    ('Hộ kinh doanh Thực phẩm sạch Hùng Liên', 'HĐ-01/2026-HL', DATE '2026-09-01', DATE '2027-08-31'),
    ('Hộ kinh doanh Thủy sản Minh Thu',        'HĐ-02/2026-MT', DATE '2026-09-01', DATE '2027-08-31'),
    ('Hợp tác xã Rau an toàn Đường An',        'HĐ-03/2026-ĐA', DATE '2026-09-01', DATE '2027-08-31'),
    ('Công ty TNHH Lương thực Bình Giang',     'HĐ-04/2026-BG', DATE '2026-09-01', DATE '2027-08-31'),
    ('Đại lý Sữa và Trứng Hạnh Phúc',          'HĐ-05/2026-HP', DATE '2026-09-01', DATE '2027-06-30'),
    ('Hộ kinh doanh Hoa quả Ngọc Lâm',         'HĐ-06/2026-NL', DATE '2026-09-01', DATE '2026-12-31')
) AS v (name, contract_no, contract_start, contract_end)
WHERE s.name = v.name AND s.contract_no IS NULL;

-- 2. Nhà cung cấp theo thực phẩm: từ ngày hiệu lực hợp đồng, mỗi thực phẩm có một nhà cung cấp (giữ giá ban đầu).
-- Hàng khô → Bình Giang (khớp các phiếu nhập kho khô); hàng tươi chia theo nhóm.
CREATE TEMP TABLE k14_food_supplier AS
SELECT f.id AS food_id, f.food_group, f.unit_price,
       CASE
           WHEN f.storage = 'DRY' THEN 'Công ty TNHH Lương thực Bình Giang'
           WHEN f.food_group = 'EGG_DAIRY' THEN 'Đại lý Sữa và Trứng Hạnh Phúc'
           WHEN f.food_group = 'FRUIT' THEN 'Hộ kinh doanh Hoa quả Ngọc Lâm'
           WHEN f.food_group = 'SEAFOOD' THEN 'Hộ kinh doanh Thủy sản Minh Thu'
           WHEN f.food_group = 'VEGETABLE' THEN 'Hợp tác xã Rau an toàn Đường An'
           WHEN f.food_group = 'GRAIN' AND f.code NOT IN ('VDD1020', 'VDD1013') THEN 'Hợp tác xã Rau an toàn Đường An'
           ELSE 'Hộ kinh doanh Thực phẩm sạch Hùng Liên'
       END AS supplier_name
FROM foods f
WHERE f.code LIKE 'VDD%';

INSERT INTO food_prices (food_id, supplier_id, price, effective_from, reason, created_at, updated_at)
SELECT m.food_id, s.id, m.unit_price, DATE '2026-09-01', 'Hợp đồng ' || s.contract_no,
       TIMESTAMPTZ '2026-08-28 09:00+07', TIMESTAMPTZ '2026-08-28 09:00+07'
FROM k14_food_supplier m
JOIN suppliers s ON s.name = m.supplier_name AND s.contract_no IS NOT NULL
ON CONFLICT (food_id, effective_from) DO NOTHING;

-- 3. Điều chỉnh giá giữa kỳ cho thịt, thủy sản, trứng sữa (+3..8%, làm tròn 500 đồng) để lịch sử giá có nhiều mức
INSERT INTO food_prices (food_id, supplier_id, price, effective_from, reason, created_at, updated_at)
SELECT m.food_id, p.supplier_id,
       round(m.unit_price * (1 + (3 + abs(hashtext('fp:' || m.food_id)) % 6) / 100.0) / 500) * 500,
       DATE '2026-10-15', 'Điều chỉnh giá quý IV theo thông báo của nhà cung cấp',
       TIMESTAMPTZ '2026-10-12 10:00+07', TIMESTAMPTZ '2026-10-12 10:00+07'
FROM k14_food_supplier m
JOIN food_prices p ON p.food_id = m.food_id AND p.effective_from = DATE '2026-09-01'
WHERE m.food_group IN ('MEAT', 'SEAFOOD', 'EGG_DAIRY')
ON CONFLICT (food_id, effective_from) DO NOTHING;

-- 4. Dòng kiểm nhận hàng tươi ghi nhà cung cấp theo thực phẩm (chỉ điền dòng còn trống)
UPDATE fresh_check_lines l
SET supplier_id = (SELECT p.supplier_id FROM food_prices p
                   WHERE p.food_id = l.food_id AND p.supplier_id IS NOT NULL
                   ORDER BY p.effective_from LIMIT 1)
WHERE l.supplier_id IS NULL;

-- 5. Lịch sử thực phẩm: mỗi thực phẩm có dòng tạo ban đầu (giá, nhà cung cấp lấy từ food_prices khi đọc lịch sử)
INSERT INTO food_history (food_id, user_id, changed_at, action)
SELECT f.id,
       (SELECT a.user_id FROM staff_assignments a JOIN campuses c ON c.id = a.campus_id
        WHERE a.staff_role = 'VICE_PRINCIPAL' AND c.is_main AND (a.valid_to IS NULL OR a.valid_to >= CURRENT_DATE)
        ORDER BY a.id LIMIT 1),
       TIMESTAMPTZ '2026-08-20 14:00+07', 'CREATED'
FROM foods f
WHERE f.code LIKE 'VDD%'
  AND NOT EXISTS (SELECT 1 FROM food_history h WHERE h.food_id = f.id AND h.action = 'CREATED');

DROP TABLE k14_food_supplier;

-- 6. Yêu cầu mở ghi biên bản kiểm thực ngày cũ (INS-05): ngày xa nhất trong 9 ngày có sổ báo ăn gần đây, chưa có biên bản.
-- PHT cơ sở xin, Hiệu trưởng duyệt. Mỗi cơ sở: 1 đã duyệt còn hạn, 1 từ chối, 1 chờ duyệt, 1 đã thu hồi.
CREATE TEMP TABLE k14_grant_day AS
SELECT d.campus_id, d.day, row_number() OVER (PARTITION BY d.campus_id ORDER BY d.day DESC) AS rn
FROM (SELECT DISTINCT campus_id, date AS day FROM meal_counts
      WHERE status = 'CONFIRMED' AND date < CURRENT_DATE AND date >= CURRENT_DATE - 30) d;

INSERT INTO inspection_day_grants (campus_id, sheet_date, tab, status, self_opened, reason, requested_by, requested_by_name,
                                   requested_at, decided_by, decided_by_name, decided_at, decision_note, expires_at)
SELECT g.campus_id, g.day, v.tab, v.status, FALSE, v.reason, vp.user_id, vpu.full_name,
       (g.day + 1) + TIME '08:15' AT TIME ZONE 'Asia/Ho_Chi_Minh',
       CASE WHEN v.status <> 'PENDING' THEN pr.user_id END, CASE WHEN v.status <> 'PENDING' THEN pru.full_name END,
       CASE WHEN v.status <> 'PENDING' THEN (g.day + 1) + TIME '10:40' AT TIME ZONE 'Asia/Ho_Chi_Minh' END,
       v.note,
       CASE WHEN v.status = 'APPROVED' THEN (CURRENT_DATE + 1) + TIME '18:00' AT TIME ZONE 'Asia/Ho_Chi_Minh' END
FROM k14_grant_day g
JOIN (VALUES (6, 'FRESH', 'APPROVED', 'Quên ghi biên bản nhận hàng tươi, đã đối chiếu phiếu giao hàng', 'Đồng ý, ghi bù và ký trong ngày'),
             (7, 'DRY', 'REJECTED', 'Ghi bù kiểm nhận hàng khô', 'Không đủ chứng từ đối chiếu, nhập bù ở lần nhận sau'),
             (8, 'SAMPLE', 'PENDING', 'Chưa soát sổ lưu mẫu của ngày do nghỉ ốm', NULL),
             (9, 'SERVE', 'REVOKED', 'Bổ sung ghi chép chia ăn', 'Thu hồi vì đã có bản ghi tay đủ thông tin')
     ) AS v (rn, tab, status, reason, note) ON v.rn = g.rn
-- Mỗi cơ sở một PHT, toàn trường một hiệu trưởng (lấy người có phân công sớm nhất) để không nhân dòng
JOIN LATERAL (SELECT a.user_id FROM staff_assignments a
              WHERE a.campus_id = g.campus_id AND a.staff_role = 'VICE_PRINCIPAL'
                AND (a.valid_to IS NULL OR a.valid_to >= CURRENT_DATE)
              ORDER BY a.id LIMIT 1) vp ON TRUE
JOIN user_accounts vpu ON vpu.id = vp.user_id
JOIN LATERAL (SELECT a.user_id FROM staff_assignments a
              WHERE a.staff_role = 'PRINCIPAL' AND (a.valid_to IS NULL OR a.valid_to >= CURRENT_DATE)
              ORDER BY a.id LIMIT 1) pr ON TRUE
JOIN user_accounts pru ON pru.id = pr.user_id
WHERE NOT EXISTS (SELECT 1 FROM inspection_day_grants x
                  WHERE x.campus_id = g.campus_id AND x.sheet_date = g.day AND x.tab = v.tab);

DROP TABLE k14_grant_day;
