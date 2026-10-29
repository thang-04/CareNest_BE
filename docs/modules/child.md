# Module — child

Feature: `child` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: đang làm (bảng scope)

## Mục đích
Hồ sơ gốc của trẻ: thông tin cơ bản, lớp đang học theo thời gian, phụ huynh, dị ứng.

## Owns
Child, Enrollment (childId, classroomId, from, to), GuardianChildLink (childId, userId, relation), ChildAllergy/basic info.

## KHÔNG owns
Điểm danh, sức khỏe định kỳ, quan sát, hồ sơ phát triển (read model ở learning-observation).

## Rules
AUTH-04, AUTH-18, AUTH-20, AUTH-21, CHD-01..CHD-10, HLT-06, HLT-08, ATT-09, NUT-09.

## Phụ thuộc
- Dùng: common/utils, service `organization` (classroom/campus).
- Được dùng bởi: attendance, nutrition (dị ứng tổng hợp), health, learning-observation, reporting, kiểm tra scope (`security/`).
- **Không** đọc ngược health/learning (tránh cycle).

## API & bảng
- Bảng: `children`, `enrollments`, `guardian_child_links` (`V3__create_children.sql`); V10 (`V10__child_enrollment_guardian_accounts.sql`) thêm cột trẻ (mã, giới tính, trạng thái, điểm trường tiếp nhận, ngày tiếp nhận/nghỉ/mở lại), `child_code_sequences`, `child_profiles`, `child_guardians`, `child_health_declarations`, `child_allergies`, `child_health_declaration_history`, `child_history`; xếp lớp năm học mới `class_placements` (`V20__create_class_placements.sql`, gộp từ G94-235) — cột, ràng buộc: `docs/database/DATA_DICTIONARY.md`. Entity (29/10/2026): `Child` map đủ cột V10; `ChildProfile`, `ChildGuardian`, `ChildHealthDeclaration`, `ChildAllergy`, `ChildHealthDeclarationHistory`, `ChildHistory`, `ChildCodeSequence` (cột JSONB `changes`, `allergy_change`, `history_change` chưa map — tiếp nhận và mở lại hồ sơ không ghi).
- API: `GET /children/{childId}` (`ChildController` → `ChildService.getChildProfile`: `ChildAccessPolicy.checkCanViewChild` trước khi load; DTO `ChildProfileResponse` chỉ id, họ tên, ngày sinh, lớp hiện tại).
- API phụ huynh (AUTH-18, AUTH-19; quyền `PARENT_ACCOUNT_MANAGE`, phạm vi `StaffAccessPolicy`): `GET /children` (`ChildService.searchChildren` — HT mọi trẻ, PHT trẻ đang học trong lớp thuộc campus mình), `GET|POST /children/{id}/guardians`, `DELETE /children/{id}/guardians/{userId}` (`GuardianController` → `GuardianAccountService` của feature account; ghi liên kết qua `GuardianChildLinkService.createLink/deleteLink`). Quan hệ: enum `GuardianRelationship` + CHECK V9.
- API tiếp nhận (Phase 2–3 plan tiếp nhận, 29/10/2026; `ChildEnrollmentController`): `GET /children/duplicate-check` (`CHILD_ENROLL`; CHD-04), `GET /children/placement-classes` (`CHILD_ENROLL` hoặc `CHILD_PLACEMENT`; CHD-10), `POST /children/{id}/readmit` (`CHILD_ENROLL`; CHD-03) → `ChildIntakeService`; `POST /children/guardian-check` (`CHILD_ENROLL`; AUTH-18), `GET /children/parent-accounts/by-citizen-id` (`CHILD_ENROLL` hoặc `CHILD_PROFILE_EDIT`), `POST /children/enrollments` (`CHILD_ENROLL`; AUTH-20) → `ChildEnrollmentService` của feature account (tạo user Keycloak, SMS; feature child không gọi account), ghi hồ sơ qua `ChildIntakeService.prepareEnrollment` / `createEnrolledChild`. Phạm vi: HT toàn trường, PHT điểm trường được phân công (`StaffAccessPolicy`); điểm trường của trẻ = lớp đang học, không có lớp thì `intake_campus_id`, rồi lớp cuối. Lỗi theo ô: 400 `INVALID_REQUEST_DATA` (9001) + `data [{field, message}]` (kiểm ở `EnrollmentFormValidator`, câu và đường dẫn ô như BE giả FE); 409 `CHILD_DUPLICATE` (3007), `GUARDIAN_IDENTITY_CONFLICT` (3008, kèm ô), `CHILD_NOT_LEFT` (3009). Contract đủ: plan mục 4.1, 4.4; khác BE giả: mục "Progress log 2026-10-29 — Phase 2–3".
- Chuyển năm học (PROPOSED — chờ nhóm/ADR, ghi trong plan `2026-10-22-cau-hinh-truong.md`): bảng `class_placements` (V20), API `/school-years/{id}/placements` (GET/PUT), `/placements/auto`, `/readiness`, `/activate` — `YearTransitionController`, `YearTransitionService`. Đặt ở child vì kích hoạt năm phải ghi `enrollments`; đọc cấu hình năm qua `YearSetupService` và kích hoạt qua `SchoolYearService` (school-structure) trong cùng transaction.
- Service cho kiểm tra scope: `EnrollmentService.findActiveClassroomIds(childId, date)`, `GuardianChildLinkService.isGuardianOf(userId, childId)`, `findChildIdsOfGuardian(userId)`. Không gọi ngược `security/authorization`.
- Query còn cần khi làm nghiệp vụ: `childrenInClass(classroomId, date)`, `allergySummary(campusId|classroomId)` (`guardiansOf`, `childrenOfGuardian` đã có).

