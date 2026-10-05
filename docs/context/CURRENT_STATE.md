# Current State

Cập nhật: 2026-10-04. **Agent: cập nhật file này khi một feature bắt đầu có code, hoàn thành, hoặc một PENDING được chốt.**

## Tổng quan

| Hạng mục | Trạng thái |
| --- | --- |
| Scaffold BE | **Có** — Java 21, Spring Boot 4.1.1, Maven, PostgreSQL 17, Flyway, springdoc, Docker Compose |
| Response/exception chuẩn | **Có** — `ApiCode`, `ResponseJson`, `GlobalException`, `GlobalExceptionHandler`, `PageResponse`, `ResponseContractTest` |
| Prefix API cấu hình được | **Có** — `carenest.api.prefix` (`WebMvcConfig`), mặc định `/api` |
| Coding guide | **Có** — `docs/backend-coding-guide.md` |
| Docs nghiệp vụ / kiến trúc / memory | Bản đầu |
| Authentication/authorization | **Đang làm** — Keycloak + Resource Server (ADR-0011). Xong: kiểm JWT, role, 401/403, realm import dev, `user_accounts` + CurrentUser + `GET /accounts/me`. Tiếp: bảng scope, policy, cấp tài khoản (`docs/plans/active/2026-10-04-keycloak-auth.md`) |
| Schema/migration nghiệp vụ | Bắt đầu — `V1__create_user_accounts.sql` |
| OpenAPI export (`docs/api/`) | Chưa |
| AI provider | OPEN (ADR-0007) |
| Hạ tầng deploy | Chưa chốt (có Dockerfile, compose dev/demo: API + PostgreSQL + Keycloak + Mailpit) |

## Feature

| Module (feature) | Thiết kế | Code | Ghi chú |
| --- | --- | --- | --- |
| identity-access (`account`, `security/`) | Có | Đang làm | Nền Resource Server xong; CurrentUser/policy/cấp tài khoản tiếp theo |
| school-structure (`organization`) | Có | — | |
| child (`child`) | Có | — | |
| attendance (`attendance`) | Có | — | Giờ chốt, xử lý đến muộn PENDING |
| nutrition (`meals`) | Có | — | Bếp/thực đơn theo campus PENDING |
| health (`health`) | Có | — | Ngưỡng tham chiếu PENDING |
| learning-observation (`observation`) | Có | — | Activity context OPEN (ADR-0006) |
| facility-issue (`facilities`) | Có | — | |
| notification | Có | — | Kênh push chưa chốt |
| audit | Có | — | |
| ai-assistance (`integration/`) | Có | — | Provider OPEN |
| reporting (`report`) | Có | — | |

## Thứ tự triển khai (guide mục 15)

1. Nền móng — **xong**.
2. Đăng nhập và phạm vi: user/role, campus/class assignment, liên kết phụ huynh–trẻ, test quyền — **đang làm** (ADR-0011).
3. Điểm danh → BGH xác nhận số ăn → bếp xem.
4. Hồ sơ trẻ: quan sát/sức khỏe, chia sẻ phụ huynh, audit, lịch sử.
5. Mở rộng: sự cố CSVC, thông báo, thực đơn; AI sau khi dữ liệu + baseline sẵn sàng.
