# Module — school-structure

Feature: `organization` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: đang làm (bảng scope)

## Mục đích
Mô hình tổ chức 1 trường / nhiều campus / lớp theo năm học, và phân công nhân sự (nguồn scope nhân sự).

## Owns
School (duy nhất — chưa cần bảng vì chỉ 1 trường), Campus, SchoolYear, AgeGroup, Classroom (+ageGroupId), StaffAssignment (userId, staffRole, campusId hoặc classroomId hoặc ageGroupId, validFrom, validTo).

## KHÔNG owns
UserAccount/role (identity-access), trẻ (child).

## Rules
AUTH-01..03, AUTH-05, CFG-YR-01..03. ADR-0002 (campus scoping), ADR-0003.

## Phụ thuộc
- Dùng: common/utils. Tham chiếu userId bằng ID, không import identity.
- Được dùng bởi: education-plan, child, attendance, nutrition, health, learning-observation, facility-issue, reporting, kiểm tra scope (`security/`).

## API & bảng
- Bảng: `attendance_cutoffs` (V14), `rooms` (V12), `campuses`, `school_years`, `classrooms`, `staff_assignments` (`V2__create_school_structure.sql`), `age_groups` + cột nhóm tuổi của lớp/phân công Tổ trưởng (`V7__create_age_groups.sql`); xem `docs/database/DATA_DICTIONARY.md`.
- Entity hiện có: `Classroom`, `StaffAssignment`, `SchoolYear`, `AgeGroup` (+ `Campus` V11, `Room` V12); enum `StaffRole` ở `utils/enums/` (ADR-0014).
- Service cho kiểm tra scope: `StaffAssignmentService` (`hasActivePrincipalAssignment`, `hasActiveCampusAssignment`, `hasActiveTeacherAssignment` — lọc theo ngày, tính cả hai đầu), `ClassroomService.findCampusId`. Không gọi ngược `security/authorization`.
- API năm học: `/school-years` (danh sách, xem, tạo, sửa, xóa, `/{id}/activate`, `/{id}/close`) — `SchoolYearController` + `SchoolYearService`; vòng đời `PLANNED → ACTIVE → CLOSED`, tối đa một năm ACTIVE (`V10__add_school_year_lifecycle.sql`). Ghi chỉ Hiệu trưởng có phân công còn hiệu lực; HT/PHT/GV được xem.
- API điểm trường: `/campuses` (danh sách kèm số lớp/trẻ/PHT của năm học, chi tiết, tạo, sửa, xóa) — `CampusController` + `CampusService`; entity `Campus` (V11). HT ghi; PHT chỉ xem điểm trường được phân công. Số trẻ và họ tên nhân sự đọc bằng native query chỉ đọc qua bảng `enrollments`, `user_accounts` (không gọi service child/identity để giữ school-structure ở tầng L0).
- API nhóm tuổi `/age-groups`, lớp `/classes`, cấu trúc năm học `/school-structure`, sao chép lớp `/school-years/{id}/classes/copy` (`AgeGroupController`, `ClassController`, `AgeGroupService`, `ClassManagementService`) và phòng `/locations` (`RoomController`, `RoomService`, entity `Room`, V12–V13). Quyền dùng chung `SchoolConfigAccess`.
- API giờ chốt điểm danh `/school-years/{id}/cutoff` (GET mọi nhân sự, PUT Hiệu trưởng) — `AttendanceCutoffController`, `AttendanceCutoffService.findCutoffTime(schoolYearId, date)` cho module điểm danh/suất ăn đọc (V14).
- API phân công GV `/teacher-assignments`, `PUT /classes/{id}/teachers` (đúng 2 GV, 1 chủ nhiệm) và tổ trưởng `/team-leader-assignments` (1 người/nhóm tuổi/năm, 1 GV tối đa 1 nhóm) — `TeacherAssignmentController`, `TeacherAssignmentService` (V15). Danh sách GV lấy qua `TeacherDirectory`: bản tạm (phương án C) = người đang có phân công GV/tổ trưởng; thay bằng hồ sơ nhân sự khi nhóm chốt Tài khoản nhân sự.
- `YearSetupService.getYearSetup(schoolYearId)`: cấu hình năm (lớp, phòng, GV, tổ trưởng, PHT, giờ chốt) cho module child đọc khi xếp lớp/kích hoạt. `POST /school-years/{id}/activate` chuyển sang `YearTransitionController` (child).
- Chưa có API phân công PHT (dev dùng seed `db/dev/R__dev_seed.sql`).

## PENDING
P-06 (bếp theo campus), P-14 (Hiệu phó).

## Known pitfalls
- Không biến School thành tenant: không thêm `school_id` vào mọi bảng; chiều phân vùng là `campus_id`.
- Assignment có hiệu lực theo thời gian: luôn lọc theo ngày khi resolve scope.
- Kết thúc phân công bằng `valid_to`, không xóa dòng (giữ lịch sử ai từng có quyền).
- Campus assignment phải lọc theo `staff_role`: bếp và HP cùng campus là hai phạm vi khác nhau.
- Ghi dữ liệu gắn năm/lớp/ngày: gọi `SchoolYearService#checkYearOpen` / `checkClassYearOpen` / `checkDateYearOpen` (CFG-YR-01), không tự viết lại kiểm tra năm đã kết thúc.

## Related issues
Chưa có.

## Đọc thêm khi
Thay đổi cấu trúc campus/lớp ảnh hưởng báo cáo ⇒ `docs/system/MODULE_MAP.md` (L3).
