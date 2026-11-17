---
title: Báo ăn khi tiếp nhận trẻ (cờ trẻ ăn ở trường)
status: approved     # user duyệt 2026-11-18, chạy Subagent-driven
owner: Giáp
jira: G94-180 / BE-FEAT-43
branch: feature/G94-180-BE-FEAT-43-meal-registration
modules: [child, attendance, nutrition]
rules: [CHD-13, ATT-02a, HLT-06, NUT-07]
risk: high           # đổi contract tiếp nhận/khai báo/điểm danh, đổi số dự trù
created: 2026-11-18
updated: 2026-11-18
---
# Báo ăn khi tiếp nhận trẻ — BE Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hồ sơ trẻ có cờ "Báo ăn" (trẻ ăn ở trường). Trẻ không báo ăn: không khai dị ứng/chế độ ăn, điểm danh không có suất ăn, bếp không tính dị ứng, dự trù không tính trẻ.

**Architecture:** Cột `children.meal_registered` (mặc định `true`). Client gửi cờ trong `declaration.mealRegistered` ở tiếp nhận, khai báo hồ sơ, nhập Excel; BE trả cờ ở hồ sơ, danh sách, bảng điểm danh. Điểm danh bỏ qua `meals` của trẻ không báo ăn; sĩ số dự trù đếm bằng truy vấn mới chỉ lấy trẻ báo ăn, không đổi truy vấn sĩ số lớp.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Spring Data JPA, Flyway, PostgreSQL 17, JUnit 5 + Mockito + Testcontainers, Spotless (palantir-java-format).

**Spec:** các mục "Context" tới "Acceptance criteria" trong chính file này (repo: spec vào plan). Phần client: `FE:docs/plans/active/2026-11-18-intake-meal-registration.md`.

## Global Constraints

- JDK 21: trước mọi lệnh Maven đặt `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot` và đưa `%JAVA_HOME%\bin` lên đầu PATH (máy còn JDK 25).
- Không thêm/nâng dependency; không sửa migration đã commit; migration mới là `V144__child_meal_registered.sql` (đỉnh dev hiện tại V143 — chạy lại `git ls-tree --name-only origin/dev src/main/resources/db/migration/` trước khi commit).
- Không thêm `ApiCode`: điểm danh bỏ qua `meals` của trẻ không báo ăn, không báo lỗi.
- Field JSON đúng tên: `mealRegistered` (boolean) ở mọi nơi. Thiếu field trong request ⇒ `true`.
- Nhãn cột Excel đúng chữ: `Báo ăn (Có/Không, để trống = Có)`; key `mealRegistered`; FE dùng cùng nhãn (`FE:src/models/children/childrenConstants.js` `IMPORT_COLUMNS`).
- Log theo guide mục 10 (`LOG_PREFIX`, START/END, chỉ id); comment tiếng Việt ngắn, dẫn rule ID.
- Commit: `<JIRA>: <English verb …>` ≤72 ký tự, 1 thay đổi logic/commit, không ghi công cụ AI; chỉ commit/push khi user cho phép trong tin nhắn hiện tại.
- Đổi DTO ⇒ cập nhật snapshot: `mvnw.cmd test -Dtest=OpenApiSnapshotTest -Dopenapi.snapshot.update=true`, rồi chạy lại không có cờ phải pass.

## Review Focus

1. Bỏ tích "Báo ăn" khi bảng I đang chọn "Có dị ứng" mà chưa nhập chất ⇒ vẫn lưu được (dị ứng/chế độ ăn không kiểm). Test: Task 2 `mealNotRegisteredSkipsAllergyAndDiet`.
2. Ô Excel "Báo ăn" ghi `không`, ` KHÔNG `, `có` ⇒ hiểu đúng; chữ khác ⇒ lỗi dòng, không đoán. Test: Task 4 `mealColumnIgnoresCaseAndSpaces`, `invalidMealValueIsRowError`.
3. Trẻ đang có dị ứng chuyển sang không báo ăn ⇒ dữ liệu giữ nguyên, báo ăn lại thì hiện lại. Test: Task 3 `unregisterKeepsAllergiesAndReregisterShowsThem`.
4. Client cũ (App giáo viên) vẫn gửi `meals` bật cho trẻ không báo ăn ⇒ lưu không ăn, không lỗi, không suất thay thế. Test: Task 5 `childWithoutMealsNeverGetsMeals`.
5. Dự trù/thống kê thực đơn vẫn đếm trẻ không báo ăn nếu sót một chỗ gọi `countActiveChildren`. Test: Task 6 `previewCountsOnlyMealRegisteredChildren`, `enrolledHeadcountSkipsChildrenWithoutMeals`.

---

## Context

Màn tiếp nhận trẻ (Web) bước 3 "Khai báo sức khỏe" luôn bắt khai bảng I (dị ứng, chế độ ăn, thông tin khác) và bảng II (tiền sử). Trường có trẻ không ăn bán trú; hiện không có cách ghi điều đó, nên trẻ vẫn có suất ăn khi điểm danh (ATT-02a bật sẵn "Tham gia ăn") và vẫn vào sĩ số dự trù (NUT-07).

