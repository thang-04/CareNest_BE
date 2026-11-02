---
title: Sửa P1 review E2E 01/11 (phân công GV, suất ăn, điểm danh, hồ sơ lớp, user VPS)
status: in-progress
owner: team BE
jira: chưa có / BE-FIX
branch: dev
modules: [identity-access, school-structure, attendance, nutrition, education-plan]
rules: [AUTH-16, AUTH-24, SCH-04, ATT-02, ATT-09, NUT-01, NUT-02, NUT-04, EDU-18]
risk: high
created: 2026-11-01
updated: 2026-11-01
---
# Sửa P1 review E2E 01/11

Chế độ tự chạy (CLAUDE.md): plan tự viết, không chờ duyệt; giả định ghi ở Key decisions và Progress log.

## Context
Nguồn: `CareNest_CODE/plans/reports/final-261101-1407-carenest-e2e-business-flow-review-report.md` FLOW-004b, 005, 007, 008, 009, 012 và bảng "Đã chốt" D2, D3, D5, D6, D8, D10.

## Quyết định đã chốt
1. D3/AUTH-24: 1 GV dạy chính 1 lớp/năm; dạy thay miễn nhưng phải có ngày kết thúc; mọi đường phân công GV cùng luật — user 01/11.
2. D8/ATT-02: suất từ trạng thái; giữ `meals` deprecated, bỏ qua giá trị — user 01/11.
3. D2/NUT-02: bỏ sửa suất thường, giữ suất thay thế không bị điểm danh ghi đè; FE gửi `normal: null` — user 01/11.
4. D10/NUT-02: chặn xác nhận khi còn trẻ chưa điểm danh; xác nhận theo cơ sở–ngày — user 01/11.
5. D6/ATT-09: GV không gửi được `EXCUSED` — user 01/11.
6. D5b: file user VPS dùng placeholder `${KEYCLOAK_DEV_USER_PASSWORD}` — user 01/11.

## Làm rõ nghiệp vụ
- Rule đã CONFIRMED (BUSINESS_RULES 01/11). Không còn câu hỏi mở.

## Acceptance criteria
- **AC-1** (AUTH-24): GV đang dạy chính lớp khác + `/staff-assignments` không ngày kết thúc (hoặc tới hết năm) ⇒ 409 `TEACHER_ALREADY_ASSIGNED`; có ngày kết thúc trước hết năm ⇒ dạy thay, 201; PHT cơ sở A gán GV cơ sở B ⇒ 400 `TEACHER_WRONG_CAMPUS`; TK khóa ⇒ 409; năm CLOSED ⇒ 409; `assigned_by` = người làm.
- **AC-2** (ATT-02): lưu `{childId, status: PRESENT}` ⇒ mọi bữa nhóm tuổi; `meals` gửi kèm bị bỏ qua.
- **AC-3** (NUT-02): `normal` null ⇒ giữ; khác số hiện tại ⇒ 409 mã mới; đổi điểm danh giữ `substitute`.
- **AC-4** (NUT-02): còn trẻ chưa điểm danh ⇒ 409 mã mới; xác nhận một bữa ⇒ xác nhận mọi bữa chờ của cơ sở–ngày.
- **AC-5** (ATT-09): GV gửi `EXCUSED` mới ⇒ 400 mã mới; giữ nguyên bản ghi `EXCUSED` cũ được.
- **AC-6** (EDU-18): gỡ Tổ trưởng khi `PENDING_TL` ⇒ hồ sơ hiện/duyệt ở bước kế; đổi chuỗi cấu hình khi đang chờ không làm hồ sơ tự APPROVED.
- **AC-7** (D5b): VPS mount file user placeholder; local giữ file hash `demo123`.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Phân biệt dạy thay | Phân công GV có `validTo` trước ngày kết thúc năm học của lớp = dạy thay; `validTo` trống/≥ hết năm = dạy chính (cắt về hết năm) | Cột `is_substitute` + field request mới | Không đổi schema/contract; `PUT /classes/{id}/teachers` luôn đặt `validTo` = hết năm nên khớp |
| Validator chung | `TeacherClassRules` (package-private, organization/impl) dùng cho cả 2 đường | Chặn TEACHER ở `/staff-assignments` | Đúng AUTH-24 "mọi đường cùng luật"; PHT vẫn xếp được dạy thay |
| Xác nhận theo ngày | Xác nhận 1 bữa ⇒ xác nhận mọi bữa chờ cùng cơ sở–ngày; gọi lại bữa đã xác nhận ⇒ 200 trả trạng thái hiện tại | Giữ 409 `MEAL_COUNT_ALREADY_CONFIRMED` | FE `MealCountPage` lặp confirm từng bữa và còn nút xác nhận từng bữa; 409 ở lượt 2 làm vỡ vòng lặp |
| Chuỗi duyệt hồ sơ lớp | Chụp chuỗi lúc gửi vào cột `sign_chain` (V41); bước Tổ trưởng không còn người hợp lệ ⇒ trạng thái hiệu lực = bước kế (tính lúc đọc và lúc duyệt) | Tính lại theo cấu hình mỗi lần | Cấu hình thứ tự tùy ý nên không suy được; tránh tự APPROVED |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| V41 trùng số với nhánh khác | Flyway lỗi khi merge | Báo team khi merge | Đổi số file trước merge |
| Realm VPS đã import | Mật khẩu cũ còn | Owner đổi tay trong admin console | — |

