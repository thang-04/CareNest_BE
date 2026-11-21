---
title: Cơ sở công tác khi thêm tài khoản nhân sự (G94-412)
status: in-progress
owner: Thắng
jira: G94-412 (subtask của G94-305)
branch: fix/G94-305-BE-FIX-03-school-config-bugs
created: 2026-11-21
---
# Cơ sở công tác khi thêm tài khoản nhân sự (G94-412)

## Context
Bug BUG-38 (Vy): form **Thêm tài khoản nhân sự** chưa có trường cơ sở. Hiện trạng:
- Form đã có "Điểm trường (*)" cho PHT, bếp và khi người tạo là PHT (FE `StaffAccountsBeView.jsx`, từ 31/10).
- Thiếu đúng một trường hợp: **HT tạo Giáo viên**. Theo AUTH-15 (CONFIRMED 01/11) GV chưa dạy lớp nào thì chưa thuộc điểm trường nào; điểm trường của GV suy từ lớp được phân công (`TeacherDirectoryImpl.findTeachers`), không lưu riêng.
- Người dùng chốt 21/11/2026: làm **hướng a**, lưu cơ sở công tác riêng cho GV, bắt buộc với mọi vai trò.

## Quyết định đã chốt
- Hướng a (user 21/11/2026): mọi vai trò nhân sự có cơ sở công tác ngay khi tạo, hiển thị ở danh sách/chi tiết/sửa.
- Xếp GV vào lớp cơ sở khác: cho phép kèm xác nhận, tự chuyển cơ sở công tác theo lớp (Q1=b).
- Nhập Excel bắt buộc cột cơ sở công tác cho GV (Q2).
- Được sửa phần school-config của Cần liên quan điểm trường GV (Q3).

## Làm rõ nghiệp vụ
Đổi rule AUTH-15 ("GV chưa dạy lớp chưa thuộc điểm trường nào") ⇒ phải cập nhật AUTH-15 sau khi duyệt.

Đề xuất quy tắc:
1. **PHT, bếp:** cơ sở công tác = điểm trường phân công (như hiện tại), không lưu thêm giá trị thứ hai.
2. **Giáo viên (kể cả tổ trưởng):** lưu cơ sở công tác riêng, bắt buộc khi tạo. Danh sách cơ sở chọn = điểm trường đang hoạt động trong phạm vi người tạo (HT: mọi cơ sở; PHT: cơ sở mình / cơ sở HT giao).
3. GV thuộc cơ sở công tác của mình: PHT thấy/quản GV theo cơ sở công tác (thay cho "khi GV có lớp trong campus mình").
4. Xếp GV vào lớp ở cơ sở khác cơ sở công tác ⇒ xem Câu hỏi mở Q1.
5. GV cũ chưa có cơ sở công tác: migration điền theo lớp đang dạy gần nhất; GV chưa có lớp nào để trống, form sửa bắt buộc chọn.

### Câu hỏi mở
- [x] Q1 (user 21/11: **b**). Xếp GV vào lớp ở cơ sở khác cơ sở công tác: (a) chặn, phải đổi cơ sở công tác trước; (b) **cho phép kèm xác nhận, tự chuyển cơ sở công tác theo lớp** (đề xuất); (c) cho phép, giữ nguyên cơ sở công tác.
- [x] Q2 (user 21/11: **có**). Nhập Excel tài khoản nhân sự có bắt buộc cột cơ sở công tác cho GV không? (Đề xuất: có, thiếu thì báo lỗi dòng.)
- [x] Q3 (user 21/11: cho sửa). Phần đổi cách suy điểm trường GV (`TeacherDirectoryImpl`, `TeacherClassRules`, `TeacherAssignmentServiceImpl`) thuộc module school-config của **Cần** ⇒ anh xác nhận cho sửa hoặc báo Cần.

## Acceptance criteria
- AC-1: Given HT mở Thêm tài khoản, vai trò Giáo viên, When chưa chọn cơ sở, Then hiện "Vui lòng chọn cơ sở công tác" và không tạo được.
- AC-2: Given tạo GV chọn Cơ sở 2, When tạo xong, Then danh sách, chi tiết, form sửa hiện Cơ sở 2 ngay (chưa cần lớp).
- AC-3: Given PHT Cơ sở 1, When tạo GV, Then chỉ chọn được cơ sở trong phạm vi mình; gửi cơ sở khác ⇒ máy chủ từ chối.
- AC-4: Given tạo PHT/bếp, Then cơ sở công tác = điểm trường phân công, không có hai giá trị.
- AC-5: Given GV cơ sở 2, When xếp vào lớp cơ sở 1, Then theo Q1.
- AC-6: Given PHT Cơ sở 2, Then thấy/sửa hồ sơ GV có cơ sở công tác Cơ sở 2 kể cả GV chưa có lớp.

## Key decisions
- **Khác plan (21/11, khi code):** lưu ở bảng mới `teacher_work_campuses` (module tổ chức, V168) thay cho cột `user_accounts.work_campus_id`. Lý do: Q1=b cần `TeacherAssignmentServiceImpl` (organization) tự chuyển cơ sở công tác, mà MODULE_MAP cấm organization gọi account; bảng thuộc organization thì account gọi qua `StaffAssignmentService` đúng chiều.
- Nơi lưu (bản đầu, đã thay): cột `user_accounts.work_campus_id` (FK `campuses`, chỉ dùng cho GV). Không dùng dòng `staff_assignments` TEACHER không lớp, vì CHECK `ck_staff_assignments_scope` (V10) bắt GV phải có lớp và nhiều truy vấn coi dòng TEACHER là phân công lớp ⇒ rủi ro cao hơn.