Hiện trạng code:
- `Child` không có cờ ăn: `entity/child/Child.java:21-25`, factory `enroll` :97-114. Cột boolean mẫu: `ChildGuardian.java:61-62` + `V81__selected_parent_account.sql:2`.
- Tiếp nhận: `EnrollChildRequest(child, declaration, classId, campusId)` `dto/child/EnrollChildRequest.java:7-8`; khai báo `HealthDeclarationInput(allergies, diet, otherNotes, medicalHistory)` `dto/child/HealthDeclarationInput.java:4-8`; kiểm tra `EnrollmentFormValidator.validateDeclaration` :279-366 (dị ứng :293-299, chế độ ăn :301-309 bắt buộc chọn trạng thái); tạo trẻ `ChildIntakeServiceImpl.createEnrolledChild` :279-372.
- Khai báo hồ sơ: `PUT /children/{childId}/health-declaration` `ChildEnrollmentController.java:207-216` → `ChildRecordServiceImpl.saveDeclaration` :159-279 (xóa rồi lưu lại dị ứng :230-233).
- Nhập Excel: `ChildEnrollmentImportServiceImpl` `COLUMNS` :31-65, `payload(raw)` :273-330, `importRows` dựng lại request :155-156 (giữ nguyên `declaration`).
- Trả về client: `ChildViewResponse` :12-33 (+ `withoutGuardianAccounts()` :34-70), dựng ở `ChildIntakeServiceImpl.getChildView` :395-416; `ChildListItemResponse` :17-38 dựng ở `ChildListServiceImpl` :253-274.
- Điểm danh: `SaveAttendanceRequest.Entry(childId, status, Map<MealSession,Boolean> meals)` :13-17; `AttendanceServiceImpl.mealsOf` :501-520 (6001 :514, 6002 :517); bảng điểm danh `ClassAttendanceResponse.ChildBrief(id, fullName, dateOfBirth)` :40, map ở `toBrief` :611-613; danh sách trẻ lớp `ClassChildResponse(id, fullName, dateOfBirth)` dựng ở `EnrollmentServiceImpl` :91, :111.
- Bếp tính dị ứng qua suất thay thế: `AttendanceServiceImpl.tally` :444 và `summarize` :556 gọi `ChildService.findChildIdsWithAllergies`, chỉ trên trẻ có suất ⇒ trẻ không có suất tự bị loại.
- Sĩ số dự trù: `MealDemandCalculator.enrolledByAgeGroup` :94-106 và `MealPlanServiceImpl.headcountOf` :361 gọi `countActiveChildren` (`ClassroomRepository.java:74-81`). Truy vấn này cũng nuôi sĩ số lớp (`AgeGroupServiceImpl:151`, `CampusServiceImpl:119`, `ClassManagementServiceImpl:509`, `TeacherAssignmentServiceImpl:486`) ⇒ không được sửa.

## Quyết định đã chốt

1. Bước 3 tiếp nhận đổi tên "Báo ăn và sức khỏe", có ô tích "Báo ăn" — user, 2026-11-18.
2. "Báo ăn" = trẻ ăn ở trường: lưu cờ vào hồ sơ trẻ; không báo ăn ⇒ điểm danh không có phần suất ăn, không tính suất ăn và dự trù — user, 2026-11-18.
3. Không tích ⇒ ẩn dị ứng + chế độ ăn; "Thông tin sức khỏe khác" chuyển sang bảng II, luôn khai (dị ứng thuốc nằm chung dòng dị ứng nên cũng ẩn) — user, 2026-11-18.
4. Mặc định tích sẵn — user, 2026-11-18.
5. Phạm vi: màn tiếp nhận, BE (cờ + contract), màn khai báo hồ sơ, nhập Excel — user, 2026-11-18.
6. Bỏ báo ăn ở hồ sơ ⇒ giữ nguyên dị ứng/chế độ ăn đã có, báo ăn lại thì hiện lại; trẻ không báo ăn thì bếp không tính phần dị ứng — user, 2026-11-18.
7. Trẻ không báo ăn ⇒ điểm danh không có phần đánh suất ăn — user, 2026-11-18.

## Làm rõ nghiệp vụ

- **Hiểu nghiệp vụ:** actor HT/PHT tiếp nhận (CHD-01) và người sửa hồ sơ (quyền `CHILD_PROFILE_EDIT`) · outcome: hồ sơ có cờ báo ăn; trẻ không báo ăn không có suất · rule mới CHD-13 (CONFIRMED, user 18/11; CHD-11, CHD-12 đã dùng cho tiếp nhận năm học mới trong WIP G94-180) · flow: [B] tiếp nhận → [S] lưu cờ → [S] điểm danh bỏ suất → [S] dự trù bỏ trẻ.
- **Đối chiếu thiết kế ban đầu:** ATT-02a (`BUSINESS_RULES.md:85`) coi mọi trẻ có mặt là ăn; HLT-06 (:150) coi dị ứng là thông tin khai khi nhập học; NUT-07 (:116) lấy sĩ số dự kiến từ trẻ đang học. CHD-13 thêm ngoại lệ cho cả ba.
- **Ảnh hưởng:** module child (cờ, tiếp nhận, khai báo, import), attendance (lưu suất, bảng điểm danh), nutrition (dự trù, thống kê thực đơn) · dữ liệu cũ: mọi trẻ hiện có = báo ăn · FE: `/children/enroll`, `/children/:id/health-declaration`, `/children/:id`, `/children` (import), `/attendance` · APP: màn điểm danh giáo viên vẫn hiện nút "Tham gia ăn" cho trẻ không báo ăn tới khi App sửa (BE bỏ qua, không lỗi).

