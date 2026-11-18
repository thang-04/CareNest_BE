---
title: Tiếp nhận trẻ cho năm học sắp tới từ ngày mở chuẩn bị
status: approved     # user duyệt 2026-11-18 kèm trả lời Q1–Q5, chạy Subagent-driven
owner: Giáp
jira: G94-180 / BE-FEAT-43
branch: feature/G94-180-BE-FEAT-43-new-year-intake   # worktree D:\project_capstone\project\web\be-new-year-intake, tạo từ 39d09bf3 (nhánh G94-236 của Cần)
modules: [child, dev-seed]
rules: [G94-180/CHD-09, G94-180/CHD-10, G94-180/CHD-11, G94-180/CHD-12, G94-180/CHD-03, G94-180/AUTH-20, CFG-YR-05]
risk: high           # đổi contract tiếp nhận, nhập Excel, mở lại hồ sơ, hồ sơ, danh sách; thêm dữ liệu dev
created: 2026-11-18
updated: 2026-11-18
---
# Tiếp nhận trẻ cho năm học sắp tới — BE Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Phần "Quản lý trẻ" của việc chuyển năm học: chỉ từ ngày mở chuẩn bị mới tiếp nhận trẻ cho năm sắp tới; trẻ đã đăng ký hiện ở danh sách và hồ sơ của năm đó; trẻ thôi học thì hủy đăng ký; nhập Excel và mở lại hồ sơ cũng vào được năm sau; dữ liệu dev có đủ các trường hợp để thử.

**Architecture:** Làm tiếp trên mô hình của Cần (đã merge ngày 18/11): trẻ năm sau là trẻ "Chờ xếp lớp" kèm một dòng `planned_admissions` (PENDING hoặc PLACED). Plan thêm: cổng ngày mở chuẩn bị (cờ `SchoolYearDetailResponse.preparing`, mã 2412 có sẵn), một method đọc đăng ký dùng chung cho hồ sơ và danh sách, hủy đăng ký khi cho nghỉ học, tham số năm học cho nhập Excel và mở lại hồ sơ, seed dev. Tài khoản phụ huynh giữ như tiếp nhận thường. Không có migration mới, không sửa file của Cần.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Spring Data JPA, PostgreSQL 17, Flyway (seed dev `db/dev`), JUnit 5 + Mockito + Testcontainers, Spotless (palantir-java-format).

**Spec:** các mục từ "Kịch bản" tới "Acceptance criteria" trong file này (repo: spec nằm trong plan). Phần Web: `FE:docs/plans/active/2026-11-18-new-year-intake-prep-date.md`.

## Global Constraints

- JDK 21: trước mọi lệnh Maven đặt `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot` và đưa `%JAVA_HOME%\bin` lên đầu PATH. Chạy trong `D:\project_capstone\project\web\be-new-year-intake`, gọi `.\mvnw.cmd`.
- Không thêm migration. Dùng bảng `planned_admissions` (`V147__planned_child_admissions.sql` của Cần). Không sửa V146–V148.
- Trẻ năm sau = trẻ `PENDING_PLACEMENT` có đăng ký `PENDING`/`PLACED`. Không thêm trạng thái trẻ mới (bỏ `UPCOMING` của WIP 17/11).
- "Đã tới ngày mở chuẩn bị" chỉ đọc từ `SchoolYearDetailResponse.preparing()` (`SchoolYear.isPreparing`, CFG-YR-05). Không tự tính lại ngày.
- Không thêm `ApiCode`. Trước ngày mở chuẩn bị luôn trả 2412 `PREP_YEAR_NOT_OPEN` (có sẵn). Không đổi 2413, 2414 của Cần.
- JSON: request dùng `schoolYearId`, `startDate` (đã có ở `EnrollChildRequest`), thêm cùng tên vào body nhập Excel và mở lại hồ sơ. Response thêm `plannedAdmission` (object hoặc `null`) gồm `schoolYearId`, `schoolYearName`, `campusId`, `classId`, `className`, `startDate`, `status`.
- Tài khoản phụ huynh của trẻ năm sau xử lý như tiếp nhận thường (Q2 = b): không sửa `ChildEnrollmentServiceImpl`, `SelectedParentAccountServiceImpl`.
- Không sửa `YearTransitionServiceImpl`, `SchoolYearServiceImpl`, DTO và controller xếp lớp năm mới, `R__dev_06_year_history.sql`, `R__dev_10_planned_admissions.sql` (của Cần), `R__dev_01_people_structure.sql` (file sinh tự động).
- Log theo guide mục 10; comment tiếng Việt ngắn, dẫn rule ID. Đổi DTO thì cập nhật snapshot: `.\mvnw.cmd test -Dtest=OpenApiSnapshotTest -Dopenapi.snapshot.update=true`, rồi chạy lại không có cờ phải pass.
- Commit nhỏ (user nhắc 18/11): mỗi commit 1 thay đổi logic kèm test của nó, build xanh sau mỗi commit; message `G94-180: <English verb …>` ≤72 ký tự, không ghi công cụ AI. Không push.

