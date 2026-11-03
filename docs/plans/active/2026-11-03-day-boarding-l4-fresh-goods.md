---
title: Bán trú L4 — bản dự trù hàng tươi, kiểm nhận 2 lần (BE)
status: in-progress
owner: thangnd
jira: G94-192 / BE-FEAT-27   # FE: G94-191 / FE-FEAT-48
branch: feature/G94-254-BE-FEAT-27-day-boarding
modules: [nutrition, school-structure]
rules: [NUT-07, NUT-10, NUT-12, NUT-18, AUTH-20]
risk: high
created: 2026-11-03
updated: 2026-11-03
---
# Bán trú L4 — bản dự trù hàng tươi, kiểm nhận 2 lần (BE)

Plan con của `2026-11-02-day-boarding-redesign.md` (lát L4), sau L2/L3. User 03/11: "làm tiếp các làn còn lại" — phạm vi L4 + BE/Web L5, L6; App Flutter và L7 AI để sau; mã Jira như L1–L3. Chế độ tự chạy (CLAUDE.md): plan ghi giả định, làm tiếp.

## Context
- Hàng tươi (`foods.storage = FRESH`, NUT-10) không vào kho (NUT-12). Thực đơn tuần `meal_plan_*` (V57); nhu cầu theo suất đã có cho kho khô (`StockLedgerServiceImpl.dryDemand`).
- Sĩ số đang học theo lớp: `ClassroomRepository.countActiveChildren` (organization) — meals chỉ gọi qua `ClassroomService` (ADR-0001).
- FE demo: `/kitchen/forecast` (`WeeklyForecastPage`), `/kitchen/fresh-receipts` (`FreshReceiptPage`); hub L3 trả `freshReceiptStatus = NOT_AVAILABLE`.

## Làm rõ nghiệp vụ
- **Actor:** PHT có `STOCK_MANAGE` (điểm trường) lập bản dự trù, ghi kiểm nhận; HT có sẵn; PHT khác xem.
- **Flow:** [S] chọn điểm trường, kỳ (≤ 31 ngày) → sĩ số dự kiến điền sẵn theo nhóm tuổi (trẻ đang học), PHT sửa → [S] tính lượng hàng tươi theo thực đơn tuần của các ngày trong kỳ (nháp hoặc đã công bố, bỏ ngày nghỉ), lưu → [B] in, gửi NCC → [B] bếp kiểm, báo PHT → [S] lần 1: PHT ghi đủ/thiếu/thừa so bản dự trù ngày đó → [S] lần 2 (khi tuần đã công bố và suất ngày đã xác nhận): ghi so nhu cầu theo suất.
- **Giả định (chờ soát):** PHT chọn đủ/thiếu/thừa cho từng dòng (không tự so số — không có ngưỡng sai số trong rule); số lượng thực nhận không bắt buộc. Lần 1 dùng bản dự trù mới nhất có kỳ chứa ngày đó.

### Câu hỏi mở
Không có câu hỏi chặn.

## Thiết kế
**V60__create_fresh_goods.sql** (đánh số V60: V58/V59 đã có file khác trên nhánh — định mức gam, bộ thực đơn mẫu): `fresh_forecasts(campus_id, period_from, period_to, note, created_by, created_at, updated_at, CHECK from ≤ to)` · `fresh_forecast_counts(forecast_id, age_group_id, enrolled_count, expected_count ≥ 0, UK(forecast, age_group))` · `fresh_forecast_lines(forecast_id, menu_date, food_id, quantity, UK)` · `fresh_checks(campus_id, check_date, round 1|2, forecast_id NULL, checked_by, checked_at, note, UK(campus, date, round))` · `fresh_check_lines(check_id, food_id, expected_qty, received_qty NULL, result ENOUGH|SHORT|OVER, note)`. Số lượng theo đơn vị gốc (g/ml) như kho khô. Bảng con có khóa thay thế `id` (như `stock_receipt_lines`), khóa nghiệp vụ là UK.

**Nhu cầu dùng chung:** tách `MealDemandCalculator` (impl, thuần): món của ngày × suất theo (bữa, nhóm tuổi) × gam/trẻ ÷ (1 − thải bỏ%), lọc theo `FoodStorage`. Kho khô (suất đã chốt, DRY), dự trù (sĩ số dự kiến × bữa nhóm tuổi phục vụ, FRESH), kiểm nhận lần 2 (suất đã chốt, FRESH).

**API** (`/nutrition`, đọc: HT/PHT; ghi: `STOCK_MANAGE` điểm trường):