## Progress log
### 2026-11-01 — triển khai (chưa commit)
- Commit: chưa commit
- Verify: xem báo cáo bàn giao
- Giả định: (1) dạy thay suy từ `validTo` < ngày cuối năm học, không thêm cột/field; (2) luật cơ sở GV dùng lại quy tắc có sẵn của PUT (cơ sở theo phân công lớp gần nhất, GV chưa dạy lớp nào xếp được mọi nơi) cho cả HT và PHT; (3) PUT `/classes/{id}/teachers` chỉ kiểm luật 1 lớp với GV mới thêm; (4) `EXCUSED` đã có được gửi lại (để đơn nghỉ sau này không làm vỡ lưu sheet); (5) xác nhận lại bữa đã xác nhận ⇒ 200 thay vì 409 `6005` (FE lặp từng bữa); (6) kiểm "chưa điểm danh" theo điểm danh sống (`tallyMeals.missing`) mọi lớp của cơ sở trong năm của sổ; (7) chuỗi chỉ còn bước Tổ trưởng mà hết Tổ trưởng ⇒ vẫn chờ (không tự duyệt); (8) chỉ áp bỏ bước khi vắng người cho bước Tổ trưởng (EDU-18), không cho PHT/HT.
- Contract: `MealAdjustRequest.normal` hết `@NotNull` (snapshot OpenAPI cập nhật có chủ đích); ApiCode mới 4018, 6008, 6009; hành vi `POST /meal-counts/{id}/confirm` đổi (cả ngày, idempotent). Migration V41.
- 2026-11-01 (user chốt "Thêm cờ dạy thay", thay giả định 1): V42 `staff_assignments.is_substitute` + CHECK (chỉ TEACHER, bắt buộc `valid_to`); `CreateStaffAssignmentRequest.substitute` (null = dạy chính), `StaffAssignmentResponse.substitute`; ApiCode 2320 `SUBSTITUTE_TEACHER_ONLY`, 2321 `SUBSTITUTE_END_DATE_REQUIRED`; `TeacherClassRules` bỏ suy từ `validTo`, dùng cờ. Snapshot OpenAPI cập nhật có chủ đích (thêm 2 field). FE chưa gọi `/staff-assignments` (màn Nhân sự demo) ⇒ không vỡ.
- 2026-11-01 user chốt giả định 5: xác nhận lại sổ đã xác nhận trả 200 + trạng thái hiện tại (idempotent), không trả 409 `6005`.
