---
title: Rút gọn bảng DB bán trú (29 → 22) — migration V83
status: in-progress
owner: thangnd
jira: G94-254 / BE-FEAT-27
branch: feature/G94-254-BE-FEAT-27-day-boarding
modules: [nutrition]
rules: [HLT-06, NUT-07, NUT-08, NUT-11, NUT-16, NUT-21]
risk: high
created: 2026-11-09
updated: 2026-11-10
---
# Rút gọn bảng DB bán trú

## Context
User thấy schema bán trú tách quá nhiều bảng, muốn ERD gọn cho báo cáo đồ án. Bỏ 7 bảng nhỏ hoặc trùng dữ liệu, gộp
vào bảng cha. Giữ các cặp phiếu + dòng (header/lines) và bảng audit. Phần auth (permission_grants, bỏ
guardian_child_links) làm riêng trên nhánh auth.

## Quyết định đã chốt (user, 09/11/2026)
1. Mục tiêu: ERD gọn; chấp nhận jsonb/mảng thay bảng con nhỏ, mất vài FK.
2. ~~Reset DB, sửa baseline tại chỗ~~ → đổi 10/11/2026: hook `.githooks/pre-commit` chặn sửa migration đã commit,
   user chọn **migration mới `V83__consolidate_boarding_tables.sql`** (chuyển dữ liệu rồi xóa bảng cũ); không cần
   reset DB. Nhánh auth dùng V82 ⇒ auth phải merge vào `dev` trước nhánh này (Flyway không chạy V82 sau V83).
3. Phần bán trú làm trên nhánh day-boarding, auth trên nhánh auth.
4. API contract giữ nguyên (FE không sửa).

## Thay đổi
| Bỏ bảng | Thay bằng | Chi tiết |
| --- | --- | --- |
| allergens, food_allergens | enum `Allergen` (id 1–9 giữ như seed cũ) + `foods.allergen_codes` (`EnumListConverter`) | V83 |
| stock_lots | lô = `stock_receipt_lines` của phiếu không phải DRAFT; `StockLot` là entity chỉ đọc (`@Immutable`, `@SQLRestriction`, `@Formula`); `stock_movements.lot_id` FK dòng phiếu | V83 |
| meal_plan_days | `meal_plan_weeks.days` jsonb `[{date, off, note}]`; `meal_plan_items` có `week_id` + `menu_date`; `MealPlanDay` thành value object | V83 |
| fresh_forecast_counts | `fresh_forecasts.counts` jsonb | V83 |
| food_sample_dishes | `food_samples.dish_ids bigint[]` | V83 |
| meal_photo_windows | `edu_settings.meal_photo_windows` jsonb; entity `MealPhotoSettings` (module meals) map cùng dòng singleton | V83 |

Converter jsonb chung: `entity/meals/JsonListConverter` (Jackson 3 `JsonMapper`).

## Verify
- `node scripts/verify.mjs` (Flyway sạch từ V1, toàn bộ test, ArchUnit, Spotless, snapshot OpenAPI không đổi).
- `DevSeedIntegrationTest`: seed chạy 2 lần không nhân đôi, khớp kho/thực đơn.

## Rủi ro
- Thứ tự merge: auth (V82) trước bán trú (V83).
- `edu_settings` thuộc module eduplan nhưng giữ thêm cột bán trú; tên bảng chưa đổi để không kéo module eduplan vào.
- Mất FK: `food_samples.dish_ids`, `fresh_forecasts.counts.ageGroupId`, `meal_photo_windows` check start < end (service vẫn kiểm).

## Progress log
- 2026-11-09: Sửa V54, V56, V57, V60, V61 (đã trả lại 10/11); entity/repo/service; seed `db/dev`; test fixture; DATA_DICTIONARY.
  `KitchenRecordsIntegrationTest` 7/7 pass sau khi đổi khung giờ ảnh + mẫu lưu.
- 2026-11-09 22:20: `node scripts/verify.mjs` PASS full — 1217 test, 0 fail, ArchUnit ok, snapshot OpenAPI không đổi,
  Spotless ok. Chưa commit, chưa reset DB VPS. Còn: phần auth trên nhánh auth.
- 2026-11-10: Commit bị hook chặn (sửa migration đã commit). Trả V54–V61 về nguyên trạng, thêm V83 chuyển dữ liệu.
  Làm gọn code: `MealPlanWeek.changeDays` tách bước, `FoodSample.dishIds` dùng `List<Long>`.
