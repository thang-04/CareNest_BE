---
title: Sửa các lỗi user ghi ở tab Test (23/11) — phần BE
status: done  # draft → approved (chỉ user duyệt) → in-progress → done | blocked | cancelled
owner: ATPX48-69
jira: G94-180 / BE-FIX-10   # mỗi phần commit phải có mã task Jira — hỏi user trước khi commit
branch: fix/G94-180-BE-FIX-10-school-year-scope
modules: [child, health]
rules: [G94-180/CHD-03, G94-180/CHD-04, G94-180/CHD-08, G94-180/CHD-09, G94-180/CHD-14, CHD-01, HLT-01, HLT-08, HLT-09, HLT-13]
risk: high
created: 2026-11-24
updated: 2026-11-24
---
# Sửa các lỗi user ghi ở tab Test (23/11) — phần BE

> **Cho agent thực thi:** làm theo `superpowers:executing-plans` (hoặc `superpowers:subagent-driven-development`), từng task một, bước có ô `- [ ]`. Plan FE đi kèm: `FE:docs/plans/active/2026-11-24-test-log-fixes.md`.

**Mục tiêu:** sửa phần BE của 16 lỗi user ghi trong tab "Test" của Google Doc "Nghiệp vụ Sức Khỏe" (bản export 23/11, 19 ảnh), để các ca B6, B7, C0, C5, F5, H1 trong `D:\project_capstone\project\web\TEST-tac-dong-Can-2026-11-23.md` chạy đúng.

**Cách làm:** sửa nhỏ ở đúng tầng.
- Truy vấn danh sách trẻ: bỏ lớp đã đóng của trẻ chờ xếp lớp.
- Thêm trường vào DTO (chỉ thêm, không đổi trường cũ).
- Đổi luật ngày nghỉ học, cách tính sĩ số khi xếp lớp, cách nhập Excel, và quyền xem/sửa số đo khi trẻ chuyển lớp.
- Thêm 1 seed dev và (nếu Q3 chọn A) 1 migration.

**Stack:** Java 21, Spring Boot, JPA + native SQL, Flyway, JUnit 5 + Testcontainers (Docker Desktop phải bật).

**Nguồn yêu cầu:** Google Doc tab Test (`https://docs.google.com/document/d/1mvbYTIcN_Ea3Hoyxd8WNMhiCSC24Rl8OTZY_CW3hsxo/edit?tab=t.x5076hkgu5br`). Ảnh đã đối chiếu từng mục (bảng Context).

## Context

Đánh số theo mục trong tab Test. "Code của" = người viết phần lớn file (git log). Nguyên nhân đã kiểm trên code và DB `carenest_scope2` ngày 23–24/11.

| # | Lỗi user ghi | Nguyên nhân (đã kiểm) | Sửa ở | Code của |
| --- | --- | --- | --- | --- |
| 1 | Tiền sử nên thêm được như dị ứng | Chỉ là UI: tiền sử bản thân/gia đình là 1 ô chữ (`HealthDeclarationFields.jsx:123-149`) | FE | user |
| 2 | Ngày dd/mm/yyyy thiếu số 0 | `DateInput` không nhận "5/09/2027", chuỗi thô vào `startDateError` nên so chuỗi ra lỗi sai | FE | Cần (ô dùng chung) |
| 3 | Header 2027-2028, Xếp lớp vẫn hiện bảng | Trang luôn xếp ở năm đang hoạt động (Y-3, 28/10), chỉ có dòng báo nhỏ (`ClassPlacementPage.jsx:50-53`) | FE | user |
| 4 | B6 bước 4: file Excel sai | File test `TEST-nhap-tre-2027-2028.csv` mất số 0 đầu (SĐT `916231201`, CCCD `1623120001`). `PhoneNumbers.normalize` và `CitizenIds.normalize` không bù số 0 ⇒ dòng lỗi | BE + dữ liệu test | user |
| 5 | Mở lại hồ sơ: bỏ "Giữ điểm trường cũ", dùng điểm trường của trẻ | `DuplicateCheckResponse` không có điểm trường; FE thêm lựa chọn rỗng (`EnrollChildPage.jsx:744`) | BE + FE | user |
| 6 | B7: Tuệ Nga mở lại hồ sơ vẫn có lớp | `ChildRepository.findListRows` (`:29-39`): khi có `yearId`, lấy lớp cuối của trẻ trong năm **kể cả lớp đã đóng**. HS26041 có enrollment Mầm 1 đóng 19/10 ⇒ danh sách ghi "Lớp Mầm 1", FE coi là chuyển lớp | BE | user |
| 7 | Sửa hồ sơ luôn mất tuần thai | Không phải code: seed dev để `gestation_weeks` NULL cho 216/233 trẻ (`R__dev_01_people_structure.sql:1294+`, `R__dev_11_new_year_intake.sql:69+`). HS26311 nay có 41 vì user tự nhập. Trái CHD-08 ("seed dev nhập đủ") | BE seed | thangnd / user |
| 8 | 09/12/2026 là ngày nghỉ HT đặt (thứ 4): câu lỗi chưa hợp | Câu chung "phải là ngày học (thứ 2 tới thứ 6, không trùng ngày nghỉ…)" ở `ChildRecordServiceImpl.java:737`, `ChildIntakeServiceImpl.java:97`, FE `startDate.js:48,65` | BE + FE | user |
| 9 | C5: không cho ngày nghỉ học ở quá khứ; trẻ có điểm danh hôm nay ⇒ khóa hôm nay, mặc định ngày mai | Luật hiện tại `leftAt == today` (`ChildRecordServiceImpl.java:624`) cộng chặn dữ liệu trong ngày của Cần (`:646`) ⇒ hôm đó không cho nghỉ được. FE dùng ô ngày trình duyệt, chọn được quá khứ | BE + FE | user (+ Cần dòng 646) |
| 10 | Vừa chuyển 1 trẻ làm lớp đầy, thẻ lớp không cập nhật | Thẻ hiện `enrolledCount` = trẻ đang học **hôm nay** (`ChildIntakeServiceImpl.java:905`), BE kiểm sĩ số bằng `countOccupyingFrom` (gồm trẻ hẹn vào sau). Mầm 1: 24 hôm nay + HS26042 hẹn vào 24/11 = 25 | BE + FE | user |
| 11 | Đinh Anh Thịnh chuyển lớp: lớp cũ vẫn sửa số đo, lớp mới "Chưa cân đo" | `HealthCheckRules.rosterOf` (`:227-230`) chỉ lấy số đo cùng kỳ **và cùng lớp đã đo**; quyền sửa theo lớp đã đo (`HealthMeasurementServiceImpl.java:179-184`). HS26140 đo ở kỳ 4 (Cơ sở 2) ngày 19/11, nay ở Mầm 1 Cơ sở 1 (kỳ 3) | BE + FE | user |
| 12 | Trên 2 lớp thì hiện dạng accordion | UI xếp chồng các lớp (`HealthCheckDetailPage.jsx:326`) | FE | user |
| 13 | F5: trẻ vắng vẫn nhập được số đo | `createMeasurement` không đọc điểm danh (`HealthMeasurementServiceImpl.java:126-141`) | BE + FE | user |
| 14 | Phiếu khám: bước nào cũng báo lỗi, không lưu nháp được | HS26066 đã có số đo kỳ 3 ngày 23/11, phiếu khám (đợt 6) cũng ngày 23/11 ⇒ lỗi ô `date` "trẻ đã có kết quả đo khác" (`HealthExamServiceImpl.java:157-166`, luật HLT-08, ràng buộc `uk_health_measurements_child_date`). FE không có ô `date` nên chỉ hiện toast chung, mỗi lần chuyển bước tự lưu nháp lại báo lỗi | BE (nếu Q3=A) + FE | user |
| 15 | H1: trả trẻ xong vẫn ở "Chờ đón" tới khi tải lại | `refreshOnDataChange` chỉ nghe BE giả; `closeModals` không gọi `reload` (`PickupListPage.jsx:54-60`) | FE | user |
| 16 | Mầm 1 báo đủ mà vẫn hiện để chọn | Cùng nguyên nhân mục 10 | BE + FE | user |

