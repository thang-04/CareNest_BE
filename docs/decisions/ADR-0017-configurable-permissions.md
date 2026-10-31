# ADR-0017 — Phân quyền cấu hình được: ma trận theo role + ngoại lệ từng người

- Status: ACCEPTED
- Date: 2026-10-16
- Liên quan: G94-60, identity-access, ADR-0003, ADR-0011, AUTH-06, AUTH-10..14, plan `docs/plans/active/2026-10-16-permission-management.md`

## Bối cảnh

ADR-0003 yêu cầu permission gán theo role và cấu hình được, nhưng code kiểm `@PreAuthorize("hasRole(...)")` cố định và không có bảng permission. Team chốt 2026-10-16: Hiệu trưởng (không phải System Admin) cấp tài khoản và phân quyền nghiệp vụ cho Phó hiệu trưởng, giáo viên, bếp; cần bật/tắt theo role, cấp thêm/thu hồi cho từng người, có lịch sử. Admin giữ phần kỹ thuật (tạo tài khoản PRINCIPAL, khóa/mở, xử lý sự cố).

## Quyết định

1. **Danh mục quyền nằm trong code** (`utils/enums/Permission`): mỗi quyền có `defaultRoles` và `configurableRoles`. Chỉ khai quyền BE thực sự kiểm; module mới tự thêm quyền của nó cùng migration seed.
2. **Role cố định / role cấu hình:** quyền của ADMIN, PRINCIPAL, PARENT lấy thẳng từ code; VICE_PRINCIPAL, TEACHER, KITCHEN_STAFF lấy từ bảng `role_permissions` (seed = `defaultRoles`, DB có CHECK chỉ nhận 3 role này).
3. **Quyền hiệu lực** = phần cố định ∪ (ma trận ∪ GRANT − REVOKE của `user_permission_overrides`). REVOKE không trừ phần cố định; GRANT/ô ma trận ngoài `configurableRoles` bị bỏ qua (`EffectivePermissionResolver`).
4. **Kiểm tra lớp 1** bằng `@PreAuthorize("@permissionGuard.has('CODE')")`. `CurrentUserService` tính quyền một lần mỗi request (chỉ query khi user có role cấu hình) và lưu trong `CurrentUser`; HT đổi quyền có hiệu lực ngay ở request kế tiếp. Quyền **không** đưa vào JWT, role Keycloak vẫn toàn cục.
5. **Lớp 2 không đổi:** quyền chỉ trả lời "được làm hay không"; phạm vi campus/lớp/trẻ vẫn do `*AccessPolicy` kiểm. Không có mức "toàn trường" vượt AUTH-02/03.
6. **Chống leo quyền:** `PERMISSION_MANAGE` chỉ PRINCIPAL và không giao được; không ai tự sửa quyền của mình (API + CHECK DB); đích có PRINCIPAL/ADMIN hoặc không có role cấu hình thì không có ngoại lệ.
7. **Lịch sử đầy đủ** `permission_change_log`: mỗi quyền đổi một dòng (trước/sau, người, lý do, thời điểm), cùng một lần lưu chung `batch_id`; lý do bắt buộc.
8. Role của người đích đọc từ Keycloak (`role-mappings/realm`) khi xem/sửa ngoại lệ; lỗi Keycloak ⇒ 503.

## Phương án đã cân nhắc

| Phương án | Ưu | Nhược |
| --- | --- | --- |
| **Bean guard đọc quyền đã tính theo request** | Hiệu lực ngay, không phụ thuộc token, dễ test | Thêm tối đa 2 query mỗi request cho role cấu hình |
| Nạp quyền thành authority trong JWT converter | `hasAuthority` chuẩn Spring | Converter phụ thuộc DB, chạy trước bước chặn tài khoản INACTIVE, khó test |
| Client role / composite role Keycloak | Không cần bảng | Chỉ đổi khi token mới; ngoại lệ từng người làm rối role mapping; trái rule "role Keycloak toàn cục" |
| Mức Full/Restricted/None như mock FE | Khớp SRS 4.4 | "Full" cho PHT vượt scope campus, phá AUTH-02 |