## Review Focus

1. Trẻ đăng ký năm sau rồi thôi học trước ngày kích hoạt ⇒ không được vào lớp khi kích hoạt. Test: Task 2 `withdrawCancelsPlannedAdmission`.
2. Hôm nay đúng ngày mở chuẩn bị (biên) ⇒ tiếp nhận được. Test: Task 1 `plannedYearOpensOnPrepDate`.
3. Client gửi `schoolYearId` của năm đang hoạt động ⇒ tiếp nhận như cũ, không tạo đăng ký. Test: Task 1 `activeYearIdKeepsCurrentIntake`.
4. PHT điểm trường B không thấy trẻ năm sau của điểm trường A. Test: Task 3 `plannedListFollowsCampusScope`.
5. Phụ huynh đã có tài khoản (anh/chị đang học) ⇒ liên kết khi tiếp nhận năm sau; phụ huynh mới ⇒ tạo tài khoản, gửi SMS như thường. Test: Task 1 `plannedIntakeHandlesParentAccountsAsUsual`.

---

## Kịch bản (user duyệt 18/11)

Năm 2026 - 2027 đang hoạt động. HT tạo năm 2027 - 2028: bắt đầu 06/09/2027, ngày mở chuẩn bị 01/08/2027.

1. **20/07/2027, chưa tới ngày mở chuẩn bị.**
   - PHT chưa thấy năm 2027 - 2028 ở header (Cần đã làm, CFG-YR-05).
   - HT chọn 2027 - 2028 ở header: màn Danh sách trẻ, hồ sơ, sức khỏe, đón trẻ hiện "Năm học 2027 - 2028 mở chuẩn bị từ 01/08/2027 – chỉ xem."; không có nút Tiếp nhận, Nhập Excel, Sửa, Xếp lớp (CHD-12, phần Web).
   - Ô "Năm học tiếp nhận" chỉ có năm 2026 - 2027. Gọi API thẳng với năm 2027 - 2028 ⇒ 409, mã 2412 "Năm học 2027-2028 chưa tới ngày mở chuẩn bị (01/08/2027)".
2. **01/08/2027, từ ngày mở chuẩn bị.**
   - PHT tiếp nhận bé An: năm 2027 - 2028 (header đang ở năm đó thì chọn sẵn), ngày bắt đầu học 06/09/2027 (mặc định), lớp dự kiến Mầm 1. Lưu ⇒ An "Chờ xếp lớp", có đăng ký PLACED vào Mầm 1.
   - Mẹ An đã có tài khoản (anh trai An đang học) ⇒ liên kết. Bố An chưa có tài khoản ⇒ tạo tài khoản, gửi SMS như tiếp nhận thường. Bố mẹ mở App thấy An chỉ với thông tin đã khai báo (họ tên, ngày sinh, khai báo sức khỏe), chưa có lớp, điểm danh, thực đơn.
   - Header 2027 - 2028: Danh sách có An, nhãn "Sắp nhập học", lớp "Mầm 1 (dự kiến)". Header 2026 - 2027: không có An.
   - Hồ sơ An: "Đăng ký năm học 2027 - 2028, lớp dự kiến Mầm 1, vào học từ 06/09/2027."
   - Xếp lớp năm nay cho An ⇒ bị chặn (2414, Cần). Đổi lớp dự kiến ở Xếp lớp năm học mới, nhóm "Trẻ mới tiếp nhận" (Cần).
   - Nhập Excel 20 trẻ, chọn năm 2027 - 2028 và ngày bắt đầu ⇒ 20 đăng ký chưa có lớp (Phase 2).
   - Mở lại hồ sơ bé Bình (thôi học năm 2025) cho năm 2027 - 2028 ⇒ Bình "Chờ xếp lớp", có đăng ký (Phase 2).
3. **15/08/2027, bé Chi (đã đăng ký) thôi học.** PHT bấm Cho nghỉ học ⇒ Chi "Đã nghỉ học", đăng ký của Chi thành CANCELLED.
4. **06/09/2027, HT kích hoạt năm 2027 - 2028 (Cần).** An vào Mầm 1 từ 06/09 ("Đang học"); trẻ đăng ký chưa có lớp thành "Chờ xếp lớp" của năm mới; Chi không vào lớp.

## Hiện trạng code (đã kiểm trên `39d09bf3`, 18/11)