Việc riêng FE (1, 2, 3, 12, 15) nằm ở plan FE. Plan này gồm phần BE của 4–11, 13, 14, 16.

## Global constraints

- Mọi trường mới trong DTO chỉ **thêm**; không đổi tên, không bỏ trường cũ (FE `fe-school-year-scope`, App không dùng các API này).
- Không sửa code của Cần: `SchoolYearService`, `AttendanceService`, dòng chặn dữ liệu trong ngày `ChildRecordServiceImpl.java:646-650`, `SchoolDayUsageCheck`. Chỉ **gọi** `SchoolYearService.getHolidays(actor, yearId)` và `AttendanceService.findDayStatuses(childIds, from, to)`.
- Không gọi repository của feature khác (`ArchitectureRulesTest.serviceUsesOnlyOwnFeatureRepositories`): health đọc điểm danh qua `AttendanceService`.
- Câu lỗi tiếng Việt, gắn đúng ô (`GlobalException.withFieldErrors`), không lộ mã kỹ thuật.
- Migration mới lấy số kế tiếp sau `V177__placement_effective_from` (kiểm lại số trên `origin/dev` và nhánh Cần trước khi tạo; hook đổi số: memory `be-migration-renumber-hook`).
- Không commit/push khi user chưa cho phép trong tin nhắn hiện tại. Được phép thì commit nhỏ, mỗi task 1 commit, dòng đầu `G94-xxx: <Verb> …` (mã Jira hỏi user).
- Iron Law: chưa có output `node scripts/verify.mjs` sau lần sửa cuối thì không báo xong. Docker Desktop phải bật; chạy verify ở worktree tạm nếu BE đang chạy (memory `school-year-scope-plans-1122`).
- Thời gian dùng `Clock` đã tiêm (`LocalDate.now(clock)`), không gọi `LocalDate.now()` trần.

## Quyết định đã chốt

1. Nhóm tuổi chỉ gợi ý, xếp lớp khác nhóm vẫn được (G94-180/CHD-13 — user 22/11; WIP 2A chưa commit trên cùng nhánh).
2. Ngày đo/ngày khám do hệ thống ghi, sửa số đo không đổi ngày (HLT-01, HLT-08, HLT-13 — user 04/11).
3. Hồ sơ trẻ luôn đủ thông tin, seed dev nhập đủ (G94-180/CHD-08 — user 28/10).
4. Xếp, chuyển lớp luôn ở năm đang hoạt động; năm sau xếp ở bảng "Xếp lớp năm học mới" của Cần (G94-180/CHD-10, Y-3 — user 28/10).

## Làm rõ nghiệp vụ

- **Actor:** HT/PHT (tiếp nhận, xếp lớp, cho nghỉ học, nhập Excel), GV (cân đo, phiếu khám, đón trẻ), tổ trưởng nhóm tuổi (GV nhiều lớp).
- **Lệch tài liệu:** mục 9 đổi CHD-03 ("không ở tương lai"); mục 14 phương án A đổi HLT-01/HLT-08 ("ngày đó trẻ đã có kết quả đo khác ⇒ không lưu"); mục 11 thêm luật mới cho số đo khi chuyển lớp (chưa có rule).
- **Ảnh hưởng FE:** `/children/placement`, `/children/new`, `/children` (hộp Cho nghỉ học, Mở lại hồ sơ), `/children/:id/edit`, `/children/health-checks/:id`, `/children/health-checks/:id/exam/:childId`, `/pickup`. App: không dùng các API đổi ở đây (đã grep `carenest_app` khi thực thi — bước kiểm ở Phase B6).
- **Dữ liệu cũ:** seed dev (mục 7). DB `carenest_scope2` giữ dữ liệu test của user; migration mới (nếu có) chạy thêm, không reset.

### Câu hỏi mở

Trả lời xong mới `approved`. Mỗi câu có phương án khuyến nghị (A).

- [x] **Q1 (mục 9) Cho nghỉ học chọn ngày nào?**
  - **A:** Hôm nay hoặc ngày học kế tiếp, không chọn ngày đã qua, không xa hơn. Trẻ đã có điểm danh, sức khỏe, đánh giá hôm nay ⇒ chỉ ngày học kế tiếp (mặc định sẵn). Trạng thái "Đã nghỉ học" ghi ngay, nhưng lớp vẫn có trẻ tới hết hôm nay. Tài khoản phụ huynh khóa từ ngày nghỉ (job 0h), không khóa ngay. *(khuyến nghị)*
  - **B:** Cho chọn ngày bất kỳ từ hôm nay tới hết năm học. Cần thêm trạng thái "Sắp nghỉ học" và job đổi trạng thái; to hơn A khoảng 2 lần.
- [x] **Q2 (mục 11) Trẻ chuyển lớp khi kỳ cân đo đang mở, số đo đã nhập ở lớp cũ xử lý thế nào?**
  - **A:** Số đo giữ nguyên chỗ đã đo.
    - Lớp cũ chỉ xem, có nhãn "Đã chuyển sang …", không tính vào tiến độ lớp cũ.
    - Lớp mới thấy số đo, sửa được; sửa thì người ghi đổi thành người sửa (lý do bắt buộc như HLT-13).
    - Khác điểm trường: kỳ cân đo ở điểm trường mới, cùng năm, cùng loại, khoảng ngày chứa ngày đo, coi là cùng kỳ. Lớp mới không nhập được số đo thứ hai trong kỳ đó.
    - *(khuyến nghị)*
  - **B:** Lớp mới sửa thì chuyển hẳn số đo sang kỳ và lớp mới. Khi đó lớp cũ mất dòng của trẻ.
- [x] **Q3 (mục 14) Ngày khám trùng ngày đã cân đo ở kỳ cân đo (trường hợp HS26066)?**
  - **A:** Cho lưu. Bỏ ràng buộc "mỗi trẻ một lần đo một ngày" giữa kỳ cân đo và đợt khám (migration mới). Xu hướng, báo cáo gặp 2 lần đo cùng ngày thì lấy phiếu khám. Phiếu khám mới điền sẵn chiều cao, cân nặng của lần đo cùng ngày. *(khuyến nghị: bác sĩ chỉ đến một ngày, chặn thì trẻ không có phiếu khám)*
  - **B:** Giữ luật HLT-08. Mở phiếu thấy ngay câu "Ngày 23/11/2026 trẻ đã có số đo ở «Cân đo định kỳ tháng 11/2026». Báo HT/PHT", khóa nhập. Không lưu nháp.
- [x] **Q4 (mục 3, plan FE) Header chọn 2027-2028, trang Xếp lớp hiện gì?**
  - **A:** Không hiện bảng năm đang học. Hiện khung "Xếp lớp cho năm học 2027-2028 làm ở màn Xếp lớp năm học mới", có nút mở màn đó và nút về 2026-2027. *(khuyến nghị)*
  - **B:** Giữ bảng như Y-3, chỉ làm khung báo to hơn.
