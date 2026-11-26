---
title: Dị ứng chỉ cảnh báo, bỏ suất thay thế
status: in-progress
owner: Thắng
jira: G94-483 (BE), G94-484 (FE) / BE-FEAT-184, FE-FEAT-184
branch: feature/G94-483-BE-FEAT-184-allergy-warning-only
modules: [meals, attendance, child]
rules: [NUT-02, NUT-23, AUTH-05, HLT-06, CHD-13]
risk: high
created: 2026-11-25
updated: 2026-11-25
---
# Dị ứng chỉ cảnh báo, bỏ suất thay thế

Plan liên repo: BE (phần chính), FE (phase 3), APP (chỉ kiểm tra).

## Context
User chốt 2026-11-25: hệ thống chỉ cảnh báo món có chất gây dị ứng; nhà trường và phụ huynh tự thỏa thuận cách xử lý, Bộ Giáo dục không bắt buộc. `BUSINESS_RULES.md` (NUT-02, NUT-23, AUTH-05, HLT-06, CHD-13) và flow `meal-management*.md`, `USER_ROLES.md` đã sửa. Code còn tính và hiển thị suất thay thế (dev đang chạy):
- BE tính: `AttendanceServiceImpl.java:607` (trẻ có dị ứng ⇒ `substitute`), `MealCountServiceImpl`, `MealCountLine` (`substitute`, `substituteAdjusted`, `attendanceChanged`), `MealSubstituteLookup(+Impl)`, `MealDemandCalculator` (cộng `normal + substitute`).
- FE hiển thị: `ClassMealCard` (dòng "N suất thay thế"), `MealCountPage` (cột + ô tổng + nút chỉnh tay), `MealPlanGrid.jsx:307` và `weekGridLayout.js:60` ("chưa có suất thay thế"), `menuChecks.js:103`.
- FE mock đã bị chuyển hướng khỏi dev: `AllergySubstitutes`, `AllergyMenuChanges`, `AllergyMenu*Page`, `MenuFormPage` (dev dùng `/menu/templates`).

## Quyết định đã chốt
1. Dị ứng chỉ cảnh báo, không có suất/món thay thế — user, 2026-11-25.
2. Mức nhẹ/nặng giữ làm thông tin tham khảo trong hồ sơ — theo đề xuất, user duyệt 2026-11-25.
3. BE **ngừng tính và ngừng hiển thị** suất thay thế trước; chưa xóa cột DB — theo đề xuất, user duyệt 2026-11-25.
4. Bếp vẫn xem cảnh báo và số trẻ dị ứng theo món, lớp, không tên trẻ — theo đề xuất, user duyệt 2026-11-25.

## Làm rõ nghiệp vụ
- **Hiểu nghiệp vụ:** GV điểm danh, mọi trẻ có mặt báo ăn tính một loại suất; PHT/HT xác nhận sổ suất; cảnh báo dị ứng ở kế hoạch tuần/thực đơn/bếp. Rule NUT-02, NUT-23 (CONFIRMED, 25/11).
- **Ảnh hưởng:** module meals + attendance; contract `GET /meal-counts/class`, `/meal-counts*`, `POST /meal-counts/adjustments`, `confirm?seenSubstitute`; FE `/attendance`, `/attendance/meal-count`, `/menu/weekly`; APP chưa kiểm.
- **Dữ liệu cũ:** dòng sổ đã lưu có `substitute` > 0 (dạng `normal = ăn − thay thế`, V77). Cần quy về `normal` khi ngừng tính.

### Câu hỏi mở
- [x] Mã task Jira: chưa có; user duyệt tiếp tục, chưa commit đến khi có mã (hỏi lại lúc commit).
- [x] Sổ đã lưu có `substitute` > 0: gộp vào `normal` khi đọc/ghi (tổng suất không đổi), không migration — theo đề xuất, user 2026-11-25.
- [x] `adjustments` và `seenSubstitute`: giữ nhận, bỏ qua (client cũ không vỡ) — theo đề xuất, user 2026-11-25.
- [x] APP: grep `CareNest_APP/src`, `app` không thấy `substitute`; xác nhận lại ở phase 3.

