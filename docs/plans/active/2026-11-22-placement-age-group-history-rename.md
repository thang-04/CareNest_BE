---
title: Xếp lớp khác nhóm tuổi, lịch sử "Báo ăn", đổi tên "Khai báo ăn và sức khỏe" (đợt 1)
status: in-progress
owner: ATPX48-69
jira: G94-180 / BE-FIX-10
branch: fix/G94-180-BE-FIX-10-school-year-scope
modules: [child, school-structure, learning-observation]
rules: [G94-180/CHD-13, CHD-13, CHD-06, CFG-YR-04]
risk: medium
created: 2026-11-22
updated: 2026-11-22
---
# Xếp lớp khác nhóm tuổi, lịch sử "Báo ăn", đổi tên "Khai báo ăn và sức khỏe" (đợt 1)

Đợt 1 trong 2 đợt (user test tới B5 ngày 22/11 rồi yêu cầu 5 mục). Đợt 2 (chọn năm, ngày bắt đầu đi học): `2026-11-22-intake-start-date.md`. Plan FE: `FE:docs/plans/active/2026-11-22-placement-age-group-history-rename.md`.

## Context
- Code chặn xếp lớp khác nhóm tuổi (Cần, G94-236 "Standardize yearly age group placement"). Tài liệu thì cho phép: G94-180/CHD-13 (`docs/business/BUSINESS_RULES.md:413`, CONFIRMED 22/11) ghi "được xếp vào lớp khác nhóm tuổi … chỉ để gợi ý, cảnh báo". Các chỗ chặn:
  - `PlacementRules.classProblem(actor, classroom, today, dob)` gọi `ageGroupProblem` (`PlacementRules.java:64-86`). Tiếp nhận năm đang học gọi ở `ChildIntakeServiceImpl.java:667`; xếp lớp và chuyển lớp gọi ở `ChildRecordServiceImpl.java:357`.
  - Tiếp nhận năm sau gọi `ageGroupProblem` (`ChildIntakeServiceImpl.java:669`).
  - Chuyển lớp chỉ cùng nhóm (`ChildRecordServiceImpl.java:358-363`).
  - Bảng xếp lớp năm mới của Cần: trẻ mới tiếp nhận vào lớp khác nhóm ⇒ 2416 (`YearTransitionServiceImpl.java:318-324`, `ApiCode.java:351`).
  - Test `ChildEnrollmentIntegrationTest#plannedClassOfOtherAgeGroupIsAccepted` (`:1660`) đỏ từ lúc gộp nhánh Cần.
- Lịch sử hồ sơ đã có: `child_history` (V63) ghi thay đổi từng trường (`ChildRecordServiceImpl.java:739-741`) và trả trong `ChildViewResponse.history`. Riêng đổi "Báo ăn" khi sửa khai báo (`ChildRecordServiceImpl.java:239`) không được ghi.
- Câu người dùng thấy có chữ "khai báo sức khỏe": `ChildRecordServiceImpl.java:275`, `:354`; `EnrollmentFormValidator.java:290`; `ChildProfileChangeRequestServiceImpl.java:103`.

## Quyết định đã chốt
1. 2A: tiếp nhận (năm đang học, năm sau), xếp lớp, chuyển lớp đều được chọn lớp khác nhóm tuổi; nhóm tuổi chỉ gợi ý, cảnh báo ở FE. Bỏ chặn 2416 cho trẻ mới trên bảng của Cần; giữ 2407 (lên lớp/ở lại) — user 22/11.
2. Hệ quả user chấp nhận: trẻ chuyển sang lớp khác nhóm giữa năm thì phiếu cuối năm, đánh giá chủ đề không lấy kết quả lớp cũ (`ThemeEvaluationServiceImpl.java:281-286`, `YearEndSheetServiceImpl.java:329`, Cần chốt 04/11) — user 22/11.
3. 3A: đổi "Báo ăn" khi sửa khai báo ⇒ ghi `child_history` (UPDATED, trường `mealRegistered`, nhãn "Báo ăn", Có/Không); không migration — user 22/11.
4. 4A: đổi "khai báo sức khỏe" ⇒ "khai báo ăn và sức khỏe" ở câu người dùng thấy của module child. Giữ nhãn quyền `Permission.java:32`, App, đường dẫn API, tên bảng — user 22/11.

