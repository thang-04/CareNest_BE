---
title: Bán trú L3 — kế hoạch tuần theo khối, công bố cả tuần, trừ kho theo suất, Bán trú hôm nay (BE)
status: in-progress
owner: thangnd
jira: G94-192 / BE-FEAT-27   # FE: G94-191 / FE-FEAT-48
branch: feature/G94-254-BE-FEAT-27-day-boarding
modules: [nutrition, attendance, identity-access]
rules: [NUT-06, NUT-07, NUT-08, NUT-09, NUT-11, NUT-13, NUT-17, NUT-19, NUT-20, NUT-24, P-38, ATT-07, NUT-03, AUTH-20, AUTH-21]
risk: high
created: 2026-11-03
updated: 2026-11-03
---
# Bán trú L3 — kế hoạch tuần, công bố cả tuần, trừ kho theo suất, Bán trú hôm nay (BE)

Plan con của `2026-11-02-day-boarding-redesign.md` (lát L3), cần L2 (`2026-11-03-day-boarding-l2-dry-stock.md`). Plan FE: `CareNest_FE:docs/plans/active/2026-11-03-day-boarding-l2-l3-fe.md`.

## Context
- Món + gam/trẻ theo nhóm tuổi: `dish_ingredients` (V54); định mức 6 chỉ số + giá suất theo nhóm tuổi, ngày hiệu lực: `age_group_meal_configs` (V54, V55). Bữa nhóm tuổi phục vụ: `age_groups.meal_sessions` (V16:16–22; `ag-2` 3 bữa, mẫu giáo `LUNCH,AFTERNOON`).
- Cân đối 6 chỉ số hiện chỉ tính ở FE (`CareNest_FE:src/utils/menu-planning/menuCalculations.js` `compareToNorm`, `overallNormStatus`); chặn công bố phải tính lại ở BE (NUT-06, NUT-09).
- Xác nhận suất: `MealCountServiceImpl.confirm` (`service/meals/impl/MealCountServiceImpl.java:180`) xác nhận theo điểm trường–ngày; chưa phát event. Mẫu event sau commit: `MealCountAttendanceListener` (ADR-0004).
- FE: `/menu/weekly` (`WeeklyMenuPage`, mock, công bố theo nhóm tuổi), `/kitchen` (`KitchenFlowPage`), `/menu/flow` (`NutritionFlowPage`) là trang tạm.

## Quyết định đã chốt
1. Công bố **cả tuần**, một lần cho cả trường; chưa cân đối thì chặn; đã công bố không sửa (NUT-08, user 02/11). Trừ kho khi suất điểm trường được xác nhận, lô hết hạn trước, thiếu không chặn, xác nhận lại chỉ ghi chênh lệch (NUT-11, P-38).
2. L2 + L3 một đợt; nhánh L1; mã Jira như L1 — user 03/11.

## Làm rõ nghiệp vụ
- **Actor:** PHT có `MENU_MANAGE` (cả trường, HT cấp, mặc định tắt) lập + công bố; HT có sẵn; PHT khác xem (AUTH-20). Trừ kho: hệ thống.
- **Flow:** [S] lập tuần (thứ 2–6) × bữa × khối (Nhà trẻ / Mẫu giáo — NUT-13) → [S] kiểm tự động mỗi ngày × nhóm tuổi → [S] công bố cả tuần (chặn nếu có lỗi chặn) → [S] suất điểm trường–ngày được xác nhận ⇒ trừ kho khô → [S] xác nhận lại ⇒ điều chỉnh chênh lệch → [S] phiếu nhập mới ⇒ trừ tiếp phần thiếu.
- **Ngoài phạm vi (lát sau):** bộ mẫu 4 tuần, món thay thế dị ứng (L3 phần còn lại), cảnh báo thiếu kho theo sĩ số dự kiến khi công bố (cần sĩ số dự kiến — làm cùng L4), sửa sau công bố `MENU_PUBLISHED_EDIT` (P-39), AI gợi ý (L7), hàng tươi (L4), lưu mẫu (L5).

### Câu hỏi mở
Không có câu hỏi chặn; 3 giả định ở Key decisions (đánh dấu "chờ soát").