### Câu hỏi mở
- [x] Mã Jira + mã công việc cho phần BE ⇒ `G94-180`; mã công việc `BE-FEAT-43` theo luồng G94-180 sẵn có.
- [x] Có làm App (`CareNest_APP`, màn điểm danh giáo viên ẩn phần tham gia ăn cho trẻ không báo ăn) trong đợt này không? ⇒ Chưa làm.

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| "Báo ăn" nghĩa là gì? | Trẻ ăn ở trường, lưu cờ, không tính suất/dự trù | user | 2026-11-18 | CHD-13 (Task 1) |
| Không tích ẩn dòng nào? | Ẩn dị ứng + chế độ ăn; thông tin khác sang bảng II | user | 2026-11-18 | CHD-13, HLT-06 |
| Mặc định ô tích? | Tích sẵn | user | 2026-11-18 | CHD-13 |
| Phạm vi? | Tiếp nhận FE, BE cờ + contract, khai báo hồ sơ, nhập Excel | user | 2026-11-18 | plan này |
| Bỏ báo ăn thì dữ liệu cũ? | Giữ nguyên, báo ăn lại thì hiện lại; bếp không tính dị ứng trẻ này | user | 2026-11-18 | CHD-13, HLT-06 |
| Điểm danh trẻ không báo ăn? | Không có phần đánh suất ăn | user | 2026-11-18 | ATT-02a |
| Mã Jira? | G94-180 | user | 2026-11-18 | frontmatter |
| Làm App đợt này? | Chưa | user | 2026-11-18 | Rủi ro & rollback |

## Acceptance criteria

- **AC-1** (`CHD-13`): Given trẻ cũ trước migration, When đọc hồ sơ/danh sách, Then `mealRegistered = true`.
- **AC-2** (`CHD-13`): Given tiếp nhận với `declaration.mealRegistered = false` và bảng I bất kỳ, When lưu, Then không lỗi dị ứng/chế độ ăn, trẻ có `mealRegistered = false`, dị ứng và chế độ ăn lưu `NOT_PROVIDED`, không có dòng `child_allergies`.
- **AC-3** (`CHD-13`): Given thiếu `mealRegistered`, When tiếp nhận, Then coi là `true` và dị ứng/chế độ ăn vẫn bắt buộc như cũ.
- **AC-4** (`CHD-13`, `HLT-06`): Given trẻ báo ăn có dị ứng, When PUT khai báo với `mealRegistered = false`, Then dị ứng/chế độ ăn giữ nguyên trong DB, cờ thành `false`; When PUT lại `true` không đổi dị ứng, Then GET trả lại dị ứng cũ.
- **AC-5** (`CHD-13`): Given file Excel cột "Báo ăn" = Không (không phân biệt hoa thường, khoảng trắng), When xem trước/nhập, Then trẻ `mealRegistered = false`, cột Dị ứng/Chế độ ăn bị bỏ qua kèm ghi chú dòng; ô trống hoặc Có ⇒ `true`; chữ khác ⇒ lỗi dòng `mealRegistered`.
- **AC-6** (`ATT-02a`, `CHD-13`): Given trẻ không báo ăn có mặt, When lưu điểm danh với `meals` null hoặc bật, Then bản ghi không có bữa, không lỗi 6001/6002, không tính suất và suất thay thế; bảng điểm danh trả `child.mealRegistered = false`.
- **AC-7** (`NUT-07`, `CHD-13`): Given lớp có trẻ không báo ăn, When xem trước bản dự trù/chợ ngày/thống kê thực đơn mặc định, Then sĩ số chỉ đếm trẻ báo ăn; sĩ số lớp ở Cấu hình trường không đổi.

## Key decisions

| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Chỗ lưu cờ | Cột `children.meal_registered` | Cột trong `child_health_declarations` | User chốt cờ thuộc hồ sơ trẻ; điểm danh/dự trù đọc trẻ, không đọc khai báo |
| Field request | `declaration.mealRegistered` ở cả tiếp nhận, PUT khai báo, import | Field riêng ở gốc body | Client sửa cờ cùng form khai báo; import và `importRows` giữ `declaration` sẵn |
| Request cũ thiếu field | `null` ⇒ `true` | Bắt buộc gửi | Không vỡ App/Web cũ; khớp mặc định tích sẵn |
| Client gửi suất cho trẻ không báo ăn | BE bỏ qua, ghi không ăn | Lỗi mã mới 6091 | User: trẻ không báo ăn không có phần suất ăn; App giáo viên cũ không bị lỗi khi lưu |
| Bếp không tính dị ứng | Không sửa `findChildIdsWithAllergies` | Lọc trong `ChildServiceImpl` | Suất thay thế chỉ tính trên trẻ có suất (`tally` :444, `summarize` :556), trẻ không báo ăn không có suất |
| Sĩ số dự trù | Truy vấn mới `countMealRegisteredChildren` | Sửa `countActiveChildren` | Truy vấn cũ nuôi sĩ số lớp ở 4 màn |