## Làm rõ nghiệp vụ
- **Hiểu nghiệp vụ:** HT, PHT theo điểm trường · xếp được lớp bất kỳ nhóm tuổi của năm · G94-180/CHD-13 (CONFIRMED), CHD-13 Báo ăn (CONFIRMED), CHD-06 khai báo trước khi xếp (giữ) · flow `[B]` tiếp nhận/xếp lớp → `[S]` lưu enrollment hoặc đăng ký.
- **Đối chiếu thiết kế ban đầu:** tài liệu cho phép khác nhóm (`BUSINESS_RULES.md:413`); code Cần chặn ⇒ sửa code theo tài liệu.
- **Ảnh hưởng:** module child (của user, dòng do Cần viết), school-structure (bảng xếp lớp năm mới của Cần), learning-observation (hệ quả ở quyết định 2) · FE: `/children/new`, `/children/placement`, `/children/:id`, `/school/placement` · APP: không · dữ liệu cũ: không đổi.

### Câu hỏi mở
| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| Tiếp nhận, xếp lớp, chuyển lớp vào lớp khác nhóm tuổi? | 2A: được, chỉ cảnh báo; bỏ 2416 trẻ mới; giữ 2407 | user | 2026-11-22 | G94-180/CHD-13 (thêm "chuyển lớp", hệ quả đánh giá) — phase 1 |
| Lịch sử sửa hồ sơ | 3A: tab ở FE; BE ghi thêm đổi "Báo ăn" | user | 2026-11-22 | `docs/modules/child.md` — phase 2 |
| Đổi tên | 4A: câu người dùng thấy của module child | user | 2026-11-22 | `docs/modules/child.md` — phase 2 |

## Acceptance criteria
- **AC-1** (`G94-180/CHD-13`): Given trẻ 41 tháng tại 01/09/2026, When HT tiếp nhận vào lớp Chồi (MG 4–5) năm 2026-2027, Then lưu được, trẻ vào lớp Chồi.
- **AC-2** (`G94-180/CHD-13`): Given năm sau đã tới ngày chuẩn bị, When tiếp nhận năm sau vào lớp khác nhóm, Then lưu đăng ký PLACED.
- **AC-3** (`G94-180/CHD-13`): Given trẻ đang học Nhà trẻ 2, When chuyển sang Mầm 2 (khác nhóm, có lý do, ngày học, chưa có dữ liệu trong ngày), Then chuyển được và có dòng lịch sử xếp lớp; các luật còn lại giữ nguyên.
- **AC-4** (`CFG-YR-04`, `G94-180/CHD-13`): Given trẻ mới năm sau PLACED ở lớp khác nhóm, When HT lưu bảng xếp lớp năm mới, Then không lỗi 2416; lựa chọn lên lớp/ở lại sai nhóm vẫn lỗi 2407.
- **AC-5** (`CHD-13`): Given trẻ "Báo ăn" = Có, When sửa khai báo thành Không, Then `history` có dòng UPDATED "Báo ăn: Có → Không" kèm người sửa, thời điểm; không đổi cờ ⇒ không thêm dòng.
- **AC-6** (`CHD-06`): Given trẻ chưa khai báo, When xếp lớp, Then lỗi ô lớp "Trẻ chưa có khai báo ăn và sức khỏe. Khai báo trước khi xếp lớp."; form thiếu khai báo ⇒ "Khai báo ăn và sức khỏe còn thiếu".

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Bỏ chặn nhóm tuổi | Bỏ lời gọi ở 3 chỗ, bỏ kiểm cùng nhóm, bỏ 2416 trẻ mới; xóa `ageGroupProblem` và test của nó (`PlacementRulesTest.java:39-46`) khi không còn nơi gọi | Giữ hàm, trả cảnh báo trong response | FE đã gợi ý, cảnh báo bằng `suggestAgeGroupId`; không thêm contract |
| Mã 2416 | Giữ hằng trong `ApiCode` | Xóa | Không đổi bảng mã lỗi |
| Ghi đổi "Báo ăn" | `child_history` UPDATED | Thêm cột vào lịch sử khai báo | Không migration; tab lịch sử hồ sơ đọc sẵn |

## Phases
### Phase 1 — Bỏ chặn nhóm tuổi (2A)
- Files: M `service/child/impl/PlacementRules.java` · M `ChildIntakeServiceImpl.java` · M `ChildRecordServiceImpl.java` · M `YearTransitionServiceImpl.java` · M test `PlacementRulesTest.java`, `YearTransitionIntegrationTest.java` (`:400-407` đổi kỳ vọng), `ChildEnrollmentIntegrationTest.java` (thêm AC-1, AC-3) · M `docs/business/BUSINESS_RULES.md:413`
- Tests: AC-1 `#otherAgeGroupClassAcceptedAtIntake` · AC-2 `#plannedClassOfOtherAgeGroupIsAccepted` · AC-3 `#transferToOtherAgeGroupIsAccepted` · AC-4 `YearTransitionIntegrationTest#newAdmissionInOtherAgeGroupIsSaved`
- Exit: `./mvnw verify` (Docker bật) ⇒ hết error `plannedClassOfOtherAgeGroupIsAccepted`; ngoài 5 failure Edu có sẵn trên nhánh Cần (đo 22/11 trên `c1dd9512`) không có failure mới.
- **Dừng chờ user review.**