## Acceptance criteria
- **AC-1** (NUT-02): Given trẻ có mặt báo ăn và có khai dị ứng, When điểm danh, Then suất của trẻ nằm trong `normal`, `substitute` = 0.
- **AC-2** (NUT-02): Given sổ suất, When PHT xem hoặc xác nhận, Then không còn cột/ô/nút suất thay thế.
- **AC-3** (NUT-23): Given kế hoạch tuần có món chứa chất dị ứng, When mở kế hoạch tuần, Then hiện cảnh báo món + chất, không có cụm "chưa có suất thay thế".
- **AC-4** (AUTH-05): Given bếp, When xem ngày, Then thấy cảnh báo và số trẻ dị ứng theo món/lớp, không thấy tên trẻ.
- **AC-5** (NUT-07): Given suất đã chốt, When tính nhu cầu thực phẩm, Then kết quả bằng tổng trẻ ăn × gam (không đổi so với trước).

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Cột `substitute` | Giữ, ngừng ghi/đọc | Xóa cột + migration | Ít rủi ro; xóa là migration phá dữ liệu và ảnh hưởng client cũ; dọn ở plan sau |
| Contract | Giữ trường `substitute` = 0 trong response | Bỏ trường | Client cũ không vỡ; bỏ sau khi FE/APP xác nhận |

## Phases
### Phase 1 — BE ngừng tính suất thay thế
- Files: M `AttendanceServiceImpl`, `MealCountServiceImpl`, `MealDemandCalculator`, `MealCountLine`; M test tương ứng.
- Steps: 1. bỏ nhánh `allergic.contains(...)` ⇒ `substitute` luôn 0. 2. bỏ giữ cờ `substituteAdjusted`/`attendanceChanged` khi điểm danh đổi. 3. `adjustments` trả 400 hoặc bỏ qua (theo câu hỏi mở). 4. dữ liệu cũ theo câu hỏi mở.
- Tests: AC-1, AC-5 → `MealCountIntegrationTest`, `AttendanceIntegrationTest`.
- Exit: `node scripts/verify.mjs` → `VERIFY PASS full`, skip 0.
- **Dừng chờ user review.**

### Phase 2 — Tài liệu theo code
- Files: M `docs/modules/nutrition.md`, `docs/api/FE_MAPPING.md`, `docs/api/openapi.yaml`, `docs/database/DATA_DICTIONARY.md`, `docs/knowledge/` nếu cần.
- Exit: `node scripts/check-ai-layer.mjs` pass (nếu user cho chạy).
- **Dừng chờ user review.**

### Phase 3 — FE (repo `CareNest_FE`, cần user cho phép sửa)
- Files: M `ClassMealCard.jsx`, `MealCountPage.jsx`, `MealPlanGrid.jsx`, `weekGridLayout.js`, `menuChecks.js`; D thành phần mock `AllergySubstitutes`, `AllergyMenuChanges`, `AllergyMenu*Page` nếu không còn dùng.
- Tests: AC-2, AC-3, AC-4 → `npm run verify`.
- **Dừng chờ user review.**

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1 | NUT-02 | `MealCountIntegrationTest` (tích hợp) | 1 | chưa chạy |
| AC-5 | NUT-02 | `MealDemandCalculator` (unit) | 1 | chưa chạy |
| AC-2..4 | NUT-02, NUT-23, AUTH-05 | FE test + xem trên dev | 3 | chưa chạy |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Sổ cũ có `substitute` > 0 | Tổng suất lệch nếu bỏ cột | Không xóa cột; quy về `normal` | Revert commit; cột còn nguyên |
| Client cũ gửi `seenSubstitute` | 409 khi xác nhận | Giữ nhận, bỏ qua | Revert |
| APP đọc `substitute` | Hiển thị 0 | Giữ trường = 0 | Revert |

## Validation log
- [x] Khẳng định về code có `file:line` (mức file; dòng đã xem: `AttendanceServiceImpl:607`, `MealPlanGrid.jsx:307`, `weekGridLayout.js:60`, `menuChecks.js:103`)
- [ ] Đổi contract ⇒ consumer cụ thể (FE đã nêu; APP chưa kiểm)
- [ ] Mỗi AC có test; mỗi phase có Exit
- [x] ≤3 phase, ≤8 file mỗi phase BE
- [x] Rule dùng đều CONFIRMED
- [x] Câu hỏi mở đã đóng

## Progress log
### 2026-11-25 — Plan approved
- User duyệt plan và 4 câu hỏi mở theo đề xuất; bắt đầu Phase 1.

### 2026-11-25 — Rule và flow đã sửa
- Commit: chưa commit
- Verify: chưa chạy (chỉ sửa tài liệu)
- Tiếp: user trả lời câu hỏi mở, duyệt plan.

