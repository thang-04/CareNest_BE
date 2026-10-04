# Backend Architecture

Nguồn chuẩn cho code: `docs/backend-coding-guide.md` (từ `CareNest_Backend_Architecture_v2.docx` + quyết định nhóm). File này tóm tắt và bổ sung phần nghiệp vụ liên module; không lặp guide.

## Style (ADR-0001)

- **Layered monolith**: 1 Spring Boot app (Java 21, Boot 4.1.1), 1 PostgreSQL 17, `controller → service → repository` (guide mục 1, 4).
- Ranh giới nghiệp vụ = **feature sub-package** trong mỗi lớp (`service/attendance`, `service/meals`...). Ánh xạ module → feature: `PACKAGE_STRUCTURE.md`. Dependency giữa feature: `docs/system/MODULE_MAP.md`.
- Không microservice, không message broker, không Redis/Keycloak khi chưa có use case và nhóm chưa đồng ý (guide mục 2).

## Giao tiếp giữa feature (ADR-0004)

| Nhu cầu | Cơ chế |
| --- | --- |
| Đọc dữ liệu feature khác | Gọi service của feature đó (không inject repository của nó) |
| Phản ứng ngược chiều phụ thuộc (vd. attendance đổi sau khi suất đã xác nhận) | Spring application event, listener sau commit (PROPOSED) |
| AI, storage, notification | Interface trong `integration/` (`AiClient`, `StorageService`, ...) |

## Transaction

`@Transactional` ở service (guide mục 5). Use case ghi dữ liệu nhiều feature: ưu tiên 1 service điều phối trong 1 transaction nếu cùng nghiệp vụ; nếu là phản ứng phụ (thông báo, đánh dấu thay đổi) ⇒ event sau commit.

## Dữ liệu dẫn xuất (ADR-0005)

| Dẫn xuất | Chiến lược |
| --- | --- |
| Số suất ăn | NUT-01, NUT-02, NUT-03 (ADR-0005, guide 13.1) |
| Định lượng thực phẩm | Từ số suất đã xác nhận × định lượng thực đơn đã duyệt |
| Hồ sơ phát triển, báo cáo | Query lúc đọc, không lưu bản sao |
| Trend sức khỏe | Tính lúc đọc |

## Access scope (ADR-0002, ADR-0003)

- Campus/class/child scope kiểm tra ở service/security, đối chiếu assignment; không tin `campusId` client gửi (guide mục 9).
- Dữ liệu campus-scoped có campus; dữ liệu theo trẻ suy campus qua lớp tại ngày dữ liệu.
- Ngoài scope ⇒ `GlobalException(ApiCode.FORBIDDEN, ...)`. Test bắt buộc cho trường hợp bị từ chối.

## AI (ADR-0007, ADR-0008; guide 13.3)

Service nghiệp vụ → `AiClient` (`integration/`) → adapter provider. Kết quả là bản nháp chờ người duyệt; lỗi/timeout ⇒ `SERVICE_UNAVAILABLE`/`GATEWAY_TIMEOUT`, giữ dữ liệu người dùng; luồng không AI vẫn chạy.

## Response & lỗi

`{code, desc, data}` với `code` = HTTP status (`ApiCode`, `ResponseJson`), lỗi qua `GlobalException` + `GlobalExceptionHandler` — guide mục 8, 11; `docs/contracts/ERROR_CONTRACT.md`.
