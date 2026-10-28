# Current State

Cập nhật: 2026-10-04. **Agent: cập nhật file này khi một feature bắt đầu có code, hoàn thành, hoặc một PENDING được chốt.**

## Tổng quan

Auth React cập nhật2026-10-12: extension Phase1 và backend gateway Phase2 opt-in đã có source, V4/session/OTP/cookie-CSRF; FE Phase3 chưa vào FE `dev` (2026-10-22: FE `dev` vẫn mock; source gateway chỉ ở nhánh FE G94-209 — `docs/quality/reports/2026-10-22-auth-fe-integration-gap.md`). Bằng chứng/lưu ý: `docs/quality/reports/2026-10-12-react-auth-phase2.md`. Không thay trạng thái merge hay triển khai thật của phần Resource Server bên dưới.

Phân quyền cấu hình được (ADR-0017) cập nhật 2026-10-16 trên `feature/G94-60-BE-FEAT-01-authentication`, chưa commit: V7 `role_permissions`/`user_permission_overrides`/`permission_change_log`, `@permissionGuard`, API ma trận/ngoại lệ/lịch sử cho Hiệu trưởng, `permissions` trong `/accounts/me`. Phần B 2026-10-17 (plan `docs/plans/active/2026-10-17-staff-account-provisioning.md`): HT/PHT cấp tài khoản kèm phân công, khóa/mở, mật khẩu tạm, danh sách nhân sự, phân công, đọc campus/lớp (V8). Phần C 2026-10-17 (plan `docs/plans/active/2026-10-17-parent-account-provisioning.md`): tài khoản phụ huynh gắn trẻ (SĐT, mật khẩu tạm hệ thống sinh), danh sách trẻ, gỡ liên kết, khóa/mở, mật khẩu phụ huynh (V9).

| Hạng mục | Trạng thái |
| --- | --- |
| Scaffold BE | **Có** — Java 21, Spring Boot 4.1.1, Maven, PostgreSQL 17, Flyway, springdoc, Docker Compose |
| Response/exception chuẩn | **Có** — `ApiCode`, `ResponseJson`, `GlobalException`, `GlobalExceptionHandler`, `PageResponse`, `ResponseContractTest` |
| Prefix API cấu hình được | **Có** — `carenest.api.prefix` (`WebMvcConfig`), mặc định `/api` |
| Coding guide | **Có** — `docs/backend-coding-guide.md` |
| Docs nghiệp vụ / kiến trúc / memory | Bản đầu |
| Authentication/authorization | **Xong trên `feature/authentication`, chờ merge** — Keycloak + Resource Server (ADR-0011): kiểm JWT, role, 401/403/503, `user_accounts` + `GET /accounts/me`, bảng scope (V2/V3) + seed dev, policy campus/lớp/trẻ, `GET /children/{childId}`, cấp/khóa tài khoản qua Keycloak Admin API (Admin không tự khóa mình — AUTH-09). Chưa có: API quản lý phân công/liên kết phụ huynh. Giới hạn: KI-003..KI-009 (`docs/plans/active/2026-10-04-keycloak-auth.md`) |
| Schema/migration nghiệp vụ | Bắt đầu — V1 `user_accounts`, V2 campus/năm học/lớp/phân công, V3 trẻ/enrollment/liên kết PH, V7 phân quyền, V8 `primary_role`, V9 quyền phụ huynh; seed dev `db/dev` |
| OpenAPI export (`docs/api/`) | Chưa |
| AI provider | OPEN (ADR-0007) |
| Hạ tầng deploy | Chưa chốt (có Dockerfile, compose dev/demo: API + PostgreSQL + Keycloak + Mailpit) |

## Feature

| Module (feature) | Thiết kế | Code | Ghi chú |
| --- | --- | --- | --- |
| identity-access (`account`, `security/`) | Có | Gần xong | Resource Server, CurrentUser, policy, phân quyền cấu hình được (ADR-0017), HT/PHT cấp/khóa tài khoản, mật khẩu tạm, danh sách nhân sự, phân công (AUTH-15..17) xong (chưa commit), tài khoản phụ huynh gắn trẻ (AUTH-18, AUTH-19) xong (chưa commit); AUTH-18 sửa 2026-10-28: tên đăng nhập nhân sự không được là SĐT; thêm phụ huynh nhận, lưu email liên hệ (không gửi Keycloak, không dùng quên mật khẩu) (chưa commit) |
| school-structure (`organization`) | Có | Bảng scope + phân công + đọc campus/lớp | Chưa có API tạo/sửa campus/lớp/năm học |
| child (`child`) | Có | Bảng scope + `GET /children/{childId}` | Mới có hồ sơ cơ bản. Nghiệp vụ tiếp nhận chốt 2026-10-28 (CHD-01..10, AUTH-18 sửa, AUTH-20, AUTH-21, HLT-07, ATT-08; P-17, P-18 đóng) — chưa có code/API; plan `docs/plans/active/2026-10-28-child-enrollment-guardian-accounts.md` (`docs/modules/child.md`) |
| attendance (`attendance`) | Có | — | Giờ chốt, xử lý đến muộn PENDING; đơn nghỉ đã chốt 02/10: thông báo, không duyệt (ATT-05) |
| nutrition (`meals`) | Có | — | Bếp/thực đơn theo campus PENDING |
| health (`health`) | Có | — | Ngưỡng tham chiếu PENDING |
| learning-observation (`observation`) | Có | — | Activity context OPEN (ADR-0006) |
| facility-issue (`facilities`) | Có | — | |
| notification | Có | — | Kênh push chưa chốt |
| audit | Có | — | |
| ai-assistance (`integration/`) | Có | — | Provider OPEN |
| reporting (`report`) | Có | — | |

## Giới hạn hiện tại

Có xác thực Keycloak, phạm vi campus/lớp/trẻ và cấp/khóa tài khoản; chưa có nghiệp vụ điểm danh trở đi. Mọi rule PENDING (P-xx trong `BUSINESS_RULES.md`) chưa có hành vi chốt ⇒ làm cấu hình được, không hard-code.

## Thứ tự triển khai (guide mục 15)

1. Nền móng — **xong**.
2. Đăng nhập và phạm vi: user/role, campus/class assignment, liên kết phụ huynh–trẻ, test quyền — xác thực + phạm vi **xong** (ADR-0011, chờ merge); API quản lý phân công/liên kết chưa làm.
3. Điểm danh → BGH xác nhận số ăn → bếp xem.
4. Hồ sơ trẻ: quan sát/sức khỏe, chia sẻ phụ huynh, audit, lịch sử.
5. Mở rộng: sự cố CSVC, thông báo, thực đơn; AI sau khi dữ liệu + baseline sẵn sàng.
