---
title: Cân đối khẩu phần 6 chỉ số theo bảng PMS (đạm, béo động vật / thực vật)
status: in-progress
owner: thangnd
jira: G94-192 / BE-FEAT-27   # FE: G94-191 / FE-FEAT-48
branch: feature/G94-254-BE-FEAT-27-day-boarding
modules: [nutrition]
rules: [NUT-24, NUT-17, NUT-18, NUT-06]
risk: high
created: 2026-11-02
updated: 2026-11-02
---
# Cân đối khẩu phần 6 chỉ số theo bảng PMS

## Context
User yêu cầu 2026-11-02 (bảng dinh dưỡng PMS tham chiếu): đổi cân đối khẩu phần từ 4 chỉ số (kcal, đạm, béo, glucid) sang 6 chỉ số (đạm ĐV, đạm TV, béo ĐV, béo TV, glucid g, kcal); tách rõ giá trị thực tế (g), tỷ lệ cơ cấu (%), tỷ lệ đạt định mức (%); đồng bộ thực đơn mẫu, kế hoạch tuần, AI gợi ý. BE đã có danh mục V41 (plan `docs/plans/completed/2026-11-02-day-boarding-l1-catalog.md`); FE tính dinh dưỡng ở `src/utils/menu-planning/menuCalculations.js`.

## Quyết định đã chốt
1. Rule NUT-24 (`BUSINESS_RULES.md`) — user yêu cầu 2026-11-02.
2. Agent chọn (chế độ tự chạy, chờ soát): nguồn gốc gán **theo thực phẩm** (không tách cột đạm/béo của thực phẩm); dữ liệu cũ gán theo nhóm (thịt, thủy sản, trứng sữa ⇒ ĐV; còn lại ⇒ TV); "phạm vi bữa ăn" = các bữa trường phục vụ nhóm tuổi (`age_groups.meal_sessions`); định mức thêm % đạm ĐV/tổng đạm và % béo ĐV/tổng béo, số do PHT nhập.

## Acceptance criteria
- **AC-1** (`NUT-24`): thực phẩm thiếu nguồn gốc ⇒ 400 mã 6026; nhập hàng loạt báo "thiếu nguồn gốc" theo dòng.
- **AC-2** (`NUT-24`): chi tiết món trả `animalProtein`, `plantProtein`, `animalLipid`, `plantLipid` và tổng `protein`, `lipid` = ĐV + TV.
- **AC-3** (`NUT-24`): cấu hình nhóm tuổi lưu/đọc khoảng % đạm ĐV, % béo ĐV; min > max ⇒ 400.
- **AC-4** (`NUT-24`, FE): cân đối khẩu phần, thực đơn mẫu, tuần, AI gợi ý hiện 6 chỉ số (g), % cơ cấu, % đạt định mức; thiếu số liệu ⇒ "chưa đủ dữ liệu", không đánh giá; sửa định lượng tính lại ngay.
- **AC-5**: báo ăn, số suất, dự trù không đổi kết quả.

## Phases
### Phase 1 — BE
- Files: C `db/migration/V55__add_food_origin_and_animal_norms.sql`, `utils/enums/FoodOrigin.java` · M `Food`, `FoodRequest/Response`, `AgeGroupMealConfig*`, `DishDetailResponse`, `FoodServiceImpl`, `DishServiceImpl`, `AgeGroupMealConfigServiceImpl`, `NutritionCatalogMapper`, `ApiCode` (6026), `R__dev_seed.sql`, `docs/api/openapi.yaml`, `DATA_DICTIONARY`, `CROSS_REPO_MAP`, flow
- Tests: AC-1 `FoodServiceImplTest#createRejectsMissingOrigin`; AC-2 `NutritionCatalogIntegrationTest#proteinAndLipidSplitByFoodOrigin`; AC-3 `AgeGroupMealConfigServiceImplTest`
- Exit: `node scripts/verify.mjs` → `VERIFY PASS full`
### Phase 2 — FE (nhánh `feature/G94-191-FE-FEAT-48-nutrition-management`)
- Engine 6 chỉ số, form thực phẩm có nguồn gốc, UI cân đối khẩu phần, đồng bộ thực đơn mẫu/tuần/AI, test. Plan FE: `CareNest_FE:docs/plans/active/2026-11-02-day-boarding-l1-catalog-fe.md`.

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Gán nguồn gốc theo nhóm sai với vài thực phẩm (mỡ lợn thuộc nhóm dầu, đường) | Tỷ lệ ĐV lệch | PHT sửa trên form; danh mục thật nhập lại có cột nguồn gốc | Sửa dữ liệu, không cần migration |
| Định mức % ĐV chưa có số | Không đánh giá được | Để trống = "chưa đặt định mức" | — |

## Progress log
### 2026-11-03 — Merge `origin/dev`
- `dev` đã có V41–V53 và mã lỗi 6008–6009 ⇒ đổi số migration nhánh: V41 → V54, V42 → V55; mã lỗi danh mục +2 (6010–6026). Hai migration chưa từng lên remote nên đổi số an toàn. FE `beErrorFields` cập nhật theo.

### 2026-11-03 — Lưu cấu hình theo lô (BE)
- `POST /nutrition/age-group-configs/batch`: một transaction cho nhiều nhóm tuổi; test `AgeGroupMealConfigServiceImplTest#batchStopsOnLockedVersion`, `#batchUpdatesFutureVersionAndCreatesMissing`.

### 2026-11-03 — Vá tồn đọng (BE)
- `GET /nutrition/dishes` trả `DishDetailResponse` (EntityGraph một truy vấn), bỏ `DishSummaryResponse` — FE hết gọi 1 + N.
- `POST /nutrition/foods/import`: `items[{id?, data}]`, thêm + sửa nguyên khối, `{created, updated}`; dòng sửa để trống mã ⇒ giữ mã cũ. Test `bulkImportUpdatesAndCreatesAtomically`, `dishListReturnsIngredientsAndNutrition`.
- FE (agent): nối Thiết lập dinh dưỡng, đổi khóa định mức, bỏ màn giá suất, HT có Thực phẩm/Món, NCC về `/menu/suppliers`, bỏ `REPORTERS`.

### 2026-11-03 — Phase 2 FE done (agent), chờ user soát
- Commit: chưa commit (nhánh FE `feature/G94-191-FE-FEAT-48-nutrition-management`)
- Verify FE: `node scripts/verify.mjs` → `VERIFY PASS full | npm run verify` (test menu 129/129; tệp mới `tests/menu/nutrition-six-indicators.test.mjs`, có hồi quy dự trù/đi chợ không đổi)
- Giả định FE: % đạt = thực tế ÷ giữa khoảng định mức (g đạm/béo/bột mục tiêu = kcal mục tiêu × giữa khoảng % năng lượng ÷ 4 hoặc 9); thiếu nguồn gốc thì cộng vào tổng nhưng không vào ĐV/TV; khóa định mức FE `animalProteinShare`/`animalLipidShare` ≠ BE `animalProteinPct*`/`animalLipidPct*` — map khi nối màn Thiết lập (đang demo).

### 2026-11-02 — Phase 1 BE done, Phase 2 FE đang làm (agent)
- Commit: chưa commit
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 647 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 252s`
- V42 (nay V55) + seed dev chạy thử trên DB tạm 2 lần: TP001/TP003 PLANT, TP002/TP004 ANIMAL.