## Đã chốt, BE chưa làm (user chốt 2026-10-28)
Nghiệp vụ tiếp nhận trẻ chốt theo danh sách lệch BE–FE của nhánh G94-180 (`FE:docs/integration/CHILD_ENROLLMENT_BE_GAPS.md`); FE đang chạy bằng BE giả, contract đề xuất ở `FE:src/services/children/api/childrenApi.js` (PROPOSED). **Plan làm BE (đặc tả đủ dữ liệu, quyền, API, lỗi): `docs/plans/active/2026-10-28-child-enrollment-guardian-accounts.md`.**
Đã làm (Phase 1, 2026-10-28): mã quyền `CHILD_ENROLL`, `CHILD_PROFILE_EDIT`, `CHILD_PLACEMENT` trong enum `Permission` + seed ma trận PHT (CHD-01); enum `GuardianRelationship` 6 loại, CHECK V10 (AUTH-18); migration V10 cho mọi bảng/cột dưới đây; seed dev nhập đủ thông tin (CHD-08). Đã làm (Phase 2–3, 2026-10-29): kiểm trùng hồ sơ, kiểm phụ huynh, tra tài khoản theo CCCD, lớp tiếp nhận, `POST /children/enrollments` (tạo/liên kết tài khoản, SMS qua cổng giả), mở lại hồ sơ — mục "API & bảng". Còn lại:
- Quan hệ phụ huynh 6 loại cho hồ sơ lẫn liên kết, tối đa 3 phụ huynh mỗi trẻ (AUTH-18 — DB đã chặn); hồ sơ luôn đủ thông tin (CHD-08).
- Theo năm học: danh sách trẻ theo lớp của năm đó (CHD-09); tiếp nhận/xếp lớp chỉ vào lớp năm đang hoạt động (CHD-10); đợt khám, phiếu theo dõi (HLT-08); đón trẻ (ATT-09).
- Bỏ `POST /children/{childId}/guardians` (AUTH-20, C-4): phụ huynh nhập ở hồ sơ trẻ rồi kích hoạt.
- Dữ liệu trẻ: mã trẻ, giới tính, điểm trường tiếp nhận cho trẻ chưa có lớp (CHD-02), trạng thái Đang học / Chờ xếp lớp / Đã nghỉ học, ngày tiếp nhận, ngày + lý do nghỉ (CHD-03); bảng hành chính (CHD-05); khai báo sức khỏe khi tiếp nhận (CHD-06).
- Phụ huynh: thông tin phụ huynh trên hồ sơ trẻ trước khi có tài khoản (AUTH-20), CCCD phụ huynh làm khóa nhận diện (AUTH-18), sửa thông tin phụ huynh cập nhật tài khoản, đổi SĐT đổi tên đăng nhập (AUTH-21 — xem `docs/modules/identity-access.md`).
- API: nhập Excel xem trước + nhập (+ `campusId`, không tạo tài khoản), Sửa hồ sơ (sửa tài khoản theo hồ sơ, đổi SĐT đổi tên đăng nhập — AUTH-21), cho nghỉ học, xếp/chuyển lớp (đóng enrollment cũ, mở enrollment mới), khai báo sức khỏe, màn Tài khoản phụ huynh.
- Thông báo tiếp xúc người bệnh lao (CHD-07) khi có module notification.

