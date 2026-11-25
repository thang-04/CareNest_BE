---
title: Sửa 3 mục tab Test (25/11) — phần BE
status: done  # 2026-11-25, full verify 2529 tests 0 fail (14 Edu flake)
owner: ATPX48-69
jira: G94-180 / BE-FIX-10
branch: fix/G94-180-BE-FIX-10-school-year-scope
modules: [child, health]
rules: [G94-180/CHD-03, G94-180/CHD-04, G94-180/CHD-14, HLT-08]
risk: high
created: 2026-11-25
updated: 2026-11-25
---
# Sửa 3 mục tab Test (25/11) — phần BE

> **Cho agent thực thi:** làm theo `superpowers:subagent-driven-development`, từng task một, bước có ô `- [ ]`. Plan FE đi kèm: `FE:docs/plans/active/2026-11-25-test-log-round-2.md`.

**Mục tiêu:** sửa phần BE của 3 mục user ghi ở tab "Test" (Google Doc "Nghiệp vụ Sức Khỏe", bản 25/11/2026):
1. Nhiệt độ phiếu khám nhận số vô lý (3115345).
2. "Ngày bắt đầu đi học" trống dù trẻ đã xếp lớp.
3. Cho nghỉ học chỉ được hôm nay hoặc ngày học kế tiếp.

**Stack:** Java 21, Spring Boot, JPA + native SQL, Flyway, JUnit 5 + Testcontainers (Docker Desktop phải bật).

## Quyết định của user (25/11/2026)

1. **Nhiệt độ:** đúng 2 chữ số phần nguyên, tối đa 2 chữ số sau dấu phẩy (vd 36,5; 37,25).
2. **Ngày bắt đầu đi học:** trẻ chưa có ngày bắt đầu thì lấy ngày trẻ vào lớp.
3. **Cho nghỉ học:**
   - Ngày nghỉ là **bất kỳ ngày nào** từ hôm nay tới hết năm học. Không bắt buộc là ngày học, không chọn ngày đã qua.
   - Chọn được hôm nay chỉ khi hôm nay trẻ chưa có dữ liệu nào. Dữ liệu gồm điểm danh, sức khỏe đầu ngày, đón trẻ, quan sát và đánh giá ngày (đã kiểm sẵn), cộng thêm số đo kỳ cân đo và phiếu khám.
   - Ngày nghỉ sau hôm nay: trẻ **vẫn Đang học** tới ngày nghỉ. Lúc 00:05 ngày nghỉ, hệ thống tự chuyển trẻ sang Đã nghỉ học và khóa phụ huynh (CHD-04).
   - Có **Hủy lịch nghỉ** (HT/PHT, trước ngày nghỉ).

## Global Constraints

- **Không sửa code của Cần:**
  - `SchoolYearService*`, `AttendanceService*`, `YearTransitionServiceImpl`.
  - Interface `SchoolDayUsageCheck` và `ChildDayUsageCheck`.
  - Trong `ChildRecordServiceImpl`: khối chặn dữ liệu cùng ngày của `withdraw` (`if (leftAt != null && leftAt.equals(today) && hasDailyOperationalData(childId, today))`), và hàm `hasDailyOperationalData`.
- **Migration:** chỉ tạo mới, không sửa migration đã commit.
  - Task 2 dùng V187, Task 3 dùng V188.
  - Trước khi tạo, chạy `git ls-tree --name-only origin/dev src/main/resources/db/migration/` và quét các nhánh remote (`git for-each-ref refs/remotes/origin`) xem V187/V188 đã có ai dùng chưa.
  - Sau khi đổi tên migration, xóa bản cũ trong `target/classes`.
- **Commit:** mỗi task 1–3 commit nhỏ.
  - Dòng đầu `G94-180: <động từ tiếng Anh ...>`, ≤72 ký tự, chỉ ký tự ASCII.
  - Không ghi attribution AI, không push.
- **Service:** theo `.claude/rules/service.md` (log START/END, try-catch `RuntimeException`).
  - Lỗi ô trả qua `GlobalException.withFieldErrors(ApiCode.INVALID_REQUEST_DATA, ...)` như `withdraw` đang làm.
