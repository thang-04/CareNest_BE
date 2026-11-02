---
title: Bán trú L1 — danh mục thực phẩm, món, cấu hình nhóm tuổi, NCC (BE)
status: done
owner: thangnd
jira: G94-254 / BE-FEAT-27
branch: feature/G94-254-BE-FEAT-27-day-boarding
modules: [nutrition, identity-access]
rules: [NUT-05, NUT-10, NUT-12, NUT-17, NUT-18, NUT-19, HLT-06, AUTH-02, AUTH-20, SCH-06]
risk: high
created: 2026-11-02
updated: 2026-11-02
---
# Bán trú L1 — danh mục thực phẩm, món, cấu hình nhóm tuổi, NCC (BE)

Plan con của `2026-11-02-day-boarding-redesign.md` (lát L1). Plan FE tương ứng: `CareNest_FE:docs/plans/active/2026-11-02-day-boarding-l1-catalog-fe.md`.

## Context
Mọi lát sau (kho, thực đơn, dự trù, bếp) cần danh mục. BE hiện chỉ có sổ số suất (`controller/meals/MealCountController.java`); không có bảng thực phẩm/món/NCC/chất dị ứng (grep `allergen` trong `db/migration` = 0). Migration cuối V40. Nhóm tuổi có sẵn: `age_groups` (V10, `ag-2..ag-5`). Bữa cố định `MORNING/LUNCH/AFTERNOON` (`V26__create_meal_counts.sql:17`). Quyền theo enum `utils/enums/Permission.java` + `@permissionGuard`.

## Quyết định đã chốt
1. Rule NUT-* chốt 2026-11-01; danh mục dùng chung hai cơ sở (SCH-06).
2. Key decisions của plan ô (bữa giữ enum 3 giá trị) — agent chọn, chờ user soát.

## Làm rõ nghiệp vụ
- **Actor:** PHT phụ trách dinh dưỡng (có `NUTRITION_MANAGE`), HT; PHT khác chỉ xem (AUTH-20).
- **Outcome:** danh mục đủ để lát L3 tính cân đối, giá, định lượng; L2 nhập kho theo đơn vị mua.
- **Ảnh hưởng:** FE `/menu/foods`, `/menu/dishes`, `/menu/setup`, `/menu/prices`, `/kitchen/suppliers` (đổi sang `/nutrition/*`). APP: chưa dùng ở L1. Dữ liệu cũ: không có.

### Câu hỏi mở
Không có. Danh mục thật (Viện Dinh dưỡng) do team chuẩn bị (NUT-18) — nạp qua API nhập hàng loạt, ngoài plan này.

## Thiết kế

**Bảng (V41)** — mọi bảng có `created_at/updated_at`, xóa = đổi `status` `ACTIVE/INACTIVE` (lát sau tham chiếu):
- `allergens(code UK, name, system BOOL)` — seed 9 chất theo FE (`Tôm, Cua, Cá, Trứng, Sữa bò, Đậu phộng, Đậu nành, Lúa mì, Mè`). Thêm mục "khác" do PHT (HLT-06) để lát dị ứng của module child.
- `foods(code UK, name, food_group, unit g|ml, purchase_unit, purchase_unit_size, unit_price /đơn vị mua, kcal, protein, lipid, glucid /100 unit, nutrient_source, nutrient_source_version, waste_pct, storage FRESH|DRY (khớp FE; DRY = kho khô — NUT-10), note, status)` + `food_allergens(food_id, allergen_id)`.
- `dishes(code UK, name, dish_type CARB|MAIN|SOUP|VEGETABLE|SNACK|DESSERT, description, reference BOOL, status)` + `dish_ingredients(dish_id, food_id, age_group_id, grams_per_child > 0, UK(dish,food,age_group))`.
- `age_group_meal_configs(age_group_id, effective_from, meal_price, kcal_min/max, protein/lipid/glucid_pct_min/max (để trống được — chuẩn chưa chốt), norm_basis, UK(age_group,effective_from))`. Bữa trong ngày **giữ ở `age_groups.meal_sessions`** (V16, sổ suất đang dùng), không lặp ở đây.
- `suppliers(name, phone, address, id_card, delivery_person, status)`.