- Tiếp nhận: `EnrollChildRequest` có `schoolYearId`, `startDate`. Năm PLANNED ⇒ lưu `PlannedAdmission` (`ChildIntakeServiceImpl.java:380-388`). `placeOf` (`:558-617`) kiểm năm kế tiếp, ngày bắt đầu, lớp năm sau, nhưng không kiểm ngày mở chuẩn bị. `listPlacementClasses` (`:171-214`) cũng không kiểm.
- Ngày mở chuẩn bị: `SchoolYear.isPreparing` (`entity/organization/SchoolYear.java:103-105`), DTO `SchoolYearDetailResponse.preparing` (`:25`). Mã 2412 `ApiCode.java:330`, Cần dùng ở `YearTransitionServiceImpl.java:96-97` (định dạng `dd/MM/yyyy` ở `:68`).
- Danh sách: `ChildRepository.findListRows` (`:23-71`) ẩn trẻ có đăng ký khỏi năm đang hoạt động, không có nhánh cho năm sau ⇒ trẻ năm sau không hiện ở năm nào. Item dựng ở `ChildListServiceImpl.java:240-274`.
- Hồ sơ: `ChildViewResponse` (`dto/child/ChildViewResponse.java:12-34`) dựng ở `ChildIntakeServiceImpl.java:418-437`; không có thông tin đăng ký.
- Cho nghỉ học `ChildRecordServiceImpl.withdraw` (`:503-…`) không đụng đăng ký; `PlannedAdmissionStatus.CANCELLED` chưa được dùng. Kích hoạt nạp mọi đăng ký PENDING/PLACED (`YearTransitionServiceImpl.java:789-797`) rồi đưa trẻ vào lớp (`:487-527`), kể cả trẻ đã nghỉ.
- Tài khoản: `ChildEnrollmentServiceImpl.enrollWithPhotos` (`:181-280`) tạo tài khoản, gửi SMS khi người tiếp nhận có quyền `PARENT_ACCOUNT_MANAGE`, cả với trẻ năm sau — giữ nguyên (Q2 = b).
- Nhập Excel: `ChildEnrollmentImportServiceImpl.importRows` (`:150-180`) dựng `new EnrollChildRequest(child, declaration, null, campusId)` ⇒ luôn năm đang hoạt động. `ImportRequest(rows, campusId)` ở `ChildEnrollmentImportService.java:24`.
- Mở lại hồ sơ: `ChildIntakeServiceImpl.readmit` (`:222-262`), `ReadmitChildRequest(reason)`.
- Seed dev: năm 2027-2028 PLANNED, bắt đầu 01/09/2027, ngày mở chuẩn bị 02/08/2027 (`R__dev_06_year_history.sql:39-44`), lớp năm sau chép tên lớp năm nay (`:93-…`). `R__dev_10_planned_admissions.sql` (Cần) có 2 trẻ đăng ký PENDING (HS26301, HS26302) nhưng thiếu hồ sơ, khai báo, phụ huynh (lệch G94-180/CHD-08). Seed được kiểm bởi `DevSeedIntegrationTest` + `src/test/resources/dev-seed/*.sql`.

## Quyết định đã chốt (user, 2026-11-18)

1. Làm tiếp trên mô hình `planned_admissions` của Cần, bỏ `UPCOMING` (Q1).
2. Trước ngày mở chuẩn bị: BE từ chối tiếp nhận, nhập Excel, mở lại hồ sơ, xem lớp tiếp nhận của năm đó (2412). Web khóa màn của năm đó (CHD-12).
3. Trẻ năm sau chỉ hiện ở danh sách của năm đăng ký, nhãn "Sắp nhập học", lớp dự kiến; hồ sơ có dòng đăng ký.
4. Cho nghỉ học ⇒ đăng ký thành CANCELLED.
5. Tài khoản phụ huynh trẻ năm sau (Q2 = b): tạo tài khoản như tiếp nhận thường; phụ huynh thấy trẻ chỉ với thông tin đã khai báo, chưa có lớp và dữ liệu hằng ngày tới khi năm học bắt đầu. Không thêm mã lỗi.
6. Làm cả Phase 2 (Q3). Nhánh mới (Q4). Không sửa mock của Cần; Web chạy với BE thật, thêm dữ liệu dev đủ trường hợp (Q5).
7. Rule text (sửa/thêm ở `docs/business/BUSINESS_RULES.md`, bảng G94-180):
   - G94-180/CHD-09: thêm "trẻ có đăng ký tiếp nhận năm sau (CHD-11) chỉ ở năm đó" vào chỗ nói trẻ chờ xếp lớp.
   - G94-180/CHD-10: "Tiếp nhận, xếp/chuyển lớp vào lớp của năm học đang hoạt động; tiếp nhận cho năm sắp diễn ra theo CHD-11 (trẻ đó không xếp lớp năm nay được — 2414). Xếp lớp năm sau ở Xếp lớp năm học mới. Tài khoản phụ huynh không theo năm học."
   - G94-180/CHD-11 (mới): "Từ ngày mở chuẩn bị (CFG-YR-05) của năm sắp diễn ra gần nhất, HT/PHT tiếp nhận trẻ cho năm đó bằng form, nhập Excel, mở lại hồ sơ: lưu năm học, điểm trường, ngày bắt đầu học (trong năm học, mặc định ngày bắt đầu năm), lớp dự kiến (không bắt buộc) thành đăng ký tiếp nhận; trẻ Chờ xếp lớp, chỉ thuộc năm đó. Trước ngày mở chuẩn bị ⇒ từ chối (2412). Cho nghỉ học ⇒ hủy đăng ký. Tài khoản phụ huynh như tiếp nhận thường (G94-180/AUTH-20); phụ huynh thấy trẻ với thông tin đã khai báo, chưa có lớp và dữ liệu hằng ngày tới khi năm học bắt đầu. Kích hoạt năm: trẻ có lớp vào lớp từ ngày bắt đầu học, trẻ chưa có lớp Chờ xếp lớp (CFG-YR-03)." | CONFIRMED | User duyệt plan 2026-11-18; mô hình đăng ký, kích hoạt: Cần 18/11
   - G94-180/CHD-12 (mới): "Web: header chọn năm sắp diễn ra chưa tới ngày mở chuẩn bị ⇒ màn trẻ, sức khỏe, đón trẻ của năm đó chỉ xem, kèm câu báo ngày mở chuẩn bị. BE chặn tạo trẻ vào năm đó theo CHD-11." | CONFIRMED | User chốt 2026-11-17, duyệt plan 2026-11-18

