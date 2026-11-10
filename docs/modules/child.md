# Module — child

Feature: `child` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: đang làm (bảng scope)

## Mục đích
Hồ sơ gốc của trẻ: thông tin cơ bản (CHD-02), nhập hồ sơ bằng form hoặc Excel (CHD-01), lớp đang học theo thời gian, phụ huynh, dị ứng.

## Owns
Child, Enrollment (childId, classroomId, from, to), GuardianChildLink (childId, userId, relation), ChildAllergy (chất, mức nhẹ/nặng — HLT-06, NUT-23), danh mục chất gây dị ứng (cố định + PHT thêm được); hồ sơ tiếp nhận (V63): ChildProfile, ChildGuardian, ChildHealthDeclaration, lịch sử hồ sơ.

## KHÔNG owns
Điểm danh, sức khỏe định kỳ, quan sát, hồ sơ phát triển (read model ở learning-observation).

## Rules
AUTH-04, AUTH-18, CHD-01..05, HLT-06, NUT-09, NUT-23. Luồng tiếp nhận (nhánh G94-180): G94-180/AUTH-20, G94-180/AUTH-21, G94-180/CHD-01..10, G94-180/HLT-08, G94-180/ATT-09 — `docs/business/BUSINESS_RULES.md` mục "G94-180" (trùng mã với rule `dev`, chờ đánh lại mã). Trong card này, mã CHD-06..10, AUTH-20, AUTH-21 ở phần tiếp nhận theo nghĩa của mục đó.

## Phụ thuộc
- Dùng: common/utils, service `organization` (classroom/campus).
- Được dùng bởi: attendance, nutrition (dị ứng tổng hợp), health, learning-observation, reporting, kiểm tra scope (`security/`).
- **Không** đọc ngược health/learning (tránh cycle).

## API & bảng
- Bảng: `children`, `enrollments`, `guardian_child_links` (`V3__create_children.sql`); V63 (`V63__child_enrollment_guardian_accounts.sql`; trên nhánh G94-180 trước khi gộp `dev` 31/10 là V10, trước khi gộp `main` 03/11 là V47) thêm cột trẻ (mã, giới tính, trạng thái, điểm trường tiếp nhận, ngày tiếp nhận/nghỉ/mở lại), `child_code_sequences`, `child_profiles`, `child_guardians`, `child_health_declarations`, `child_allergies`, `child_health_declaration_history`, `child_history`; xếp lớp năm học mới `class_placements` (`V19__create_class_placements.sql`, gộp từ G94-235) — cột, ràng buộc: `docs/database/DATA_DICTIONARY.md`. Entity (29/10/2026): `Child` map đủ cột V63; `ChildProfile`, `ChildGuardian`, `ChildHealthDeclaration`, `ChildAllergy`, `ChildHealthDeclarationHistory`, `ChildHistory`, `ChildCodeSequence` (cột JSONB `changes`, `allergy_change`, `history_change` chưa map — tiếp nhận và mở lại hồ sơ không ghi).
- API: `GET /children/{childId}` (`ChildController` → `ChildService.getChildProfile`: `ChildAccessPolicy.checkCanViewChild` trước khi load; DTO `ChildProfileResponse` chỉ id, họ tên, ngày sinh, lớp hiện tại).
- API phụ huynh (AUTH-18, AUTH-19; quyền `PARENT_ACCOUNT_MANAGE`, phạm vi `StaffAccessPolicy`): `GET /children` (`ChildService.searchChildren` — HT mọi trẻ, PHT trẻ đang học trong lớp thuộc campus mình), `GET|POST /children/{id}/guardians`, `DELETE /children/{id}/guardians/{userId}` (`GuardianController` → `GuardianAccountService` của feature account; ghi liên kết qua `GuardianChildLinkService.createLink/deleteLink`). Quan hệ: enum `GuardianRelationship` + CHECK V9.
- API tiếp nhận (Phase 2–3 plan tiếp nhận, 29/10/2026; `ChildEnrollmentController`): `GET /children/duplicate-check` (`CHILD_ENROLL`; CHD-04), `GET /children/placement-classes` (`CHILD_ENROLL` hoặc `CHILD_PLACEMENT`; CHD-10), `POST /children/{id}/readmit` (`CHILD_ENROLL`; CHD-03) → `ChildIntakeService`; `POST /children/guardian-check` (`CHILD_ENROLL`; AUTH-18), `GET /children/parent-accounts/by-citizen-id` (`CHILD_ENROLL` hoặc `CHILD_PROFILE_EDIT`), `POST /children/enrollments` (`CHILD_ENROLL`; AUTH-20) → `ChildEnrollmentService` của feature account (tạo user Keycloak, SMS; feature child không gọi account), ghi hồ sơ qua `ChildIntakeService.prepareEnrollment` / `createEnrolledChild`. Phạm vi: HT toàn trường, PHT điểm trường được phân công (`StaffAccessPolicy`); điểm trường của trẻ = lớp đang học, không có lớp thì `intake_campus_id`, rồi lớp cuối. Lỗi theo ô: 400 `INVALID_REQUEST_DATA` (9001) + `data [{field, message}]` (kiểm ở `EnrollmentFormValidator`, câu và đường dẫn ô như BE giả FE); 409 `CHILD_DUPLICATE` (3008), `GUARDIAN_IDENTITY_CONFLICT` (3009, kèm ô), `CHILD_NOT_LEFT` (3010) — đánh lại số khi gộp `dev` 31/10 (3007 thuộc `GUARDIAN_ACCOUNT_CONFIRMATION_REQUIRED`); SĐT là tài khoản nhân sự ⇒ lỗi ô `PHONE_OF_STAFF_ACCOUNT` (1130). Contract đủ: plan mục 4.1, 4.4; khác BE giả: mục "Progress log 2026-10-29 — Phase 2–3".
- Chuyển năm học (PROPOSED — chờ nhóm/ADR, ghi trong plan `2026-10-22-cau-hinh-truong.md`): bảng `class_placements` (V19), API `/school-years/{id}/placements` (GET/PUT), `/placements/auto`, `/readiness`, `/activate` — `YearTransitionController`, `YearTransitionService`. Đặt ở child vì kích hoạt năm phải ghi `enrollments`; đọc cấu hình năm qua `YearSetupService` và kích hoạt qua `SchoolYearService` (school-structure) trong cùng transaction.
- Service cho kiểm tra scope: `EnrollmentService.findActiveClassroomIds(childId, date)`, `GuardianChildLinkService.isGuardianOf(userId, childId)`, `findChildIdsOfGuardian(userId)`. Không gọi ngược `security/authorization`.
- Query còn cần khi làm nghiệp vụ: `childrenInClass(classroomId, date)`, `allergySummary(campusId|classroomId)` — trả số đếm theo chất, mức, lớp; không danh tính (AUTH-05) (`guardiansOf`, `childrenOfGuardian` đã có).
- Mật khẩu tạm phụ huynh: thêm phụ huynh (`POST /children/{id}/guardians`) hiện mật khẩu tạm một lần (AUTH-18 của `dev`, chốt 30/10: không gửi SMS); lưu form tiếp nhận gửi SMS qua cổng giả (G94-180/AUTH-20) — lệch, chờ team chốt.