## Thiết kế
**V57__create_meal_plans.sql:** `meal_plan_weeks(week_start UK, CHECK thứ 2, status DRAFT|PUBLISHED, published_by/at, audit)` · `meal_plan_days(week_id, menu_date, off BOOL, note, UK)` · `meal_plan_items(day_id, session MORNING|LUNCH|AFTERNOON, menu_block NURSERY|KINDERGARTEN, dish_id, sort_order, UK(day, session, block, dish))` · `stock_daily_demands(campus_id, meal_date, food_id, required_qty, shortfall_qty, UK(campus, date, food))` — nhu cầu đã chốt theo suất và phần chưa trừ được (= "phần thiếu chờ bổ sung").

**Khối:** suy từ nhóm tuổi — `age_to_months ≤ 36` ⇒ `NURSERY`, còn lại `KINDERGARTEN` (nhà trẻ đến 36 tháng); không thêm cột.

**Kiểm tự động** (`MealPlanEvaluator`, lớp thuần, cùng công thức FE): mỗi ngày không nghỉ × nhóm tuổi có lớp năm học hiện hành: món của khối trong các bữa nhóm phục vụ, gam/trẻ của nhóm ⇒ 6 chỉ số, % P:L:G theo năng lượng, % đạm/béo ĐV, giá/trẻ; so định mức hiệu lực ngày đó.
- **Chặn:** `NO_DISHES` (bữa phục vụ chưa có món), `NOT_BALANCED` (LOW/HIGH), `INCOMPLETE_DATA` (thiếu số liệu/nguồn gốc/định lượng cho nhóm), `NORM_NOT_SET` (chưa đặt 4 khoảng định mức chính).
- **Cảnh báo:** `OVER_BUDGET` (giá/trẻ > giá suất — NUT-19), `DISH_REPEATED` (món chính lặp trong tuần cùng khối — NUT-20). Dị ứng: liệt kê chất có trong ngày (NUT-09; dữ liệu dị ứng của trẻ chưa có ở BE).

**Trừ kho:** `MealCountService.confirm` phát `MealCountConfirmedEvent(campusId, date)` khi có số chuyển sang chốt; `StockDeductionListener` (`@TransactionalEventListener` + `REQUIRES_NEW`) gọi `StockLedgerService.syncDay(campus, date)`: nhu cầu thực phẩm `DRY` = Σ bữa × nhóm tuổi (suất đã chốt `normal + substitute` × gam/trẻ) ÷ (1 − thải bỏ%) theo thực đơn tuần đã công bố. Chênh với `required_qty` cũ: tăng ⇒ trừ FIFO hạn dùng (`DEDUCT` lần đầu, `ADJUST` khi xác nhận lại), thiếu ⇒ cộng `shortfall_qty`; giảm ⇒ bớt `shortfall_qty` trước, rồi trả lại đúng lô đã trừ (`ADJUST` dương). Không chênh ⇒ không ghi gì (một lần). Công bố tuần ⇒ đồng bộ các ngày đã có suất chốt. Xác nhận phiếu nhập (L2) ⇒ trừ phần thiếu theo ngày cũ trước (`SHORTFALL_DEDUCT`). Mọi bước khóa dòng `campuses` FOR UPDATE.

**API** (`/nutrition`):

| Method · path | Quyền | Ghi chú |
| --- | --- | --- |
| `GET /meal-plans?from&to` | đọc | Danh sách tuần + trạng thái |
| `GET /meal-plans/{weekStart}` | đọc | Lưới + kết quả kiểm (ngày × nhóm tuổi) + `issues[]` + `canEdit/canPublish`; chưa có ⇒ `status: NONE` |
| `PUT /meal-plans/{weekStart}` | `MENU_MANAGE` | Ghi đè cả lưới, tạo nếu chưa có; chỉ `DRAFT`; món `ACTIVE`; bữa phải thuộc bữa khối phục vụ |
| `POST /meal-plans/{weekStart}/publish` | `MENU_MANAGE` | Có lỗi chặn ⇒ 409 + `data.issues`; xong ⇒ `PUBLISHED` |
| `GET /stock/shortfalls?campusId` | đọc | Phần thiếu chờ bổ sung |
| `GET /day-board?date&campusId` | đọc (HT, PHT) | Theo điểm trường: thực đơn tuần này + tuần sau, suất đã chốt theo bữa, kho khô (chưa có suất / đã trừ / thiếu n thực phẩm), hàng tươi + lưu mẫu = `NOT_AVAILABLE` (L4, L5) |

`ApiCode` 6034–6039: tuần không hợp lệ (không phải thứ 2), tuần đã công bố, ngày ngoài tuần, bữa không thuộc khối, món ngừng dùng, chưa đạt kiểm tra công bố.

