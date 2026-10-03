# Module — school-structure

Feature: `organization` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: thiết kế

## Mục đích
Mô hình tổ chức 1 trường / nhiều campus / lớp theo năm học, và phân công nhân sự (nguồn scope nhân sự).

## Owns
School (duy nhất), Campus, SchoolYear, Classroom, StaffAssignment (userId, scopeType SCHOOL|CAMPUS|CLASS, scopeId, from, to).

## KHÔNG owns
UserAccount/role (identity-access), trẻ (child).

## Rules
AUTH-01..03, AUTH-05. ADR-0002 (campus scoping), ADR-0003.

## Phụ thuộc
- Dùng: common/utils. Tham chiếu userId bằng ID, không import identity.
- Được dùng bởi: child, attendance, nutrition, health, learning-observation, facility-issue, reporting, kiểm tra scope (`security/`).

## API & bảng
Chưa có. Query cần thiết: `campusOf(classroomId)`, `classroomsOf(campusId)`, `activeAssignments(userId, date)`.

## PENDING
P-06 (bếp theo campus), P-14 (Hiệu phó).

## Known pitfalls
- Không biến School thành tenant: không thêm `school_id` vào mọi bảng; chiều phân vùng là `campus_id`.
- Assignment có hiệu lực theo thời gian: luôn lọc theo ngày khi resolve scope.

## Related issues
Chưa có.

## Đọc thêm khi
Thay đổi cấu trúc campus/lớp ảnh hưởng báo cáo ⇒ `docs/system/MODULE_MAP.md` (L3).