## PENDING
Không còn PENDING riêng của module. P-17 (CHD-06), P-18 (AUTH-20: người lưu không có quyền quản lý tài khoản phụ huynh vẫn lưu, phụ huynh chờ kích hoạt) đã đóng 2026-10-28.

## Known pitfalls
- Danh sách trẻ cho PHT lọc theo enrollment **hôm nay**: trẻ chưa có lớp hoặc đã rời lớp không hiện với PHT (chỉ HT thấy mọi trẻ). Lệch CHD-02 (2026-10-28): trẻ chờ xếp lớp phải hiện với PHT của điểm trường tiếp nhận. API tiếp nhận (Phase 2–3) đã tính điểm trường tiếp nhận; `GET /children`, `ChildAccessPolicy`, `StaffAccessPolicy.checkCanManageChildGuardians` chưa sửa (làm cùng D-4 `GET /children`, Phase 8).
- Lưu form tiếp nhận: phụ huynh khớp tài khoản có sẵn ⇒ hồ sơ ghi họ tên, SĐT, email của tài khoản, không sửa tài khoản (như C-2), `phoneChanged` luôn false — sửa tài khoản theo hồ sơ và đổi tên đăng nhập làm ở Phase 4 (AUTH-21). SMS gửi sau khi giao dịch DB commit, không outbox: gửi lỗi ⇒ `smsStatus = FAILED`, mật khẩu tạm không lưu nên phải cấp lại. Gỡ liên kết phụ huynh không xóa tài khoản.
- Campus của trẻ suy từ Enrollment tại ngày cụ thể; trẻ chuyển lớp/campus giữa năm ⇒ dữ liệu lịch sử phải theo enrollment tại ngày đó.
- DB chưa chặn enrollment chồng thời gian (V10 chỉ chặn 2 enrollment đang mở); khi viết API ghi enrollment phải kiểm ở service.
- V10 có ràng buộc NOT VALID (`ck_children_complete`, `ck_child_guardians_citizen_id_required`): dòng có trước V10 được giữ, nhưng **mọi UPDATE** dòng đó phải nhập đủ (giới tính; CCCD phụ huynh) nếu không DB trả lỗi CHECK.
- UNIQUE của `child_guardians` theo trẻ là DEFERRABLE INITIALLY DEFERRED: trùng SĐT/CCCD/vị trí chỉ lỗi lúc commit, không lỗi lúc flush ⇒ service phải tự kiểm (422/409 theo ô) trước khi lưu, DB chỉ là chốt chặn cuối.
- Seed dev: `parent.a`, `parent.b` có tên đăng nhập kiểu cũ (không phải SĐT) nên SĐT của họ trên hồ sơ trẻ (`0900000006`, `0900000007`) khác tên đăng nhập; phụ huynh khác của trẻ demo chờ kích hoạt.
- Dữ liệu trẻ là nhạy cảm: không đưa vào log/test fixture thật.

## Related issues
Chưa có.

## Đọc thêm khi
Thay đổi enrollment ảnh hưởng điểm danh/suất ăn ⇒ `docs/business/flows/attendance.md`.
