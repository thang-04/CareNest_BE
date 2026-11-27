---
status: done
---

# BR code alignment (BE + App) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Make BE (and the parent App pickup line) match the 26/11 business rules for child records, health and pickup.

**Architecture:** Small changes inside existing services; two new multipart endpoints for photo replacement; one extra field on two response DTOs. No migration. FE work is in `FE:docs/plans/active/2026-11-26-br-code-alignment.md` and consumes the contracts produced here.

**Tech Stack:** Spring Boot 4 / Java 21, JUnit 5 + Mockito, Testcontainers integration tests; Flutter (App task).

**Spec:** `D:\project_capstone\project\web\report\sua_sau\CODE-can-sua-theo-BR-2026-11-26.md` (items) and `D:\project_capstone\project\web\report\BR-HLT-PAR-CP-PCK-2026-11-26.docx` (rules CP-xx, HLT-xx, PAR-xx, PCK-xx).

## Global Constraints

- Branch `fix/G94-180-BE-FIX-10-school-year-scope`; commit message prefix `G94-180: `; App commits on `feature/TBD-4-FE-FEAT-0-app-foundation` prefix `TBD-4: `. Local only, no push. Many small commits.
- The BE worktree has uncommitted peer changes in `EnrollmentRepository.java` and `ChildIntakeServiceImpl.java` (`occupiedCounts` batching). Never revert or commit those hunks; stage only own hunks (commit by file when the file is untouched by the peer; otherwise stop and ask the controller).
- No new Flyway migration (history uses action `UPDATED` with a note, not a new action).
- Items 15 and 17 are already implemented (`PhoneChangeSupport.open`, `EnrollmentImportAccountServiceImpl.preview/importRows`): no task.
- Vietnamese UI/error strings exactly as written below.
- Run tests with `.\mvnw.cmd -q -Dtest=<Class> test` (PowerShell). Integration tests need Docker Desktop running.

## Review Focus

- Photo replace on a LEFT child or out-of-scope child must be rejected like edit (`CHILD_ALREADY_LEFT` / `CHILD_ACCESS_DENIED`) — test in Task 6.
- Replacing a photo must delete the old stored file only after commit; a failed save keeps the old photo — test in Task 6.
- Read-only exam view must still hide data from users who cannot view the child (`canViewChild` false ⇒ 404) — test in Task 5.
- Teacher of the old class must not be able to save the exam of a child who moved campus (read-only even though `canRecord(old class)` is true) — test in Task 5.
- Parent attendance must show the pickup only for `HANDED_OVER` records, never a `NOT_HANDED_OVER` call — test in Task 8.

---

### Task 1: BE wording fixes (items 10, 22, 27-BE)

**Files:**
- Modify: `src/main/java/com/carenest/service/child/impl/ChildRecordServiceImpl.java` (prepareEdit, ~l.1150)
- Modify: `src/main/java/com/carenest/service/health/impl/HealthNotifier.java` (l.49, l.66)
- Modify: `src/main/java/com/carenest/service/notification/impl/NotificationRuleCatalog.java` (l.78, `HEALTH_RESULT_PUBLISHED` default only)
- Modify: `src/main/java/com/carenest/service/health/impl/HealthCheckRules.java` (`YearWindow`, l.152-164), `HealthCheckServiceImpl.java` (`checkWithinYear` l.791 and its 3 callers)
- Test: `ChildRecordServiceImplTest`, `HealthCheckIntegrationTest` (l.759, 766, 783), `HealthCheckServiceImplTest`

**Interfaces:**
- Produces: `YearWindow.message(HealthCheckKind kind)`; `checkWithinYear(YearWindow year, HealthCheckKind kind, LocalDate start, LocalDate end)`.

- [x] **Step 1: Update tests first**
  - `prepareEditRejectsDroppedLinkedGuardianAndLateBirthDate`: also assert message `"Ngày sinh không được sau ngày đầu tiên đi học (dd/MM/yyyy)"` (formatted first study day).
  - `HealthCheckIntegrationTest` l.759/766/783: EXAM keeps `"Ngày khám phải trong năm học 2026-2027 (01/09/2026 – 31/08/2027)"`; add one MEASUREMENT case expecting `"Ngày cân đo phải trong năm học 2026-2027 (01/09/2026 – 31/08/2027)"`.
  - Notifier test: updated result body ends `"Mở Sổ sức khỏe để xem."`.