## Acceptance criteria
- **AC-1** (`NUT-08`, `NUT-24`): Given ngày thứ 4 nhóm `ag-3` vượt định mức năng lượng, When công bố, Then 409, `issues` nêu ngày + nhóm tuổi + chỉ số; sửa đạt hết ⇒ `PUBLISHED`; `PUT` sau đó ⇒ 409.
- **AC-2** (`NUT-11`, `P-38`): Given tuần đã công bố, kho X có 2 lô cùng thực phẩm hạn khác nhau, When suất ngày D của X được xác nhận, Then trừ đúng gam × suất ÷ (1 − thải bỏ%), lô hết hạn trước; gọi lại không ghi thêm; kho thiếu ⇒ xác nhận vẫn 200, `shortfall_qty` đúng phần thiếu.
- **AC-3** (`NUT-11`, `ATT-07`, `NUT-03`): Given D đã trừ, điểm danh đổi và suất được xác nhận lại, Then chỉ ghi `ADJUST` bằng chênh lệch (tăng: trừ thêm; giảm: trả lại lô / bớt phần thiếu).
- **AC-4** (`NUT-11`): Given phần thiếu của D, When xác nhận phiếu nhập có thực phẩm đó, Then `SHORTFALL_DEDUCT` và `shortfall_qty` giảm.
- **AC-5** (`AUTH-20`): PHT không có `MENU_MANAGE` ⇒ `PUT`/`publish` 403, `GET` 200; bếp ⇒ `GET /day-board` 403.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Chặn khi chưa đặt định mức / thiếu số liệu (chờ soát) | Chặn (`NORM_NOT_SET`, `INCOMPLETE_DATA`) | Chỉ cảnh báo | NUT-08 "chưa đạt cân đối thì chặn"; NUT-24 liệt kê việc không chặn mà không có công bố |
| Suất tính kho (chờ soát) | `normal + substitute`, theo món chuẩn | Bỏ suất thay thế | Món thay thế dị ứng chưa có; trẻ thay thế vẫn ăn bữa |
| Nhóm tuổi được kiểm (chờ soát) | Nhóm có lớp trong năm học hiện hành | Cả 4 nhóm | Nhóm không có lớp không cần cân đối |
| Lưu thực đơn | Theo khối, gam lấy theo nhóm tuổi của món | Theo từng nhóm tuổi (FE mock) | NUT-13 mẫu giáo cùng món khác định lượng |
| Hub | Một API tổng hợp đọc, không tạo sổ suất | FE gọi 4 API | `GET /meal-counts` tự tổng hợp khi đọc; hub chỉ đọc |

## Phases
### Phase 1 — Kế hoạch tuần, kiểm, công bố
- Files: C `V57__create_meal_plans.sql`, `entity/meals/MealPlan{Week,Day,Item}.java`, `utils/enums/{MenuBlock,MealPlanStatus}.java`, repos, `service/meals/MealPlanService.java` + impl, `service/meals/impl/MealPlanEvaluator.java`, `controller/meals/MealPlanController.java`, `dto/meals/MealPlan*` · M `Permission` (`MENU_MANAGE`), `ApiCode`
- Tests: AC-1, AC-5 → `MealPlanEvaluatorTest` (unit, tính số), `MealPlanIntegrationTest`
### Phase 2 — Trừ kho theo suất
- Files: C `entity/meals/StockDailyDemand.java` + repo, `service/meals/StockLedgerService.java` + impl, `service/meals/impl/StockDeductionListener.java` · M `MealCountService` (event), `MealCountServiceImpl.confirm`, `StockReceiptServiceImpl.confirm` (trừ phần thiếu), `MealPlanServiceImpl.publish`
- Tests: AC-2, AC-3, AC-4 → `StockDeductionIntegrationTest`
### Phase 3 — Bán trú hôm nay, contract, docs, seed
- Files: C `service/meals/DayBoardService.java` + impl, `dto/meals/DayBoardResponse.java` · M `MealPlanController` (hoặc C `DayBoardController`), `docs/api/openapi.yaml`, `docs/modules/nutrition.md`, `docs/database/DATA_DICTIONARY.md`, `docs/system/CROSS_REPO_MAP.md`, `docs/context/CURRENT_STATE.md`, `db/dev/R__dev_seed.sql` (định mức, món, tuần đã công bố để demo), plan ô Progress log
- Tests: `DayBoardIntegrationTest` (trạng thái từng dòng, bếp 403); `OpenApiSnapshotTest`
- Exit mỗi phase: `node scripts/verify.mjs` → `VERIFY PASS full`, skip 0; Phase 3 thêm `node scripts/check-ai-layer.mjs` 0 lỗi + chạy `compose.yaml` local.

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1 | NUT-08, NUT-24 | `MealPlanEvaluatorTest` (6 ca), `MealPlanIntegrationTest#publishBlockedThenPublishedThenLocked` | 1 | PASS |
| AC-5 | AUTH-20 | `MealPlanIntegrationTest#editRequiresMenuManage`, `#dayBoardShowsMenuCountsAndStock` (bếp 403) | 1, 3 | PASS |
| AC-2 | NUT-11, P-38 | `DryStockIntegrationTest#confirmDeductsFifoOnce`, `#shortfallRecordedThenSettledByNextReceipt`; `StockDeductionListenerIntegrationTest` (event sau commit) | 2 | PASS |
| AC-3 | NUT-11, ATT-07 | `DryStockIntegrationTest#reconfirmWritesOnlyDelta`; `StockDeductionListenerIntegrationTest` (trẻ đến muộn) | 2 | PASS |
| AC-4 | NUT-11 | `DryStockIntegrationTest#shortfallRecordedThenSettledByNextReceipt` | 2 | PASS |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| BE và FE lệch cách tính cân đối | Màn báo đạt, BE chặn | Cùng công thức, làm tròn 0,1 như FE; FE hiện kết quả BE khi công bố | Sửa evaluator |
| Listener lỗi sau commit xác nhận suất | Kho không trừ | Log `ERROR`; xác nhận lại hoặc công bố lại gọi `syncDay` (idempotent) | — |
| Trừ sai | Tồn lệch | Ledger + AC-2/3/4; điều chỉnh bằng bút toán, không xóa | Bút toán đảo |