**Quyền:** thêm `Permission.NUTRITION_MANAGE` (module `meals`, default `PRINCIPAL`, configurable `VICE_PRINCIPAL`, không campusScoped — danh mục cả trường). Không seed `role_permissions` ⇒ PHT mặc định tắt (AUTH-20). Đọc: `PRINCIPAL`, `VICE_PRINCIPAL`.

**API** (`/nutrition`, `ResponseJson` như controller hiện có):

| Method · path | Quyền | Ghi chú |
| --- | --- | --- |
| `GET /allergens` | đọc | |
| `GET /foods` (`keyword, group, storage, status`) · `GET /foods/{id}` | đọc | |
| `POST /foods` · `PUT /foods/{id}` · `PATCH /foods/{id}/status` | MANAGE | `nutrient_source` bắt buộc (NUT-05) |
| `POST /foods/import` | MANAGE | JSON `items[]`, tất cả hoặc không; lỗi trả theo dòng. Không thêm thư viện Excel — FE đọc file |
| `GET /dishes` (`keyword, type, reference, status`) · `GET /dishes/{id}` | đọc | detail trả định lượng theo nhóm tuổi + tính sẵn kcal/P/L/G, giá/suất, chất dị ứng suy ra từ thực phẩm |
| `POST /dishes` · `PUT /dishes/{id}` · `PATCH /dishes/{id}/status` · `POST /dishes/{id}/copy` | MANAGE | món `reference` chỉ đọc, sao chép mới sửa được; sao chép định lượng giữa nhóm tuổi làm ở client, BE nhận cả lưới |
| `GET /age-group-configs` (`date`, mặc định hôm nay → bản hiệu lực) · `GET /age-group-configs/{ageGroupId}/versions` | đọc | |
| `POST /age-group-configs` · `PUT /age-group-configs/{id}` | MANAGE | chỉ tạo/sửa bản có `effective_from > hôm nay`; bản đã hiệu lực chỉ đọc |
| `GET /suppliers` · `GET /suppliers/{id}` | đọc | `idCard` chỉ trả cho người có MANAGE, còn lại `null` (dữ liệu cá nhân — NUT-12) |
| `POST /suppliers` · `PUT /suppliers/{id}` · `PATCH /suppliers/{id}/status` | MANAGE | |

`ApiCode` mới (60xx): không tìm thấy, trùng mã, thiếu nguồn dinh dưỡng, chất dị ứng/thành phần không hợp lệ, nhập hàng loạt lỗi, sửa món tham khảo, cấu hình đã hiệu lực, ngày hiệu lực/chuẩn không hợp lệ.

