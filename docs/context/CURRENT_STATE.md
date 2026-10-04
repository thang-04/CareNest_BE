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
| Authentication/authorization | Chưa — team đang chốt phương án |
| Schema/migration nghiệp vụ | Chưa (`db/migration` trống) |
| OpenAPI export (`docs/api/`) | Chưa |
| AI provider | OPEN (ADR-0007) |
| Hạ tầng deploy | Chưa chốt (có Dockerfile, compose cho dev/demo) |

## Feature

| Module (feature) | Thiết kế | Code | Ghi chú |
| --- | --- | --- | --- |
| identity-access (`account`, `security/`) | Có | — | Auth chưa chốt |
| school-structure (`organization`) | Có | — | |
| child (`child`) | Có | — | |
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

Mới có scaffold: chưa auth, chưa nghiệp vụ. Mọi rule PENDING (P-xx trong `BUSINESS_RULES.md`) chưa có hành vi chốt ⇒ làm cấu hình được, không hard-code.

## Thứ tự triển khai (guide mục 15)

1. Nền móng — **xong**.
2. Đăng nhập và phạm vi: user/role, campus/class assignment, liên kết phụ huynh–trẻ, test quyền — **kế tiếp** (chờ chốt auth).
3. Điểm danh → BGH xác nhận số ăn → bếp xem.
4. Hồ sơ trẻ: quan sát/sức khỏe, chia sẻ phụ huynh, audit, lịch sử.
5. Mở rộng: sự cố CSVC, thông báo, thực đơn; AI sau khi dữ liệu + baseline sẵn sàng.
