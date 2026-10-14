# ADR-0016 — Log START/END đơn giản cho controller và service

- Status: ACCEPTED
- Date: 2026-10-14
- Liên quan: G94-25, identity-access, school-structure, child, ADR-0014

## Bối cảnh

Team cần log để xem luồng chạy và tìm lỗi nhanh. Bản thử trong ngày (controller tự `try-catch` trả lỗi qua mapper chung, filter tạo `requestId`, log `END` trong `finally` kèm `outcome`/`durationMs`, log cả private helper) làm mỗi method dài gấp nhiều lần, khó đọc và khó review; user yêu cầu bỏ.

## Quyết định

1. Controller và `XxxServiceImpl` có `private static final String LOG_PREFIX = "[TênClass]"`. Method public ghép `logPrefix = LOG_PREFIX + "|tênMethod|id=value..."` (chỉ id/role/status), ghi `log.info` `START` và `END`. Private helper không log.
2. Controller không `try-catch`; lỗi đi lên `GlobalExceptionHandler` như trước, response lỗi `{code, desc, data}` và HTTP status không đổi.
3. Service bọc thân method public bằng `try-catch (RuntimeException)`: `log.warn("{}|ERROR|type={}", ...)` rồi `throw ex`. Catch bù trừ/fallback sẵn có giữ nguyên.
4. Không dùng `finally`, `requestId`, `durationMs` cho log. `finally` chỉ giữ khi là logic nghiệp vụ (xóa cookie khi logout, thu hồi token Keycloak).
5. Lỗi không dự kiến ở `GlobalExceptionHandler` log `error` kèm exception như trước (user chọn, chấp nhận message của thư viện có thể xuất hiện trong log).
6. Auth/OTP/reset chỉ log tên method (và `userId` khi đã đăng nhập); không log username, email, cookie, OTP request id, token.

## Phương án đã cân nhắc

| Phương án | Ưu | Nhược |
| --- | --- | --- |
| Controller catch + `ExceptionResponseMapper` + `RequestTraceFilter` + END trong `finally` | Đo thời gian, gom log theo request | Boilerplate lớn, khó đọc; thêm 4 class hạ tầng |
| **START/END đơn giản, service catch log ERROR rồi ném lại** | Dễ đọc, dễ grep theo `[TênClass]|tênMethod` | Không gom log theo request, không đo thời gian |
| Chỉ log ở service | Ít log nhất | Không thấy request vào endpoint nào |

## Hệ quả

- Code mới theo mẫu guide mục 10; review kiểm `LOG_PREFIX`, START/END, catch ở service luôn `throw ex`.
- Muốn đo thời gian hay gom theo request về sau ⇒ ADR mới (vd. MDC/filter), không thêm lẻ từng method.

## Còn chờ

Không.