- [x] **Q5 Xác nhận cách hiểu (mặc định nếu không trả lời):**
  - (a) Mục 1: "thêm như dị ứng" = ô nhập + nút "Thêm" + chip cho tiền sử bản thân và gia đình (phiếu khai báo và phiếu khám). Lưu nối bằng "; " vào ô chữ hiện có, BE không đổi.
  - (b) Mục 12: accordion khi GV có **từ 2 lớp** trở lên, lớp đầu mở sẵn.
  - (c) Mục 13: chặn cả phiếu khám (đợt khám) khi trẻ vắng, không chỉ kỳ cân đo. Thứ 7, chủ nhật luôn cho nhập. Chưa điểm danh thì vẫn cho nhập.
  - (d) Mục 2: sửa luôn ô `DateInput` dùng chung (file của Cần, 30 file dùng), báo Cần. Phương án khác: chỉ sửa ở màn của mình.

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| Q1 | A — hôm nay hoặc ngày học kế tiếp, khóa tài khoản từ ngày nghỉ | user ("các phần bạn khuyến nghị tôi duyệt toàn bộ") | 2026-11-24 | CHD-03 ở B1.5 |
| Q2 | A — số đo giữ chỗ đã đo, lớp hiện tại xem/sửa, kỳ liên quan khác điểm trường | user | 2026-11-24 | HLT-09 ở B4 |
| Q3 | A — cho lưu phiếu khám cùng ngày kỳ cân đo, migration bỏ ràng buộc ngày | user | 2026-11-24 | HLT-01, HLT-08 ở B5 |
| Q4 | A — năm sắp diễn ra: không hiện bảng, khung + nút | user | 2026-11-24 | plan FE F2.5 |
| Q5 | Mặc định (a) chip tiền sử, (b) accordion từ 2 lớp, (c) chặn cả phiếu khám khi vắng, (d) sửa `DateInput` chung, báo Cần | user | 2026-11-24 | plan FE F1, F2.1, F4 |

Cũng ngày 24/11, user cho phép:
- commit 2A trên nhánh G94-180 (mã Jira G94-180, commit local, không push);
- chạy một subagent cho mỗi task, làm hết rồi báo cáo một lần.

## Acceptance criteria

Viết theo phương án A của Q1–Q3. User chọn B thì sửa AC và phase tương ứng trước khi làm.

- **AC-4** (CHD-01): Nhập Excel (.csv, .xlsx, .xls) có SĐT 9 chữ số bắt đầu 3/5/7/8/9, hoặc CCCD 10–11 chữ số (CCCD 9 chữ số giữ nguyên, báo lỗi như trước).
  - Xem trước bù số 0 đầu (`0916231201`, `001623120001`), dòng hợp lệ, `notes` có "Đã thêm số 0 đầu cho SĐT/CCCD (Excel bỏ mất). Kiểm tra lại trước khi nhập."
  - Nhập tạo đúng hồ sơ.
- **AC-5** (G94-180/CHD-04): `GET /children/duplicate-check` trả trẻ đã nghỉ trong phạm vi ⇒ có `campusId` = điểm trường của trẻ (lớp cuối, không có lớp thì điểm trường tiếp nhận).
- **AC-6** (G94-180/CHD-09): Trẻ "Chờ xếp lớp" (mới tiếp nhận hoặc mở lại hồ sơ), lớp cũ trong năm đã đóng ⇒ danh sách theo năm trả `classroomId = null`. Trẻ chờ xếp lớp có lớp dự kiến năm sau (enrollment chưa đóng) vẫn hiện lớp đó (B2 không vỡ).
- **AC-7** (G94-180/CHD-08): DB dev mới (Flyway + seed) không còn `child_profiles.gestation_weeks IS NULL`.
- **AC-8** (G94-180/CHD-14): Ngày vào lớp / chuyển lớp / bắt đầu đi học / nghỉ học không phải ngày học ⇒ câu lỗi nói rõ lý do:
  - thứ 7, chủ nhật: "Ngày vào lớp 28/11/2026 là thứ Bảy. Chọn ngày từ thứ 2 tới thứ 6."
  - trùng ngày nghỉ: "Ngày vào lớp 09/12/2026 trùng ngày nghỉ của trường: <tên> (09/12/2026). Chọn ngày học khác."
  - Đợt nghỉ nhiều ngày ghi "(dd/mm/yyyy – dd/mm/yyyy)".
- **AC-9** (G94-180/CHD-03, Q1=A):
  - `leftAt` trước hôm nay ⇒ "Ngày nghỉ học không được trước hôm nay".
  - Sau ngày học kế tiếp ⇒ "Chỉ chọn hôm nay hoặc ngày học kế tiếp (dd/mm/yyyy)".
  - Hôm nay mà trẻ có dữ liệu trong ngày ⇒ lỗi của Cần giữ nguyên.
  - Ngày học kế tiếp ⇒ lưu được: lớp kết thúc hôm nay, trẻ còn trong điểm danh, đón trẻ hôm nay.
  - Phụ huynh chỉ có trẻ này: tài khoản chưa khóa hôm nay, job 0h ngày nghỉ mới khóa.
- **AC-10** (G94-180/CHD-14): `GET /children/placement-classes?from=YYYY-MM-DD&childId=` trả thêm `occupiedCount` = `countOccupyingFrom(class, from, childId)`.
  - Mầm 1 ngày 23/11: `enrolledCount = 24`, `occupiedCount = 25`.
  - Thiếu `from` ⇒ hôm nay.
  - Năm sắp diễn ra ⇒ `occupiedCount = enrolledCount = plannedCount`.
- **AC-11** (HLT-09, HLT-13, Q2=A). Trẻ chuyển lớp trong khi kỳ mở:
  - Dòng của trẻ ở lớp mới có số đo cũ, `canEdit = true`, `measuredIn = {className, campusName, date}`.
  - Dòng ở lớp cũ có `movedTo = {className, campusName, from}`, `canEdit = false`, không tính vào tiến độ lớp cũ.
  - `PUT` số đo bởi GV lớp mới ⇒ được, `measuredBy` = người sửa, có bản ghi lịch sử sửa.
  - `PUT` bởi GV lớp cũ ⇒ 403 `HEALTH_MEASUREMENT_ACCESS_DENIED`.
  - `POST` số đo mới ở kỳ điểm trường mới cho trẻ đã có số đo cùng kỳ ⇒ `HEALTH_MEASUREMENT_EXISTS`, câu nêu kỳ, ngày đo.
- **AC-13** (HLT-01, HLT-09). Ngày đo (thứ 2–6) trẻ có điểm danh vắng (`EXCUSED`/`UNEXCUSED`):
  - `POST` số đo / lưu phiếu khám mới ⇒ lỗi "Trẻ vắng ngày dd/mm/yyyy (điểm danh). Nhập khi trẻ có mặt."
  - Thứ 7, chủ nhật, hoặc chưa điểm danh ⇒ lưu được.
  - Dòng danh sách có `absentToday = true`.
- **AC-14** (HLT-08, Q3=A): Phiếu khám mới ngày đã có số đo kỳ cân đo ⇒ lưu nháp, kết luận được.
  - Lịch sử tăng trưởng trả 1 điểm cho ngày đó (của phiếu khám).
  - Phiếu mới điền sẵn chiều cao, cân nặng của lần đo cùng ngày.

## Key decisions

| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Bù số 0 Excel | Ở `ChildEnrollmentImportServiceImpl` khi đọc ô (chỉ nhập file), kèm `notes` | Sửa `PhoneNumbers`/`CitizenIds` dùng chung | Form tay không bị Excel cắt số 0. Đổi hàm chung thì ảnh hưởng đăng nhập, tài khoản |
| Câu lỗi ngày nghỉ | Hàm riêng `SchoolDayMessages` trong `service/child/impl`, đọc ngày nghỉ qua `SchoolYearService.getHolidays` | Thêm hàm vào `SchoolYearService` | Không sửa code Cần |
| Sĩ số khi xếp | Thêm `occupiedCount` (cơ sở kiểm sĩ số), giữ `enrolledCount` | Đổi nghĩa `enrolledCount` | FE cũ vẫn đúng, chỉ thêm |
| Số đo khi chuyển lớp | Hiển thị và quyền theo **lớp hiện tại** của trẻ, không di chuyển dữ liệu | Chuyển dòng sang kỳ/lớp mới | Lịch chuyển có thể hẹn ngày; giữ lịch sử đúng nơi đo |
| Điểm danh cho health | `AttendanceService.findDayStatuses` | Đọc `AttendanceRecordRepository` | Luật kiến trúc ADR-0001 |

## Phases

Mỗi phase: làm → verify → ghi Progress log → **dừng chờ user review**. Thứ tự đề xuất: B1 → B2 → B3 → B4 → B5 → B6. Phase B4, B5 chờ Q2, Q3.

