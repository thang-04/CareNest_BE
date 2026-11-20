---
title: Khóa ghi dữ liệu bán trú sau khi chốt
status: in-progress
owner: thangnd
jira: G94-417 / BE-FIX-08
branch: fix/G94-417-BE-FIX-08-boarding-write-lock
modules: [nutrition]
rules: [NUT-02, NUT-03, NUT-11, NUT-12, NUT-16, NUT-21, INS-03, INS-04]
risk: high
created: 2026-11-18
updated: 2026-11-20
---
# Khóa ghi dữ liệu bán trú sau khi chốt

- Ngày: 18–19/11/2026 · Làn L (API contract, migration, nhiều luồng) · Rule: NUT-02/03, NUT-11, NUT-12, NUT-16, NUT-21, INS-03, INS-04 · Plan được user duyệt 19/11/2026.

## Bối cảnh

Kiểm nhận hàng tươi ngày cũ vẫn sửa được kể cả khi M1 tươi đã ký. Rà toàn bán trú thấy: ghi lùi/tới ngày tùy ý (lưu mẫu, phiếu nhập, thực đơn tuần đã qua, dự trù), và nguồn của biên bản kiểm thực đã ký vẫn đổi (suất, kho khô, lưu mẫu) ⇒ "Sửa lại" (INS-04) dựng lại từ nguồn lệch.

## Quyết định (user chốt)

Một màn bán trú ngừng cho sửa ở mốc khóa đầu tiên:

1. Qua giờ khóa của luồng (HT cài) — mở bằng HT/PHT mở khóa có lý do. *(đợt 2–3)*
2. Ngày ghi khác hôm nay — mở bằng HT/PHT mở khóa có lý do. *(đợt 3 cho phần mở khóa)*
3. Biên bản kiểm thực đã ký — mở bằng Sửa lại biên bản (INS-04). Mở khóa ① ② không vượt mốc ③.

- Trừ kho tự động cho ngày ăn đã ký M1 khô ⇒ ghi vào hôm nay kèm `correction_for` (không chặn, sổ kho vẫn đúng).
- Giờ khóa mỗi luồng một giờ riêng (kiểm nhận lần 1, lần 2, lưu mẫu theo bữa, phiếu nhập), chung toàn trường, để trống ⇒ khóa lúc hết ngày.
- Mở khóa: HT hoặc PHT có `FOOD_SAFETY_APPROVE` ở điểm trường; theo (điểm trường, ngày, luồng[, bữa]); lý do ≥ 10 ký tự; hiệu lực tới khi khóa lại hoặc hết ngày hiện tại; mở được cả ngày cũ; có lịch sử.
- Loại bỏ: chỉ cảnh báo lệch thay vì chặn; một giờ khóa chung.

## Đợt 1 — xong (chưa commit)

- `BoardingWriteGuard` (khóa dòng `campuses` + biên bản đã ký); ApiCode 6047, 6048, 6051–6056, 6076, 6077, 6104–6106 (6104–6106 đổi từ 6044–6046 khi merge dev 20/11 vì dev đã dùng các mã đó).
- Luồng: `FreshGoodsServiceImpl` (kiểm nhận, dự trù), `MealCountServiceImpl` (confirm/adjust), `StockLedgerServiceImpl` (resyncDay chặn; syncDay/settleShortfalls ghi điều chỉnh hôm nay), `StockReceiptServiceImpl`, `FoodSampleServiceImpl`, `MealPlanServiceImpl`, `MealPhotoServiceImpl`.
- Migration `V157__add_stock_movement_correction_for.sql`; đọc movement theo ngày hiệu lực ở `returnToLots` và `InspectionIntakeSkeleton.dry`.
- Cờ response: `FreshCheckResponse.editable/lockReason`, `MealCountResponse.inspectionSigned`, `FoodSampleResponse.editable`, `DayBoardResponse.DryStock.m1DrySigned`, `FreshForecastSummaryResponse.deletable`, `StockMovementResponse.correctionFor`.
- Test: `support/MovableClock`; `FreshGoods`, `KitchenRecords`, `DryStock`, `DryStockOutlook`, `MealCount`, `MealPlan`, `MealPlanPublishConcurrency` integration tests.

## Đợt 2 — giờ khóa cấu hình (chưa làm)

Bảng `boarding_lock_times` (luồng, bữa, giờ) + lịch sử; `GET|PUT /nutrition/boarding-lock-times` (HT); guard thêm lý do `AFTER_LOCK_TIME`; màn FE mở rộng từ khung giờ ảnh bữa.

## Đợt 3 — mở khóa có lý do (chưa làm)

Bảng `boarding_day_unlocks`; `GET|POST /nutrition/boarding-unlocks`, `POST /{id}/relock`; guard bỏ qua mốc ① ② khi có mở khóa còn hiệu lực; FE nút Mở khóa/Khóa lại + lịch sử.

## Tác động FE/APP

- FE: kiểm nhận hàng tươi (đã làm), phiếu nhập kho (ngày = hôm nay, đổi ngày nháp cũ), bảng bán trú (ẩn đồng bộ khi `m1DrySigned`), dự trù (min ngày, ẩn Xóa), suất ăn (`inspectionSigned`; file trong thư mục attendance — kiểm chủ module), `beErrorFields` map mã lỗi mới.
- APP: lưu mẫu (6053/6054/6047, `editable`), ảnh bữa (6077) — báo team App.

## Progress log

- 18/11/2026: vá kiểm nhận hàng tươi (nay 6104/6105) + FE `FreshCheckPage`.
- 19/11/2026: đợt 1 BE xong, test nhóm bán trú xanh; chờ chạy toàn bộ suite, FE, đợt 2–3.
