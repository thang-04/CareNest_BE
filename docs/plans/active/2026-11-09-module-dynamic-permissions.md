---
title: Phân quyền động cho cấu hình trường, điểm danh và đánh giá trẻ
status: in-progress
owner: cannd
jira: G94-60 / BE-FEAT-01; G94-209 / FE-FEAT-53
branch: feature/G94-60-BE-FEAT-01-authentication
modules: [identity-access, school-structure, attendance, learning-observation]
rules: [AUTH-07, AUTH-10, AUTH-11, AUTH-13, AUTH-20, SCH-05, ATT-08, NUT-02, OBS-19, OBS-20]
risk: high
created: 2026-11-09
updated: 2026-11-09
---
# Phân quyền động cho ba module nghiệp vụ

## Context

Hệ phân quyền đã có nhưng nhiều controller của attendance/observation/organization vẫn dùng `hasRole` trực tiếp. `Permission.java` chưa có mã cho các việc này; FE route/menu cũng chủ yếu theo role. User yêu cầu HT cấp quyền gì thì PHT/GV/tổ trưởng/bếp mới được làm việc đó.

## Quyết định đã chốt

1. Permission quyết định được làm gì; policy quyết định được làm ở đâu — AUTH-13, user 09/11/2026.
2. HT có quyền nghiệp vụ cố định toàn trường và là người duy nhất quản lý quyền — AUTH-11.
3. PHT/GV/tổ trưởng/bếp chỉ làm việc có trong effective permissions; đúng role không đủ — user 09/11/2026.
4. Cấu hình thời gian/năm học/campus/nhóm tuổi-lớp và duyệt khen thưởng cuối cùng giữ cố định HT.
5. Dữ liệu cũ backfill thành role grant để không gián đoạn; từ đó HT thu hồi/cấp lại qua ma trận.
6. Không sửa màn/logic Vai trò & quyền, Tài khoản nhân sự, phân công PHT hoặc module của thành viên khác; catalog hiện có tự đọc mã mới từ BE — user 09/11/2026.

## Làm rõ nghiệp vụ

- **Hiểu nghiệp vụ:** HT cấu hình quyền; actor thực hiện trong scope · AUTH-07/10/11/13/20 CONFIRMED · `[B] HT cấp → [S] request tính quyền → [S] kiểm scope → [S] thực hiện/403`.
- **Đối chiếu:** code hiện dùng role cứng ở AttendanceController, AssessmentDayUnlockController, DailyAssessmentController, RoomController; yêu cầu mới đưa các việc có thể giao vào catalog.
- **Ảnh hưởng:** identity-access + ba module; FE route `/school/rooms`, `/attendance/*`, `/assessment/*`; APP ngoài phạm vi; DB chỉ thêm migration seed code.

### Câu hỏi mở

Không còn câu hỏi chặn. User duyệt triển khai ngày 09/11/2026.

7. Các quyền nghiệp vụ gắn campus mặc định áp dụng tại campus được phân công; nếu HT cấp override `SCHOOL` thì PHT được xem/thao tác toàn trường đối với đúng mã quyền đó. Quyền theo lớp của GV/tổ trưởng vẫn lấy từ phân công lớp/nhóm tuổi.

## Acceptance criteria

- **AC-1** (`AUTH-10`, `ATT-08`): thu hồi/cấp ATTENDANCE_UNLOCK có hiệu lực request kế tiếp; PHT mặc định campus mình và chỉ vượt campus khi quyền đó có override `SCHOOL`.
- **AC-2** (`AUTH-10`, `AUTH-13`): ATTENDANCE_RECORD/DAILY_HEALTH_RECORD/PICKUP_RECORD và ASSESSMENT_RECORD chỉ cho actor có quyền và đúng lớp.
- **AC-3** (`NUT-02`): xác nhận/điều chỉnh suất thay thế dùng MEAL_COUNT_CONFIRM và scope campus.
- **AC-4** (`OBS-19`, `OBS-20`): ASSESSMENT_UNLOCK/CLASS_REVIEW/REWARD_REVIEW độc lập; mặc định campus, hỗ trợ override `SCHOOL` riêng từng mã.
- **AC-5** (`SCH-05`, `AUTH-11`): cấu hình cố định HT và PERMISSION_MANAGE không giao được.
- **AC-6** (`AUTH-07`): FE ẩn menu/nút theo quyền/capability; BE vẫn trả 403 nếu gọi trực tiếp.
- **AC-7** (`AUTH-10`): migration giữ hành vi cũ qua role grant, không còn dựa role cứng ở endpoint ghi trong scope.
- **AC-8** (`AUTH-13`): ROOM_MANAGE và STAFF_ASSIGNMENT_MANAGE áp đúng CAMPUS/SCHOOL; phân công tổ trưởng toàn trường vẫn chỉ HT vì là một vị trí chung theo nhóm tuổi/năm học.