## Hệ quả

- Thêm quyền mới = thêm enum + migration seed cho role cấu hình + `@permissionGuard` ở endpoint; `ControllerSecurityCoverageTest` bắt mã gõ sai.
- Quyền ghi đang dùng chung policy "xem" ⇒ chỉ để `configurableRoles = TEACHER` cho quyền ghi tới khi có policy ghi riêng.
- Bỏ mã khỏi enum không làm vỡ DB (mã lưu chuỗi, resolver bỏ qua mã lạ); nên kèm migration xóa dòng cũ.
- `GET /accounts/me` trả `permissions` để client ẩn/hiện menu; quyết định thật vẫn ở BE (AUTH-07).

## Bổ sung 2026-10-17 — quản lý nhân sự (AUTH-15..17)

- `ACCOUNT_MANAGE`, `ACCOUNT_STATUS_MANAGE` cố định cho ADMIN + PRINCIPAL, cấu hình cho PHT (seed tắt); thêm `STAFF_ASSIGNMENT_MANAGE` (PRINCIPAL cố định, PHT cấu hình — seed bật).
- Quyền chỉ mở hành động; **ai được đụng role/campus nào** do `security/authorization/StaffAccessPolicy` quyết (Admin → PRINCIPAL; HT có phân công PRINCIPAL → PHT/GV/bếp; PHT → GV/bếp campus mình).
- `user_accounts.primary_role` (V8) là **bản sao** role Keycloak, ghi khi CareNest tạo tài khoản, dùng lọc danh sách và kiểm phạm vi quản lý; quyền của chính người gọi vẫn theo token. Đổi role trên Keycloak console sẽ lệch cột này — không đổi role qua console với tài khoản đã có (tạo tài khoản mới).
- Tạo tài khoản + phân công trong một transaction DB sau khi tạo user Keycloak; lỗi ⇒ xóa user Keycloak (bù trừ như ADR-0011).

## Bổ sung 2026-10-17 — tài khoản phụ huynh (AUTH-18, AUTH-19)

- Quyền `PARENT_ACCOUNT_MANAGE`: PRINCIPAL cố định, PHT cấu hình (seed bật, V9). Phạm vi theo **lớp hiện tại của trẻ** (enrollment hôm nay → campus), không theo cột lưu ở tài khoản: trẻ chuyển lớp/campus thì phạm vi tự đúng.
- Khóa/mở, mật khẩu tạm của phụ huynh đi qua endpoint riêng `/parent-accounts/...` dưới quyền này; không mở `ACCOUNT_*` của nhân sự cho phụ huynh.
- `POST /accounts` không nhận PARENT nữa; tạo phụ huynh luôn kèm liên kết trẻ.
- Vòng đời Keycloak (tạo + bù trừ, khóa/mở, mật khẩu tạm) dùng chung `AccountLifecycleOperations` cho nhân sự và phụ huynh.

## Bổ sung 2026-10-31 — màn của Cấu hình trường và Đánh giá trẻ

- `GET|PUT /school-years/{id}/vice-principal-assignments` (PHT phụ trách điểm trường theo năm học) dùng lớp 1 `STAFF_ASSIGNMENT_MANAGE` như `/staff-assignments`; service vẫn chỉ cho HT. Hai API cùng ghi `staff_assignments` vai trò PHT — nhóm cần thống nhất giữ một màn (Cần, Thắng).
- `GET /assessment/children/{childId}/development` (hồ sơ phát triển) dùng lớp 1 `CHILD_PROFILE_VIEW`; phạm vi lớp/điểm trường kiểm ở service, phụ huynh bị từ chối.

## Còn chờ

- Không còn mục nào của luồng phân quyền/cấp tài khoản.
- P-16 (Admin xem dữ liệu trẻ) giữ nguyên: Admin mặc định không có `CHILD_PROFILE_VIEW`.