## Acceptance criteria
- **AC-1** (`AUTH-20`): Given PHT không có `NUTRITION_MANAGE`, When `POST /nutrition/foods`, Then 403; `GET` vẫn 200.
- **AC-2** (`NUT-05`): Given thực phẩm thiếu `nutrientSource`, When tạo/nhập, Then 400 mã thiếu nguồn.
- **AC-3** (`NUT-05`): Given `import` có 1 dòng lỗi, Then không dòng nào được lưu, response nêu số dòng lỗi.
- **AC-4** (`NUT-18`): Given món có định lượng gam/trẻ cho `ag-2` và `ag-5`, When `GET /dishes/{id}`, Then dinh dưỡng và giá tính riêng từng nhóm tuổi = Σ gam × giá trị/100.
- **AC-5** (`NUT-18`): Given món `reference`, When `PUT`, Then 409; `POST /copy` tạo món mới sửa được.
- **AC-6** (`NUT-17`, `NUT-19`): Given cấu hình có `effective_from` ≤ hôm nay, When `PUT`, Then 409; `GET ?date=` trả đúng bản có `effective_from` lớn nhất ≤ date.
- **AC-7** (`NUT-18`): Given chuẩn có min > max, Then 400.
- **AC-8** (`NUT-12`): Given PHT chỉ xem, When `GET /suppliers`, Then `idCard = null`.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Định lượng | gam/trẻ lưu từng nhóm tuổi (form món FE đã là lưới này, ảnh chụp 02/11) | Một định lượng chuẩn × hệ số nhóm (chỉ seed demo FE dùng) | NUT-18 ghi rõ gam/trẻ theo nhóm tuổi |
| Giá suất | Gộp vào cấu hình nhóm tuổi có ngày hiệu lực | Bảng giá suất riêng (FE `/menu/prices`) | NUT-17 cấu hình chung bữa, chuẩn, giá, ngày hiệu lực |
| Đơn vị | `unit` + `purchase_unit` + `purchase_unit_size` | Thêm đơn vị kho riêng (FE `stockUnit`) | YAGNI; kho L2 tính theo đơn vị mua |
| Nhập hàng loạt | JSON từ client | Upload Excel ở BE | Không thêm dependency |
| Prefix | `/nutrition` | Giữ đề xuất FE `/menu/*`, `/kitchen/suppliers` | NCC không phải việc bếp; gom một module |
| Xóa | Đổi trạng thái | Xóa cứng | Lát L2/L3 tham chiếu |

## Phases
### Phase 1 — Schema, quyền, entity
- Files: C `db/migration/V41__create_nutrition_catalog.sql` · M `utils/enums/Permission.java` · C `entity/meals/{Allergen,Food,Dish,DishIngredient,AgeGroupMealConfig,Supplier}.java` · C `repository/meals/*Repository.java` · M `db/dev/R__dev_seed.sql` (vài thực phẩm/món mẫu, `nutrient_source='DEV_SAMPLE'`)
- Tests: AC-1 phần quyền → `PermissionTest`/ma trận quyền hiện có
- Exit: `node scripts/verify.mjs` → `VERIFY PASS full`, skip 0. **Dừng chờ review.**
### Phase 2 — Chất dị ứng, thực phẩm, nhập hàng loạt
- Files: C `controller/meals/NutritionFoodController.java` · C `service/meals/FoodService.java` + `impl/FoodServiceImpl.java` · C `dto/meals/Food*` · M `utils/ApiCode.java`
- Tests: AC-1, AC-2, AC-3 → `FoodServiceImplTest`, `NutritionFoodControllerTest`
- Exit: như trên. **Dừng chờ review.**
### Phase 3 — Món, định lượng, sao chép
- Files: C `controller/meals/NutritionDishController.java` · C `service/meals/DishService.java` + impl · C `dto/meals/Dish*`
- Tests: AC-4, AC-5 → `DishServiceImplTest`
- Exit: như trên. **Dừng chờ review.**
### Phase 4 — Cấu hình nhóm tuổi, NCC, contract
- Files: C `controller/meals/{AgeGroupMealConfig,Supplier}Controller.java` + service/impl/dto · M `docs/api/openapi.yaml` (snapshot) · M `docs/modules/nutrition.md`, `docs/system/CROSS_REPO_MAP.md`, `docs/context/CURRENT_STATE.md`
- Tests: AC-6, AC-7, AC-8 → `AgeGroupMealConfigServiceImplTest`, `SupplierServiceImplTest`; `OpenApiSnapshotTest`
- Exit: `node scripts/verify.mjs` → `VERIFY PASS full`; `node scripts/check-ai-layer.mjs` 0 lỗi. **Dừng chờ review.**

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1 | AUTH-20 | `NutritionFoodControllerTest` (web) | 2 | pass |
| AC-2, AC-3 | NUT-05 | `FoodServiceImplTest` (unit) | 2 | pass |
| AC-4, AC-5 | NUT-18 | `NutritionCatalogIntegrationTest` (Testcontainers — thay unit test để kiểm luôn V41, mapping, thay lưới định lượng) | 3 | pass |
| AC-6, AC-7 | NUT-17, NUT-18, NUT-19 | `AgeGroupMealConfigServiceImplTest` (unit) | 4 | pass |
| AC-8 | NUT-12 | `SupplierServiceImplTest` (unit) | 4 | pass |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| FE mock khác shape (seed theo hệ số nhóm, bảng giá suất riêng, thiếu nguồn dinh dưỡng/ngày hiệu lực) | FE phải sửa mock và vài form | Plan FE L1 đổi cùng lúc, mock theo shape mới | FE giữ `source: 'demo'` tới khi xong |
| Override GRANT bắt buộc `scope` (V40) với mã không theo cơ sở | HT không cấp được | Phase 1 kiểm `EffectivePermissionResolver` với mã `campusScoped=false` | Đánh dấu tạm `campusScoped=true`, dùng `SCHOOL` |
| CCCD NCC lộ qua log | Dữ liệu cá nhân | Không log DTO NCC (AGENTS.md mục log) | — |