Trước khi làm: WIP 2A (chưa commit) đang sửa `ChildIntakeServiceImpl`, `ChildRecordServiceImpl`, `PlacementRules` và 4 test. Nên commit 2A trước để tách diff (cần user cho phép + mã Jira).

### Phase B1 — Hồ sơ trẻ: danh sách, mở lại hồ sơ, seed, câu lỗi ngày học, nghỉ học

#### Task B1.1: Trẻ chờ xếp lớp không mang lớp đã đóng (mục 6)

**Files:** M `src/main/java/com/carenest/repository/child/ChildRepository.java:26-67` · Test `src/test/java/com/carenest/service/child/impl/ChildLeftSchoolIntegrationTest.java`

- [x] **Bước 1: Viết test hỏng.** Hai test mới:
  - `readmittedChildDoesNotShowClosedClassOfYear`:
    - Trẻ có lớp năm đang học, cho nghỉ, mở lại hồ sơ vào năm đang học.
    - `childListService` lọc theo `schoolYear` = năm đang học ⇒ trẻ `status = PENDING_PLACEMENT`, `classId == null`, `className == null`.
  - `pendingChildKeepsPlannedClassOfNextYear`:
    - Trẻ tiếp nhận năm sau có lớp dự kiến.
    - Lọc theo năm sau ⇒ `classId` = lớp dự kiến.
- [x] **Bước 2: Chạy, thấy test 1 hỏng.** `./mvnw -q test -Dtest=ChildLeftSchoolIntegrationTest` ⇒ FAIL ở `classId` (đang là lớp cũ).
- [x] **Bước 3: Sửa truy vấn.** Trong `LATERAL` thêm điều kiện: `c.status <> 'PENDING_PLACEMENT' OR e.valid_to IS NULL OR e.valid_to >= :today`. Sửa Javadoc dòng 13-25 cho khớp.
- [x] **Bước 4: Chạy lại** ⇒ PASS cả 2 test cũ và mới của class.

#### Task B1.2: Duplicate-check trả điểm trường (mục 5)

**Files:** M `src/main/java/com/carenest/dto/child/DuplicateCheckResponse.java` (thêm `Long campusId` cuối record; `otherCampus` truyền `null`) · M `ChildIntakeServiceImpl.java:155-166` (truyền `currentCampusOf(child, today)` đã có) · Test `src/test/java/com/carenest/service/account/impl/ChildEnrollmentIntegrationTest.java`

**Interfaces:** Tạo ra `DuplicateCheckResponse.campusId: Long`. FE Task F2.2 dùng trường này.

- [x] **Bước 1: Viết test hỏng.** `duplicateOfLeftChildReturnsCampus`: trẻ nghỉ ở lớp Cơ sở 2 ⇒ `checkDuplicate(...).campusId()` = id Cơ sở 2. `otherCampus` ⇒ `campusId == null`.
- [x] **Bước 2:** chạy ⇒ không biên dịch / FAIL.
- [x] **Bước 3:** thêm trường, truyền giá trị. Sửa `ChildEnrollmentControllerTest` nếu dựng record bằng constructor.
- [x] **Bước 4:** chạy class test ⇒ PASS.

#### Task B1.3: Seed dev đủ tuần thai (mục 7)

**Files:** C `src/main/resources/db/dev/R__dev_12_profile_gestation_weeks.sql`

- [x] **Bước 1:** viết SQL lặp lại được: `UPDATE child_profiles SET gestation_weeks = CASE WHEN premature = 'YES' THEN 35 ELSE 39 END WHERE gestation_weeks IS NULL;`. Thêm 1 dòng chú thích lý do (CHD-08).
- [x] **Bước 2:** kiểm trên DB mới bằng test Flyway có sẵn (verify chạy toàn bộ seed). Sau đó trên `carenest_scope2`: `select count(*) from child_profiles where gestation_weeks is null` ⇒ `0` (chạy khi BE khởi động lại, Flyway áp R__ mới).

#### Task B1.4: Câu lỗi ngày học rõ lý do (mục 8)

**Files:** C `src/main/java/com/carenest/service/child/impl/SchoolDayMessages.java` · M `ChildRecordServiceImpl.java:725-740` (`effectiveDateProblem`) và `:626-627` (nghỉ học) · M `ChildIntakeServiceImpl.java:97` và chỗ dùng · Test C `src/test/java/com/carenest/service/child/impl/SchoolDayMessagesTest.java`

**Interfaces:**
- `static String problem(String label, LocalDate date, List<SchoolHolidayItem> holidays)` trả `null` khi là ngày học.
- Ngày nghỉ lấy bằng `schoolYearService.getHolidays(actor, yearId)`; `yearId` = năm của lớp đích hoặc năm tiếp nhận.

- [x] **Bước 1: Viết test hỏng** (JUnit thuần, không Spring):
  - `problem("Ngày vào lớp", 2026-11-28, [])` = `"Ngày vào lớp 28/11/2026 là thứ Bảy. Chọn ngày từ thứ 2 tới thứ 6."`
  - Chủ nhật ⇒ chữ "Chủ nhật".
  - Ngày nghỉ "Nghỉ thử" 09/12–09/12 ⇒ `"Ngày vào lớp 09/12/2026 trùng ngày nghỉ của trường: Nghỉ thử (09/12/2026). Chọn ngày học khác."`
  - Đợt 01/01–03/01/2027 ⇒ "(01/01/2027 – 03/01/2027)".
  - Ngày học ⇒ `null`.
- [x] **Bước 2:** chạy ⇒ FAIL (chưa có class).
- [x] **Bước 3:** viết `SchoolDayMessages`. Thay câu chung ở 3 chỗ:
  - Nghỉ học: label "Ngày nghỉ học", cuối câu ngày nghỉ thêm " Thực hiện vào ngày học kế tiếp."
  - Vẫn gọi `schoolYearService.isSchoolDay(date)` để quyết định có lỗi hay không, chỉ dùng `SchoolDayMessages` để tạo câu.
- [x] **Bước 4:** chạy test mới + `ChildRecordServiceImplTest`, `ChildPlacementScheduleIntegrationTest`, `ChildEnrollmentIntegrationTest`. Câu cũ trong test cũ thì sửa sang câu mới ⇒ PASS.

#### Task B1.5: Nghỉ học hôm nay hoặc ngày học kế tiếp (mục 9, Q1=A)

**Files:** M `ChildRecordServiceImpl.java:606-719` (`withdraw`) · C `src/main/java/com/carenest/service/child/impl/ScheduledLeaveJob.java` (job 0h) · M `docs/business/BUSINESS_RULES.md:405` (CHD-03) · Test `src/test/java/com/carenest/service/account/impl/ChildRecordIntegrationTest.java`, `ChildRecordServiceImplTest.java`

**Interfaces:**
- `withdraw` nhận `leftAt` ∈ {hôm nay, `nextSchoolDay(today)`}.
- Endpoint mới `GET /children/{id}/leave-options` (cùng quyền như `withdraw`) trả `record LeaveOptionsResponse(LocalDate today, boolean todayAllowed, String todayBlockedReason, LocalDate nextSchoolDay)`.
  - `todayAllowed = false` khi hôm nay không phải ngày học hoặc trẻ có dữ liệu trong ngày (`hasDailyOperationalData`).
  - `todayBlockedReason` lấy đúng câu đang dùng cho từng trường hợp đó.
  - FE Task F2.4 dùng endpoint này.
- Job: `@Scheduled(cron = "0 5 0 * * *", zone = "${carenest.time-zone}") void lockParentsOfChildrenLeavingToday()` publish `ChildrenLeftSchoolEvent(ids, today)` cho trẻ `status = LEFT AND left_on = today`.