- **Câu lỗi:** tiếng Việt, đúng nguyên văn trong plan. Ngày định dạng `dd/MM/yyyy` (hằng `DAY`).
- **Kiểm chứng:** `node scripts/verify.mjs --quick` sau mỗi task. Full verify ở cuối (biết trước 14 lỗi chập chờn ở `EduYearPlanServiceIntegrationTest`).

## Review Focus

- Job chạy 2 lần cùng ngày (00:05 rồi khởi động lại BE): lịch nghỉ không bị áp 2 lần, không ghi trùng lịch sử.
- Trẻ có lịch chuyển lớp trước ngày nghỉ: tới ngày chuyển trẻ sang lớp mới, tới ngày nghỉ thì nghỉ ở lớp mới.
- Kích hoạt năm mới trước ngày nghỉ: bảng xếp lớp năm mới đã ghi "Nghỉ học" (hàm `markLeavingOnNewYearBoard` có sẵn) nên trẻ không được xếp lớp.
- Trẻ tiếp nhận lại (`readmitted_on` có giá trị): ngày bắt đầu đi học điền lại phải lấy lớp của lần học hiện tại, không lấy lớp cũ.
- Phiếu khám lưu nháp với nhiệt độ sai định dạng phải bị chặn, không lưu âm thầm.

---

### Task 1: Nhiệt độ phiếu khám (mục 1)

**Files:**
- Sửa: `src/main/java/com/carenest/service/health/impl/HealthExamNormalizer.java` (`vitals`: nhiệt độ `round(..., 1)` ⇒ `round(..., 2)`)
- Sửa: `src/main/java/com/carenest/service/health/impl/HealthExamServiceImpl.java` (`draftErrors` thêm kiểm nhiệt độ)
- Sửa: `src/main/java/com/carenest/service/health/impl/HealthExamValidator.java` (khi kết luận: kiểm định dạng trước khoảng 34–42)
- Test: `src/test/java/com/carenest/service/health/impl/HealthExamIntegrationTest.java` (hoặc unit test của validator)

**Quy tắc:**
- Kiểm trên giá trị **người dùng gửi** (`request.exam().vitals().temperatureC()`, trước khi normalize).
- Giá trị hợp lệ khi đủ 3 điều kiện:
  - lớn hơn 0;
  - phần nguyên có đúng 2 chữ số;
  - `stripTrailingZeros().scale() <= 2`.
- Ô lỗi: `exam.vitals.temperatureC`. Câu lỗi: `Nhập nhiệt độ gồm 2 chữ số, tối đa 2 chữ số sau dấu phẩy (vd 36,5)`.
- Áp dụng cả lưu nháp và kết luận. Khi kết luận, kiểm định dạng trước, sau đó mới kiểm khoảng 34–42 như hiện có.

- [x] Test (fail trước):
  - Lưu nháp với 3115345, 9.5 và 36.555 ⇒ 400, ô `exam.vitals.temperatureC`.
  - 36.55 ⇒ lưu được, đọc lại vẫn 36.55.
  - 36.5 ⇒ lưu được.
  - Kết luận với 33.5 ⇒ vẫn ra lỗi khoảng như cũ.
- [x] Sửa code, chạy test, `node scripts/verify.mjs --quick`, commit.

### Task 2: Ngày bắt đầu đi học = ngày vào lớp (mục 2)

**Files:**
- Sửa: `src/main/java/com/carenest/service/child/impl/ChildRecordServiceImpl.java`, hàm xếp/chuyển lớp có gọi `child.placeInClass()` (khoảng dòng 448).
  - Trước khi gọi, nếu trẻ đang `PENDING_PLACEMENT` và `getStartDate() == null` thì gọi `child.startOn(effectiveDate)`.
  - Không sửa `YearTransitionServiceImpl`.
- Tạo: `src/main/resources/db/migration/V187__child_start_date_from_first_class.sql`.
  - Câu `UPDATE` điền `start_date` cho trẻ có `start_date IS NULL` và có ít nhất một enrollment.
  - Giá trị = `MIN(enrollments.valid_from)`. Nếu `readmitted_on` có giá trị thì chỉ xét các enrollment có `valid_from >= readmitted_on`.
