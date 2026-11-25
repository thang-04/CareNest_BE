---
title: Tiếp nhận theo năm học — điểm trường của năm, tuổi tối thiểu, xếp lớp trước kích hoạt
status: done         # xong 2026-11-22: Phase 1–3; verify full chỉ lỗi ở test hẹn giờ của dev (MenuAiDraftIntegrationTest, dev sửa ở 29c45ef0), xem Progress log
owner: Giáp
jira: G94-180 / BE-FIX-10 (Phase 2: G94-433)
branch: fix/G94-180-BE-FIX-10-school-year-scope   # tạo từ feature/G94-180-BE-FEAT-43-new-year-intake @3f0f9a25 (PR #84 chưa vào dev)
modules: [child]
rules: [G94-180/CHD-02, G94-180/CHD-11, SCH-01]
risk: medium         # thêm lỗi 2125 và lỗi tuổi tối thiểu ở tiếp nhận, nhập Excel, tiếp nhận lại; thêm campusId (tùy chọn) cho tiếp nhận lại; không migration
created: 2026-11-19
updated: 2026-11-22
---
# Tiếp nhận theo năm học — BE

Plan này làm các mục 1, 2, 5 trong bản phân tích 22/11 (code G94-180 lệch mô hình năm học của Cần). Mục 4, 6: `2026-11-22-health-year-scope.md`. Phần Web: `FE:docs/plans/completed/2026-11-19-intake-campus-in-year.md`.

## Context
- **Mục 1:** kích hoạt năm bị chặn khi còn trẻ đã tiếp nhận chưa xếp lớp (`YearTransitionServiceImpl.java:723`, Cần chốt 19/11, BUG-43). Câu CHD-11 (`docs/business/BUSINESS_RULES.md:409`) vẫn ghi trẻ chưa có lớp thành "Chờ xếp lớp" khi kích hoạt. Nhánh PENDING ở `YearTransitionServiceImpl.java:502` không còn chạy tới.
- **Mục 2:** tiếp nhận không chọn lớp chỉ kiểm điểm trường đang hoạt động (`ChildIntakeServiceImpl.java:669`) và phạm vi (`:672-674`). Không kiểm danh sách điểm trường của năm tiếp nhận (V151, V154 của Cần).
  - Tiếp nhận lại (`readmit`, `:228`) dùng điểm trường cũ.
  - Form và nhập Excel cùng đi qua `prepareEnrollment` (`:289`) → `placeOf` (`:623`).
  - Chiều ngược lại (gỡ điểm trường khỏi năm khi năm còn trẻ PENDING/PLACED) Cần đã chặn: `CampusRepository.java:255-259`.
- **Mục 5 / BUG-52 (G94-433):** `EnrollmentFormValidator.java:149-153` chỉ chặn ngày sinh ở tương lai và trước 2018-01-01. Validator dùng chung cho form (`ChildIntakeServiceImpl.java:294`) và nhập Excel (`ChildEnrollmentImportServiceImpl.java:104`).

## Quyết định đã chốt
1. Theo luật của Cần: trẻ đã tiếp nhận phải được xếp lớp trước khi kích hoạt năm; không xếp được thì "Cho nghỉ học" để hủy đăng ký. Sửa câu CHD-11 cho khớp, không đổi code Cần — user 22/11.
2. Điểm trường tiếp nhận (form, nhập Excel, tiếp nhận lại; năm đang hoạt động và năm sau) phải có trong danh sách điểm trường của năm tiếp nhận ⇒ lỗi 2125 ở ô `campusId` — user 22/11 (Q2).
3. Tiếp nhận lại thêm `campusId` tùy chọn; để trống thì giữ điểm trường cũ — user 22/11 (Q1 = b).
4. Trẻ vẫn được vào lớp khác nhóm tuổi. Tiếp nhận không thêm kiểm nhóm tuổi — nghiệp vụ chốt, user báo 22/11.
5. BUG-52: chỉ tiếp nhận trẻ **đủ 3 tháng tuổi**, tính tháng tròn từ ngày sinh tới ngày tiếp nhận (ngày lưu hồ sơ). Áp dụng form và nhập Excel, kể cả tiếp nhận cho năm sau. Không thêm giới hạn trên; giữ chặn ngày sinh trước 2018-01-01 — nghiệp vụ chốt, user báo 22/11.
6. Không sửa code của Cần (`organization/**`, `YearTransitionServiceImpl`, migration và seed của Cần). Việc cần Cần làm ghi ở mục "Ngoài plan".