- [x] **Bước 1: Viết test hỏng:**
  - `withdrawRejectsPastDate`: `leftAt = today-1` ⇒ lỗi ô `leftAt` "Ngày nghỉ học không được trước hôm nay".
  - `withdrawRejectsBeyondNextSchoolDay`: thứ 2 chọn thứ 4 ⇒ "Chỉ chọn hôm nay hoặc ngày học kế tiếp (dd/mm/yyyy)".
  - `withdrawFromNextSchoolDayKeepsTodayRoster`: có điểm danh hôm nay, `leftAt` = ngày học kế tiếp ⇒ lưu được.
    - `enrollment.validTo` = ngày trước `leftAt`.
    - `enrollmentService.findChildrenInClassroom(class, today)` còn trẻ.
    - Tài khoản phụ huynh (chỉ có trẻ này) vẫn `ACTIVE`.
  - `scheduledLeaveJobLocksParentOnLeaveDay`: đặt clock = `leftAt`, gọi job ⇒ tài khoản `INACTIVE`.
  - Thứ 6 chọn ngày học kế tiếp = thứ 2 tuần sau (qua cuối tuần) ⇒ được.
  - `leaveOptionsBlockTodayAfterAttendance`: trẻ có điểm danh hôm nay ⇒ `todayAllowed = false`, `todayBlockedReason` = câu của Cần, `nextSchoolDay` = ngày học kế tiếp. Trẻ chưa có dữ liệu ⇒ `todayAllowed = true`.
- [x] **Bước 2:** chạy ⇒ FAIL.
- [x] **Bước 3: Sửa `withdraw`:**
  - Thay `!leftAt.equals(today)` bằng 2 lỗi trên. Tính "ngày học kế tiếp" bằng vòng `isSchoolDay` từ `today+1` (tối đa 400 ngày, như FE `firstSchoolDay`).
  - Giữ nguyên khối chặn của Cần (`:646`).
  - Chỉ `publishEvent(ChildrenLeftSchoolEvent)` khi `leftAt.equals(today)`; còn lại để job.
  - Thông báo GV giữ câu "nghỉ học từ ngày dd/mm/yyyy".
- [x] **Bước 4:** viết `ScheduledLeaveJob` (trong `service/child/impl`, dùng `ChildRepository` — thêm truy vấn `findIdsLeavingOn(LocalDate)`). Dùng `ShedLock` nếu các job khác có dùng (kiểm `HealthCheckServiceImpl.java:357`).
  - Thêm `leaveOptions(actor, childId)` vào `ChildRecordService` và route `GET /children/{id}/leave-options` ở controller đang có `POST /children/{id}/withdraw`.
  - Dùng chung hàm tính `nextSchoolDay` và kiểm dữ liệu trong ngày với `withdraw`.
- [x] **Bước 5:** chạy test ⇒ PASS. Cập nhật CHD-03 (bỏ "không ở tương lai", ghi luật mới, "user chốt 2026-11-24").

- [x] **Exit B1:** `node scripts/verify.mjs` ⇒ `VERIFY PASS`. Cho phép chỉ các lỗi đã biết, ghi số: Edu nanoTime flake theo memory `be-edu-nanotime-flake`. **Dừng chờ review.**

### Phase B2 — Sĩ số khi xếp lớp tính cả trẻ hẹn vào (mục 10, 16)

**Files:** M `src/main/java/com/carenest/dto/child/PlacementClassResponse.java` (thêm `long occupiedCount`) · M `ChildIntakeServiceImpl.java:180-220` (nhận `from`, `childId`) · M controller của `GET /children/placement-classes` (2 `@RequestParam` tùy chọn) · Test `ChildPlacementScheduleIntegrationTest.java`

**Interfaces:**
- `GET /children/placement-classes?schoolYearId=&from=YYYY-MM-DD&childId=` ⇒ mỗi lớp thêm `occupiedCount: number`.
- Năm đang học: `occupiedCount = enrollmentRepository.countOccupyingFrom(class, from ?: today, childId ?: -1)`.
- Năm sắp diễn ra: `occupiedCount = plannedCount`.
- FE Task F3.1 dùng.

- [x] **Bước 1: Viết test hỏng.** `placementClassesCountScheduledArrivals`:
  - Lớp sức chứa 3: 2 trẻ đang học, 1 trẻ hẹn chuyển vào ngày mai.
  - `from` = hôm nay ⇒ `enrolledCount = 2`, `occupiedCount = 3`.
  - Truyền `childId` của trẻ hẹn ⇒ `occupiedCount = 2`.
- [x] **Bước 2:** chạy ⇒ FAIL.
- [x] **Bước 3:** thêm trường + tham số. Tách hàm `occupiedCounts(ids, from, childId)` dùng `countOccupyingFrom` cho từng lớp: chỉ vài chục lớp, chấp nhận N truy vấn; nếu verify chậm thì gộp 1 truy vấn `GROUP BY`.
- [x] **Bước 4:** chạy ⇒ PASS. Sinh lại `docs/integration/API_INDEX` nếu repo có script (`scripts/` — kiểm tên khi làm).
- [x] **Exit B2:** verify ⇒ PASS. **Dừng chờ review.**

### Phase B3 — Nhập Excel bù số 0 đầu (mục 4)

**Files:** M `src/main/java/com/carenest/service/child/impl/ChildEnrollmentImportServiceImpl.java:300-320` (đọc ô phone, guardianIdNumber, phone2/3, CCCD 2/3) · C `src/main/java/com/carenest/service/child/impl/ExcelZeroFix.java` · Test C `src/test/java/com/carenest/service/child/impl/ExcelZeroFixTest.java` + 1 test preview ở `ChildEnrollmentIntegrationTest`

**Interfaces:**
- `static Optional<String> phone(String raw)`: chỉ chữ số, dài 9, bắt đầu `[35789]` ⇒ `"0" + raw`, còn lại `Optional.empty()` (giữ nguyên).
- `static Optional<String> citizenId(String raw)`: chỉ chữ số, dài 9–11 ⇒ đệm `0` bên trái tới 12, còn lại empty.

- [x] **Bước 1: Viết test hỏng:**
  - `phone("916231201")` = `"0916231201"`.
  - `phone("0916231201")` empty.
  - `phone("123456789")` empty.
  - `citizenId("1623120001")` = `"001623120001"`.
  - `citizenId("30224591837")` = `"030224591837"`.
  - `citizenId("030224591837")` empty.
  - `citizenId("12345678")` empty.
  - Preview của file 2 dòng như `TEST-nhap-tre-2027-2028.csv` ⇒ 0 lỗi dòng, `notes` có `"Đã thêm số 0 đầu cho SĐT/CCCD (Excel bỏ mất). Kiểm tra lại trước khi nhập."`
- [x] **Bước 2:** chạy ⇒ FAIL.
- [x] **Bước 3:** áp `ExcelZeroFix` lên giá trị thô trước khi dựng payload. Ghi note 1 lần mỗi dòng.
- [x] **Bước 4:** chạy ⇒ PASS.
- [x] **Exit B3:** verify ⇒ PASS. **Dừng chờ review.**

### Phase B4 — Kỳ cân đo: trẻ chuyển lớp, trẻ vắng (mục 11, 13) — chờ Q2, Q5(c)

**Files:**
- M `src/main/java/com/carenest/service/health/impl/HealthCheckRules.java:217-255` (`rosterOf`)
- M `HealthCheckServiceImpl.java:149-230` (dựng dòng, tiến độ)
- M DTO dòng trẻ `HealthCheckClassResponse.HealthCheckChild`: thêm `boolean canEdit`, `MovedTo movedTo`, `MeasuredIn measuredIn`, `boolean absentToday`
- M `HealthMeasurementServiceImpl.java:115-210` (tạo, sửa)
- M `HealthExamServiceImpl.java:105-170` (vắng; khi Q5(c) = có)
- Test `src/test/java/com/carenest/service/health/impl/HealthCheckIntegrationTest.java`, `HealthRecordIntegrationTest.java`, `HealthExamIntegrationTest.java`

**Interfaces:**
- `record MovedTo(String className, String campusName, LocalDate from)`
- `record MeasuredIn(String className, String campusName, LocalDate date)`
- "Kỳ liên quan" của kỳ K là kỳ L cùng `kind`, cùng `school_year_id`, khác `campus_id`, chưa hủy, khoảng ngày giao nhau với K, và ngày đo nằm trong khoảng của K. Một hàm `linkedCampaignIds(K)` ở `HealthCheckRules`.
- Lớp hiện tại của trẻ = lớp tại `referenceDate(K)` (`enrollmentService` sẵn có).