- [x] **Step 2: Run, expect FAIL** — `.\mvnw.cmd -q -Dtest=ChildRecordServiceImplTest,HealthCheckIntegrationTest,HealthCheckServiceImplTest test`
- [x] **Step 3: Implement** — DOB message `"Ngày sinh không được sau ngày đầu tiên đi học (" + DAY.format(firstDay) + ")"`; replace `"Mở mục Sức khỏe để xem."` with `"Mở Sổ sức khỏe để xem."` in HealthNotifier (both) and the `HEALTH_RESULT_PUBLISHED` default in NotificationRuleCatalog (daily-health default unchanged); `message(kind)` prefix `"Ngày khám"` for EXAM, `"Ngày cân đo"` for MEASUREMENT.
- [x] **Step 4: Run, expect PASS** (same command)
- [x] **Step 5: Commit** — `G94-180: Align date of birth, health notice and year window messages`

### Task 2: Exam draft range checks (item 26, HLT-06)

**Files:**
- Modify: `src/main/java/com/carenest/service/health/impl/HealthExamServiceImpl.java` (`draftErrors` l.517-528, caller l.157)
- Test: `src/test/java/com/carenest/service/health/impl/HealthExamLogicTest.java`

**Interfaces:**
- Consumes: ranges in `HealthExamCatalog` (pulse 40–220, respiratoryRate 10–90) and `HealthExamValidator` (lengthForAgeSd / weightForAgeSd −6..6, armCircumferenceMm 50–300).

- [x] **Step 1: Write failing tests** — `draftRejectsPulseOutOfRange` (pulse 300 ⇒ field error on the pulse field with message `"Nhập Mạch (lần/phút) từ 40 đến 220 …"` exactly as `checkNumber` builds it), `draftRejectsSdOutOfRange` (−7), `draftRejectsArmCircumferenceOutOfRange` (400), `draftAcceptsBlankOptionalValues` (all null ⇒ no errors).
- [x] **Step 2: Run, expect FAIL** — `.\mvnw.cmd -q -Dtest=HealthExamLogicTest test`
- [x] **Step 3: Implement** — extend `draftErrors` to range-check those four groups only when a value is present, reusing the validator's field names and message builder (extract a package-private static helper in `HealthExamValidator` if needed; do not duplicate the numbers).
- [x] **Step 4: Run, expect PASS**; also `-Dtest=HealthExamIntegrationTest`
- [x] **Step 5: Commit** — `G94-180: Range-check vitals, SD and arm circumference on exam drafts`

### Task 3: Forbidden words in teacher-edited trend content (item 30, HLT-11)

**Files:**
- Modify: `src/main/java/com/carenest/service/health/impl/HealthTrendContentRules.java` (`checkEdited` l.149-188 and javadoc l.145-148)
- Test: `src/test/java/com/carenest/service/health/impl/HealthTrendContentRulesTest.java`

- [x] **Step 1: Write failing tests** — `checkEditedRejectsDiagnosisWordInOverview` (overview contains "chẩn đoán" ⇒ error on `content.overview` message `"Không dùng từ ngữ chẩn đoán hoặc điều trị"`), same for an attention point text (`content.attentionPoints.0.text`) and a follow-up text; `checkEditedAcceptsCleanContent`.
- [x] **Step 2: Run, expect FAIL** — `.\mvnw.cmd -q -Dtest=HealthTrendContentRulesTest test`
- [x] **Step 3: Implement** — in `checkEdited` (and `checkItem`) add an error when `containsForbidden(text)`; update javadoc.
- [x] **Step 4: Run, expect PASS**; also `-Dtest=HealthTrendServiceImplTest`
- [x] **Step 5: Commit** — `G94-180: Reject diagnosis words in edited health trend content`

### Task 4: Block a new record for a duplicate of a left child (item 7-BE, CP-06, CP-13)