## Phases

### Phase 1 — Cờ báo ăn: hồ sơ, tiếp nhận, khai báo, nhập Excel

#### Task 1: Rule CHD-13, cột cờ, trả cờ trong hồ sơ và danh sách

**Files:**
- Modify: `docs/business/BUSINESS_RULES.md` (thêm dòng CHD-13 sau CHD-05 :65; ghi chú CHD-13 vào ATT-02a :85, HLT-06 :150, NUT-07 :116)
- Create: `src/main/resources/db/migration/V144__child_meal_registered.sql`
- Modify: `src/main/java/com/carenest/entity/child/Child.java`
- Modify: `src/main/java/com/carenest/dto/child/ChildViewResponse.java`, `src/main/java/com/carenest/service/child/impl/ChildIntakeServiceImpl.java:395-416`
- Modify: `src/main/java/com/carenest/dto/child/ChildListItemResponse.java`, `src/main/java/com/carenest/service/child/impl/ChildListServiceImpl.java:253-274`
- Modify: `docs/database/DATA_DICTIONARY.md:219`, `docs/api/openapi.yaml` (snapshot)
- Test: `src/test/java/com/carenest/service/account/impl/ChildEnrollmentIntegrationTest.java`; sửa constructor ở `ChildEnrollmentControllerTest.java:1078`, `ChildPhotoControllerTest.java:93`, `ChildPhotoServiceImplTest.java:263`, các chỗ `new ChildListItemResponse(` trong test

**Interfaces:**
- Produces: `Child#isMealRegistered(): boolean`, `Child#changeMealRegistration(boolean mealRegistered): void`; `ChildViewResponse.mealRegistered` và `ChildListItemResponse.mealRegistered` (boolean, ngay sau `hasDeclaration`).

- [ ] **Step 1: Viết test hỏng** — `ChildEnrollmentIntegrationTest#enrolledChildIsMealRegisteredByDefault`, `@DisplayName("CHD-13: trẻ tiếp nhận không gửi cờ thì báo ăn")`: tiếp nhận bằng request hiện có của lớp test ⇒ `response.mealRegistered()` là `true`; danh sách `/children` có `mealRegistered = true` cho trẻ đó.
- [ ] **Step 2: Chạy, thấy hỏng** — `mvnw.cmd -B test -Dtest=ChildEnrollmentIntegrationTest#enrolledChildIsMealRegisteredByDefault` ⇒ lỗi biên dịch "cannot find symbol mealRegistered".
- [ ] **Step 3: Ghi rule** — dòng `| CHD-13 | Hồ sơ trẻ có cờ "Báo ăn" (trẻ ăn ở trường), mặc định có. Không báo ăn ⇒ không khai dị ứng, chế độ ăn (dữ liệu đã có giữ nguyên, báo ăn lại thì hiện lại); điểm danh không có phần suất ăn; không tính suất, suất thay thế, sĩ số dự trù | CONFIRMED | User chốt 2026-11-18 |`; thêm "Trẻ không báo ăn (CHD-13): không có nút, không có suất." vào ATT-02a, "Chỉ khai khi trẻ báo ăn (CHD-13)." vào HLT-06, "Chỉ đếm trẻ báo ăn (CHD-13)." vào NUT-07.
- [ ] **Step 4: Migration + entity** — `ALTER TABLE children ADD COLUMN meal_registered BOOLEAN NOT NULL DEFAULT TRUE;`; field `@Column(name = "meal_registered", nullable = false) private boolean mealRegistered = true;` theo mẫu `ChildGuardian.java:61-62`; mutator `changeMealRegistration`. Ghi cột vào DATA_DICTIONARY.
- [ ] **Step 5: Trả cờ** — thêm component `boolean mealRegistered` vào 2 DTO (cả `withoutGuardianAccounts()`), map từ `child.isMealRegistered()`; sửa các constructor test liệt kê ở trên.
- [ ] **Step 6: Snapshot + chạy test** — cập nhật snapshot (Global Constraints) rồi `mvnw.cmd -B spotless:apply test -Dtest=ChildEnrollmentIntegrationTest,ChildEnrollmentControllerTest,ChildPhotoControllerTest,ChildPhotoServiceImplTest,ChildListServiceImplTest,OpenApiSnapshotTest` ⇒ BUILD SUCCESS.
- [ ] **Step 7: Commit (khi user cho phép)** — `<JIRA>: Add meal registration flag to child profile`.

#### Task 2: Tiếp nhận nhận `declaration.mealRegistered`