- [x] **Bước 1: Viết test hỏng (Q2=A):**
  - `transferWithinRoundMovesEditingToNewClass`: cùng kỳ, đo ở lớp A, chuyển sang lớp B hôm nay.
    - Danh sách lớp B: trẻ `RECORDED`, `canEdit = true`, `measuredIn.className = A`.
    - Danh sách lớp A: trẻ có `movedTo.className = B`, `canEdit = false`; tiến độ lớp A không đếm trẻ.
  - `transferAcrossCampusesShowsMeasurementFromLinkedRound`: kỳ 2 điểm trường cùng ngày, đo ở kỳ của Cơ sở 2, chuyển sang lớp Cơ sở 1 ⇒ danh sách kỳ Cơ sở 1 có số đo đó.
  - `newClassTeacherEditsMovedMeasurement`: GV lớp B `PUT` ⇒ 200, `measuredBy` = GV B, có 1 dòng `health_measurement_edits`.
  - `oldClassTeacherCannotEditMovedMeasurement`: GV lớp A `PUT` ⇒ `HEALTH_MEASUREMENT_ACCESS_DENIED`.
  - `secondMeasurementInLinkedRoundRejected`: `POST` ở kỳ Cơ sở 1 ⇒ `HEALTH_MEASUREMENT_EXISTS`.
- [x] **Bước 2: Viết test hỏng (mục 13):**
  - `absentChildCannotBeMeasuredOnSchoolDay`: điểm danh `UNEXCUSED` hôm nay (thứ 2–6) ⇒ `POST` lỗi; dòng có `absentToday = true`.
  - `weekendMeasurementAllowedWithoutAttendance`: clock thứ 7 ⇒ được.
  - `unmarkedAttendanceAllowsMeasurement` ⇒ được.
  - `absentChildCannotStartExam` (nếu Q5(c) = có).
- [x] **Bước 3:** chạy ⇒ FAIL.
- [x] **Bước 4: Sửa `rosterOf`:**
  - Thành viên hiện tại lấy số đo của trẻ trong K **hoặc** trong kỳ liên quan (bỏ lọc `classroomId` cho thành viên hiện tại).
  - Trẻ có số đo ở lớp này mà nay ở lớp khác ⇒ dòng `movedTo`, đánh dấu không tính tiến độ: `Progress.of` bỏ dòng `movedTo != null`.
- [x] **Bước 5: Sửa quyền:**
  - `updateMeasurement` kiểm `rules.canRecord(user, currentClassOf(child))`, với lớp hiện tại thuộc điểm trường của kỳ hoặc của kỳ liên quan đang mở.
  - Sửa thì `m.reassignRecorder(currentUser.userId())`: thêm hàm entity `HealthMeasurement`, đổi `measuredBy`.
  - `createMeasurement` từ chối khi đã có số đo ở kỳ liên quan: câu `"Trẻ đã có số đo trong kỳ «<tên kỳ>» (<điểm trường>) ngày dd/mm/yyyy. Sửa số đo đó."`.
- [x] **Bước 6: Sửa vắng:**
  - `createMeasurement` (và `saveExam` lần đầu khi Q5(c)) gọi `attendanceService.findDayStatuses(List.of(childId), date, date)`.
  - Thứ 2–6 và `status.isAbsent()` ⇒ lỗi mới `ApiCode.HEALTH_CHILD_ABSENT` (số kế tiếp trong nhóm 50xx, kiểm `ApiCodeTest`), câu "Trẻ vắng ngày dd/mm/yyyy (điểm danh). Nhập khi trẻ có mặt."
  - Dòng danh sách: `absentToday` tính 1 lần cho cả lớp bằng `findDayStatuses(ids, today, today)`.
- [x] **Bước 7:** chạy test health ⇒ PASS. Ghi luật mới vào `BUSINESS_RULES.md` (HLT-09 thêm ý chuyển lớp; HLT-01 thêm ý trẻ vắng), "user chốt 2026-11-24".
- [x] **Exit B4:** verify ⇒ PASS. **Dừng chờ review.**

### Phase B5 — Phiếu khám trùng ngày đã cân đo (mục 14) — chờ Q3

Q3 = B ⇒ BE không đổi. Chỉ thêm `dateTakenBy` vào view phiếu (`HealthExamView`, tên kỳ + ngày) để FE báo ngay. 1 test `examViewReportsSameDayMeasurement`.

Q3 = A:

**Files:** C `src/main/resources/db/migration/V180__health_measurement_same_day.sql` · M `HealthExamServiceImpl.java:155-166`, `prefill` `:346-361` · M chỗ đọc chuỗi số đo theo ngày (tìm `findByChildId` / `ORDER BY date` trong `service/health`, `HealthGrowth`, `HealthMeasurementServiceImpl.findLatestMeasurement`) · Test `HealthExamIntegrationTest.java`, `HealthRecordIntegrationTest.java`

- [x] **Bước 1: Viết test hỏng:**
  - `examSavesOnDayWithRoundMeasurement`: trẻ có số đo kỳ cân đo hôm nay ⇒ lưu nháp phiếu khám đợt khác cùng ngày được.
  - `examPrefillsHeightWeightFromSameDayMeasurement`.
  - `growthSeriesKeepsOnePointPerDayPreferringExam`: 2 dòng cùng ngày ⇒ chuỗi có 1 điểm, giá trị của phiếu khám.
- [x] **Bước 2:** chạy ⇒ FAIL.
- [x] **Bước 3:** migration bỏ `uk_health_measurements_child_date`. Ràng buộc `(child_id, campaign_id)` giữ.
- [x] **Bước 4: Sửa code:**
  - `HealthExamServiceImpl` bỏ lỗi ô `date` khi dòng trùng ngày là kỳ cân đo khác. Giữ lỗi khi trùng ngày với **đợt khám** khác.
  - `prefill` lấy chiều cao, cân nặng của lần đo cùng ngày.
  - Chỗ đọc chuỗi: cùng ngày ⇒ ưu tiên dòng có `exam_status`, rồi `id` lớn hơn.
  - `HealthMeasurementServiceImpl.createMeasurement:140`: giữ chặn khi trùng ngày với kỳ cân đo khác, bỏ chặn khi dòng trùng là phiếu khám.
- [x] **Bước 5:** chạy ⇒ PASS. Sửa HLT-01, HLT-08 trong `BUSINESS_RULES.md`.
- [x] **Exit B5:** verify ⇒ PASS. **Dừng chờ review.**

### Phase B6 — Tài liệu, contract, kiểm App

- [x] `docs/modules/child.md`, `docs/modules/health.md`: thêm luật mục 9, 11, 13, 14.
- [x] Grep `carenest_app` các path `placement-classes`, `duplicate-check`, `health-checks/.*/classes` ⇒ ghi kết quả (dự kiến không dùng).
- [ ] FE đồng bộ contract: `node scripts/api-index.mjs --from ../be-school-year-scope` (memory `fe-api-index-from-worktree`) — làm ở plan FE.
- [x] Cập nhật `TEST-tac-dong-Can-2026-11-23.md`: B6, B7, C0, C5, F5, H1 + ca mới cho mục 11 (kỳ chuyển điểm trường), mục 14.

## Test matrix

| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-4 | CHD-01 | `ExcelZeroFixTest` (unit), preview ở `ChildEnrollmentIntegrationTest` (IT) | B3 | PASS: `ExcelZeroFixTest#phone`, `#citizenId` (CCCD 9 số giữ nguyên), `#previewRestoresZeros` |
| AC-5 | G94-180/CHD-04 | `ChildEnrollmentIntegrationTest#duplicateOfLeftChildReturnsCampus` (IT) | B1 | PASS: `#duplicateOfLeftChildReturnsCampus`, `#duplicateOfWithdrawnTransferredChildReturnsLastClassCampus` |
| AC-6 | G94-180/CHD-09 | `ChildLeftSchoolIntegrationTest` 2 test (IT) | B1 | PASS: `ChildPlacementListIntegrationTest#readmittedChildDoesNotShowClosedClassOfYear`, `#pendingChildKeepsPlannedClassOfNextYear` |
| AC-7 | G94-180/CHD-08 | verify (Flyway + seed) + truy vấn DB | B1 | PASS: `DevSeedIntegrationTest#everyProfileHasGestationWeeks` + verify full; DB mới `carenest_scope4` (v180): `gestation_weeks IS NULL` = 0/223 |
| AC-8 | G94-180/CHD-14 | `SchoolDayMessagesTest` (unit), test cũ sửa câu (IT) | B1 | PASS: `SchoolDayMessagesTest` (6 test), `ChildRecordServiceImplTest#withdrawValidatesLeaveDateAndReason`, test cũ sửa câu |
| AC-9 | G94-180/CHD-03 | `ChildRecordIntegrationTest` 5 test (IT) | B1 | PASS: `ChildRecordIntegrationTest#withdrawRejectsPastDate`, `#withdrawRejectsBeyondNextSchoolDay`, `#withdrawFromNextSchoolDayKeepsTodayRoster`, `#withdrawOnFridayAllowsNextSchoolDayAcrossWeekendAndHoliday`, `#leaveOptionsBlockTodayAfterAttendance`; `ScheduledLeaveJobIntegrationTest` 4 test (00:05 ngày nghỉ, chạy bù 3 ngày sau, bỏ qua ngày nghỉ quá 7 ngày, chạy lúc khởi động); `ChildRecordServiceImplTest#withdrawFromNextSchoolDay*`; `WithdrawNewYearBoardIntegrationTest` 2 test LEAVE |
| AC-10 | G94-180/CHD-14 | `ChildPlacementScheduleIntegrationTest#placementClassesCountScheduledArrivals` (IT) | B2 | PASS: `#placementClassesCountScheduledArrivals` |
| AC-11 | HLT-09, HLT-13 | `HealthCheckIntegrationTest`, `HealthRecordIntegrationTest` 5 test (IT) | B4 | PASS: `HealthCheckIntegrationTest#transferWithinRoundMovesEditingToNewClass`, `#transferAcrossCampusesShowsMeasurementFromLinkedRound`, `#newClassTeacherEditsMovedMeasurement`, `#oldClassTeacherCannotEditMovedMeasurement`, `#secondMeasurementInLinkedRoundRejected`, `#scheduledTransferKeepsOldClassUntilEffectiveDate`, `#substituteOfNewClassEditsMovedMeasurement`; `HealthExamIntegrationTest#examOfTransferredChildFollowsCurrentClass`, `#savingExamOfTransferredChildKeepsClassWhereTaken`, `#examRowOfChildWhoLeftOrChangedCampusIsReadOnly` |
| AC-13 | HLT-01 | `HealthRecordIntegrationTest` 3–4 test (IT) | B4 | PASS: `HealthCheckIntegrationTest#absentChildCannotBeMeasuredOnSchoolDay`, `#weekendMeasurementAllowedWithoutAttendance`, `#unmarkedAttendanceAllowsMeasurement`; `HealthExamIntegrationTest#absentChildCannotStartExam` |
| AC-14 | HLT-08 | `HealthExamIntegrationTest` 3 test (IT) | B5 | PASS: `HealthExamIntegrationTest#examSavesOnDayWithRoundMeasurement`, `#examPrefillsHeightWeightFromSameDayMeasurement`, `#newExamOnDayOfAnotherExamAsksLeaders`; `HealthRecordIntegrationTest#growthSeriesKeepsOnePointPerDayPreferringExam`, `#databaseKeepsOneRoundMeasurementPerDay`; `HealthGrowthReportIntegrationTest#sameDayExamIsCountedInsteadOfMeasurement` |

## Review Focus

Các tình huống spec không nói rõ, dễ gây lỗi cho người dùng. Mỗi dòng đã có test ở task sở hữu.

1. Trẻ có **lịch chuyển lớp** (hẹn ngày) trong kỳ cân đo: lớp hiện tại tính theo `referenceDate`, nên tới ngày chuyển mới đổi lớp sở hữu. Test thêm ở B4 Bước 1: `scheduledTransferKeepsOldClassUntilEffectiveDate`.
2. Cho nghỉ học thứ 6, ngày học kế tiếp qua **ngày nghỉ của trường** (không chỉ cuối tuần): test ở B1.5 dùng ngày nghỉ thử.
3. Excel `.xlsx` có ô SĐT kiểu số: `DataFormatter` trả "916231201" ⇒ vẫn bù. Test preview xlsx ở B3.
4. Trẻ chờ xếp lớp do **mở lại hồ sơ vào năm sau** (CHD-11): danh sách năm sau không được mất lớp dự kiến. Test ở B1.1.
5. GV **dạy thay** lớp mới sửa số đo của trẻ chuyển đến: `teaches` đã nhận dạy thay (F4). Test ở B4 với GV dạy thay.

## Rủi ro & rollback

| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Đổi truy vấn danh sách (B1.1) làm mất lớp hiển thị chỗ khác | Danh sách trẻ, Xếp lớp | Chỉ thêm điều kiện cho `PENDING_PLACEMENT`; test B2 cũ + 2 test mới | Revert commit B1.1 |
| Job 0h khóa tài khoản (B1.5) chạy trùng ở nhiều instance | Khóa 2 lần (vô hại) | Hàm khóa idempotent, dùng khóa job nếu có | Tắt job, quay về publish ngay |
| Bỏ ràng buộc ngày (B5, Q3=A) làm chuỗi tăng trưởng có 2 điểm/ngày | Biểu đồ, báo cáo | Sửa mọi chỗ đọc chuỗi, test 1 điểm/ngày | Migration ngược tạo lại ràng buộc (chỉ khi chưa có dữ liệu trùng) |
| Đọc điểm danh trong health làm chậm danh sách lớp | Trang kỳ cân đo | 1 truy vấn mỗi lớp | Bỏ `absentToday` ở danh sách, giữ chặn khi lưu |
| Nhánh Cần đổi lại `ChildRecordServiceImpl` hoặc số migration | Xung đột merge | Merge Cần trước khi bắt đầu mỗi phase | Theo memory merge-conflict-prefer-incoming |

## Validation log

- [x] Khẳng định về code có `file:line` (bảng Context, Files từng task).
- [x] Đổi contract ⇒ consumer cụ thể:
  - `DuplicateCheckResponse.campusId` → FE `EnrollChildPage` (F2.2).
  - `GET /children/{id}/leave-options` (mới) → FE `WithdrawChildModal` (F2.4).
  - `placement-classes` → FE `ClassPlacementFields`, `ClassPlacementPage` (F3.1).
  - Dòng kỳ cân đo → FE `MeasurementRoundRoster` (F4.1).
- [x] Mỗi AC có test; mỗi phase có Exit = verify.
- [x] >8 file và >3 phase: đã chia 6 phase, mỗi phase một commit nhóm; có thể tách B4–B5 (sức khỏe) thành plan riêng nếu user muốn.
- [x] Không dùng rule PENDING/OPEN như đã chốt: Q1–Q5 còn mở, plan ghi rõ phụ thuộc.
- [x] Tự review lần 2 sau khi user trả lời Q1–Q5.

## Progress log

### 2026-11-24 — Lập plan (chờ user trả lời Q1–Q5)
- Commit: chưa commit
- Verify: chưa chạy (chưa sửa code)
- Điều tra: đọc 19 ảnh, DB `carenest_scope2` (HS26041, HS26066, HS26140, lớp Mầm 1), code BE/FE nêu ở bảng Context
- Tiếp: user trả lời Q1–Q5 → `approved` → Phase B1