| Method · path | Body / trả về |
| --- | --- |
| `GET /fresh-forecasts?campusId` | `[{id, campusId, periodFrom, periodTo, note, foodCount, createdAt}]` mới nhất trước |
| `POST /fresh-forecasts/preview` | `{campusId, periodFrom, periodTo, note?, counts?:[{ageGroupId, expectedCount}]}` → `FreshForecastResponse` (`id` null, không lưu; `counts` null ⇒ điền sẵn sĩ số đang học) |
| `POST /fresh-forecasts` | cùng body → 201 `FreshForecastResponse` đã lưu |
| `GET /fresh-forecasts/{id}` · `DELETE /fresh-forecasts/{id}` | |
| `GET /fresh-checks?campusId&date` | `FreshCheckResponse` |
| `PUT /fresh-checks?campusId&date&round` | `{note?, lines:[{foodId, receivedQty?, result, note?}]}` → `FreshCheckResponse` |

`FreshForecastResponse {id, campusId, periodFrom, periodTo, note, counts:[{ageGroupId, ageGroupName, enrolledCount, expectedCount}], days:[{date, off, planStatus: NONE|DRAFT|PUBLISHED}], lines:[{food: StockFoodRef, totalQty, byDate:[{date, quantity}]}], createdAt, createdBy, canManage}`.
`FreshCheckResponse {campusId, date, canManage, rounds:[{round, available, unavailableReason, source: FORECAST|CONFIRMED_SERVINGS, forecastId, recorded, checkedAt, checkedBy, note, lines:[{food: StockFoodRef, expectedQty, receivedQty, result, note}]}]}` — chưa ghi thì `lines` là danh sách cần nhận, `result` null.
Hub: `freshReceiptStatus` ∈ `NO_FORECAST`, `ROUND1_PENDING`, `ROUND1_DONE` (chờ suất để kiểm lần 2), `ROUND2_PENDING`, `DONE`, `DAY_OFF`.
`ApiCode` 6040–6043: không tìm thấy bản dự trù; kỳ không hợp lệ (from ≤ to, ≤ 31 ngày); chưa kiểm nhận lần {0} được: {1}; dòng kiểm nhận không khớp danh sách cần nhận: {0}.

## Acceptance criteria
- **AC-1** (`NUT-07`): Given điểm trường có 2 trẻ đang học nhóm `ag-3`, When xem trước bản dự trù không gửi `counts`, Then `expectedCount = enrolledCount = 2`; sửa thành 5 ⇒ lượng = 5 × gam/trẻ ÷ (1 − thải bỏ) mỗi ngày phục vụ; chỉ thực phẩm FRESH; ngày nghỉ không tính.
- **AC-2** (`NUT-12`): Given bản dự trù đã lưu chứa ngày D, When ghi kiểm nhận lần 1 ngày D, Then dòng cần nhận = dòng dự trù ngày D; thiếu dòng hoặc thực phẩm lạ ⇒ 400 6043.
- **AC-3** (`NUT-12`): Given suất ngày D chưa xác nhận, When ghi lần 2, Then 409 6042; suất đã xác nhận + tuần đã công bố ⇒ dòng cần nhận = nhu cầu FRESH theo suất đã chốt.
- **AC-4** (`AUTH-20`): PHT không có `STOCK_MANAGE` ⇒ lưu dự trù / ghi kiểm nhận 403 (6033), xem 200.
- **AC-5**: Hub trả `freshReceiptStatus` đúng theo các trạng thái trên.

## Phases
### Phase 1 — Bảng, tính nhu cầu, dự trù, kiểm nhận, hub
- Files: C `V60__create_fresh_goods.sql`, `entity/meals/Fresh*.java`, `repository/meals/Fresh*Repository.java`, `service/meals/FreshGoodsService.java` + impl, `service/meals/impl/MealDemandCalculator.java`, `controller/meals/FreshGoodsController.java`, `dto/meals/Fresh*` · M `StockLedgerServiceImpl` (dùng calculator), `DayBoardServiceImpl`, `ClassroomService` (+`countActiveChildren`), `ApiCode`, `docs/api/openapi.yaml`, docs module/data dictionary/cross-repo
- Tests: AC-1..AC-5 → `FreshGoodsIntegrationTest`; hồi quy kho khô `DryStockIntegrationTest`
- Exit: `node scripts/verify.mjs` → `VERIFY PASS full`; chạy trên stack `carenest_l3`.