- Tạo: `src/main/resources/db/dev/R__dev_13_child_start_date.sql`, cùng câu `UPDATE`. Cần file này vì seed dev chạy sau migration có số V.
- Sửa: `src/test/java/com/carenest/repository/organization/DevSeedIntegrationTest.java`: mọi trẻ có enrollment đều có `start_date`.
- Sửa: `docs/database/DATA_DICTIONARY.md`, dòng `children.start_date`. Bỏ câu "Null = hồ sơ trước V184", thay bằng: "null = chưa từng vào lớp; xếp lớp lần đầu ghi ngày vào lớp; V187 điền cho hồ sơ cũ".

- [x] Test (fail trước):
  - Trẻ chờ xếp lớp, `start_date` null, xếp lớp từ ngày D ⇒ `start_date` = D.
  - Trẻ chờ xếp lớp đã có `start_date` ⇒ giữ nguyên.
  - Chuyển lớp trẻ đang học ⇒ không đổi `start_date`.
- [x] Sửa code và migration, chạy test (cả `DevSeedIntegrationTest`), verify quick, commit.

### Task 3: Lịch nghỉ học (mục 3 — phần lõi)

**Files (chính):**
- Tạo: `V188__child_scheduled_leave.sql`:
  - `children` thêm `scheduled_leave_on DATE`, `scheduled_leave_reason` (cùng kiểu và độ dài với `left_reason`), `scheduled_leave_by BIGINT REFERENCES user_accounts(id)`.
  - CHECK: ba cột cùng null hoặc `scheduled_leave_on` và `scheduled_leave_reason` cùng có giá trị.
  - CHECK: `status <> 'LEFT' OR scheduled_leave_on IS NULL`.
  - `child_history`: tạo lại `ck_child_history_action`, thêm `LEAVE_SCHEDULED` và `LEAVE_CANCELLED`.
- Sửa: `utils/enums/ChildHistoryAction.java`: thêm `LEAVE_SCHEDULED`, `LEAVE_CANCELLED`.
- Sửa: `entity/child/Child.java`: thêm 3 trường, `scheduleLeave(LocalDate on, String reason, Long byUserId)`, `clearScheduledLeave()` và getter.
- Tạo: `service/child/ChildLeaveDataCheck.java` (`boolean hasData(Long childId, LocalDate date)`). Đây là interface mới, chỉ dùng cho nghỉ học, **không** dùng khi chuyển lớp.
- Tạo: `service/health/impl/HealthChildLeaveDataCheck.java`: trả true khi ngày đó trẻ có dòng `health_measurements` bất kỳ (số đo kỳ cân đo hoặc phiếu khám, kể cả nháp).
- Sửa: `service/child/impl/ChildRecordServiceImpl.java`:
  - **`leaveDateProblem`**, lần lượt:
    1. Trước hôm nay ⇒ `Ngày nghỉ học không được trước hôm nay`.
    2. Sau ngày kết thúc năm học đang hoạt động ⇒ `Ngày nghỉ học phải trong năm học (tới dd/MM/yyyy)`.
    3. Giữ nguyên hai câu về ngày tiếp nhận và ngày mở lại hồ sơ.
    4. **Bỏ** luật ngày học và luật "Chỉ chọn hôm nay hoặc ngày học kế tiếp".
  - **Hôm nay bị chặn** khi `hasDailyOperationalData(...)` (của Cần) **hoặc** có `ChildLeaveDataCheck` trả true.
    - Trong `leaveOptions`: dùng hằng `SAME_DAY_DATA_BLOCKED` làm lý do.
    - Trong `withdraw`: thêm kiểm tra riêng, **sau** khối của Cần, dùng `errors.putIfAbsent("leftAt", <câu của khối Cần>)`.
  - **Trẻ đã có lịch nghỉ:** lỗi ô `leftAt`: `Trẻ đã có lịch nghỉ học từ dd/MM/yyyy. Hủy lịch nghỉ trước khi chọn ngày khác`.
  - **`leftAt` = hôm nay:** giữ nguyên luồng nghỉ ngay. Tách phần tác động thành hàm riêng `applyLeave(...)`: đóng/xóa enrollment, hủy lịch chuyển lớp, giữ điểm trường, `child.withdraw`, hủy đăng ký năm sau, báo GV, phát `ChildrenLeftSchoolEvent`.
  - **`leftAt` sau hôm nay (đặt lịch):**
    - `child.scheduleLeave(leftAt, reason, actor.userId())`.
    - `markLeavingOnNewYearBoard` (có sẵn; lớp nguồn là lớp đang học hôm nay).
    - Ghi lịch sử `LEAVE_SCHEDULED` kèm lý do.
    - Báo GV các lớp đang học và lớp chưa bắt đầu: `<tên> sẽ nghỉ học từ ngày dd/MM/yyyy.`
    - **Không** đổi enrollment, **không** hủy đăng ký năm sau, **không** phát event.
  - **Thêm `applyDueScheduledLeaves(LocalDate today)`** (public, khai báo trong `ChildRecordService`), trả danh sách id trẻ đã áp.
    - Với từng trẻ có `scheduled_leave_on <= today` và `status <> LEFT`: gọi `applyLeave` với `leftAt` = ngày đã hẹn, lý do và người đặt lịch đã lưu.
    - Xóa lịch, ghi lịch sử `WITHDRAWN` (người = `scheduled_leave_by`).
    - Phát `ChildrenLeftSchoolEvent` cho các trẻ đã áp.
    - Lỗi ở một trẻ thì log và bỏ qua, không làm hỏng các trẻ khác.