**Files:**
- Modify: `src/main/java/com/carenest/dto/child/HealthDeclarationInput.java`, `src/main/java/com/carenest/dto/child/HealthDeclarationDraft.java`
- Modify: `src/main/java/com/carenest/service/child/impl/EnrollmentFormValidator.java:279-366`
- Modify: `src/main/java/com/carenest/service/child/impl/ChildIntakeServiceImpl.java:279-372`
- Test: `src/test/java/com/carenest/service/child/impl/EnrollmentFormValidatorTest.java`, `ChildEnrollmentIntegrationTest.java`

**Interfaces:**
- Consumes: `Child#changeMealRegistration` (Task 1).
- Produces: `HealthDeclarationInput(AllergiesInput allergies, DeclarationAnswerInput diet, DeclarationAnswerInput otherNotes, MedicalHistoryInput medicalHistory, Boolean mealRegistered)` + constructor 4 tham số cũ gọi `this(..., null)`; `HealthDeclarationDraft` thêm component cuối `boolean mealRegistered` (đã chuẩn hóa: `null` ⇒ `true`).

- [ ] **Step 1: Viết test hỏng** — `EnrollmentFormValidatorTest#mealNotRegisteredSkipsAllergyAndDiet` (`@DisplayName("CHD-13: không báo ăn thì không kiểm dị ứng, chế độ ăn")`): `mealRegistered = false`, allergies `REPORTED` không chất, diet `REPORTED` chữ trống ⇒ không lỗi; draft `allergiesState = NOT_PROVIDED`, `allergies` rỗng, `diet.state = NOT_PROVIDED`, `mealRegistered = false`. `#missingMealFlagMeansRegistered`: `mealRegistered = null`, thiếu trạng thái dị ứng ⇒ vẫn lỗi `declaration.allergies`; bản hợp lệ ⇒ draft `mealRegistered = true`. `ChildEnrollmentIntegrationTest#enrollWithoutMealsStoresFlagAndNoAllergies`: POST có dị ứng nhưng `mealRegistered = false` ⇒ hồ sơ `mealRegistered = false`, khai báo dị ứng `NOT_PROVIDED`, không dòng `child_allergies`.
- [ ] **Step 2: Chạy, thấy hỏng** — `mvnw.cmd -B test -Dtest=EnrollmentFormValidatorTest,ChildEnrollmentIntegrationTest` ⇒ lỗi biên dịch constructor 5 tham số.
- [ ] **Step 3: Cài đặt** — `validateDeclaration` đọc cờ trước; `false` ⇒ bỏ khối dị ứng/chế độ ăn, draft gán `NOT_PROVIDED`; `otherNotes`, tiền sử kiểm như cũ. `createEnrolledChild` gọi `child.changeMealRegistration(draft.declaration().mealRegistered())`. Sửa mọi `new HealthDeclarationDraft(` (`grep -rn "new HealthDeclarationDraft(" src`).
- [ ] **Step 4: Snapshot + chạy test** — như Task 1 Step 6 với `EnrollmentFormValidatorTest,ChildEnrollmentIntegrationTest,ChildEnrollmentServiceImplTest,OpenApiSnapshotTest` ⇒ BUILD SUCCESS.
- [ ] **Step 5: Commit (khi user cho phép)** — `<JIRA>: Accept meal registration flag at child intake`.

#### Task 3: Khai báo hồ sơ — bỏ báo ăn giữ dữ liệu cũ

**Files:**
- Modify: `src/main/java/com/carenest/service/child/impl/ChildRecordServiceImpl.java:159-279`
- Test: `src/test/java/com/carenest/service/child/impl/ChildRecordServiceImplTest.java`, `src/test/java/com/carenest/service/account/impl/ChildRecordIntegrationTest.java`

**Interfaces:**
- Consumes: draft `mealRegistered` (Task 2), `Child#changeMealRegistration` (Task 1).

- [ ] **Step 1: Viết test hỏng** — `ChildRecordServiceImplTest#saveDeclarationWithoutMealsKeepsAllergiesAndDiet` (`@DisplayName("CHD-13: bỏ báo ăn giữ dị ứng, chế độ ăn đã khai")`): khai báo cũ có dị ứng `["Sữa"]`, diet REPORTED; PUT `mealRegistered = false` kèm dị ứng khác ⇒ không gọi xóa/lưu `child_allergies`, diet giữ, cờ trẻ `false`, `otherNotes` vẫn cập nhật. `ChildRecordIntegrationTest#unregisterKeepsAllergiesAndReregisterShowsThem`: tiếp nhận có dị ứng → PUT `false` → GET khai báo còn dị ứng, hồ sơ `mealRegistered = false` → PUT `true` với cùng dị ứng ⇒ `mealRegistered = true`, dị ứng như cũ.
- [ ] **Step 2: Chạy, thấy hỏng** — `mvnw.cmd -B test -Dtest=ChildRecordServiceImplTest,ChildRecordIntegrationTest` ⇒ 2 test mới FAIL (dị ứng bị ghi đè).
- [ ] **Step 3: Cài đặt** — `false` ⇒ cập nhật khai báo bằng trạng thái/nội dung dị ứng, chế độ ăn đang lưu, bỏ bước xóa-lưu dị ứng :230-233; luôn gọi `changeMealRegistration`. Lịch sử khai báo ghi như cũ. Thông báo lao giữ nguyên.
- [ ] **Step 4: Chạy test** — cùng lệnh + `HealthRecordIntegrationTest,ChildEnrollmentControllerTest` ⇒ BUILD SUCCESS.
- [ ] **Step 5: Commit (khi user cho phép)** — `<JIRA>: Keep allergies when a child stops school meals`.

