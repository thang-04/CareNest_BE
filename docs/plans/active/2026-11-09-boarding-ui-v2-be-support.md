---
title: Hỗ trợ BE cho UI bán trú v2 — Tươi/Khô trong thống kê, sổ suất theo lớp cho giáo viên
status: approved
owner: thangnd
jira: G94-292 / BE-FEAT-27
branch: feature/G94-254-BE-FEAT-27-day-boarding
modules: [nutrition, attendance]
rules: [NUT-02, NUT-10, NUT-19, ATT-02, AUTH-05]
risk: medium
created: 2026-11-09
updated: 2026-11-09
---
# Hỗ trợ BE cho UI bán trú v2

Plan FE: FE `docs/plans/active/2026-11-09-boarding-benchmark-wave1.md` (repo FE, nhánh `feature/G94-191-FE-FEAT-48-nutrition-management`). Task Jira G94-292.

## Context
UI bán trú v2 cần BE trả thêm hai thứ, FE không được tự tính:
1. Màn "Định lượng & đi chợ" tách "Cần mua (hàng tươi)" / "Lấy từ kho khô". Hiện `MenuStatisticsResponse.Ingredient` chưa có cờ lưu trữ, response chưa có tổng theo nhóm (`src/main/java/com/carenest/dto/meals/MenuStatisticsResponse.java`). Food đã có `storage` (`src/main/java/com/carenest/entity/meals/Food.java:108`, enum `FoodStorage` FRESH/DRY, NUT-10).
2. Giáo viên xem số suất lớp mình trên Web. Hiện `GET /meal-counts` chỉ cho HT/PHT/bếp (`src/main/java/com/carenest/controller/meals/MealCountController.java:34-35`) và trả cả điểm trường (`MealCountDayResponse`).

## Quyết định đã chốt
1. User cho phép sửa BE cho hai việc trên — user, 09/11/2026.
2. Giáo viên được xem số suất lớp mình; đổi mức "P" ở `docs/business/flows/meal-management-permissions.md:86,157` thành đã chốt — user, 09/11/2026.

## Làm rõ nghiệp vụ
- Actor: PHT dinh dưỡng, HT (thống kê); giáo viên, tổ trưởng của lớp (sổ suất lớp).
- Rule: NUT-10 (Fresh/Dry, CONFIRMED), NUT-19 (giá suất chỉ cảnh báo), NUT-02 (xác nhận suất), ATT-02 (có mặt = ăn mọi bữa), AUTH-05 (bếp không thấy tên trẻ; áp tương tự: sổ lớp không trả tên trẻ).
- Ảnh hưởng: FE `/menu/statistics`, `/attendance`; APP GV có thể dùng lại B2.

### Câu hỏi mở
- [x] Mã công việc BE và nhánh → giữ nhánh `feature/G94-254-BE-FEAT-27-day-boarding` (BE-FEAT-27), user chốt 09/11/2026.

## Acceptance criteria
- **AC-B1** (NUT-10): `POST /nutrition/menu-statistics` trả `ingredients[].storage` (FRESH/DRY) và `freshCost`, `dryCost`; `freshCost + dryCost = totalCost`. Field cũ giữ nguyên.
- **AC-B2** (NUT-02, ATT-02): `GET /meal-counts/class?classId=&date=` cho giáo viên/tổ trưởng được phân công lớp đó (HT/PHT trong phạm vi cũng đọc được). Trả: lớp, ngày, giờ chốt, đã khóa chưa, mỗi bữa `{session, normal, substitute, status, needsReconfirmation, confirmedAt}`. Không có tên trẻ. Lớp không được phân công ⇒ 403.
- **AC-B3**: Cập nhật `meal-management-permissions.md`, `docs/api/FE_MAPPING.md`, module card nutrition.

## Phases
### Phase B1 — Tươi/Khô trong thống kê
- Files: M `dto/meals/MenuStatisticsResponse.java` · M `service/meals/impl/MealPlanServiceImpl.java` (dựng response, quanh dòng 263) · M test service thống kê.
- Tests: AC-B1 → thực đơn có cả thực phẩm FRESH và DRY: tổng hai nhóm khớp `totalCost`, mỗi dòng có `storage`.
- Exit: `./mvnw.cmd test` toàn bộ xanh.
- **Dừng chờ user review.**