## Làm rõ nghiệp vụ
- Actor: HT, PHT. Luồng `[S]`. Rule: G94-180/CHD-02 (`BUSINESS_RULES.md:400`), CHD-11 (`:409`), SCH-01 (`:49`). Thêm G94-180/CHD-13 (tuổi khi tiếp nhận, lớp khác nhóm tuổi).
- Ảnh hưởng: module child. Web `/children/enroll` (tiếp nhận, tiếp nhận lại), hộp nhập Excel. APP không gọi.

### Câu hỏi mở
Không còn.

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| Q1 Tiếp nhận lại khi điểm trường cũ ngoài năm | Thêm `campusId` tùy chọn | user | 22/11 | CHD-11 (Phase 3) |
| Q2 Chặn cả năm đang hoạt động | Có | user | 22/11 | CHD-02 (Phase 3) |
| Q3 Tiếp nhận trước, gỡ điểm trường sau | Cần đã chặn ở `yearBlockers` | Cần (dc22263e) | 19/11 | — |
| Q4 Nhánh | Nhánh fix mới, tạo từ new-year-intake | user | 22/11 | — |
| Lớp khác nhóm tuổi | Được | nghiệp vụ, user báo | 22/11 | CHD-13 (Phase 2) |
| Tuổi tối thiểu | Đủ 3 tháng tại ngày tiếp nhận | nghiệp vụ, user báo | 22/11 | CHD-13 (Phase 2) |

## Acceptance criteria
- **AC-1** (CHD-02): Given năm 2027-2028 có danh sách riêng chỉ gồm Cơ sở A, đã mở chuẩn bị · When HT tiếp nhận bằng form, không chọn lớp, vào Cơ sở B · Then 400 mã 2125, lỗi ô `campusId` "Điểm trường không có trong năm học đã chọn. Chọn điểm trường khác"; không tạo hồ sơ, không tạo `planned_admissions`.
- **AC-2** (CHD-02): như AC-1 qua nhập Excel ⇒ cả lô lỗi, không tạo hồ sơ nào.
- **AC-3** (CHD-02): năm đang hoạt động có danh sách riêng chỉ gồm A ⇒ tiếp nhận vào B lỗi 2125.
- **AC-4** (CHD-02): năm chưa có danh sách riêng ⇒ nhận như cũ.
- **AC-5** (CHD-11): tiếp nhận lại vào năm sau, điểm trường cũ không có trong năm:
  - không gửi `campusId` ⇒ 2125, trẻ vẫn LEFT;
  - gửi `campusId` = A ⇒ trẻ "Chờ xếp lớp" ở A, đăng ký năm sau ở A.
- **AC-6** (CHD-13): hôm nay 22/11/2026, tiếp nhận (form hoặc Excel) trẻ sinh 23/08/2026 ⇒ lỗi ô `dateOfBirth` "Trẻ phải đủ 3 tháng tuổi tại ngày tiếp nhận"; trẻ sinh 22/08/2026 ⇒ nhận.
- **AC-7** (CHD-13): tiếp nhận năm sau, chọn lớp dự kiến khác nhóm tuổi của trẻ ⇒ vẫn nhận.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Nguồn danh sách điểm trường của năm | `CampusService.getCampuses(actor, yearId)` (public, của Cần, chỉ gọi) | truy vấn thẳng `school_year_campus_members` | Không sửa module Cần; đúng chiều phụ thuộc child → organization |
| Kiểu lỗi điểm trường | 2125 `CAMPUS_NOT_IN_YEAR` kèm lỗi ô `campusId` | ApiCode mới | Web hiện dưới ô; không đụng `ApiCode.java` |
| Chỗ kiểm tuổi tối thiểu | `EnrollmentFormValidator` (một chỗ cho form và Excel) | kiểm trong `placeOf` | Cùng chỗ với các kiểm ngày sinh hiện có |
| Jira | G94-180 cho điểm trường và tài liệu; G94-433 cho tuổi tối thiểu | tạo task Jira mới | Cùng luồng G94-180 đang làm |
| Nhánh | `fix/G94-180-BE-FIX-10-school-year-scope` từ new-year-intake @3f0f9a25 | thêm vào nhánh PR #84; đợi #84 rồi tạo từ dev | Không làm PR #84 to thêm; mở PR sau khi #84 vào dev |

## Phases
### Phase 1 — Điểm trường của năm tiếp nhận (G94-180)
- Files: M `ChildIntakeServiceImpl.java` (`placeOf` :663-676, `readmit` :228) · M `dto/child/ReadmitChildRequest.java` (thêm `Long campusId`, giữ constructor cũ) · M `docs/api/openapi.yaml` (snapshot) · Test M `ChildEnrollmentIntegrationTest.java`.
- Steps:
  1. Viết test (fail) theo AC-1..AC-5. Helper chèn `school_year_campus_members` cho năm test.
  2. Thêm `requireCampusInYear(actor, campusId, yearId)`: điểm trường không có trong `getCampuses(actor, yearId)` ⇒ 2125 ô `campusId`. Gọi sau kiểm phạm vi (`:672-674`).
  3. `readmit`: `campusId` trống ⇒ điểm trường cũ; có giá trị ⇒ cùng kiểm trạng thái, phạm vi như `placeOf`, rồi gọi helper trước `child.readmit(...)`.
  4. Cập nhật snapshot OpenAPI (`-Dopenapi.snapshot.update=true`, chạy lại không cờ).