## Phases
### Phase 1 — BE
- Migration V168: thêm `user_accounts.work_campus_id` + backfill theo lớp gần nhất.
- `CreateAccountRequest`/`StaffAssignmentScopeRequest`: GV bắt buộc `campusId` (validation trả lỗi gắn ô `campusId`); lưu vào tài khoản.
- Sửa hồ sơ/đổi vai trò nhân sự nhận cơ sở công tác cho GV.
- Danh sách/chi tiết nhân sự trả `workCampusId`.
- `TeacherDirectoryImpl`: điểm trường GV = cơ sở công tác (Q3); `StaffAccessPolicy.campusesOf`: GV theo cơ sở công tác.
- Xếp lớp khác cơ sở: theo Q1.
### Phase 2 — FE
- `StaffAccountsBeView.jsx`: trường "Cơ sở công tác (*)" luôn hiện trong Thông tin cá nhân, mọi vai trò; câu lỗi AC-1; hiện ở danh sách/chi tiết/sửa.
- Excel nhập/xuất: cột cơ sở công tác (Q2).
- Phân công GV: hộp xác nhận khi khác cơ sở (Q1).

## Test matrix
- BE: tạo GV thiếu cơ sở ⇒ 400; PHT tạo GV cơ sở ngoài phạm vi ⇒ 403; tạo GV lưu cơ sở; PHT quản GV chưa có lớp cùng cơ sở; xếp lớp khác cơ sở theo Q1; migration backfill (Testcontainers, cần Docker).
- FE: validate form, hiển thị danh sách/chi tiết/sửa, xác nhận khi khác cơ sở.

## Rủi ro & rollback
- Đổi cách suy điểm trường GV ảnh hưởng mọi màn lọc GV theo cơ sở (phân công GV, danh sách nhân sự). Rollback: bỏ cột, quay lại suy từ lớp.
- Backfill sai với GV dạy thay ở cơ sở khác: chỉ lấy phân công chính (không dạy thay).

## Validation log
- AUTH-15 hiện ghi GV chưa có lớp không thuộc điểm trường nào (`docs/business/BUSINESS_RULES.md:29`) ⇒ plan đổi rule, chờ duyệt.
- Điểm trường GV suy từ lớp gần nhất: `TeacherDirectoryImpl.java:40-52`.
- CHECK GV phải có lớp: `V10__create_age_groups.sql:43`.

## Progress log
### 2026-11-21 — Duyệt
Thắng duyệt plan, chốt Q1=b, Q2=có, Q3=cho sửa. Bắt đầu Phase 1.

### 2026-11-21 — Phase 1 BE xong code
- V168 `teacher_work_campuses` + backfill; `StaffAssignmentService` thêm lưu/xóa/tra cơ sở công tác.
- Tạo GV / đổi sang GV / gộp phụ huynh làm GV: bắt buộc `assignment.campusId` (lỗi ô `campusId`), lưu cơ sở công tác; đổi khỏi GV hoặc thu hồi vai trò ⇒ xóa.
- `PATCH /accounts/{id}/profile` nhận `workCampusId` (chỉ GV, kiểm phạm vi như tạo GV; GV chưa có phải chọn; mã 1147).
- `GET /accounts`, `/accounts/{id}`, đổi vai trò trả `workCampusId`; PHT thấy/quản GV theo cơ sở công tác (`StaffAccessPolicy.campusesOf`, danh sách tài khoản).
- Danh bạ GV ưu tiên cơ sở công tác; `PUT /classes/{id}/teachers` chuyển cơ sở công tác của GV dạy chính theo lớp.
- Chưa kiểm: migration V168 trên Postgres thật (Docker tắt), `moveWorkCampus` chưa có unit test riêng (service không có test unit, chỉ integration cần Docker).
- 22/11 Thắng chốt: chặn đổi cơ sở công tác khi GV đang dạy lớp ở cơ sở khác (409 1148).
- Còn: Phase 2 FE; nhập Excel (Q2) là FE gọi `POST /accounts` từng dòng nên BE đã bắt buộc sẵn.

### 2026-11-22 — Phase 2 FE xong code
- Form Thêm tài khoản: ô "Cơ sở công tác (*)" trong Thông tin cá nhân, mọi vai trò (HT tạo GV cũng bắt buộc); chỉ điểm trường đang hoạt động, PHT chỉ thấy điểm trường mình phụ trách.
- Form Sửa thông tin: GV có ô cơ sở công tác (GV cũ chưa có phải chọn); lỗi 409 1148 hiện trên form.
- Danh sách / chi tiết: cột và dòng "Cơ sở công tác", lấy `workCampusId` trước rồi tới phân công.
- Nhập Excel: GV thiếu cột "Điểm trường" ⇒ lỗi dòng "Vui lòng chọn cơ sở công tác" (bỏ ngoại lệ HT được để trống).
- Phân công GV một lớp (`ClassTeachersModal`): chọn GV thuộc cơ sở khác ⇒ hộp xác nhận "Xếp và chuyển cơ sở".
- Kiểm: FE 9 nhóm test pass (hardening 131), `npm run check` exit 0. Chưa mở được màn trên trình duyệt (BE cần Postgres/Docker).