## Validation log
- [x] Khẳng định về code có `file:line`/đường dẫn
- [x] Đổi contract ⇒ consumer cụ thể: FE `src/services/menu-planning/api/menuPlanningApi.js`, `src/services/kitchen/api/kitchenApi.js` (suppliers), `src/config/integrationModules.js`
- [x] Mỗi AC có test; mỗi phase có Exit
- [x] >8 file, 4 phase — đã là lát tách từ plan ô; giữ 4 phase vì cùng một danh mục
- [x] Không dùng rule PENDING/OPEN
- [x] Tự review: không placeholder, không mâu thuẫn

## Progress log
### 2026-11-02 23:30 — Bổ sung khi nối FE
- `code` của thực phẩm, món, bản sao món không còn bắt buộc: để trống ⇒ tự sinh `TPnnn`/`MAnnn` (sửa để trống ⇒ giữ mã cũ), vì form FE không có ô mã. Test `NutritionCatalogIntegrationTest#blankCodeIsGeneratedAndKeptOnUpdate`. Snapshot OpenAPI bỏ `required: code`.
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 645 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 266s`

### 2026-11-02 khuya — Phase 1–4 done
- Commit: chưa commit (chưa được phép)
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 644 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 260s`; `node scripts/check-ai-layer.mjs` → 0 lỗi.
- Khác plan: (4) bản cấu hình đầu tiên của một nhóm tuổi lấy ngày hiệu lực bất kỳ (không thì trường không bắt đầu dùng được); bản sau phải từ ngày mai. (5) Danh sách không phân trang (danh mục vài trăm dòng, như `AgeGroupController`). (6) Thay lưới định lượng: xóa + flush trước khi chèn — Hibernate chèn trước xóa làm vướng unique. (7) Seed dev chạy thử trên DB tạm (2 lần, idempotent), nguồn ghi `DEV_SAMPLE`.
- Docs: DATA_DICTIONARY, module card, USER_ROLES, CROSS_REPO_MAP, CURRENT_STATE, OpenAPI snapshot (15 path `/api/nutrition/*`).
- Tiếp: FE L1 (cần mã Jira FE + nhánh); BE L2 kho khô.

### 2026-11-02 tối — User duyệt, bắt đầu (chế độ tự chạy: làm hết 4 phase rồi bàn giao)
- Khác plan: (1) bữa trong ngày giữ ở `age_groups.meal_sessions` (V16) thay vì cột mới — tránh 2 nguồn; AC-7 cũ bỏ, thay bằng kiểm min ≤ max. (2) `storage` dùng `FRESH/DRY` khớp FE. (3) Rủi ro GRANT phạm vi đã kiểm: `EffectivePermissionResolver.resolve` chỉ xét GRANT/REVOKE, mã không theo điểm trường cấp được bình thường.

### 2026-11-02 — Lập plan (chờ user duyệt)
- Commit: chưa commit
- Giả định agent tự chọn: 6 dòng Key decisions; quyền `NUTRITION_MANAGE` mặc định tắt cho PHT; seed 9 chất dị ứng theo FE.
- Tiếp: user duyệt → Phase 1.