## Test matrix
| AC | Rule | Test | Kết quả |
| --- | --- | --- | --- |
| AC-1 | NUT-07 | `FreshGoodsIntegrationTest#previewPrefillsEnrolmentAndScalesWithExpectedCount` | PASS |
| AC-2 | NUT-12 | `FreshGoodsIntegrationTest#roundOneUsesForecastLines` | PASS |
| AC-3 | NUT-12 | `FreshGoodsIntegrationTest#roundTwoNeedsConfirmedServings` | PASS |
| AC-4 | AUTH-20 | `FreshGoodsIntegrationTest#writesNeedStockManage` | PASS |
| AC-5 | — | `FreshGoodsIntegrationTest#dayBoardFreshStatus` | PASS |
| bổ sung | NUT-07 | `#draftWeekCountsAndMissingMenuHasNoLines`, `#periodAndAgeGroupAreValidated` | PASS |
| bổ sung | NUT-12 | `#deletingForecastKeepsCheckHistory`, `#rejectsUnknownCampusAndRound` | PASS |
| hồi quy | NUT-11 | `DryStockIntegrationTest`, `StockDeductionListenerIntegrationTest`, `StockDeductionRecoveryIntegrationTest`, `MealPlanIntegrationTest`, `MealPlanPublishConcurrencyIntegrationTest`, `ArchitectureRulesTest` | PASS (29/29) |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Tách tính nhu cầu làm lệch trừ kho khô | Sai kho | Giữ nguyên test kho khô, chạy lại | Hoàn tác tách |

## Progress log
### 2026-11-03 — Plan (chế độ tự chạy, user yêu cầu làm tiếp)
- Commit: chưa commit

### 2026-11-03 — BE phase 1 xong
- Làm: `V60__create_fresh_goods.sql`, entity/repo `Fresh*`, `FreshGoodsService(+Impl)`, `FreshGoodsController`, DTO `Fresh*`, `MealDemandCalculator` (tách từ `StockLedgerServiceImpl.dryDemand`, hành vi kho khô giữ nguyên — test kho khô xanh), `ClassroomService.countActiveChildren`, hub `freshReceiptStatus`, `ApiCode` 6040–6043, snapshot OpenAPI, docs module/dictionary/cross-repo.
- Giả định (chờ soát): (1) đánh số **V60** thay V58 vì V58 (định mức gam) và V59 (bộ thực đơn mẫu) đã có file khác trên nhánh; (2) xem trước (`/preview`) tính là đọc — HT/PHT không cần `STOCK_MANAGE`; (3) `counts` gửi thiếu nhóm tuổi ⇒ nhóm đó lấy sĩ số đang học, nhóm tuổi lạ hoặc gửi trùng ⇒ 400 `6024`; chỉ liệt kê nhóm tuổi có lớp năm học hiện hành ở điểm trường hoặc được gửi; (4) sĩ số đang học tính tại `periodFrom`; (5) lần 2 khả dụng khi tuần đã công bố, ngày không nghỉ và mọi sổ báo ăn của điểm trường–ngày đã `CONFIRMED` (tính theo dòng đã chốt, bỏ dòng chờ xác nhận lại); (6) lần 1 khả dụng khi có bản dự trù có kỳ chứa ngày (kể cả ngày nghỉ — dòng rỗng), hub ưu tiên `DAY_OFF`; (7) đã ghi thì GET trả dòng đã lưu (lượng cần nhận chụp lúc ghi), chưa ghi thì trả lượng cần nhận hiện tại; (8) xóa bản dự trù giữ lịch sử kiểm (`forecast_id` về null); (9) điểm trường không tồn tại ⇒ 404 `2103`.
- Test: `FreshGoodsIntegrationTest` 9/9; hồi quy kho khô/kế hoạch tuần/ArchUnit 29/29 (xem bảng trên). `MealPlanIntegrationTest` đổi kỳ vọng hub `freshReceiptStatus` từ `NOT_AVAILABLE` sang `NO_FORECAST` (hành vi mới theo plan).
- Commit: chưa commit

### 2026-11-03 — Verify + kiểm trên stack `carenest_l3` (dữ liệu thật, qua UI web)
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 795 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 484s`
- Stack `carenest_l3` migrate tới V60. CS-A, kỳ 03–07/11, thực đơn tuần đã công bố: xem trước theo sĩ số (2 trẻ MG nhỡ) ⇒ 4 thực phẩm tươi; đổi dự kiến 20 trẻ ⇒ lượng ×10 (bí xanh 3,556 kg); T7 07/11 hiện "Ngày nghỉ". Lưu ⇒ bản dự trù id 1.
- Hub 03/11 CS-A "Chờ kiểm lần 1", CS-B "Chưa có bản dự trù". Kiểm lần 1 ngày 05/11 (đánh dấu tất cả Đủ) ⇒ DB 4 dòng `ENOUGH`, lượng cần nhận chụp theo bản dự trù (888,89 g bí xanh) ⇒ hub "Chờ kiểm lần 2". Kiểm lần 2 (so 2 suất đã xác nhận; thịt lợn thiếu, thực nhận 0,04 kg) ⇒ DB `SHORT` 40 g/58,95 g ⇒ hub "Đã kiểm đủ 2 lần".
- Ghi nhận: hub `DONE` không báo dòng thiếu của lần 2 — đúng plan hiện tại; nếu cần cảnh báo thiếu thì là việc sau.
- Commit: chưa commit