### Câu hỏi mở

- [x] Q1. Mô hình ⇒ theo `planned_admissions` của Cần, bỏ `UPCOMING`.
- [x] Q2. Tài khoản phụ huynh trẻ năm sau ⇒ b: tạo như thường, phụ huynh chỉ thấy thông tin đã khai báo.
- [x] Q3. Phase 2 ⇒ làm.
- [x] Q4. Nhánh ⇒ nhánh mới `feature/G94-180-BE-FEAT-43-new-year-intake` (và FE tương ứng).
- [x] Q5. Mock của Cần ⇒ không làm; làm BE và thêm dữ liệu dev đủ trường hợp (Task 4).

## Acceptance criteria

- **AC-1** (CHD-11, CFG-YR-05): Given năm sắp diễn ra gần nhất chưa tới ngày mở chuẩn bị, When tiếp nhận, nhập Excel, mở lại hồ sơ hoặc xem lớp tiếp nhận của năm đó, Then 409 mã 2412 "Năm học {tên} chưa tới ngày mở chuẩn bị ({dd/MM/yyyy})", không tạo trẻ.
- **AC-2** (CHD-11): Given đã tới ngày mở chuẩn bị (kể cả đúng ngày), When tiếp nhận có hoặc không có lớp dự kiến, Then trẻ Chờ xếp lớp, có đăng ký PLACED hoặc PENDING với năm, điểm trường, ngày bắt đầu (mặc định ngày bắt đầu năm), lớp.
- **AC-3** (CHD-09, CHD-11): Given trẻ có đăng ký năm sau, When xem danh sách năm đó, Then có trẻ, kèm `plannedAdmission`; When xem năm đang hoạt động, Then không có; PHT điểm trường khác không thấy.
- **AC-4** (CHD-11): Hồ sơ trẻ có đăng ký mở ⇒ `plannedAdmission` có giá trị; trẻ không đăng ký hoặc đã vào học ⇒ `null`.
- **AC-5** (CHD-11, G94-180/CHD-03): Cho nghỉ học trẻ có đăng ký ⇒ đăng ký CANCELLED, kích hoạt năm không đưa trẻ vào lớp.
- **AC-6** (CHD-11, G94-180/AUTH-20): Tiếp nhận năm sau ⇒ phụ huynh nhận tài khoản là người mới thì `CREATED` và có SMS; tài khoản có sẵn `LINKED`.
- **AC-7** (CHD-11, Phase 2): Nhập Excel với `schoolYearId` năm sau ⇒ mỗi dòng một đăng ký PENDING ở điểm trường đã chọn, ngày bắt đầu theo body.
- **AC-8** (CHD-11, Phase 2): Mở lại hồ sơ với `schoolYearId` năm sau ⇒ Chờ xếp lớp, đăng ký PENDING ở điểm trường cũ.
- **AC-9** (dữ liệu dev): Seed dev có trẻ năm sau PLACED ở 2 điểm trường, PENDING không báo ăn, CANCELLED đã nghỉ, ngày bắt đầu muộn hơn đầu năm, phụ huynh dùng chung tài khoản với anh/chị đang học; trẻ nào cũng đủ hồ sơ, khai báo, đúng 1 người nhận tài khoản.

## Key decisions

| Vấn đề | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Mô hình trẻ năm sau | `planned_admissions` của Cần | Trạng thái `UPCOMING` (WIP) | Đã có trong code; Cần dùng cho xếp lớp và kích hoạt; không cần migration |
| Mã lỗi trước ngày mở chuẩn bị | Dùng lại 2412 | Mã mới | Đúng nghĩa; Cần đã dùng ở màn chuẩn bị lớp |
| Trẻ đã nghỉ không vào lớp khi kích hoạt | Hủy đăng ký lúc cho nghỉ học | Lọc trẻ LEFT trong `applyPlannedAdmissions` | Sửa trong nghiệp vụ của mình, không đụng code Cần |
| Đưa đăng ký cho client | Field `plannedAdmission` ở hồ sơ và danh sách | API riêng | Màn đã gọi 2 API này; 1 method `findOpenPlannedAdmissions` dùng chung |
| Khóa màn trước ngày mở chuẩn bị (CHD-12) | Chỉ ở Web | BE chặn từng màn | Năm sau chưa có trẻ, lớp đang học để ghi; BE đã chặn mọi đường tạo trẻ vào năm đó |
| Dữ liệu dev | File mới `R__dev_11_new_year_intake.sql` | Sửa seed của Cần / generator | Không đụng file người khác; seed tay như `R__dev_06`, `R__dev_10` |