### 2026-11-25 — Phase 1 (chờ review)
- Commit: chưa commit (chưa có mã Jira)
- Verify (Maven trực tiếp theo chỉ đạo 07/11, không chạy `scripts/verify.mjs`): `./mvnw -o test -Dtest='MealCountIntegrationTest,MealCountConcurrencyIntegrationTest,AttendanceIntegrationTest,KitchenRecordsIntegrationTest,DayMarketIntegrationTest'` → 54 test, 0 fail; `ArchitectureRulesTest, OpenApiSnapshotTest, DayBoard*, *Inspection*, *FreshGoods*, *DryStock*, MealDemand*` → 82 test, 0 fail. Chưa chạy toàn bộ suite.
- Khác plan: (1) `POST /meal-counts/adjustments` không "bỏ qua" mà trả 6006 `MEAL_COUNT_UNCHANGED` sau khi kiểm quyền (không còn gì để điều chỉnh, tránh báo thành công giả). (2) `ClassMealTally.substitutes`, `MealSubstituteLookup(+Impl)`, `MealCount.adjustSubstitute/currentEating` đã xóa; `addLine`/`replaceClassFigures` bỏ tham số suất thay thế. (3) Sổ cũ có `substitute` > 0: đọc gộp vào `normal`, trả `substitute` = 0; chưa migration (cột còn). (4) `seenSubstitute` vẫn nhận, không kiểm. `ApiCode.MEAL_SUBSTITUTE_EXCEEDS_EATING` còn nguyên (không dùng nữa).
- Tiếp: user review Phase 1; Phase 2 (docs theo code), Phase 3 (FE, cần user cho phép sửa repo FE).

### 2026-11-25 — Phase 2 (chờ review)
- Commit: chưa commit
- Verify: chỉ sửa tài liệu; `OpenApiSnapshotTest` đã pass ở Phase 1 (contract không đổi, `openapi.yaml` không cần sửa). Chưa chạy `check-ai-layer.mjs` (chỉ đạo 07/11: không tự chạy harness).
- Đã sửa: `docs/modules/nutrition.md` (bếp, trừ kho, NUT-02, NUT-03; bỏ 2 dòng tự tính suất thay thế), `docs/api/FE_MAPPING.md` (3 dòng), `docs/database/DATA_DICTIONARY.md` (`normal/substitute`), `docs/qa/dev-dataset-screen-coverage.md`.
- Khác plan: `openapi.yaml` không đổi vì contract giữ nguyên; incident `BUG-261101-meal-substitute-reset-on-attendance.md` giữ nguyên (lỗi lịch sử).
- Tiếp: Phase 3 (FE) cần user cho phép sửa repo `CareNest_FE`.

### 2026-11-25 — Phase 3 FE (chờ review)
- Commit: chưa commit (nhánh FE `feature/G94-447-FE-FEAT-01-nutrition-norm` còn nhiều file sửa dở không thuộc plan này)
- Verify (npm trực tiếp): `npm run lint` → 0 lỗi (2 cảnh báo cũ ở `NutritionSetupGroup.jsx`); `test:menu` 266/266, `test:kitchen` 192/192, `test:merge` 56/56, `test:dashboard` 87/87, `test:hardening` 131/131. `npm run build` lỗi sẵn có: không resolve được `mammoth` (`utils/announcement/readTemplateFile.mjs`), không liên quan. `check:design` fail do `styles/modules/kitchen.css` (sửa dở có sẵn). Chưa chạy các bộ test auth, health, children, edu.
- Đã sửa: ClassMealCard, classMealCount, MealRegisterCard (bỏ cột/ô tổng/nút/hộp thoại/lịch sử chỉnh suất thay thế), MealCountPage, AttendanceSummaryPage, mealRegister, attendancePermissions, attendanceApi + attendanceService + mock (bỏ `adjustMealDay`, `seenSubstitute`), MealPlanGrid, WeekGrid, weekGridLayout, menuChecks, AllergenWarnings, WeekChecks, KitchenTodayPage, ConfirmedMealCountPage, confirmedMealCountApi, dashboardService, dashboardSummaryService; test tương ứng.
- Khác plan: màn demo cũ (`AllergySubstitutes`, `AllergyMenu*Page`, `MenuFormPage`, `MenuDetailPage`, `WeeklyMenuPage`) và logic tính suất thay thế trong mock repo (`attendanceMockRepository`) chưa dọn; chỉ chạy ở chế độ demo, nằm ngoài danh sách (có `routes/**` là vùng dùng chung).
- Việc cần báo: Cần (chủ module điểm danh) vì Phase 1 và 3 chạm điểm danh; mã Jira chưa có.