## Sửa hồ sơ, cho nghỉ học — 10/11/2026
Plan `docs/plans/active/2026-11-10-child-edit-withdraw.md`.
- `POST /children/{id}/withdraw` (`CHILD_PLACEMENT`) → `ChildRecordService.withdraw`: `leftAt` = ngày đầu không học (lớp kết thúc `leftAt - 1`; nghỉ ngay ngày vào lớp ⇒ xóa enrollment đó như chuyển năm học), không ở tương lai, không trước ngày tiếp nhận/mở lại hồ sơ; lịch sử WITHDRAWN; thông báo `CHILD_WITHDRAWN` cho GV lớp; phát `ChildrenLeftSchoolEvent` ⇒ listener feature account khóa tài khoản chỉ là phụ huynh không còn con đang học (CHD-04). Trẻ đã nghỉ ⇒ 409 `CHILD_ALREADY_LEFT` (3011).
- `PUT /children/{id}` (`CHILD_PROFILE_EDIT`) → `ChildProfileEditService` (feature account điều phối) → `ChildRecordService.prepareEdit`/`applyEdit`: kiểm như tiếp nhận trừ khai báo sức khỏe, người nhận tài khoản (`EnrollmentFormValidator.validateEdit`); ngày sinh không sau ngày tiếp nhận/lớp đầu tiên; trùng hồ sơ khác 409; ghép phụ huynh theo `guardians[].id` (mới, tùy chọn) → CCCD → vị trí; bỏ phụ huynh đang liên kết tài khoản ⇒ lỗi ô `guardians`; lịch sử UPDATED ghi `changes` (CCCD/số định danh 3 số cuối), trả trong `history[].changes`. Phụ huynh có tài khoản: xem `docs/modules/identity-access.md` (AUTH-21).
- Nhập Excel đã làm trước đó (Spec013): `ChildEnrollmentImportController`.

