# Module — child

Feature: `child` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: đang làm (bảng scope)

## Mục đích
Hồ sơ gốc của trẻ: thông tin cơ bản (CHD-02), nhập hồ sơ bằng form hoặc Excel (CHD-01), lớp đang học theo thời gian, phụ huynh, dị ứng.

## Owns
Child, Enrollment (childId, classroomId, from, to), GuardianChildLink (childId, userId, relation), ChildAllergy (chất, mức nhẹ/nặng — HLT-06, NUT-23), danh mục chất gây dị ứng (cố định + PHT thêm được).

## KHÔNG owns
Điểm danh, sức khỏe định kỳ, quan sát, hồ sơ phát triển (read model ở learning-observation).

## Rules
AUTH-04, AUTH-18, CHD-01..05, HLT-06, NUT-09, NUT-23.

## Phụ thuộc
- Dùng: common/utils, service `organization` (classroom/campus).
- Được dùng bởi: attendance, nutrition (dị ứng tổng hợp), health, learning-observation, reporting, kiểm tra scope (`security/`).
- **Không** đọc ngược health/learning (tránh cycle).

## API & bảng
- Bảng: `children` (khung tối thiểu), `enrollments`, `guardian_child_links`, `class_placements` (V19) (`V3__create_children.sql`, `docs/database/DATA_DICTIONARY.md`).
- API: `GET /children/{childId}` (`ChildController` → `ChildService.getChildProfile`: `ChildAccessPolicy.checkCanViewChild` trước khi load; DTO `ChildProfileResponse` chỉ id, họ tên, ngày sinh, lớp hiện tại).
- Chuyển năm học (PROPOSED — chờ nhóm/ADR, ghi trong plan `2026-10-22-cau-hinh-truong.md`): bảng `class_placements` (V19), API `/school-years/{id}/placements` (GET/PUT), `/placements/auto`, `/readiness`, `/activate` — `YearTransitionController`, `YearTransitionService`. Đặt ở child vì kích hoạt năm phải ghi `enrollments`; đọc cấu hình năm qua `YearSetupService` và kích hoạt qua `SchoolYearService` (school-structure) trong cùng transaction.
- API phụ huynh (AUTH-18, AUTH-19; quyền `PARENT_ACCOUNT_MANAGE`, phạm vi `StaffAccessPolicy`): `GET /children` (`ChildService.searchChildren` — HT mọi trẻ, PHT trẻ đang học trong lớp thuộc campus mình), `GET|POST /children/{id}/guardians`, `DELETE /children/{id}/guardians/{userId}` (`GuardianController` → `GuardianAccountService` của feature account; ghi liên kết qua `GuardianChildLinkService.createLink/deleteLink`). Quan hệ: enum `GuardianRelationship` + CHECK V9.
- Service cho kiểm tra scope: `EnrollmentService.findActiveClassroomIds(childId, date)`, `GuardianChildLinkService.isGuardianOf(userId, childId)`, `findChildIdsOfGuardian(userId)`. Không gọi ngược `security/authorization`.
- Query còn cần khi làm nghiệp vụ: `childrenInClass(classroomId, date)`, `allergySummary(campusId|classroomId)` — trả số đếm theo chất, mức, lớp; không danh tính (AUTH-05) (`guardiansOf`, `childrenOfGuardian` đã có).
- Gửi tài khoản phụ huynh qua SMS (AUTH-18): chưa làm; code hiện tại hiện mật khẩu tạm một lần để in phiếu.

## PENDING
P-28 (một trẻ cần mấy tài khoản phụ huynh). Đã đóng 28/10: P-17 (khai báo dị ứng — HLT-06).

## Known pitfalls
- Danh sách trẻ cho PHT lọc theo enrollment **hôm nay**: trẻ chưa có lớp hoặc đã rời lớp không hiện với PHT (chỉ HT thấy mọi trẻ). Gỡ liên kết phụ huynh không xóa tài khoản.
- Campus của trẻ suy từ Enrollment tại ngày cụ thể; trẻ chuyển lớp giữa năm (không chuyển cơ sở — CHD-03) ⇒ dữ liệu lịch sử phải theo enrollment tại ngày đó.
- Thôi học: phụ huynh mất quyền xem trẻ đó ngay, dữ liệu giữ nguyên, không xóa (CHD-04).
- Số định danh/CCCD và tiền sử bệnh là dữ liệu nhạy cảm (CHD-02).
- DB chưa chặn enrollment chồng thời gian; khi viết API ghi enrollment phải kiểm ở service.
- Dữ liệu trẻ là nhạy cảm: không đưa vào log/test fixture thật.

## Related issues
Chưa có.

## Đọc thêm khi
Thay đổi enrollment ảnh hưởng điểm danh/suất ăn ⇒ `docs/business/flows/attendance.md`.
