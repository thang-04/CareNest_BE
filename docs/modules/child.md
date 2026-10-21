# Module — child

Feature: `child` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: đang làm (bảng scope)

## Mục đích
Hồ sơ gốc của trẻ: thông tin cơ bản, lớp đang học theo thời gian, phụ huynh, dị ứng.

## Owns
Child, Enrollment (childId, classroomId, from, to), GuardianChildLink (childId, userId, relation), ChildAllergy/basic info.

## KHÔNG owns
Điểm danh, sức khỏe định kỳ, quan sát, hồ sơ phát triển (read model ở learning-observation).

## Rules
AUTH-04, HLT-06, NUT-09.

## Phụ thuộc
- Dùng: common/utils, service `organization` (classroom/campus).
- Được dùng bởi: attendance, nutrition (dị ứng tổng hợp), health, learning-observation, reporting, kiểm tra scope (`security/`).
- **Không** đọc ngược health/learning (tránh cycle).

## API & bảng
- Bảng: `children` (khung tối thiểu), `enrollments`, `guardian_child_links` (`V3__create_children.sql`, `docs/database/DATA_DICTIONARY.md`).
- API: `GET /children/{childId}` (`ChildController` → `ChildService.getChildProfile`: `ChildAccessPolicy.checkCanViewChild` trước khi load; DTO `ChildProfileResponse` chỉ id, họ tên, ngày sinh, lớp hiện tại).
- Service cho kiểm tra scope: `EnrollmentService.findActiveClassroomIds(childId, date)`, `GuardianChildLinkService.isGuardianOf(userId, childId)`, `findChildIdsOfGuardian(userId)`. Không gọi ngược `security/authorization`.
- Query còn cần khi làm nghiệp vụ: `childrenInClass(classroomId, date)`, `guardiansOf(childId)`, `childrenOfGuardian(userId)`, `allergySummary(campusId|classroomId)`.

## PENDING
P-17 (ai khai báo/xác nhận dị ứng).

## Known pitfalls
- Campus của trẻ suy từ Enrollment tại ngày cụ thể; trẻ chuyển lớp/campus giữa năm ⇒ dữ liệu lịch sử phải theo enrollment tại ngày đó.
- DB chưa chặn enrollment chồng thời gian; khi viết API ghi enrollment phải kiểm ở service.
- Dữ liệu trẻ là nhạy cảm: không đưa vào log/test fixture thật.

## Related issues
Chưa có.

## Đọc thêm khi
Thay đổi enrollment ảnh hưởng điểm danh/suất ăn ⇒ `docs/business/flows/attendance.md`.