## Đã chốt, BE chưa làm (user chốt 2026-10-28)
Nghiệp vụ tiếp nhận trẻ chốt theo danh sách lệch BE–FE của nhánh G94-180 (`FE:docs/integration/CHILD_ENROLLMENT_BE_GAPS.md`); FE đang chạy bằng BE giả, contract đề xuất ở `FE:src/services/children/api/childrenApi.js` (PROPOSED). **Plan làm BE (đặc tả đủ dữ liệu, quyền, API, lỗi): `docs/plans/active/2026-10-28-child-enrollment-guardian-accounts.md`.**
Đã làm (Phase 1, 2026-10-28): mã quyền `CHILD_ENROLL`, `CHILD_PROFILE_EDIT`, `CHILD_PLACEMENT` trong enum `Permission` + seed ma trận PHT (CHD-01); enum `GuardianRelationship` 6 loại, CHECK V63 (AUTH-18); migration V63 cho mọi bảng/cột dưới đây; seed dev nhập đủ thông tin (CHD-08). Đã làm (Phase 2–3, 2026-10-29): kiểm trùng hồ sơ, kiểm phụ huynh, tra tài khoản theo CCCD, lớp tiếp nhận, `POST /children/enrollments` (tạo/liên kết tài khoản, SMS qua cổng giả), mở lại hồ sơ — mục "API & bảng". Còn lại:
- Quan hệ phụ huynh 6 loại cho hồ sơ lẫn liên kết, tối đa 3 phụ huynh mỗi trẻ (AUTH-18 — DB đã chặn); hồ sơ luôn đủ thông tin (CHD-08).
- Theo năm học: danh sách trẻ theo lớp của năm đó (CHD-09); tiếp nhận/xếp lớp chỉ vào lớp năm đang hoạt động (CHD-10); đợt khám (HLT-08; phiếu theo dõi đã bỏ 2026-11-04 — HLT-16); đón trẻ (ATT-09).
- Bỏ `POST /children/{childId}/guardians` (AUTH-20, C-4): phụ huynh nhập ở hồ sơ trẻ rồi kích hoạt.
- Dữ liệu trẻ: mã trẻ, giới tính, điểm trường tiếp nhận cho trẻ chưa có lớp (CHD-02), trạng thái Đang học / Chờ xếp lớp / Đã nghỉ học, ngày tiếp nhận, ngày + lý do nghỉ (CHD-03); bảng hành chính (CHD-05); khai báo sức khỏe khi tiếp nhận (CHD-06).
- Phụ huynh: thông tin phụ huynh trên hồ sơ trẻ trước khi có tài khoản (AUTH-20), CCCD phụ huynh làm khóa nhận diện (AUTH-18), sửa thông tin phụ huynh cập nhật tài khoản, đổi SĐT đổi tên đăng nhập (AUTH-21 — xem `docs/modules/identity-access.md`).
- API: xếp/chuyển lớp (đóng enrollment cũ, mở enrollment mới), khai báo sức khỏe, màn Tài khoản phụ huynh. (Nhập Excel, Sửa hồ sơ, cho nghỉ học: đã làm — mục "Sửa hồ sơ, cho nghỉ học — 10/11/2026".)
- Thông báo tiếp xúc người bệnh lao (CHD-07) khi có module notification.

## PENDING
P-28 đã đóng 05/11/2026 theo Spec013: một recipient cho tiếp nhận mới, giữ nguyên historical links. Đã đóng 28/10: P-17 (khai báo dị ứng — HLT-06); G94-180/P-17 (CHD-06), G94-180/P-18 (AUTH-20: người lưu không có quyền quản lý tài khoản phụ huynh vẫn lưu, phụ huynh chờ kích hoạt).