- Sửa: `repository/child/ChildRepository.java`: thay `findIdsLeftBetween` bằng `findIdsWithLeaveDue(LocalDate today)`.
- Sửa: `service/child/impl/ScheduledLeaveJob.java`:
  - 00:05 và lúc khởi động gọi `applyDueScheduledLeaves(today)`.
  - Bỏ cửa sổ 7 ngày. Việc khóa bù vẫn đúng vì lịch chưa áp vẫn còn trong bảng.
  - Cập nhật comment cho đúng cách mới.
- Sửa: `dto/child/LeaveOptionsResponse.java`: bỏ `nextSchoolDay`, thêm 2 trường:
  - `minDate` = hôm nay nếu được chọn hôm nay, ngược lại là ngày mai;
  - `maxDate` = ngày kết thúc năm học đang hoạt động.
  - Giữ `today`, `todayAllowed`, `todayBlockedReason`.

- [x] Test (fail trước):
  - `leave-options` trả `minDate`/`maxDate` đúng trong 2 trường hợp: hôm nay chưa có dữ liệu và hôm nay đã có dữ liệu.
  - Hôm nay có số đo (không có điểm danh) ⇒ `todayAllowed=false`; gọi `withdraw` với hôm nay ⇒ lỗi ô `leftAt`.
  - Nghỉ từ thứ Bảy, hoặc từ một ngày cách 3 tuần (trong năm):
    - trẻ vẫn `ACTIVE`, các cột lịch có giá trị;
    - enrollment không đổi;
    - bảng năm mới ghi `LEAVE`, lịch sử có `LEAVE_SCHEDULED`.
  - Ngày sau năm học ⇒ lỗi. Ngày đã qua ⇒ lỗi.
  - Đặt lịch lần 2 ⇒ lỗi.
  - `applyDueScheduledLeaves` với ngày hẹn = hôm nay:
    - `status=LEFT`, `left_on` = ngày hẹn;
    - enrollment kết thúc vào ngày hẹn − 1;
    - lịch chuyển lớp từ sau ngày hẹn bị hủy;
    - event được phát; lịch được xóa.
  - Chạy lần 2 ⇒ không đổi gì.
  - Sửa các test cũ đang kiểm luật ngày học kế tiếp (ví dụ `WithdrawNewYearBoardIntegrationTest`).
- [x] Sửa code, chạy test liên quan, verify quick, commit (2–3 commit: migration + entity, luật + đặt lịch, job).

### Task 4: Hủy lịch nghỉ, DTO, tài liệu (mục 3 — phần còn lại)