## Phases

### Phase 1 — Ngày mở chuẩn bị, danh sách, hồ sơ, nghỉ học, dữ liệu dev

#### Task 1: Cổng ngày mở chuẩn bị và rule CHD-11, CHD-12

**Files:**
- Modify: `docs/business/BUSINESS_RULES.md:402-403` (CHD-09, CHD-10) và thêm CHD-11, CHD-12 ngay sau (nguyên văn ở Quyết định 7)
- Modify: `src/main/java/com/carenest/service/child/impl/ChildIntakeServiceImpl.java:171-214`, `:558-617`
- Test: `src/test/java/com/carenest/service/account/impl/ChildEnrollmentIntegrationTest.java`

**Interfaces:**
- Produces: `private PlannedTarget plannedTarget(Long schoolYearId, LocalDate requestedStartDate)` với `private record PlannedTarget(SchoolYearDetailResponse year, LocalDate startDate)` trong `ChildIntakeServiceImpl`: năm sắp diễn ra gần nhất, đã mở chuẩn bị, ngày bắt đầu trong năm (trống ⇒ ngày bắt đầu năm). Task 6 dùng lại.
- Test fixture: field `activeYear`, `plannedYear` (đang là biến cục bộ ở `setUp` `:161-164`); helper `openPrep(String date)` = `UPDATE school_years SET prep_date = ? WHERE id = plannedYear`; `plannedRequest(String fullName, Long classId, Long campusId, String startDate, GuardianInput... g)`.

- [ ] **Step 1: Viết test fail** (đồng hồ test 2026-10-29; ngày mở chuẩn bị mặc định của năm sau là 2027-08-05)
  - `@DisplayName("CHD-11: năm sau chưa tới ngày mở chuẩn bị ⇒ 2412 ở tiếp nhận và lớp tiếp nhận")` `plannedYearClosedBeforePrepDate`: `enrollmentService.enroll(principal, plannedRequest(…, plannedClass, campusA, "2027-09-05", …))` ném `GlobalException` có `getApiCode() == PREP_YEAR_NOT_OPEN`; `intakeService.listPlacementClasses(principal, plannedYear)` cùng mã; bảng `planned_admissions` 0 dòng.
  - `@DisplayName("CHD-11: đúng ngày mở chuẩn bị ⇒ tiếp nhận vào năm sau, có đăng ký")` `plannedYearOpensOnPrepDate`: `openPrep("2026-10-29")`; trẻ `PENDING_PLACEMENT`, `classId == null`; 1 dòng `school_year_id = plannedYear, classroom_id = plannedClass, start_date = 2027-09-05, status = 'PLACED'`; `listPlacementClasses(principal, plannedYear)` có `plannedClass` với `enrolledCount == 1`.
  - `@DisplayName("CHD-10: gửi schoolYearId năm đang hoạt động ⇒ như cũ, không đăng ký")` `activeYearIdKeepsCurrentIntake`: `new EnrollChildRequest(child, declaration, classA, campusA, activeYear, null)` ⇒ `classId == classA`, `status == ACTIVE`, 0 dòng đăng ký.
  - `@DisplayName("CHD-11, AUTH-20: tiếp nhận năm sau tạo, liên kết tài khoản như tiếp nhận thường")` `plannedIntakeHandlesParentAccountsAsUsual`: `openPrep`; trẻ 1 có người nhận tài khoản mới ⇒ kết quả `CREATED`, `smsStatus != null`, `identityProviderClient.createUser` được gọi; trẻ 2 có người nhận tài khoản là `existingParent` ⇒ `LINKED`.
- [ ] **Step 2:** `.\mvnw.cmd -q test -Dtest=ChildEnrollmentIntegrationTest` ⇒ `plannedYearClosedBeforePrepDate` FAIL (chưa có 2412); 3 test còn lại có thể đã pass — ghi lại kết quả thật.
- [ ] **Step 3:** Tách phần năm sau của `placeOf` thành `plannedTarget`; năm PLANNED mà `!preparing()` ⇒ `GlobalException.of(ApiCode.PREP_YEAR_NOT_OPEN, year.name(), dd/MM/yyyy của year.prepDate())`; `listPlacementClasses` dùng cùng cổng khi năm yêu cầu là PLANNED. Comment dẫn `CHD-11`, `CFG-YR-05`.
- [ ] **Step 4:** Chạy lại ⇒ PASS; `.\mvnw.cmd -q spotless:apply`.
- [ ] **Step 5:** 2 commit: `G94-180: Add next-year intake rules CHD-11 and CHD-12` (chỉ `BUSINESS_RULES.md`), `G94-180: Open next-year intake only from its prep date` (code + test).