## Known pitfalls
- Danh sách cấp tài khoản đã bao gồm trẻ chờ xếp lớp trong điểm trường tiếp nhận. Các API recipient dùng SelectedParentAccountService để kiểm scope; không dùng enrollment-only của policy cũ để bỏ sót trẻ chưa có lớp. PHT không được mở rộng quyền sang trẻ đã nghỉ.
- Lưu form tiếp nhận: phụ huynh khớp tài khoản có sẵn ⇒ hồ sơ ghi họ tên, SĐT, email của tài khoản, không sửa tài khoản (như C-2), `phoneChanged` luôn false — sửa tài khoản theo hồ sơ và đổi tên đăng nhập làm ở Phase 4 (AUTH-21). SMS gửi sau khi giao dịch DB commit, không outbox: gửi lỗi ⇒ `smsStatus = FAILED`, mật khẩu tạm không lưu nên phải cấp lại. Gỡ liên kết phụ huynh không xóa tài khoản.
- Campus của trẻ suy từ Enrollment tại ngày cụ thể; trẻ chuyển lớp giữa năm ⇒ dữ liệu lịch sử phải theo enrollment tại ngày đó. CHD-03 của `dev`: không chuyển cơ sở; xếp lớp G94-180 cho chuyển điểm trường — lệch, chờ team chốt.
- Thôi học: phụ huynh mất quyền xem trẻ đó ngay, dữ liệu giữ nguyên, không xóa (CHD-04).
- Số định danh/CCCD và tiền sử bệnh là dữ liệu nhạy cảm (CHD-02).
- DB chưa chặn enrollment chồng thời gian (V63 chỉ chặn 2 enrollment đang mở); khi viết API ghi enrollment phải kiểm ở service.
- V63 có ràng buộc NOT VALID (`ck_children_complete`, `ck_child_guardians_citizen_id_required`): dòng có trước V63 được giữ, nhưng **mọi UPDATE** dòng đó phải nhập đủ (giới tính; CCCD phụ huynh) nếu không DB trả lỗi CHECK.
- Tuần thai khi sinh bắt buộc với mọi trẻ khi tiếp nhận, khớp "Sinh non" (dưới 37 tuần là sinh non) — G94-180/CHD-05, user chốt 2026-11-04; kiểm ở `EnrollmentFormValidator` (lỗi ô `profile.gestationWeeks`), lưu cả khi không sinh non. DB vẫn cho NULL (hồ sơ cũ, seed dev); CHECK V63 chỉ bắt khi `premature = YES` ⇒ đọc hồ sơ phải chịu `gestationWeeks` null.
- Ngày sinh không sau ngày vào lớp đầu tiên: module health dựa vào điều này (ngày đo/ngày khám hệ thống ghi không kiểm ngày sinh — 2026-11-04). Khi làm Sửa hồ sơ đổi ngày sinh phải chặn ngày sinh sau ngày vào lớp đầu tiên và sau lần đo/khám sớm nhất.
- UNIQUE của `child_guardians` theo trẻ là DEFERRABLE INITIALLY DEFERRED: trùng SĐT/CCCD/vị trí chỉ lỗi lúc commit, không lỗi lúc flush ⇒ service phải tự kiểm (422/409 theo ô) trước khi lưu, DB chỉ là chốt chặn cuối.
- Seed dev: `parent.a`, `parent.b` có tên đăng nhập kiểu cũ (không phải SĐT) nên SĐT của họ trên hồ sơ trẻ (`0900000006`, `0900000007`) khác tên đăng nhập; phụ huynh khác của trẻ demo chờ kích hoạt.
- Dữ liệu trẻ là nhạy cảm: không đưa vào log/test fixture thật.

## Related issues
Chưa có.

## Đọc thêm khi
Thay đổi enrollment ảnh hưởng điểm danh/suất ăn ⇒ `docs/business/flows/attendance.md`.

## Ảnh tiếp nhận — 05/11/2026

User duyệt thêm ảnh tùy chọn của trẻ và từng ChildGuardian trong cùng request tiếp nhận; không ghi đè ảnh tài khoản. POST /children/enrollments giữ JSON, nhận thêm multipart payload JSON + childPhoto/guardianPhoto0..2 (JPEG/PNG/WEBP ≤2 MiB). V68 lưu metadata nullable, tệp private S3 qua ChildPhotoStorage. GET /children/{id}/photo và /children/{id}/guardian-photos/{guardianId} kiểm phạm vi đọc; PHT còn phân công đọc mọi campus theo AUTH-20 kể cả trẻ chờ xếp lớp. GET /children/{id}/profile trả child view để Web mở lại hồ sơ; thông tin quản trị tài khoản/SMS chỉ có khi người đọc quản lý tài khoản ở đúng campus. Không bổ sung PUT sửa hồ sơ trong task này. Chi tiết: specs/012-child-enrollment-photos/contracts/enrollment-photos.md.

## Thiết kế chọn người nhận tài khoản — 05/11/2026

User yêu cầu chọn một guardian làm người nhận tài khoản, form kích hoạt ngay khi lưu, Excel sau nhập ở màn quản lý tài khoản. Không lọc bỏ guardian gốc hoặc ảnh/người đón. Đây là thiết kế chưa implement, plan `docs/plans/completed/2026-11-05-selected-parent-account.md`, Spec013; luồng source hiện tại vẫn tạo/kích hoạt cả danh sách. Các endpoint import/pending/activation FE hiện khai nhưng BE chưa có phải được triển khai/kiểm scope nếu duyệt thay đổi.

Known pitfall: recipient backfill trên guardian legacy có CHECK NOT VALID cần kiểm UPDATE dữ liệu cũ; xem [incident](../knowledge/incidents/BUG-261105-recipient-backfill-not-valid-check.md).