**Files:**
- Modify: `src/main/java/com/carenest/service/child/impl/ChildIntakeServiceImpl.java` (`prepareEnrollment` l.346-358) — **peer has uncommitted hunks in this file; see Global Constraints**
- Modify: `src/main/java/com/carenest/service/child/impl/ChildEnrollmentImportServiceImpl.java` (preview l.135-140)
- Modify: `src/main/java/com/carenest/utils/ApiCode.java` (new code next to `CHILD_DUPLICATE` l.384)
- Test: `src/test/java/com/carenest/service/account/impl/ChildEnrollmentIntegrationTest.java` (l.665-730, l.1156)

**Interfaces:**
- Produces: `ApiCode.CHILD_DUPLICATE_LEFT` (HTTP 409) message `"Trẻ đã có hồ sơ ở trạng thái Đã nghỉ học. Mở lại hồ sơ cũ thay vì tạo hồ sơ mới."`; import preview row error text `"Trẻ từng học ở trường (hồ sơ cũ {mã}, đã nghỉ học): mở lại hồ sơ cũ ở Tiếp nhận trẻ, không nhập mới."`.

- [x] **Step 1: Update tests** — form enrollment of a duplicate of a LEFT child ⇒ `CHILD_DUPLICATE_LEFT`; `importPreviewNotesWithdrawnDuplicateAndExistingAccount` now expects the row in `errors` (not `notes`); importing that row ⇒ rejected.
- [x] **Step 2: Run, expect FAIL** — `.\mvnw.cmd -q -Dtest=ChildEnrollmentIntegrationTest test`
- [x] **Step 3: Implement** — `prepareEnrollment`: ACTIVE/PENDING duplicate keeps `CHILD_DUPLICATE`; LEFT duplicate throws `CHILD_DUPLICATE_LEFT`. Import preview: move the LEFT message from `notes` to `errors`. Readmission path (`readmit`) unchanged.
- [x] **Step 4: Run, expect PASS**
- [x] **Step 5: Commit** — `G94-180: Reject new records that duplicate a withdrawn child`

### Task 5: Read-only exam of a left or campus-changed child (item 4-BE, HLT-06)

**Files:**
- Modify: `src/main/java/com/carenest/service/health/impl/HealthExamServiceImpl.java` (`target` l.267-283, `view` l.349, `saveExam` l.103)
- Test: `src/test/java/com/carenest/service/health/impl/HealthExamIntegrationTest.java` (l.163, 670-673, 750)

**Interfaces:**
- Produces: `GET /health-checks/{id}/children/{childId}/exam` returns 200 with `canEdit=false, locked=true` when the child has an exam in this campaign but no active class at the campaign campus; `PUT` for that child ⇒ `HEALTH_MEASUREMENT_ACCESS_DENIED`. No exam and no class ⇒ still `HEALTH_EXAM_NOT_FOUND`.

- [x] **Step 1: Update tests** — l.750: left child with a concluded exam ⇒ GET ok, `canEdit` false, `locked` true; campus-changed child same; PUT by the old-class teacher ⇒ `HEALTH_MEASUREMENT_ACCESS_DENIED`; left child without an exam ⇒ `HEALTH_EXAM_NOT_FOUND`; user who cannot view the child ⇒ `HEALTH_EXAM_NOT_FOUND`.
- [x] **Step 2: Run, expect FAIL** — `.\mvnw.cmd -q -Dtest=HealthExamIntegrationTest test`
- [x] **Step 3: Implement** — `target()` falls back to the class stored on the existing measurement (`measurement.getClassroomId()`) when no active class at the campus, and carries `boolean readOnly`; `view()` sets `canEdit=false, locked=true` when `readOnly`; `saveExam` rejects `readOnly` before any write.
- [x] **Step 4: Run, expect PASS**; also `-Dtest=HealthCheckIntegrationTest`
- [x] **Step 5: Commit** — `G94-180: Show exams of left or moved children read-only`

### Task 6: Replace child and guardian photos; photo-missing flag (item 1-BE, CP-17)