## Validation log
- [x] Khẳng định về code có `file:line`
- [x] Đổi contract ⇒ consumer cụ thể: FE `/menu/weekly`, `/kitchen`, `/menu/flow`, menu "Bán trú hôm nay"; APP chưa dùng
- [x] >8 file ⇒ đã tách L2/L3 và 3 phase
- [x] Không dùng rule PENDING/OPEN (P-26, P-27, P-39 ngoài phạm vi)

## Progress log
### 2026-11-03 — Plan duyệt (user thangnd, chạy một mạch; chặn công bố khi chưa đặt định mức hoặc thiếu số liệu — user chọn)
- Commit: chưa commit

### 2026-11-03 — Phase 1–3 done (chạy một mạch theo user)
- Commit: chưa commit
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 777 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 377s`; `node scripts/check-ai-layer.mjs` → 0 lỗi, 7 cảnh báo cũ.
- Khác plan: 409 công bố không trả `data.issues` (GlobalException không mang data, không đổi handler) — câu lỗi 6039 nêu số lỗi + lỗi đầu, FE đọc `issues` từ `GET /{weekStart}`; hub đặt trong `MealPlanController` (`/nutrition/day-board`); test gom vào `DryStockIntegrationTest`, `MealPlanIntegrationTest`, `StockDeductionListenerIntegrationTest`.
- Sự cố đã xử lý: (1) ghi đè lưới bằng xóa-chèn ⇒ `DetachedObjectException`/vỡ khóa duy nhất vì Hibernate chèn trước khi xóa ⇒ sửa tại chỗ `MealPlanWeek.changeDays`; (2) record lồng `Line`, `Totals`, `Day`, `Item` trùng tên schema springdoc làm snapshot ghi đè schema sổ suất ⇒ đổi tên `ReceiptLineInput`, `ReceiptLine`, `DayInput`, `ItemInput`, `NutritionTotals`, `SessionServings`, `WeekMenu` (JSON không đổi). Ghi ở module card nutrition (Known pitfalls).
- Quyết định user 03/11: chặn công bố cả khi chưa đặt định mức hoặc thiếu số liệu (`NORM_NOT_SET`, `INCOMPLETE_DATA`).
- Giả định agent (chờ soát): suất tính kho = `normal + substitute` theo món chuẩn; chỉ kiểm nhóm tuổi có lớp năm học hiện hành; thứ 7, CN là ngày nghỉ trên hub; seed dev thêm định mức demo (500–750 kcal, P 13–20, L 25–35, G 47–60%) và 5 món để lập tuần demo.

### 2026-11-03 — Kiểm dữ liệu thật + chỉnh theo kết quả kiểm
- Commit: chưa commit
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 777 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 354s`
- Khác plan: `blocks` chỉ gồm khối có nhóm tuổi được kiểm (có lớp năm học hiện hành) — tab "Nhà trẻ" khi trường không có lớp nhà trẻ gây hiểu nhầm; `MealPlanIntegrationTest#editRequiresMenuManage` cập nhật.
- Kiểm E2E dữ liệu thật (user yêu cầu 03/11 "test dữ liệu thật, không mock"): stack docker riêng `carenest_l3` (Postgres :5433, Keycloak :8181 import user dev, API :8081 build từ nhánh) + FE build `VITE_USE_MOCK=false VITE_SCHOOL_CONFIG_API=true`, đăng nhập HT dev. Luồng đã chạy: phiếu nhập 2 điểm trường (API) → lập lưới tuần 02/11 → công bố trên UI → sổ suất tổng hợp từ điểm danh thật, HT xác nhận trên UI → event trừ kho: CS-A gạo 166 g lấy lô hạn 30/11 trước, dầu 10 g, sữa thiếu 360 ml; CS-B trừ đủ 3 thực phẩm (khớp tính tay) → phiếu nhập bổ sung sữa trên UI ⇒ `SHORTFALL_DEDUCT` −2 hộp, hub CS-A "Đã trừ"; hủy phiếu có lô đã trừ ⇒ 409 6032; tuần 09/11 lập trên UI, thứ Ba trống ⇒ hộp thoại 2 lỗi chặn, `POST /publish` ⇒ 409 6039.
- Ảnh: `docs/plans/active/day-boarding-screens/l3-*.jpg` (hub 3 trạng thái, kế hoạch tuần đã công bố/nháp, bảng kiểm, hộp thoại công bố), `l2-*.jpg` (tồn theo lô, lịch sử, phần thiếu, hủy bị chặn).