**Files:**
- **Endpoint mới** `DELETE /children/{childId}/scheduled-leave` trong `ChildEnrollmentController`, đặt cạnh `withdraw`.
  - Quyền như `withdraw` (`loadInScope`). Service `cancelScheduledLeave(CurrentUser, Long childId)` trả `ChildViewResponse`.
  - Trẻ không có lịch, hoặc ngày hẹn ≤ hôm nay (đã/đang áp) ⇒ lỗi `Trẻ không có lịch nghỉ học chưa tới ngày`. Dùng `ApiCode` sẵn có phù hợp (409 CONFLICT); chỉ thêm `ApiCode` mới nếu không có mã nào hợp.
  - Tác động khi hủy:
    - xóa lịch;
    - xóa dòng `class_placements` của trẻ ở năm sắp tới, chỉ khi `action = LEAVE`;
    - ghi lịch sử `LEAVE_CANCELLED`;
    - báo GV lớp đang học: `Hủy lịch nghỉ học của <tên> (từ dd/MM/yyyy).`
- **DTO (chỉ thêm, không đổi tên trường cũ):**
  - `ChildViewResponse` thêm `scheduledLeaveOn`, `scheduledLeaveReason`.
  - Dòng danh sách trẻ (`ChildListServiceImpl` / DTO danh sách) thêm `scheduledLeaveOn`.
- **Chặn chuyển lớp sau ngày nghỉ:** trong hàm xếp/chuyển lớp của `ChildRecordServiceImpl`, nếu ngày vào lớp mới ≥ `scheduled_leave_on` ⇒ lỗi ô ngày vào lớp: `Trẻ nghỉ học từ dd/MM/yyyy. Hủy lịch nghỉ trước khi xếp lớp từ ngày này`.
- **Tài liệu:**
  - `docs/business/BUSINESS_RULES.md`:
    - G94-180/CHD-03: luật ngày nghỉ mới, lịch nghỉ, hủy lịch; ghi "user chốt 2026-11-25".
    - CHD-04: khóa phụ huynh lúc áp lịch.
  - `docs/database/DATA_DICTIONARY.md`: 3 cột mới của `children`; 2 action mới của `child_history`.
  - Tài liệu module trẻ trong `docs/modules/` (file đang ghi `withdraw`/`leave-options`).
  - `docs/api/openapi.yaml` (test `OpenApiSnapshotTest` với `-Dopenapi.snapshot.update=true`) và `node scripts/ai-layer/api-index.mjs`.
  - `docs/system/FE_API_CALLS.md` sẽ chép lại từ FE sau khi FE xong; task này không sửa file đó.

- [x] Test (fail trước):
  - Hủy lịch ⇒ trẻ vẫn `ACTIVE`, các cột lịch null, dòng `LEAVE` ở bảng năm mới bị xóa, lịch sử có `LEAVE_CANCELLED`.
  - Hủy khi không có lịch ⇒ lỗi.
  - `ChildViewResponse` và dòng danh sách có `scheduledLeaveOn`.
  - Chuyển lớp từ ngày ≥ ngày nghỉ ⇒ lỗi.
- [x] Sửa code và tài liệu, cập nhật snapshot, verify quick, commit.

## Progress log

- 2026-11-25: plan viết sau khi user trả lời 4 câu hỏi (mục 2: ngày bắt đầu đi học; mục 3: vẫn Đang học tới ngày nghỉ, ngày bất kỳ trong năm, có Hủy lịch nghỉ).
- 2026-11-25: done.
  - Task 1: 539e6907, fb796cbc.
  - Task 2: 461198a5, 720dca9d.
  - Task 3: cd75ab8e, 91758578, b22c4809.
  - Task 4: bbd97b84, 057945f9, 5849928d, 585e4507.
  - FE call snapshot: 04c28177.
  - Final review fix wave f0cfdf3a..29baa391:
    - seat freed from the leave date;
    - cancelling the first class keeps start_date;
    - cancel blocked once the new year was activated without a class (3018);
    - a due leave is applied before leave actions (3019);
    - neverAttended counts from the readmission date.
  - Full `node scripts/verify.mjs`: 2529 tests, 0 fail. 14 errors, all the `EduYearPlanServiceIntegrationTest` uptime flake. ArchUnit and snapshot OK; `spotless:check` OK.
  - Parked:
    - scheduling a leave while a transfer from on/after the leave date exists (the seat is already freed; the job cancels the transfer);
    - `LEAVE_SCHEDULED` history has no date;
    - V187 PENDING campus condition;
    - LEAVE row on the new-year board when the planned year is created after scheduling, or when a transfer happens before the leave day (the board/activation belong to Cần);
    - no retry for locking parent accounts.
