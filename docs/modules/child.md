# Module — child

Feature: `child` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: thiết kế

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
Chưa có. Query cần thiết: `childrenInClass(classroomId, date)`, `campusOfChild(childId, date)`, `guardiansOf(childId)`, `childrenOfGuardian(userId)`, `allergySummary(campusId|classroomId)`.

## PENDING
P-17 (ai khai báo/xác nhận dị ứng).

## Known pitfalls
- Campus của trẻ suy từ Enrollment tại ngày cụ thể; trẻ chuyển lớp/campus giữa năm ⇒ dữ liệu lịch sử phải theo enrollment tại ngày đó.
- Dữ liệu trẻ là nhạy cảm: không đưa vào log/test fixture thật.

## Related issues
Chưa có.

## Đọc thêm khi
Thay đổi enrollment ảnh hưởng điểm danh/suất ăn ⇒ `docs/business/flows/attendance.md`.