### 2026-11-03 — Sửa 2 lỗi review E2E trên `7b9ac74` (user duyệt qua phiên review)
- Commit: chưa commit
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 780 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 376s`
- (1) Race lưu/công bố: công bố có thể ra PUBLISHED với lưới chưa kiểm (không khóa dòng tuần). Sửa: `MealPlanWeekRepository.findForUpdateByWeekStart` (`PESSIMISTIC_WRITE`) ở cả lưu và công bố. Test `MealPlanPublishConcurrencyIntegrationTest` (2 luồng thật) đỏ trên code cũ (`PUBLISHED`), xanh sau sửa (`DRAFT` + 409 6039).
- (2) Trừ kho sau commit lỗi thì mất, hub che bằng "không có thực phẩm khô". Chọn: hub so nhu cầu tính lại (chỉ đọc, `StockLedgerService.unsyncedFoodCount`) với nhu cầu đã ghi ⇒ trạng thái `NOT_SYNCED`; thao tác vá `POST /nutrition/stock/days/{date}/sync?campusId` (`resyncDay`, quyền `STOCK_MANAGE` hoặc `MEAL_COUNT_CONFIRM` của điểm trường, idempotent). Lý do không chọn "luôn phát event khi xác nhận": sổ đã xác nhận không còn nút xác nhận trên UI (`canConfirm` false) nên người dùng không vá được; hub là nơi PHT nhìn thấy lệch. Test `StockDeductionRecoveryIntegrationTest` (spy ném lỗi lần đầu) đỏ trên code cũ (`NOTHING_TO_DEDUCT`), xanh sau sửa: `NOT_SYNCED` → đồng bộ trừ 60 g đúng một lần, gọi lại không trừ thêm; GV gọi ⇒ 6033.
- Kiểm stack `carenest_l3`: sổ suất CS-A 05/11 chốt mà không qua event (mô phỏng listener lỗi) ⇒ hub "Chưa trừ 3 thực phẩm" + nút "Đồng bộ kho" ⇒ bấm ⇒ "Đã trừ 3 thực phẩm", DB mỗi thực phẩm 1 bút toán; gọi lại API 200 không thêm bút toán. Ảnh `l3-hub-4-not-synced.jpg`, `l3-hub-5-after-resync.jpg`.
- Ngoài phạm vi lần này (ghi ở trang so sánh của phiên review): `settleShortfalls` khi trả hàng về lô, kiểm trạng thái phiếu trước khi khóa (xác nhận 2 lần ⇒ 500), thứ tự khóa `campuses`, NUMERIC(14,2) vs FE 3 số lẻ, quyền kho theo cơ sở ở FE, route aiDraftId, cột "Người thực hiện".