### Phase B2 — Sổ suất theo lớp cho giáo viên
- Files: M `controller/meals/MealCountController.java` · M `service/meals/MealCountService.java` + impl · C `dto/meals/ClassMealCountResponse.java` · M docs (AC-B3) · C test controller/service.
- Steps: kiểm phân công lớp bằng cùng cách API điểm danh lớp đang dùng (`controller/attendance/AttendanceController.java`); lấy số từ `meal_counts` của điểm trường–ngày, lọc dòng của lớp; không tính lại.
- Tests: AC-B2 → GV đúng lớp 200; GV lớp khác 403; ngày chưa có sổ ⇒ danh sách bữa rỗng kèm `schoolDay`.
- Exit: `./mvnw.cmd test` toàn bộ xanh.
- **Dừng chờ user review.**

## Test matrix
| AC | Rule | Test | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-B1 | NUT-10 | test service thống kê | B1 | |
| AC-B2 | NUT-02, ATT-02 | test controller + service sổ suất lớp | B2 | |
| AC-B3 | — | rà doc | B2 | |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Lộ dữ liệu lớp khác cho GV | Vi phạm phạm vi | Kiểm phân công ở service, test 403 | Tắt endpoint |
| FE cũ đọc response thống kê | Không, chỉ thêm field | — | Bỏ field mới |

## Validation log
- [x] Khẳng định về code có `file:line`
- [x] Đổi contract ⇒ consumer: B1 `FE:src/pages/menu-planning/MenuStatisticsPage.jsx`; B2 `FE:src/components/attendance/ClassMealCard.jsx` (mới)
- [x] Mỗi AC có test; mỗi phase có Exit
- [x] Không dùng rule PENDING như đã chốt (quyền GV đã được user chốt 09/11)

## Progress log
### 2026-11-09 — Lập plan (chờ user duyệt)
- Commit: chưa commit · Verify: chưa chạy
- Tiếp: user chốt mã công việc BE, duyệt plan.

### 2026-11-09 — B1 + B2 (chờ review)
- User chốt giữ nhánh `feature/G94-254-BE-FEAT-27-day-boarding` ⇒ `approved`; plan chuyển từ checkout `CareNest_BE` sang worktree `CareNest_BE-day-boarding`.
- B2 (viết trước, nay soát lại): `GET /meal-counts/class` (`MealCountController`, `MealCountService#getClassMealCount`, `ClassMealCountResponse`); kiểm lớp bằng `attendanceService.getDayInfo` như màn điểm danh (403 khi không xem được lớp); sổ đã lập ⇒ số hiện tại của lớp (`visibleLine`), chưa lập ⇒ tạm tính từ điểm danh với `status` OPEN, không lưu; không tên trẻ. Test trong `MealCountIntegrationTest`.
- B1: `MenuStatisticsResponse.Ingredient.storage` + `freshCost`, `dryCost` (`MealPlanServiceImpl#costOf`); test `MenuStatisticsIntegrationTest#splitsCostByStorage`.
- Docs: `meal-management-permissions.md` (GV xem sổ suất lớp mình: đã chốt), `docs/api/FE_MAPPING.md` (endpoint mới), `docs/modules/nutrition.md` (field mới); `docs/api/openapi.yaml` cập nhật bằng `-Dopenapi.snapshot.update=true` (chỉ thêm 88 dòng).
- Test: `MenuStatisticsIntegrationTest` 5/5, `MealCountIntegrationTest` 13/13; toàn bộ 1217 test chỉ fail `OpenApiSnapshotTest` (đúng dự kiến do đổi contract) ⇒ cập nhật snapshot ⇒ chạy lại 3/3 qua. `check-ai-layer` 0 lỗi.
- Commit: chưa commit.
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 1217 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 633s | log target/verify.log`.
- Tiếp: chạy BE từ worktree này để FE thử phase 3 (Tươi/Khô) và phase 6 với dữ liệu thật (BE cổng 8090 đang chạy bản khác).