**Files:**
- Modify: `src/main/java/com/carenest/controller/child/ChildPhotoController.java`
- Modify: `src/main/java/com/carenest/service/child/ChildPhotoService.java`, `impl/ChildPhotoServiceImpl.java`
- Modify: `src/main/java/com/carenest/entity/child/Child.java`, `ChildGuardian.java` (photo replace method returning the old key)
- Modify: `src/main/java/com/carenest/dto/child/ChildListItemResponse.java`, `service/child/impl/ChildListServiceImpl.java` (l.304)
- Test: `ChildPhotoServiceImplTest`, `ChildPhotoControllerTest`, a new `ChildPhotoReplaceIntegrationTest`

**Interfaces:**
- Produces:
  - `PUT /children/{childId}/photo` and `PUT /children/{childId}/guardian-photos/{guardianId}`, multipart part `photo` + form field `reason` (required, ≤ 300, same message as edit `"Nhập lý do chỉnh sửa hồ sơ"`), `@PreAuthorize("@permissionGuard.has('CHILD_PROFILE_EDIT')")`; teacher-only callers ⇒ `FORBIDDEN` (as `ChildProfileChangeRequestServiceImpl.submit`). Return `ChildViewResponse`.
  - `ChildPhotoService.replaceChildPhoto(CurrentUser, Long childId, MultipartFile, String reason)`, `replaceGuardianPhoto(CurrentUser, Long childId, Long guardianId, MultipartFile, String reason)`.
  - `ChildListItemResponse.photoMissing` (boolean, appended last): true when the child or any guardian on the record has no photo.
  - History: `ChildHistory.record(childId, UPDATED, userId, reason, at)` with `changes` `[{"field":"photo","label":"Ảnh của trẻ"}]` / `"Ảnh phụ huynh {n}"`.

- [x] **Step 1: Write failing tests** — replace child photo stores new key, writes history, removes old file after commit; invalid type/size ⇒ same field error as enrollment (`inspect`); LEFT child ⇒ `CHILD_ALREADY_LEFT`; out of campus ⇒ `CHILD_ACCESS_DENIED`; guardian id not on child ⇒ `CHILD_GUARDIAN_NOT_FOUND`; list row `photoMissing` true/false.
- [x] **Step 2: Run, expect FAIL** — `.\mvnw.cmd -q -Dtest=ChildPhotoServiceImplTest,ChildPhotoControllerTest,ChildPhotoReplaceIntegrationTest test`
- [x] **Step 3: Implement** — reuse `inspect` and `store`; scope/left checks via the same loader as `ChildRecordServiceImpl.prepareEdit` (`loadInScope`); delete old key with the after-commit pattern used in `AccountPhotoServiceImpl`.
- [x] **Step 4: Run, expect PASS**; regenerate OpenAPI snapshot the way previous G94-180 commits did (`docs/api/openapi.yaml`, `docs/api/API_INDEX.md`).
- [x] **Step 5: Commit** (2 commits) — `G94-180: Replace child and guardian photos with history` and `G94-180: Flag children missing photos in the list`

### Task 7: Say which credential the parent got (item 9-BE, CP-12)

**Files:**
- Modify: the guardian account result DTO `GuardianAccountResultResponse` and `ChildEnrollmentServiceImpl.java` (~l.229), `SelectedParentAccountServiceImpl.java` (~l.159)
- Test: `ChildEnrollmentIntegrationTest`, `SelectedParentAccountServiceImplTest` (or the existing activation test)

**Interfaces:**
- Produces: `GuardianAccountResultResponse.credential` (String, appended last): `"ACTIVATION_CODE"` when `smsSender.activationCodeMode()`, `"TEMPORARY_PASSWORD"` when a password was issued, `null` when only linked.

- [x] **Step 1: Write failing tests** — new account in fake-SMS mode ⇒ `TEMPORARY_PASSWORD`; with `activationCodeMode` stubbed true ⇒ `ACTIVATION_CODE`; existing account linked ⇒ `null`.
- [x] **Step 2: Run, expect FAIL**
- [x] **Step 3: Implement** in both services where the result is built.
- [x] **Step 4: Run, expect PASS**; update OpenAPI snapshot.
- [x] **Step 5: Commit** — `G94-180: Report activation code or temporary password in account results`

