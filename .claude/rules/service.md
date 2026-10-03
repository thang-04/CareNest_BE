---
paths:
  - "src/main/java/com/carenest/service/**"
---

# Service — CareNest_BE

Nguồn chuẩn: guide mục 5, 9, 13.

- `@Transactional` (đọc: `readOnly = true`). Không dùng `HttpStatus`, `ResponseEntity`, `ResponseJson`.
- Thứ tự: kiểm tra quyền theo campus/class/child (đối chiếu assignment, không tin id client gửi) → rule nghiệp vụ (dẫn rule ID trong `docs/business/BUSINESS_RULES.md`) → lưu → trả DTO.
- Cần dữ liệu feature khác ⇒ gọi service của nó; phản ứng ngược chiều phụ thuộc ⇒ Spring event sau commit (ADR-0004), không gọi vòng.
- Số suất ăn: đã xác nhận thì không sửa ngầm; thay đổi ⇒ lịch sử + yêu cầu xác nhận lại (ADR-0005).
- AI qua `AiClient`: input tối thiểu, bí danh; kết quả là bản nháp chờ duyệt; lỗi/timeout ⇒ `SERVICE_UNAVAILABLE`/`GATEWAY_TIMEOUT`, giữ dữ liệu người dùng.
- Rule PENDING: không tự quyết — cấu hình hoặc hỏi.