- Tests: `plannedIntakeRejectsCampusOutsideYear`, `importRejectsCampusOutsideYear`, `activeIntakeRejectsCampusOutsideYear`, `yearWithoutOwnListAcceptsAnyActiveCampus`, `readmitRejectsOldCampusOutsideYear`, `readmitIntoChosenCampus`. Mỗi test có `@DisplayName("G94-180/CHD-02: …")` hoặc `CHD-11`.
- Exit: `.\mvnw.cmd -q test -Dtest=ChildEnrollmentIntegrationTest,OpenApiSnapshotTest` pass.
- Commit: `G94-180: Reject intake campuses outside the school year`, `G94-180: Let readmission choose a campus of the school year`.
- **Dừng chờ user review.**

### Phase 2 — Tuổi tối thiểu khi tiếp nhận (G94-433)
- Files: M `EnrollmentFormValidator.java` (:149-153) · Test M `EnrollmentFormValidatorTest.java`, `ChildEnrollmentIntegrationTest.java` · M `docs/business/BUSINESS_RULES.md` (thêm dòng G94-180/CHD-13).
- Steps: test (fail) AC-6, AC-7; thêm kiểm `dateOfBirth.plusMonths(3).isAfter(today)` ⇒ lỗi ô `dateOfBirth`; thêm CHD-13 ("đủ 3 tháng tuổi tại ngày tiếp nhận, tính tháng tròn từ ngày sinh; trẻ được xếp lớp khác nhóm tuổi, nhóm tuổi chỉ để gợi ý").
- Exit: `.\mvnw.cmd -q test -Dtest=EnrollmentFormValidatorTest,ChildEnrollmentIntegrationTest` pass.
- Commit: `G94-433: Require children to be at least 3 months old at intake`.
- **Dừng chờ user review.**

### Phase 3 — Tài liệu và verify (G94-180)
- Files: M `BUSINESS_RULES.md` (CHD-02 thêm điểm trường theo năm; CHD-11 sửa câu kích hoạt theo BUG-43) · M `docs/contracts/ERROR_CONTRACT.md` (2125 ở tiếp nhận) · M `docs/modules/child.md`.
- Exit: `.\mvnw.cmd -q clean spotless:check verify` exit 0, test skip = 0 (Docker Desktop đang chạy).
- Commit: `G94-180: Document intake rules for year campuses and activation`.

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1..4 | CHD-02 | 4 test campus trong `ChildEnrollmentIntegrationTest` (IT) | 1 | |
| AC-5 | CHD-11 | `readmitRejectsOldCampusOutsideYear`, `readmitIntoChosenCampus` (IT) | 1 | |
| AC-6 | CHD-13 | `EnrollmentFormValidatorTest` (unit) + 1 IT nhập Excel | 2 | |
| AC-7 | CHD-13 | `plannedClassOfOtherAgeGroupIsAccepted` (IT) | 2 | |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Cần chưa bỏ chặn 2416 | Trẻ có lớp dự kiến khác nhóm tuổi làm hỏng cả lần lưu ở bảng xếp lớp năm mới | Gửi tin nhắn Cần trước khi merge; Web vẫn cảnh báo vàng khi chọn lớp khác nhóm | — (việc của Cần) |
| `getCampuses` chậm khi nhập Excel lớn | Nhập 500 dòng chậm | Đo thử; cần thì cache theo lượt nhập | revert Phase 1 |
| Dữ liệu cũ đã tiếp nhận sai (ngoài năm, dưới 3 tháng) | Không tự sửa | Không đổi dữ liệu cũ; Cần vẫn chặn gỡ điểm trường còn trẻ | — |

## Ngoài plan — nhắn Cần (user gửi)
1. Xác nhận BUG-43 ("trẻ đã tiếp nhận phải xếp lớp trước khi kích hoạt") là luật chính thức; Giáp sửa CHD-11 cho khớp. Nhánh PENDING trong `applyPlannedAdmissions` (`YearTransitionServiceImpl.java:502`) không còn chạy tới, Cần dọn nếu muốn.
2. Nghiệp vụ chốt 22/11: trẻ được vào lớp khác nhóm tuổi. Phần của Cần đang chặn:
   - `savePlacements` trả 2416 khi lớp năm sau khác nhóm tuổi của trẻ mới (`YearTransitionServiceImpl.java:314-317`);
   - `NewAdmissionPlacementPanel.jsx:42-46` chỉ cho chọn lớp đúng nhóm tuổi.
   - Đề xuất: bỏ 2416 hoặc đổi thành cảnh báo; panel cho chọn mọi lớp đúng điểm trường, đánh dấu "khác nhóm tuổi". Hỏi thêm: luật có áp dụng cho trẻ lên lớp, ở lại (2415, `:286-291`) không.