### Task 8: Parent attendance shows pickup person and time (item 21-BE, PAR-12)

**Files:**
- Modify: `src/main/java/com/carenest/dto/attendance/ParentAttendanceDtos.java` (`ParentAttendanceDay`), `service/attendance/impl/ParentAttendanceServiceImpl.java` (l.336-350)
- Test: `ParentAttendanceIntegrationTest`, `ParentAttendanceControllerTest`

**Interfaces:**
- Produces: `ParentAttendanceDay.pickedUpByName` = pickup person name + `" (" + relationship label + ")"` when relationship present (labels Bố, Mẹ, Ông, Bà, Anh/chị, Người giám hộ), or `"Người đón đã được phụ huynh xác nhận qua điện thoại"` for `PHONE_CONFIRMED` with empty name; new `Instant pickedUpAt` (appended last) = `handedOverAt`. Only `outcome = HANDED_OVER` rows count.

- [x] **Step 1: Write failing tests** — handed-over day ⇒ name + time; call `NOT_HANDED_OVER` only ⇒ both null; no record ⇒ null.
- [x] **Step 2: Run, expect FAIL**
- [x] **Step 3: Implement** with `PickupRecordRepository.findByChildIdInAndDateOrderByRecordedAtAscIdAsc` (batch per date range, no N+1); fix the stale doc comment.
- [x] **Step 4: Run, expect PASS**; update OpenAPI snapshot.
- [x] **Step 5: Commit** — `G94-180: Return pickup person and time in parent attendance`

### Task 9: App "Đón về" line shows time and person (item 21-App)

**Repo:** `D:\project_capstone\project\web\carenest_app` (stage only these files; the worktree has unrelated uncommitted kitchen changes).

**Files:**
- Modify: `lib/features/parent/my_child/data/live_my_child_source.dart` (l.208-211)
- Test: `test/features/parent/my_child/live_my_child_source_test.dart` (l.243-268)

**Interfaces:**
- Consumes: Task 8 fields `pickedUpByName`, `pickedUpAt` (ISO instant).
- Produces: `pickupLine` = `"HH:mm · {pickedUpByName}"` in school local time (Asia/Ho_Chi_Minh, same conversion as `arrivedAt` in this file); name only when `pickedUpAt` null.

- [x] **Step 1: Update test** — `pickedUpAt: '2026-11-26T09:45:00Z', pickedUpByName: 'Nguyễn Thị Thu (Mẹ)'` ⇒ `'16:45 · Nguyễn Thị Thu (Mẹ)'`; null time ⇒ name only.
- [x] **Step 2: Run, expect FAIL** — with `D:\Flutter_Setup\stable-3.47.6\flutter\bin` first on PATH: `flutter test test/features/parent/my_child/live_my_child_source_test.dart`
- [x] **Step 3: Implement** mapping.
- [x] **Step 4: Run, expect PASS**; `flutter analyze lib/features/parent/my_child`
- [x] **Step 5: Commit** — `TBD-4: Show pickup time with the pickup person`

### Task 10: Docs and final verification

Final verification on 27/11/2026 ran 2,805 tests with zero assertion failures, 15 errors and three skips. All 15 errors were in the known `EduYearPlanServiceIntegrationTest` fixture failure, with values exceeding `varchar(20)`. Targeted integration tests passed 168/168, photo replacement integration tests passed 5/5, and OpenAPI tests passed 4/4. FE passed all 2,007 tests plus lint/build. App passed 11 tests plus analysis. The full verification result is recorded in `docs/quality/VERIFICATION.md`.

- [x] Update `docs/business/BUSINESS_RULES.md` (CP-06, CP-17, HLT-06, HLT-11 rows say what is now enforced) and `docs/modules/child.md`, `docs/modules/health.md` (new endpoints, read-only exam, draft checks).
- [x] Run `.\mvnw.cmd -q verify` (Docker on). Known flake: `EduYearPlanServiceIntegrationTest` (memory be-edu-nanotime-flake) — report, do not fix.
- [x] Commit — `G94-180: Document BR code alignment`; set plan `status: completed`, move to `docs/plans/completed/`.