#### Task 2: Cho nghỉ học thì hủy đăng ký năm sau

**Files:** Modify `src/main/java/com/carenest/entity/child/PlannedAdmission.java` (thêm `public void cancel()` ⇒ `CANCELLED`), `src/main/java/com/carenest/service/child/impl/ChildRecordServiceImpl.java:503-…` (`withdraw`, sau `child.withdraw(...)`); Test: `ChildEnrollmentIntegrationTest`.

- [ ] **Step 1:** Test `@DisplayName("CHD-11, CHD-03: cho nghỉ học trẻ năm sau ⇒ đăng ký CANCELLED")` `withdrawCancelsPlannedAdmission`: `openPrep`; tiếp nhận năm sau vào `plannedClass`; `recordService.withdraw(principal, id, new WithdrawChildRequest("2026-10-29", "Gia đình chuyển nhà"))`; DB `status = 'CANCELLED'`; không còn đăng ký PENDING/PLACED của trẻ.
- [ ] **Step 2:** Chạy ⇒ FAIL (status vẫn PLACED).
- [ ] **Step 3:** Trong `withdraw`: tìm đăng ký mở bằng `PlannedAdmissionRepository.findFirstByChildIdAndStatusIn` (service đã có repository) ⇒ `cancel()` + `save`. Comment dẫn `CHD-11`.
- [ ] **Step 4:** Chạy ⇒ PASS. **Step 5:** Commit `G94-180: Cancel next-year admission when the child leaves`.

#### Task 3: `plannedAdmission` ở hồ sơ, danh sách; danh sách năm sau

**Files:**
- Create: `src/main/java/com/carenest/dto/child/PlannedAdmissionResponse.java`
- Modify: `dto/child/ChildViewResponse.java:12-34` và `dto/child/ChildListItemResponse.java` (thêm `PlannedAdmissionResponse plannedAdmission` cuối; `withoutGuardianAccounts()` giữ field), `repository/child/PlannedAdmissionRepository.java`, `service/child/ChildIntakeService.java` + impl (`getChildView` `:418-437`), `repository/child/ChildRepository.java:39-51`, `service/child/impl/ChildListServiceImpl.java:240-274`
- Modify (thêm `null` vào constructor): `src/test/.../ChildEnrollmentControllerTest.java:1101`, `ChildPhotoControllerTest.java:93`, `ChildPhotoServiceImplTest.java:263`
- Docs: `docs/api/openapi.yaml` + snapshot; `docs/modules/child.md` (mục trẻ năm sau)

**Interfaces:**
- Produces: `record PlannedAdmissionResponse(Long schoolYearId, String schoolYearName, Long campusId, Long classId, String className, LocalDate startDate, PlannedAdmissionStatus status)`.
- Produces: `List<PlannedAdmission> findByChildIdInAndStatusIn(Collection<Long> childIds, Collection<PlannedAdmissionStatus> statuses)`.
- Produces: `Map<Long, PlannedAdmissionResponse> ChildIntakeService.findOpenPlannedAdmissions(Collection<Long> childIds)` — chỉ PENDING/PLACED.

Điều kiện thêm vào mệnh đề năm học của `findListRows`:

```sql
OR (c.status = 'PENDING_PLACEMENT' AND EXISTS (
     SELECT 1 FROM planned_admissions pa
     WHERE pa.child_id = c.id AND pa.school_year_id = CAST(:yearId AS BIGINT)
       AND pa.status IN ('PENDING', 'PLACED')))
```

- [ ] **Step 1:** Test (dùng `new ChildListFilter(year, null, null, null, null, 0, 100)`):
  - `@DisplayName("CHD-09, CHD-11: trẻ năm sau chỉ ở danh sách năm đó, kèm đăng ký")` `plannedChildListedUnderPlannedYearOnly`: lọc `plannedYear` có An với `plannedAdmission.classId == plannedClass`, `className == "Lớp A năm sau"`, `startDate == 2027-09-05`, `status == PLACED`; lọc `activeYear` không có An.
  - `@DisplayName("CHD-11, AUTH-02: PHT điểm trường B không thấy trẻ năm sau của A")` `plannedListFollowsCampusScope`: `searchChildren(vicePrincipalOfB, lọc plannedYear)` không có An.
  - `@DisplayName("CHD-11: hồ sơ trẻ năm sau có đăng ký; trẻ thường không có")` `childViewCarriesPlannedAdmission`: `intakeService.getChildView(id).plannedAdmission().schoolYearName() == "2027-2028"`; `getChildView(siblingA).plannedAdmission() == null`.
- [ ] **Step 2:** Chạy ⇒ FAIL (biên dịch: chưa có field).
- [ ] **Step 3:** Code theo Interfaces + SQL.
- [ ] **Step 4:** Chạy `ChildEnrollmentIntegrationTest`, `ChildListServiceImplTest`, `ChildPlacementListIntegrationTest`, cập nhật snapshot OpenAPI ⇒ PASS.
- [ ] **Step 5:** 3 commit: `G94-180: Add planned admission to child profile` (DTO, method dùng chung, hồ sơ, test hồ sơ, snapshot), `G94-180: Add planned admission to child list items`, `G94-180: List next-year children under their school year` (SQL + test danh sách).