3. Web đổi gợi ý nhóm tuổi sang tháng tròn tới ngày bắt đầu năm, giống `fitsAgeGroup` (`:613-624`). Cần muốn dùng chung một hàm thì tách `fitsAgeGroup` ra helper chung.

## Validation log
- [x] Khẳng định về code có `file:line` (đọc trên `be-new-year-intake` @3f0f9a25).
- [x] Đổi contract ⇒ consumer cụ thể: Web `EnrollChildPage` (tiếp nhận, tiếp nhận lại), `ImportChildrenModal`. APP không gọi.
- [x] Mỗi AC có test; mỗi phase có Exit = lệnh + kết quả.
- [x] 3 phase, 8 file. Không cần tách thêm (mục 4, 6 đã tách sang plan sức khỏe).
- [x] Không dùng rule PENDING/OPEN như đã chốt.
- [x] Không placeholder, không mâu thuẫn.

## Progress log
### 2026-11-19 — plan draft
- Commit: chưa commit. Chờ Q1–Q4.
### 2026-11-22 — cập nhật theo quyết định user
- Q1–Q4 trả lời; thêm mục 1 (CHD-11 theo BUG-43), mục 5 (lớp khác nhóm tuổi được, tuổi tối thiểu 3 tháng, G94-433). Bỏ phần cảnh báo "tiếp nhận trước, gỡ sau" vì Cần đã chặn.
- Tiếp: user duyệt plan ⇒ `status: approved`, tạo nhánh, làm Phase 1.
### 2026-11-22 — duyệt
- User duyệt plan và yêu cầu làm hết các phase liên tục (không dừng giữa phase). Nhánh tạo trong worktree riêng.
### 2026-11-22 — Phase 1 done
- Commit: `3752c667` (tiếp nhận, nhập Excel), `0cf1c716` (tiếp nhận lại có `campusId`).
- Verify: `ChildEnrollmentIntegrationTest` 48/48, `ChildRecordIntegrationTest` 10/10, `GuardianDirectoryIntegrationTest` 2/2, `OpenApiSnapshotTest` 4/4.
- Khác plan: `getCampuses` trả rỗng cho năm chưa có dòng `school_year_campus_members` (không phải cả danh mục) ⇒ helper coi danh sách rỗng là không chặn (AC-4).
### 2026-11-22 — Phase 2 done
- Commit: `d9d15d07` (G94-433). Thêm dòng G94-180/CHD-13 vào `BUSINESS_RULES.md` ngay trong commit này.
- Verify: `ChildEnrollmentControllerTest` 77/77, `ChildEnrollmentIntegrationTest` 50/50, `ChildEnrollmentImportMealColumnTest` 5/5, `EnrollmentFormValidatorTest` 11/11.
### 2026-11-22 — Phase 3 tài liệu
- Commit: `8ed38549` (tài liệu CHD-02, CHD-11, ERROR_CONTRACT, child.md). Verify đầy đủ chạy chung sau plan sức khỏe (cùng nhánh).
### 2026-11-22 — Phase 3 verify, đóng plan
- Verify `.\mvnw.cmd -q clean spotless:check verify` tại `0f78040a` (cùng nhánh với plan sức khỏe): Spotless sạch; 2229 test, 0 failure, 14 error, 2 skip.
  - 14 error chỉ ở `MenuAiDraftIntegrationTest` (G94-200, nhánh này không sửa file thực đơn): tuần test cố định 16/11/2026 nhưng đồng hồ test chạy theo giờ thật, qua thứ Sáu 20/11 thì `MEAL_PLAN_WEEK_PAST`. Dev đã sửa ở `29c45ef0` (21/11, sau base `3f0f9a25`). Áp tạm `29c45ef0` (không commit): 30/30 pass.
  - 2 skip là test AI chạy thật (`MenuAiLiveEvalTest`, `DishSimilarityLiveEvalTest`): chỉ chạy khi có biến môi trường `AI_LIVE_EVAL`, `AI_LIVE_EVAL_EMBEDDING`.
  - `.\mvnw.cmd -q verify -DskipTests` (đóng gói) exit 0.
- Nhánh không lấy `29c45ef0`: merge dev là có. Chưa push.
- Plan chuyển sang `docs/plans/completed/`.