#### Task 4: Nhập Excel — cột "Báo ăn"

**Files:**
- Modify: `src/main/java/com/carenest/service/child/impl/ChildEnrollmentImportServiceImpl.java` (`COLUMNS` :31-65 chèn trước cột dị ứng :40; `payload(raw)` :273-330)
- Test: `src/test/java/com/carenest/service/child/impl/ChildEnrollmentImportServiceImplTest.java`

**Interfaces:**
- Consumes: `HealthDeclarationInput` 5 tham số (Task 2).
- Produces: cột `new Column("mealRegistered", "Báo ăn (Có/Không, để trống = Có)", false)`.

- [ ] **Step 1: Viết test hỏng** (lồng trong `PreviewFile`) — `mealColumnNoSkipsAllergyAndDiet`: Báo ăn = Không, Dị ứng = "Trứng" ⇒ payload `declaration.mealRegistered = false`, dị ứng `NOT_PROVIDED`, `notes` chứa "Không báo ăn: bỏ qua cột Dị ứng, Chế độ ăn". `blankMealColumnMeansRegistered`: ô trống ⇒ `true`. `mealColumnIgnoresCaseAndSpaces`: ` KHÔNG ` ⇒ `false`, `có` ⇒ `true`. `invalidMealValueIsRowError`: "Ăn trưa" ⇒ lỗi khóa `declaration.mealRegistered` "Báo ăn ghi Có hoặc Không" (cùng khóa FE mock dùng). Trong `ImportRows`: `importKeepsMealFlag` ⇒ trẻ nhập có `mealRegistered = false`.
- [ ] **Step 2: Chạy, thấy hỏng** — `mvnw.cmd -B test -Dtest=ChildEnrollmentImportServiceImplTest` ⇒ 5 test FAIL.
- [ ] **Step 3: Cài đặt** — đọc ô bằng cách chuẩn hóa như `yesNo` (:246-252); `false` ⇒ `answer(null)` cho dị ứng/chế độ ăn, thêm ghi chú khi 2 cột đó có chữ.
- [ ] **Step 4: Chạy test** — `mvnw.cmd -B test -Dtest=ChildEnrollmentImportServiceImplTest,EnrollmentImportAccountServiceImplTest` ⇒ BUILD SUCCESS.
- [ ] **Step 5: Commit (khi user cho phép)** — `<JIRA>: Read meal registration column in intake import`.

**Exit Phase 1:** `mvnw.cmd -B clean spotless:check verify` ⇒ `BUILD SUCCESS`, Tests run … Failures: 0, Errors: 0. **Dừng chờ user review.**

### Phase 2 — Điểm danh và dự trù

#### Task 5: Điểm danh bỏ suất của trẻ không báo ăn

**Files:**
- Modify: `src/main/java/com/carenest/dto/child/ClassChildResponse.java`, `src/main/java/com/carenest/service/child/impl/EnrollmentServiceImpl.java:91,111`
- Modify: `src/main/java/com/carenest/dto/attendance/ClassAttendanceResponse.java:40`, `src/main/java/com/carenest/service/attendance/impl/AttendanceServiceImpl.java` (`saveClassAttendance` :274-277, `mealsOf` :501-520, `toBrief` :611-613)
- Test: `src/test/java/com/carenest/service/attendance/impl/AttendanceIntegrationTest.java`; sửa constructor `RecipientResolverTest.java:73`

**Interfaces:**
- Consumes: `Child#isMealRegistered` (Task 1).
- Produces: `ClassChildResponse(Long id, String fullName, LocalDate dateOfBirth, boolean mealRegistered)`, `ChildBrief(Long id, String fullName, LocalDate dateOfBirth, boolean mealRegistered)`, `mealsOf(SaveAttendanceRequest.Entry entry, List<MealSession> sessions, boolean mealRegistered)`.