## Key decisions

| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Độ chi tiết | Một mã cho từng việc nghiệp vụ | Một mã MANAGE/module | HT cấp đúng việc, đúng yêu cầu UI hiện tại |
| Scope | Mã có `campusScoped=true`; policy đọc override CAMPUS/SCHOOL | Cố định PHT ở campus | HT có thể giao phạm vi động mà vẫn dùng một nguồn scope |
| Chuyển dữ liệu | Backfill role grant | Mặc định tắt toàn bộ | Không khóa vận hành ngay sau deploy; HT vẫn thu hồi được |

## Phases

### Phase 1 — BE catalog, guard và scope
- Files: M `Permission.java`, controllers/services/policies liên quan · C migration · M/C tests.
- Tests: AC-1..5,7 → controller security, service permission/scope tests.
- Exit: `node scripts/verify.mjs` PASS, skip 0.
- **Dừng chờ user review.**

### Phase 2 — FE menu, route và action trong ba module
- Files: M `bePermissions`, `AppRoutes`, `menuConfig`, utilities/pages/tests thuộc school/attendance/assessment. Không sửa trang/component/service của trung tâm phân quyền hoặc tài khoản nhân sự.
- Tests: AC-6 và hồi quy role/scope.
- Exit: `node scripts/verify.mjs` PASS.
- **Dừng chờ user review.**

## Test matrix

| AC | Rule | Test | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1, AC-2 | AUTH-10, AUTH-13, ATT-08 | attendance permission/scope tests | 1 | — |
| AC-3 | NUT-02 | MealCount security/service tests | 1 | — |
| AC-4 | OBS-19, OBS-20 | observation permission/scope tests | 1 | — |
| AC-5, AC-7, AC-8 | AUTH-11, SCH-05, AUTH-10, AUTH-13 | permission schema/security coverage + teacher assignment scope | 1 | — |
| AC-6 | AUTH-07 | FE route/menu/action integration tests | 2 | — |

## Rủi ro & rollback

| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Bỏ sót guard | Leo quyền | inventory controller + coverage test | thu hồi role grant bằng migration mới |
| Permission có nhưng ngoài scope | Ghi sai campus/lớp | test permission × scope ở service | giữ policy cũ, revert guard đổi |
| FE/BE lệch catalog | menu sai | catalog BE là nguồn, test mã FE | FE vẫn xử lý 403 |

## Validation log

- [x] Claim code có đường dẫn/line trong audit trước plan.
- [x] Contract consumer cụ thể đã liệt kê trong `contracts.md`.
- [x] Mỗi AC có test và phase có Exit.
- [x] Tách 2 phase BE/FE.
- [x] Chỉ dùng rule CONFIRMED.
- [x] Không placeholder, không câu hỏi mở.

## Progress log

### 2026-11-09 — Plan approved, bắt đầu Phase 1
- Commit: chưa commit.
- Verify: chưa chạy.
- Khác plan: không.
- Tiếp: thêm catalog/migration và chuyển guard BE.

### 2026-11-09 — Hoàn thiện scope động trong ba phân hệ
- Commit: chưa commit.
- Đã nối `schoolWidePermissions` từ `/accounts/me` sang FE và policy CAMPUS/SCHOOL cho phòng ban, phân công giáo viên, điểm danh/mở lại, đánh giá/mở lại/xét duyệt.
- Không sửa màn Vai trò & quyền, Tài khoản nhân sự, phân công PHT hoặc Kế hoạch giáo dục.
- Targeted FE: 16 test PASS. Targeted BE integration đang được chạy lại sau khi chuẩn hóa fixture scope SCHOOL.

### 2026-11-10 — Rà 3 phần, sửa phạm vi và luật Cần chốt
- Commit: chưa commit. Kéo origin/dev 0b17e54 (fast-forward).
- Rà toàn bộ endpoint Cấu hình trường, Điểm danh & suất ăn, Đánh giá trẻ: guard theo mã quyền + phạm vi CAMPUS/SCHOOL. FE: `inBePermissionScope` cho PHT nhiều cơ sở; Sổ suất ăn BGH luôn xem được, nút xác nhận theo quyền.
- Thêm theo Cần chốt 10/11: SCH-04 đúng 2 GV năm đang học; dạy thay chọn người được thay (V85); HT điều chỉnh suất thay thế.
- Verify: BE full PASS 1224 test; FE full PASS.
- Còn mở: StaffAccessPolicy chưa đọc override SCHOOL (chờ Cần); đánh lại số migration trước PR (trùng V82–V84 nhánh G94-180).
