# Current State

Cập nhật: 2026-10-04. **Agent: cập nhật file này khi một feature bắt đầu có code, hoàn thành, hoặc một PENDING được chốt.**

## Tổng quan

Auth React cập nhật2026-10-12: extension Phase1 và backend gateway Phase2 opt-in đã có source, V4/session/OTP/cookie-CSRF; FE Phase3 chưa triển khai. Bằng chứng/lưu ý: `docs/quality/reports/2026-10-12-react-auth-phase2.md`. Không thay trạng thái merge hay triển khai thật của phần Resource Server bên dưới.

| Hạng mục | Trạng thái |
| --- | --- |
| Scaffold BE | **Có** — Java 21, Spring Boot 4.1.1, Maven, PostgreSQL 17, Flyway, springdoc, Docker Compose |
| Response/exception chuẩn | **Có** — `ApiCode`, `ResponseJson`, `GlobalException`, `GlobalExceptionHandler`, `PageResponse`, `ResponseContractTest` |
| Prefix API cấu hình được | **Có** — `carenest.api.prefix` (`WebMvcConfig`), mặc định `/api` |
| Coding guide | **Có** — `docs/backend-coding-guide.md` |
| Docs nghiệp vụ / kiến trúc / memory | Bản đầu |
| Authentication/authorization | **Xong trên `feature/authentication`, chờ merge** — Keycloak + Resource Server (ADR-0011): kiểm JWT, role, 401/403/503, `user_accounts` + `GET /accounts/me`, bảng scope (V2/V3) + seed dev, policy campus/lớp/trẻ, `GET /children/{childId}`, cấp/khóa tài khoản qua Keycloak Admin API (Admin không tự khóa mình — AUTH-09). Chưa có: API quản lý phân công/liên kết phụ huynh. Giới hạn: KI-003..KI-009 (`docs/plans/active/2026-10-04-keycloak-auth.md`) |
| Schema/migration nghiệp vụ | Bắt đầu — V1 `user_accounts`, V2 campus/năm học/lớp/phân công, V3 trẻ/enrollment/liên kết PH; seed dev `db/dev` |
| OpenAPI export (`docs/api/`) | Chưa |
| AI provider | OPEN (ADR-0007) |
| Hạ tầng deploy | Chưa chốt (có Dockerfile, compose dev/demo: API + PostgreSQL + Keycloak + Mailpit) |

## Feature

| Module (feature) | Thiết kế | Code | Ghi chú |
| --- | --- | --- | --- |
| identity-access (`account`, `security/`) | Có | Gần xong | Resource Server, CurrentUser, policy, cấp/khóa tài khoản xong; chưa có API phân công/liên kết |
| school-structure (`organization`) | Có | Bảng scope | Chưa có API quản lý |
| child (`child`) | Có | Bảng scope + `GET /children/{childId}` | Mới có hồ sơ cơ bản |
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