- [ ] **Step 1: Viết test hỏng** — `AttendanceIntegrationTest#childWithoutMealsNeverGetsMeals` (`@DisplayName("ATT-02a/CHD-13: trẻ không báo ăn không có suất")`): trẻ A `mealRegistered = false`; lưu có mặt với `meals` null ⇒ bản ghi không bữa; lưu lại với mọi bữa `true` ⇒ không lỗi, vẫn không bữa; `tallyMeals` đếm A vào `present`, `meals`/`substitutes` không đổi so với lớp không có A; GET bảng điểm danh ⇒ `child.mealRegistered = false`.
- [ ] **Step 2: Chạy, thấy hỏng** — `mvnw.cmd -B test -Dtest=AttendanceIntegrationTest#childWithoutMealsNeverGetsMeals` ⇒ lỗi biên dịch/FAIL.
- [ ] **Step 3: Cài đặt** — map cờ ở 2 chỗ dựng `ClassChildResponse`; `mealsOf` trả danh sách rỗng ngay khi `!mealRegistered` (trước kiểm 6001/6002); `saveClassAttendance` lấy cờ từ danh sách trẻ lớp đã nạp; `toBrief` map cờ.
- [ ] **Step 4: Snapshot + chạy test** — `AttendanceIntegrationTest,AttendanceControllerTest,MealCountIntegrationTest,RecipientResolverTest,OpenApiSnapshotTest` ⇒ BUILD SUCCESS.
- [ ] **Step 5: Commit (khi user cho phép)** — `<JIRA>: Skip meals for children without school meals`.

#### Task 6: Sĩ số dự trù chỉ đếm trẻ báo ăn

**Files:**
- Modify: `src/main/java/com/carenest/repository/organization/ClassroomRepository.java` (sau :81)
- Modify: `src/main/java/com/carenest/service/organization/ClassroomService.java:34`, `src/main/java/com/carenest/service/organization/impl/ClassroomServiceImpl.java:181-200`
- Modify: `src/main/java/com/carenest/service/meals/impl/MealDemandCalculator.java:94-106`, `src/main/java/com/carenest/service/meals/impl/MealPlanServiceImpl.java:361`
- Test: `src/test/java/com/carenest/service/meals/impl/FreshGoodsIntegrationTest.java`, `MenuStatisticsIntegrationTest.java`

**Interfaces:**
- Produces: `List<ClassroomCount> ClassroomRepository.countMealRegisteredChildren(Collection<Long> classroomIds, LocalDate date)`, `Map<Long, Long> ClassroomService.countMealRegisteredChildren(Collection<Long> classroomIds, LocalDate date)`.

- [ ] **Step 1: Viết test hỏng** — `FreshGoodsIntegrationTest#previewCountsOnlyMealRegisteredChildren` (`@DisplayName("NUT-07/CHD-13: dự trù không đếm trẻ không báo ăn")`): lớp 3 trẻ, 1 trẻ `mealRegistered = false` ⇒ sĩ số điền sẵn của nhóm tuổi = 2. `MenuStatisticsIntegrationTest#enrolledHeadcountSkipsChildrenWithoutMeals`: cùng dữ liệu ⇒ headcount mặc định = 2; sĩ số lớp (`ClassroomService.countActiveChildren`) vẫn = 3.
- [ ] **Step 2: Chạy, thấy hỏng** — `mvnw.cmd -B test -Dtest=FreshGoodsIntegrationTest#previewCountsOnlyMealRegisteredChildren,MenuStatisticsIntegrationTest#enrolledHeadcountSkipsChildrenWithoutMeals` ⇒ FAIL (3 ≠ 2).
- [ ] **Step 3: Cài đặt** — truy vấn mới chép SQL `countActiveChildren` (:74-81) thêm `join children c on c.id = e.child_id and c.meal_registered`; service map như `countActiveChildren`; `enrolledByAgeGroup` và `headcountOf` gọi hàm mới. Không đổi 4 chỗ dùng sĩ số lớp.
- [ ] **Step 4: Chạy test** — `FreshGoodsIntegrationTest,MenuStatisticsIntegrationTest,DayMarketIntegrationTest,DryStockOutlookIntegrationTest,DryStockOutlookTest` ⇒ BUILD SUCCESS.
- [ ] **Step 5: Commit (khi user cho phép)** — `<JIRA>: Count only children with school meals in forecasts`.

**Exit Phase 2:** `mvnw.cmd -B clean spotless:check verify` ⇒ `BUILD SUCCESS`, Failures: 0, Errors: 0. **Dừng chờ user review.**

## Test matrix

| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1 | CHD-13 | `ChildEnrollmentIntegrationTest#enrolledChildIsMealRegisteredByDefault` (tích hợp) | 1 | Pass |
| AC-2 | CHD-13 | `EnrollmentFormValidatorTest#mealNotRegisteredSkipsAllergyAndDiet` (unit), `ChildEnrollmentIntegrationTest#enrollWithoutMealsStoresFlagAndNoAllergies` | 1 | Pass |
| AC-3 | CHD-13 | `EnrollmentFormValidatorTest#missingMealFlagMeansRegistered` (unit) | 1 | Pass |
| AC-4 | CHD-13, HLT-06 | `ChildRecordServiceImplTest#saveDeclarationWithoutMealsKeepsAllergiesAndDiet`, `ChildRecordIntegrationTest#unregisterKeepsAllergiesAndReregisterShowsThem` | 1 | Pass |
| AC-5 | CHD-13 | `ChildEnrollmentImportMealColumnTest` (5 test Task 4) | 1 | Pass |
| AC-6 | ATT-02a, CHD-13 | `AttendanceIntegrationTest#childWithoutMealsNeverGetsMeals` | 2 | Pass |
| AC-7 | NUT-07, CHD-13 | `FreshGoodsIntegrationTest#previewCountsOnlyMealRegisteredChildren`, `MenuStatisticsIntegrationTest#enrolledHeadcountSkipsChildrenWithoutMeals` | 2 | Pass |