### Phase 2 — Lịch sử "Báo ăn" (3A), đổi tên (4A)
- Files: M `ChildRecordServiceImpl.java` (ghi history; câu `:275`, `:354`) · M `EnrollmentFormValidator.java:290` · M `ChildProfileChangeRequestServiceImpl.java:103` · M test có câu cũ (`ChildRecordServiceImplTest`, `ChildEnrollmentControllerTest`, `ChildPhotoControllerTest`) · M `docs/modules/child.md`
- Tests: AC-5 `ChildRecordServiceImplTest#mealRegistrationChangeIsRecordedInHistory` (+ ca không đổi) · AC-6 test câu lỗi
- Exit: như phase 1.
- **Dừng chờ user review.**

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1 | G94-180/CHD-13 | integration | 1 | |
| AC-2 | G94-180/CHD-13 | integration | 1 | |
| AC-3 | G94-180/CHD-13 | integration | 1 | |
| AC-4 | CFG-YR-04 | integration | 1 | |
| AC-5 | CHD-13 | unit | 2 | |
| AC-6 | CHD-06 | unit/controller | 2 | |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Sửa dòng code của Cần | Gộp nhánh Cần sau này đưa chặn trở lại | Báo Cần trước PR; ghi rule; test AC-1..4 giữ luật | Revert commit phase 1 |
| Trẻ chuyển khác nhóm mất kết quả lớp cũ trên phiếu cuối năm | Phiếu thiếu kết quả | User chấp nhận (quyết định 2); ghi rule | — |
| 5 failure Edu có sẵn | Che failure mới | So danh sách failure trước/sau | — |

## Validation log
- [x] Khẳng định về code có `file:line`
- [x] Không đổi contract (không thêm field, giữ mã lỗi)
- [x] Mỗi AC có test; mỗi phase có Exit = lệnh + kết quả mong đợi
- [x] >8 file hoặc >3 phase ⇒ đã tách thành 2 đợt (plan đợt 2 riêng)
- [x] Không dùng rule PENDING/OPEN như đã chốt
- [x] Tự review: không placeholder · không mâu thuẫn · scope vừa 1 plan

## Progress log
### 2026-11-22 — draft, chờ user duyệt
- Commit: chưa commit
- Tiếp: user duyệt ⇒ `approved`, làm phase 1.

### 2026-11-22 — Phase 1 tạm hoãn; Phase 2 (3A, 4A): chờ review
- User 22/11: phần chọn lớp khác nhóm tuổi đụng code Cần (Cần có thể đang sửa) ⇒ **Phase 1 (2A) tạm hoãn**; làm các việc không liên quan.
- Commit: chưa commit.
- RED trước: `ChildRecordIntegrationTest#mealRegistrationChangeIsRecordedInChildHistory` (expected 1, was 0); 4 test câu chữ (`EnrollmentFormValidatorTest#missingDeclarationUsesMealAndHealthName`, `ChildRecordServiceImplTest#placeValidatesFields`, `#saveDeclarationNotifiesTbDateChange`, `ChildProfileChangeRequestServiceImplTest#declarationRequestNotificationUsesMealAndHealthName`).
- Sau sửa: 4 lớp test trên pass. `./mvnw verify` (23:41 min) ⇒ `Tests run: 2260, Failures: 5, Errors: 1, Skipped: 2`, BUILD FAILURE do lỗi có sẵn: 5 failure Edu (`EduYearPlanSchemaIntegrationTest`, `EduCurriculumServiceIntegrationTest` ×3, `EduYearPlanServiceIntegrationTest`) và error `ChildEnrollmentIntegrationTest#plannedClassOfOtherAgeGroupIsAccepted` (luật nhóm tuổi của Cần, chờ phase 1). Không có failure mới (trước: 2257 test, cùng 5 failure + 1 error).
- Khác plan: câu "sửa khai báo sức khỏe" ở `ChildProfileChangeRequestServiceImpl.java:103` là dòng của thangnd (chỉ đổi chữ).
- Tiếp: user review; phase 1 chờ Cần.