#### Task 4: Dữ liệu dev đủ trường hợp tiếp nhận năm sau

**Files:**
- Create: `src/main/resources/db/dev/R__dev_11_new_year_intake.sql`
- Modify: `src/test/resources/dev-seed/year-invariants.sql`, `src/test/java/com/carenest/repository/organization/DevSeedIntegrationTest.java`

Trường hợp (dữ liệu giả nhìn như thật, mã HS26311–HS26314, năm 2027-2028 và lớp năm sau từ `R__dev_06`):

| Mã | Trường hợp | Trẻ | Đăng ký |
| --- | --- | --- | --- |
| HS26311 | Có lớp dự kiến; mẹ dùng chung tài khoản phụ huynh đang có con học (tài khoản `0985950454`, người nhận); bố chưa có tài khoản | `PENDING_PLACEMENT` | PLACED, Lớp Mầm 1 (CS-A), bắt đầu = ngày bắt đầu năm |
| HS26312 | Có lớp dự kiến, ngày bắt đầu muộn | `PENDING_PLACEMENT` | PLACED, Lớp Nhà trẻ 2 (CS-B), 01/10/2027 |
| HS26313 | Chưa có lớp, không báo ăn (CHD-13) | `PENDING_PLACEMENT`, `meal_registered = false` | PENDING, CS-A |
| HS26314 | Đăng ký rồi thôi học | `LEFT`, `left_on = CURRENT_DATE - 2`, có lý do | CANCELLED |

- Mỗi trẻ đủ như trẻ trong `R__dev_01_people_structure.sql`: `children` (`:405`), `child_guardians` (`:866`, đúng 1 người nhận tài khoản), `child_profiles` (`:1281`), `child_health_declarations` (`:1507`) cùng các bảng con khối đó ghi, `child_history` (`:1758`; HS26314 thêm dòng nghỉ học). Bổ sung phụ huynh, hồ sơ, khai báo cho HS26301, HS26302 của Cần (chỉ thêm dòng, `NOT EXISTS`).
- Chạy lại không nhân đôi (`ON CONFLICT DO NOTHING` / `NOT EXISTS`); cập nhật `child_code_sequences` như `R__dev_10`. Không đổi ngày mở chuẩn bị của năm 2027-2028 (02/08/2027 ⇒ năm đang khóa; muốn thử tiếp nhận thì HT đổi ngày mở chuẩn bị về hôm nay ở Cấu hình năm học).

- [ ] **Step 1:** Thêm kiểm vào `year-invariants.sql` (mỗi dòng tên kiểm + số vi phạm, kỳ vọng 0): `planned_open_child_not_pending` (đăng ký PENDING/PLACED mà trẻ không `PENDING_PLACEMENT`), `planned_cancelled_child_not_left`, `planned_class_other_year_or_campus` (PLACED mà lớp khác năm/điểm trường), `planned_start_outside_year`, `planned_open_year_not_planned`. Chạy `.\mvnw.cmd -q test -Dtest=DevSeedIntegrationTest` ⇒ PASS với dữ liệu hiện có. Commit `G94-180: Check next-year admissions in the dev dataset`.
- [ ] **Step 2:** Test `@DisplayName("CHD-11: dữ liệu dev có đủ trường hợp tiếp nhận năm sau")` `datasetCoversNextYearIntakeCases` trong `DevSeedIntegrationTest`: ≥1 PLACED mỗi điểm trường; ≥1 PENDING có trẻ `meal_registered = false`; ≥1 CANCELLED có trẻ `LEFT`; ≥1 đăng ký có `start_date` sau ngày bắt đầu năm; ≥1 trẻ năm sau có người nhận tài khoản là tài khoản của trẻ khác đang có lớp; mọi trẻ có đăng ký PENDING/PLACED có `child_profiles`, `child_health_declarations`, đúng 1 người nhận tài khoản. Chạy ⇒ FAIL.
- [ ] **Step 3:** Viết `R__dev_11_new_year_intake.sql` theo bảng trường hợp.
- [ ] **Step 4:** Chạy `DevSeedIntegrationTest` ⇒ PASS (cả `rerunKeepsRowCounts`).
- [ ] **Step 5:** Commit `G94-180: Seed next-year intake cases for dev` (seed + test đủ trường hợp).

**Cuối Phase 1:** `.\mvnw.cmd -q clean spotless:check verify` ⇒ exit 0 (ghi số test); ghi Progress log; dừng chờ review. Cách thử tay: `D:\project_capstone\run-be-local.ps1 -Repo D:\project_capstone\project\web\be-new-year-intake -ResetLocalDb`.

### Phase 2 — Nhập Excel, mở lại hồ sơ cho năm sau

#### Task 5: Nhập Excel vào năm sau