## Rủi ro & rollback

| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Trùng số migration V144 với nhánh khác | Flyway lỗi khi merge | Kiểm `origin/dev` trước commit; đổi số theo hook `CARENEST_ALLOW_MIGRATION_EDIT` khi user duyệt | Revert commit; cột thêm `DEFAULT TRUE` vô hại |
| App giáo viên cũ vẫn hiện nút tham gia ăn | Giáo viên bật, BE lưu không ăn, App hiện sai tới khi tải lại | BE bỏ qua không lỗi; ghi việc App ở "Câu hỏi mở" | Không cần |
| Sót chỗ gọi `countActiveChildren` cho dự trù | Dự trù vẫn tính trẻ không báo ăn | Test AC-7; `grep -rn countActiveChildren src/main` trước Exit | Revert Task 6 |
| Đổi constructor record (`ClassChildResponse`, `ChildBrief`, `ChildViewResponse`) | Lỗi biên dịch ở test | Sửa đủ chỗ liệt kê; `verify` toàn bộ | Revert task |

## Validation log
- [x] Khẳng định về code có `file:line` (dò ngày 2026-11-18 trên dev `9c314635`)
- [x] Đổi contract ⇒ consumer cụ thể: FE `/children/enroll`, `/children/:id/health-declaration`, `/children` (import), `/attendance`; APP màn điểm danh giáo viên
- [x] Mỗi AC có test; mỗi phase có Exit = lệnh + kết quả mong đợi
- [x] >8 file ⇒ đã tách: plan BE này + plan FE riêng
- [x] Không dùng rule PENDING/OPEN như đã chốt (ATT-02a, HLT-06, NUT-07 CONFIRMED; CHD-13 user chốt 18/11)
- [x] Tự review: không mâu thuẫn; mã Jira chưa có ghi ở "Câu hỏi mở"

## Progress log
### 2026-11-18 — Plan approved
- Commit: chưa commit
- Verify: chưa chạy (plan)
- User trả lời "Câu hỏi mở", duyệt plan, chọn Subagent-driven. Tiếp: Phase 1.

### 2026-11-18 — Phase 1 (chờ review)
- Commit: `d5582c45`, `752885e7` (Task 1) · `82ddc37c` (Task 2) · `59255eea` (Task 3) · `759c6c4e`, `0cdde43c`, `616e7c2b` (Task 4)
- Verify: `mvnw.cmd -B clean spotless:check verify` → `Tests run: 1983, Failures: 0, Errors: 0, Skipped: 0` · `Spotless.Java is keeping 168 files clean` · `BUILD SUCCESS` (baseline dev: 1972 test)
- Khác plan: test nhập Excel đặt ở `ChildEnrollmentImportMealColumnTest` vì `ChildEnrollmentImportServiceImplTest`, `EnrollmentImportAccountServiceImplTest`, `ChildEnrollmentServiceImplTest` chỉ có ở nhánh G94-329, chưa có trên dev (tránh xung đột add/add); lỗi ô "Báo ăn" ở BE chỉ có câu `Báo ăn ghi Có hoặc Không` vì lỗi xem trước của BE là chuỗi, không có khóa (khóa `declaration.mealRegistered` chỉ ở BE giả Web); thêm commit `616e7c2b` format Spotless cho code Task 2–3.
- Tiếp: Phase 2 (Task 5, 6) sau khi user review.

### 2026-11-18 — Phase 2 (chờ review)
- Commit: `3201090c`, `03b20fad` (Task 5) · `b517ac5f`, `6c9304e3` (Task 6)
- Verify: `mvnw.cmd -B clean spotless:check verify` → `Tests run: 1986, Failures: 0, Errors: 0, Skipped: 0` · `Spotless.Java is keeping 177 files clean` · `BUILD SUCCESS`
- `grep -rn countActiveChildren src/main`: chỉ còn 4 chỗ sĩ số lớp (`AgeGroupServiceImpl:151`, `CampusServiceImpl:119`, `ClassManagementServiceImpl:509`, `TeacherAssignmentServiceImpl:486`); dự trù (`MealDemandCalculator:99`) và thống kê thực đơn (`MealPlanServiceImpl:361`) gọi `countMealRegisteredChildren`.
- Khác plan: mỗi task tách 2 commit nhỏ theo yêu cầu user (Task 5: bảng điểm danh trả cờ; bỏ suất của trẻ không báo ăn. Task 6: dự trù; thống kê thực đơn), nên tên commit khác Step 5. `FreshGoodsIntegrationTest#previewCountsOnlyMealRegisteredChildren` dùng lớp 2 trẻ (2 → 1) thay cho 3 → 2; vẫn chứng minh AC-7.
- Tiếp: review toàn nhánh BE, sau đó plan Web.