### 2026-11-24 — Làm xong B1–B6 (BE)
- Commit theo task (nhánh `fix/G94-180-BE-FIX-10-school-year-scope`, chưa push):
  - B1.1 `c09efe73` danh sách trẻ bỏ lớp đã đóng của trẻ chờ xếp lớp
  - B1.2 `9c2b9033` + `cb6e2386` duplicate-check trả `campusId`, test
  - B1.3 `ec465085` seed tuần thai
  - B1.4 `147ac502` `SchoolDayMessages`
  - B1.5 `4b869ed9` nghỉ học hôm nay/ngày học kế tiếp, `55e8c24a` `GET /children/{id}/leave-options`, `41f316cf` LEAVE trên bảng năm mới, `4c8882a7` `ScheduledLeaveJob` 00:05, `4258bb83` test từng luật ngày nghỉ
  - B2 `f872c899` `placement-classes` `from`/`childId`/`occupiedCount`
  - B3 `fbf40eba` `ExcelZeroFix` + note xem trước
  - B4 `925910be` roster chuyển lớp/kỳ nối, `4a3af2b2` chặn trẻ vắng (5042), `82027a54` docs luật (HLT-01, HLT-09)
  - B5 `185b3d97` phiếu khám cùng ngày số đo (V180), `6edfaa6e` 1 điểm tăng trưởng mỗi ngày, `fa6a28fd` docs luật
  - B6 tài liệu module/contract/flow/data dictionary + kết quả plan (2 commit cuối)
- Verify: full tại `ec465085`: 2353 test, 0 fail, 14 error Edu nanoTime flake (không phải regression). Full tại `6edfaa6e`: 2397 test, 0 fail, 14 error Edu flake, 3 skip. B6 (docs + 1 comment): `check-ai-layer` 0 lỗi; `verify --quick` tự nâng lên full ("không map được test theo tên"): 2397 test, 0 fail, 14 error Edu flake, 3 skip (VERIFY FAIL chỉ vì flake đã biết).
- Khác plan:
  - Migration là `V180` (không phải V178; V178/V179 đã bị nhánh khác dùng): bỏ UNIQUE `(child_id, date)`, thêm 2 UNIQUE có điều kiện `uk_health_measurements_child_date_round` / `_exam`.
  - Thêm endpoint `GET /children/{id}/leave-options` (không có trong plan) cho hộp Cho nghỉ học.
  - ~~`ScheduledLeaveJob` chỉ chạy theo cron 00:05, không chạy lúc khởi động.~~ Đổi ở review cuối (Ruling 14, mục dưới).
  - Trẻ nghỉ từ ngày học kế tiếp hiện trạng thái LEAVE trên bảng xếp lớp năm mới (plan không nêu).
  - Chỉ nối các kỳ cân đo (MEASUREMENT) giữa điểm trường; đợt khám không nối.
  - Báo cáo tăng trưởng tính lớp tại lúc đo.
  - `findLatestMeasurement` không đổi.
- App: grep `carenest_app` (dart) không có `placement-classes`, `duplicate-check`, `leave-options`, `withdraw`; `/health/measurements` và `health-checks` chỉ xuất hiện trong plan/tài liệu (`docs/plans/active/2026-11-15-teacher-api-integration.md`, `2026-11-16-app-be-integration.md`, `docs/integration/BE_API_INDEX.md`), không có code dart gọi ⇒ chưa ảnh hưởng. Khi App làm màn nhập số đo của GV phải đọc `canEdit`, `movedTo`, `measuredIn`, `absentToday`, mã 5042.
- Còn lại: FE đồng bộ contract (`api-index --from ../be-school-year-scope`) ở plan FE; file test tay `TEST-tac-dong-Can-2026-11-23.md` đã cập nhật (B6, B7, C0, C5, F5, H1, ca mới F5b, F5c).

### 2026-11-24 — Review cuối BE và vòng sửa
- Review cuối toàn nhánh (`81539e50..dd1b7ae6`): cần sửa job khóa phụ huynh không chạy bù, `ExcelZeroFix` đệm CCCD 9 số, cùng các mục nhỏ (quyền sửa dòng phiếu khám, lớp của phiếu khám khi lưu lại, 2 test nằm trong file test của Cần, CHD-03, comment bị ngắt dòng).
- Commit vòng sửa (chưa push):
  - `34583e61` `ExcelZeroFix` chỉ đệm CCCD 10–11 số, AC-4
  - `294c1e2a` `ScheduledLeaveJob` xét ngày nghỉ trong 7 ngày, chạy 00:05 và một lần lúc khởi động
  - `027cfb8c` đợt khám: dòng của trẻ đã nghỉ học / sang điểm trường khác ở lớp cũ `canEdit = false`
  - `cfff3853` phiếu khám ở lại lớp lúc khám khi GV lớp mới lưu tiếp (`classroom_id` ghi ở lần lưu đầu)
  - `af0ead52` 2 test LEAVE chuyển sang `WithdrawNewYearBoardIntegrationTest`; `YearTransitionIntegrationTest` về đúng bản trước `41f316cf`
  - `da556934` CHD-03 ghi LEAVE trên bảng năm mới và job chạy bù
  - `435e6c21` gộp comment bị formatter ngắt dòng, gộp 2 khối Javadoc chồng
  - commit cuối: ghi kết quả vào plan, chuyển plan sang `completed/`
- Verify: các lớp test phủ (ScheduledLeaveJob, ExcelZeroFix, HealthExam/HealthCheck/HealthRecord/HealthGrowthReport, WithdrawNewYearBoard, YearTransition, ChildRecord IT và unit) chạy chung tại `435e6c21`: 134 test, 0 fail. `check-ai-layer` 0 lỗi. Full tại `435e6c21`: `VERIFY FAIL full | tests 2402 fail 0 err 14 skip 3 | archunit ok | snapshot ok | spotless - | 1278s` — 14 error đều là `EduYearPlanServiceIntegrationTest` (`varchar(20)`, flake nanoTime đã biết), 3 skip là test AI chạy thật (`DishSimilarityLiveEvalTest`, `MenuAiLiveEvalTest`, `PeriodCommentLiveEvalTest`); spotless không chạy trong lần đó vì build dừng ở test ⇒ chạy riêng `spotless:check` đạt.
- Kiểm vận hành:
  - AC-7 trên DB mới `carenest_scope4`: Flyway lên v180 (193 migration), `child_profiles.gestation_weeks IS NULL` = 0/223, có 2 index của V180 ⇒ đạt. `carenest_scope2` mang số migration cũ (trước khi gộp nhánh Cần 23/11) nên code này không khởi động được trên DB đó (Flyway validate) — có từ trước, không do plan này.
  - Nhập CSV `TEST-nhap-tre-2027-2028.csv` (bản có dấu cách) trên `carenest_scope4`: `POST /api/children/enrollments/import/preview` ⇒ 2 dòng, 0 lỗi ⇒ đạt.
- Khác plan:
  - Ruling 14: `ScheduledLeaveJob` xét trẻ `LEFT` có `left_on` từ hôm nay − 7 tới hôm nay, chạy 00:05 và một lần lúc khởi động (`ApplicationReadyEvent`); chạy lặp vô hại (listener bỏ tài khoản đã khóa, còn con đang học). Chấp nhận: tài khoản HT/PHT mở tay trong 7 ngày đó mà phụ huynh không còn con đang học bị khóa lại. Đổi tên: `lockParentsOfChildrenWhoLeft`, thêm `catchUpOnStartup`, truy vấn `ChildRepository.findIdsLeftBetween` (thay `findIdsLeavingOn`).
  - Ruling 15: `ExcelZeroFix` chỉ đệm CCCD 10–11 số (mã tỉnh 001–096 đứng đầu nên Excel chỉ bỏ 1–2 số 0); CCCD 9 số giữ nguyên, báo lỗi như trước. AC-4 đã sửa theo.
- Còn lại cho user: thứ tự gộp với nhánh Cần (V175–V177; V180 của nhánh này với V178/V179 của Cần); báo Cần `findLatestMeasurement` cùng ngày chưa ưu tiên phiếu khám và `savePlacements` cho đổi tay LEAVE thành PROMOTE.