**Files:** Modify `src/main/java/com/carenest/service/child/ChildEnrollmentImportService.java:24` (`ImportRequest(List<EnrollChildRequest> rows, Long campusId, Long schoolYearId, LocalDate startDate)`, giữ constructor 2 tham số), `service/child/impl/ChildEnrollmentImportServiceImpl.java:165-166` (truyền 2 field vào `EnrollChildRequest` 6 tham số); `docs/api/openapi.yaml` + snapshot; Test: `ChildEnrollmentIntegrationTest` (autowire `ChildEnrollmentImportService`).

- [ ] **Step 1:** Test `@DisplayName("CHD-11: nhập Excel vào năm sau ⇒ mỗi trẻ một đăng ký chưa có lớp")` `importIntoPlannedYearCreatesPendingAdmissions`: chưa `openPrep` ⇒ 2412, 0 trẻ mới; `openPrep` ⇒ `importRows(principal, new ImportRequest(List.of(r1, r2), campusA, plannedYear, LocalDate.of(2027, 9, 5)))` tạo 2 trẻ, 2 dòng `PENDING`, `campus_id = campusA`, `classroom_id IS NULL`.
- [ ] **Step 2:** FAIL. **Step 3:** code. **Step 4:** PASS.
- [ ] **Step 5:** Commit `G94-180: Import children into the next school year`.

#### Task 6: Mở lại hồ sơ vào năm sau

**Files:** Modify `src/main/java/com/carenest/dto/child/ReadmitChildRequest.java` (`(String reason, Long schoolYearId, LocalDate startDate)`, giữ constructor 1 tham số), `ChildIntakeServiceImpl.readmit` (`:222-262`); `docs/api/openapi.yaml` + snapshot; Test: `ChildEnrollmentIntegrationTest`.

**Interfaces:** Consumes `plannedTarget` (Task 1). Có `schoolYearId` ⇒ sau `child.readmit(campusId, today)` lưu `PlannedAdmission.create(childId, year.id(), campusId, null, startDate, actor.userId())`.

- [ ] **Step 1:** Test `@DisplayName("CHD-03, CHD-11: mở lại hồ sơ cho năm sau ⇒ Chờ xếp lớp, có đăng ký")` `readmitIntoPlannedYear`: trẻ LEFT ở điểm trường A; `openPrep`; `intakeService.readmit(principal, id, new ReadmitChildRequest("Gia đình quay lại", plannedYear, LocalDate.of(2027, 9, 5)))` ⇒ `PENDING_PLACEMENT`, 1 dòng `PENDING` ở campus A; không gửi `schoolYearId` ⇒ như cũ, 0 dòng.
- [ ] **Step 2:** FAIL. **Step 3:** code. **Step 4:** PASS.
- [ ] **Step 5:** Commit `G94-180: Readmit a child into the next school year`.

**Cuối Phase 2:** như cuối Phase 1.

## Rủi ro & rollback

- Nhánh này chồng lên nhánh G94-236 của Cần (chưa vào `dev`). PR mở sau khi PR của Cần vào `dev`; mở sớm thì PR kéo theo code của Cần.
- Cần đổi mô hình `planned_admissions` ⇒ Task 1–6 đổi theo; trước mỗi phase `git fetch` và xem lại nhánh Cần.
- Migration của team: G94-346 và G94-414 cùng dùng V145, nhánh này đã có V146–V148 ⇒ nhánh nào vào `dev` sau phải đổi số (báo team).
- App phụ huynh không đổi: phụ huynh thấy trẻ "Chờ xếp lớp" chỉ với thông tin đã khai trước năm học (Q2 = b).
- Rollback: không có migration; seed dev là file `R__` riêng, revert commit là bỏ.

## Validation log

- Mọi vị trí code ở "Hiện trạng code" kiểm ngày 18/11 trên `39d09bf3`.
- Đổi contract ⇒ client: FE `src/services/children/api/childrenApi.js` (`enrollChild`, `importChildren` `:235`, `readmitChild` `:201`) và các màn trong FE plan; App: field mới là optional, không đổi.
- 6 task, khoảng 20 file ⇒ 2 phase, dừng review sau mỗi phase.
- Nhiều phương án ⇒ bảng Key decisions; không cần ADR.

## Progress log

### 2026-11-18 — Draft

- Viết sau khi merge nhánh Cần (`39d09bf3`) vào nhánh meal-registration.

### 2026-11-18 — Plan approved

- User duyệt, chọn Subagent-driven; trả lời Q1 đề xuất, Q2 = b (tạo tài khoản như thường, phụ huynh chỉ thấy thông tin đã khai), Q3 có, Q4 nhánh mới, Q5 làm BE + dữ liệu dev đủ trường hợp; nhắc chia commit nhỏ. Plan sửa theo trả lời: bỏ task chặn tài khoản (mã 3013), thêm Task 4 dữ liệu dev. Nhánh `feature/G94-180-BE-FEAT-43-new-year-intake` tạo từ `39d09bf3` ở worktree `be-new-year-intake`.
