---
title: Kiểm nhận hàng hôm nay (thêm hàng khô)
status: approved     # user duyệt thiết kế 2026-11-19 (brainstorming 3 phần), bảo implement
owner: Thắng
jira: G94-192 / BE-FEAT-27 (mã task riêng: hỏi user khi commit)
branch: feature/G94-192-BE-FEAT-27-day-boarding (FE: feature/G94-192-FE-FEAT-27-daily-goods-intake)
modules: [nutrition]
rules: [NUT-11, NUT-12, P-24, INS-02]
risk: high           # migration + đổi API contract fresh-forecasts, fresh-checks, stock receipts
created: 2026-11-19
updated: 2026-11-19
---
# Kiểm nhận hàng hôm nay — hàng tươi và hàng khô

## Nghiệp vụ (user chốt 2026-11-19)

- Sáng giao hàng, PHT kiểm cả hàng tươi và hàng khô trên một màn "Kiểm nhận hàng hôm nay" (route giữ `/kitchen/fresh-receipts`).
- Bản dự trù chụp **lượng hàng khô đặt cho kỳ** lúc lưu (điền sẵn từ phần thiếu tới hết kỳ, PHT sửa/bỏ dòng). Danh sách đặt hàng in theo số đã chụp.
- Mỗi đợt hàng khô giao: nhà cung cấp + dòng (thực phẩm, lô, hạn dùng, lượng, đơn giá, Đạt/Không đạt, ghi chú). Xác nhận ⇒ phiếu nhập CONFIRMED chỉ gồm dòng Đạt; dòng Không đạt bắt buộc ghi biện pháp xử lý, không vào kho, không tính đã nhận.
- Đủ/thiếu/thừa = tổng dòng Đạt của các phiếu CONFIRMED gắn bản dự trù so với lượng đặt; tính lúc đọc, hủy phiếu thì tự giảm.
- Chỉ ghi trong ngày nhận (hôm nay). Ngày không có bản dự trù vẫn nhận được ("ngoài đơn").
- Sửa sai: hủy phiếu (luồng hủy có sẵn) rồi ghi đợt mới. Màn Phiếu nhập giữ cho nhập tay (tồn đầu kỳ...). M1 khô, trừ kho không đổi.

## Thay đổi BE

- `V154__add_dry_goods_intake.sql`: `fresh_forecasts.dry_order jsonb [{foodId, quantity}]`; `stock_receipts.forecast_id` (FK, `ON DELETE SET NULL`), `from_intake boolean`, `rejected_lines jsonb [{foodId, lotNo, expiryDate, quantity, note}]`.
- `POST /nutrition/fresh-forecasts/preview`: response thêm `dryOrder[{food, quantity}]` (gợi ý = `dryLines.shortQty > 0`). `POST /nutrition/fresh-forecasts` nhận `dryOrder` (null ⇒ lấy gợi ý; chỉ thực phẩm DRY đang dùng, không trùng, ≥ 0, dòng 0 bỏ).
- `GET /nutrition/fresh-checks` thêm `dry {forecastId, periodFrom, periodTo, orders[{food, ordered, received}], receipts[StockReceiptResponse]}`.
- `POST /nutrition/fresh-checks/dry-receipts?campusId&date` `{supplierId, note, lines[{foodId, lotNo, expiryDate, quantity, unitPrice, passed, note}]}` ⇒ trả lại `FreshCheckResponse`.
- `StockReceiptResponse` thêm `forecastId`, `fromIntake`, `rejectedLines[{food, lotNo, expiryDate, quantity, note}]`.
- ApiCode mới: 6044 `DRY_INTAKE_NOT_TODAY` (409), 6045 `DRY_INTAKE_NOTE_REQUIRED` (422), 6046 `DRY_ORDER_INVALID` (400).

## Thay đổi FE

- Sidebar, breadcrumb, tiêu đề: "Kiểm nhận hàng hôm nay".
- `FreshCheckPage` thêm `DryIntakeCard` (danh sách đợt trong ngày + form thêm đợt, đủ/thiếu tính ngay).
- Bản dự trù: bảng "Hàng khô đặt cho kỳ" sửa được khi lập; chi tiết và bản in dùng số đã lưu.
- Phiếu nhập: nhãn "Từ kiểm nhận", mục dòng không đạt.

## Kiểm thử

`FreshGoodsIntegrationTest`: lưu/đọc `dryOrder`, thực phẩm FRESH bị chặn; đợt có dòng đạt + không đạt ⇒ phiếu, lô, `rejected_lines`; 2 đợt cộng dồn, hủy giảm; dòng không đạt thiếu ghi chú 422; ngày khác hôm nay 409; ngày không có dự trù ⇒ `forecast_id` null. FE: lint + build + kiểm trên trình duyệt.

## Progress log

- 2026-11-19: thiết kế duyệt; nhánh BE fast-forward lên `origin/dev`, FE tạo worktree `CareNest_FE-goods-intake` từ `origin/dev`.
- 2026-11-19: BE xong (V154, `POST /nutrition/fresh-checks/dry-receipts`, `dryOrder` bản dự trù); `FreshGoodsIntegrationTest` 16/16, `DryStockOutlookIntegrationTest` 6/6, `ArchitectureRulesTest` 14/14, `OpenApiSnapshotTest` 4/4. Suite đầy đủ: `DevSeedIntegrationTest` lỗi sẵn từ dev (`R__dev_seed.sql` còn dùng `guardian_child_links` đã bỏ ở V122), không do thay đổi này.
- 2026-11-19: FE xong trên `feature/G94-192-FE-FEAT-27-daily-goods-intake` (worktree `CareNest_FE-goods-intake`): `DryIntakeCard`, bảng hàng khô đặt cho kỳ, phiếu nhập hiện nguồn kiểm nhận và dòng không đạt; snapshot route BE và `BE_API_INDEX`/`FE_API_CALLS` sinh lại từ nhánh BE này; `node scripts/verify.mjs` PASS. Chưa commit.
