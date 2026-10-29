# Module — school-structure

Feature: `organization` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: đang làm (bảng scope)

## Mục đích
Mô hình tổ chức 1 trường / nhiều campus / lớp theo năm học, và phân công nhân sự (nguồn scope nhân sự).

## Owns
School (duy nhất — chưa cần bảng vì chỉ 1 trường), Campus, SchoolYear, Classroom, StaffAssignment (userId, staffRole, campusId hoặc classroomId, validFrom, validTo).

## KHÔNG owns
UserAccount/role (identity-access), trẻ (child).

## Rules
AUTH-01..03, AUTH-05, AUTH-15, AUTH-16. ADR-0002 (campus scoping), ADR-0003, ADR-0017.

## Phụ thuộc
- Dùng: common/utils. Tham chiếu userId bằng ID, không import identity.
- Được dùng bởi: child, attendance, nutrition, health, learning-observation, education-plan (năm học, nhóm tuổi, phân công tổ trưởng), reporting, kiểm tra scope (`security/`).

## API & bảng
- Bảng: `campuses`, `school_years`, `classrooms`, `staff_assignments` (`V2__create_school_structure.sql`, `docs/database/DATA_DICTIONARY.md`).
- Entity hiện có: `Classroom`, `StaffAssignment` (Campus/SchoolYear chưa cần entity); enum `StaffRole` ở `utils/enums/` (ADR-0014).
- Service cho kiểm tra scope: `StaffAssignmentService` (`hasActivePrincipalAssignment`, `hasActiveCampusAssignment`, `hasActiveTeacherAssignment` — lọc theo ngày, tính cả hai đầu), `ClassroomService.findCampusId`. Không gọi ngược `security/authorization`.
- API đọc: `GET /campuses`, `GET /classrooms?campusId=` (lớp của năm học đang diễn ra) — `SchoolStructureService`, lọc theo `StaffAccessPolicy`. Ghi phân công: `StaffAssignmentService.createAssignment/endAssignment` (kiểm hình dạng theo V2 CHECK, campus/lớp tồn tại, trùng phân công mở ⇒ 409), gọi từ `StaffingService` (feature account — kiểm quyền ở đó). Chưa có API tạo/sửa campus/lớp/năm học (dev dùng seed `db/dev/R__dev_seed.sql`).

## PENDING
P-18 (học kỳ, trông hè), P-19 (ngày nghỉ tự khóa điểm danh). Đã đóng: P-14 (2026-10-27 — AUTH-02, AUTH-20), P-06 (2026-10-28 — mỗi cơ sở một bếp, AUTH-05). Cấu hình HT cần cho nghiệp vụ: giờ chốt theo mùa (ATT-06), số bữa theo nhóm tuổi (NUT-17), giá suất theo nhóm tuổi (NUT-19).

## Known pitfalls
- Không biến School thành tenant: không thêm `school_id` vào mọi bảng; chiều phân vùng là `campus_id`.
- Assignment có hiệu lực theo thời gian: luôn lọc theo ngày khi resolve scope.
- Kết thúc phân công bằng `valid_to`, không xóa dòng (giữ lịch sử ai từng có quyền).
- Campus assignment phải lọc theo `staff_role`: bếp và HP cùng campus là hai phạm vi khác nhau.

## Related issues
Chưa có.

## Đọc thêm khi
Thay đổi cấu trúc campus/lớp ảnh hưởng báo cáo ⇒ `docs/system/MODULE_MAP.md` (L3).
