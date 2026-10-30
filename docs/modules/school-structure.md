# Module — school-structure

Feature: `organization` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: đang làm (bảng scope, cấu hình năm học — G94-226; phân công nhân sự — G94-60)

## Mục đích
Mô hình tổ chức 1 trường / nhiều campus / lớp theo năm học, và phân công nhân sự (nguồn scope nhân sự).

## Owns
School (duy nhất — chưa cần bảng vì chỉ 1 trường), Campus, SchoolYear, AgeGroup, Classroom (+ageGroupId), StaffAssignment (userId, staffRole, campusId hoặc classroomId hoặc ageGroupId, validFrom, validTo).

## KHÔNG owns
UserAccount/role (identity-access), trẻ (child).

## Rules
AUTH-01..03, AUTH-05, AUTH-15, AUTH-16, AUTH-23, CFG-YR-01..03, CFG-CP-01. ADR-0002 (campus scoping), ADR-0003, ADR-0017.

## Phụ thuộc
- Dùng: common/utils. Tham chiếu userId bằng ID, không import identity.
- Được dùng bởi: child, attendance, nutrition, health, learning-observation, education-plan (năm học, nhóm tuổi, phân công tổ trưởng), reporting, kiểm tra scope (`security/`).

## API & bảng
- Bảng: `attendance_cutoffs` (V17), `rooms` (V15), `campuses`, `school_years`, `classrooms`, `staff_assignments` (`V2__create_school_structure.sql`), `age_groups` + cột nhóm tuổi của lớp/phân công Tổ trưởng (`V10__create_age_groups.sql`); xem `docs/database/DATA_DICTIONARY.md`.
- Entity hiện có: `Classroom`, `StaffAssignment`, `SchoolYear`, `AgeGroup` (+ `Campus` V14, `Room` V15); enum `StaffRole` ở `utils/enums/` (ADR-0014).
- Service cho kiểm tra scope: `StaffAssignmentService` (`hasActivePrincipalAssignment`, `hasActiveCampusAssignment`, `hasActiveTeacherAssignment` — lọc theo ngày, tính cả hai đầu), `ClassroomService.findCampusId`. Không gọi ngược `security/authorization`.
- API năm học: `/school-years` (danh sách, xem, tạo, sửa, xóa, `/{id}/activate`, `/{id}/close`) — `SchoolYearController` + `SchoolYearService`; vòng đời `PLANNED → ACTIVE → CLOSED`, tối đa một năm ACTIVE (`V13__add_school_year_lifecycle.sql`). Ghi chỉ Hiệu trưởng có phân công còn hiệu lực; HT/PHT/GV được xem.
- API điểm trường: `/campuses` (danh sách kèm số lớp/trẻ/PHT của năm học, chi tiết, tạo, sửa, xóa) — `CampusController` + `CampusService`; entity `Campus` (V14). HT ghi; PHT chỉ xem điểm trường được phân công. Số trẻ và họ tên nhân sự đọc bằng native query chỉ đọc qua bảng `enrollments`, `user_accounts` (không gọi service child/identity để giữ school-structure ở tầng L0).
- API nhóm tuổi `/age-groups`, lớp `/classes`, cấu trúc năm học `/school-structure`, sao chép lớp `/school-years/{id}/classes/copy` (`AgeGroupController`, `ClassController`, `AgeGroupService`, `ClassManagementService`) và phòng `/locations` (`RoomController`, `RoomService`, entity `Room`, V15–V16). Quyền dùng chung `SchoolConfigAccess`.
- API giờ chốt điểm danh `/school-years/{id}/cutoff` (GET mọi nhân sự, PUT Hiệu trưởng) — `AttendanceCutoffController`, `AttendanceCutoffService.findCutoffTime(schoolYearId, date)` cho module điểm danh/suất ăn đọc (V17).
- API phân công GV `/teacher-assignments`, `PUT /classes/{id}/teachers` (đúng 2 GV, 1 chủ nhiệm) và tổ trưởng `/team-leader-assignments` (1 người/nhóm tuổi/năm, 1 GV tối đa 1 nhóm) — `TeacherAssignmentController`, `TeacherAssignmentService` (V18). Danh sách GV lấy qua `TeacherDirectory`: bản tạm (phương án C) = người đang có phân công GV/tổ trưởng; thay bằng hồ sơ nhân sự khi nhóm chốt Tài khoản nhân sự.
- `YearSetupService.getYearSetup(schoolYearId)`: cấu hình năm (lớp, phòng, GV, tổ trưởng, PHT, giờ chốt) cho module child đọc khi xếp lớp/kích hoạt. `POST /school-years/{id}/activate` chuyển sang `YearTransitionController` (child).
- API đọc cho form tài khoản/phân công: `GET /campuses/options` (tách khỏi `GET /campuses` của `CampusController`), `GET /classrooms?campusId=` (lớp của năm học đang diễn ra) — `SchoolStructureService`, lọc theo `StaffAccessPolicy`. Ghi phân công: `StaffAssignmentService.createAssignment/endAssignment` (kiểm hình dạng theo V2 CHECK, campus/lớp tồn tại, trùng phân công mở ⇒ 409), gọi từ `StaffingService` (feature account — kiểm quyền ở đó).

## PENDING
P-18 (học kỳ, trông hè), P-19 (ngày nghỉ tự khóa điểm danh). Đã đóng: P-14 (2026-10-27 — AUTH-02, AUTH-20), P-06 (2026-10-28 — mỗi cơ sở một bếp, AUTH-05). Cấu hình HT cần cho nghiệp vụ: giờ chốt theo mùa (ATT-06), số bữa theo nhóm tuổi (NUT-17), giá suất theo nhóm tuổi (NUT-19).

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
