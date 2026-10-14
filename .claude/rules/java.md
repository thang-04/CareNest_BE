---
paths:
  - "src/**/*.java"
---

# Java — CareNest_BE

Nguồn chuẩn: `docs/backend-coding-guide.md` (mục 3–6, 10, 11). Rule này chỉ nhắc điểm hay sai; không thay guide.

- Layered: `controller → service → repository`; feature sub-package (`service/attendance/...`). Không tạo `domain/`, không DDD.
- Service feature A không inject repository feature B; gọi service B. Giữ chiều phụ thuộc `docs/system/MODULE_MAP.md`.
- Naming: hậu tố `XxxController/Service/Repository/Mapper/Request/Response`; method nói rõ nghiệp vụ (`confirmMealCount`), không `process/handle/getData`.
- Service: interface `service/<feature>/XxxService` + `service/<feature>/impl/XxxServiceImpl`; inject interface, không tiền tố `IXxx` (ADR-0014).
- Enum dùng chung ⇒ `utils/enums/`; hằng dùng ≥2 class hoặc giá trị hệ thống ⇒ `utils/Constants.java` (nested class theo nhóm). Hằng `private` 1 class giữ tại chỗ (guide 6.3).
- Lỗi nghiệp vụ: `throw new GlobalException(ApiCode.X, "desc")`; giữ cause khi bọc; không catch rỗng.
- Log `@Slf4j`, `LOG_PREFIX = "[TênClass]"`, format `[TênClass]|tênMethod|id=value|START/END` (guide mục 10, ADR-0016); chỉ log id — không dữ liệu sức khỏe, dị ứng, quan sát, họ tên, token, prompt AI.
- Comment ngắn, tiếng Việt, nói *tại sao*; có thể dẫn rule ID (`// NUT-03 ...`). Không code comment-out, không thông tin AI.
- Không hard-code giá trị nghiệp vụ PENDING (giờ chốt suất, chu kỳ đo...) — đưa vào cấu hình.
- Gọi AI/storage/notification chỉ qua `integration/`.
