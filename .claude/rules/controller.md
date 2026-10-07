---
paths:
  - "src/main/java/com/carenest/controller/**"
---

# Controller — CareNest_BE

Nguồn chuẩn: guide mục 5, 7, 8.

- Chỉ gọi service; không inject repository; không logic nghiệp vụ; không trả entity.
- `@RequestMapping("/resource")` — không ghi prefix API (prefix từ `carenest.api.prefix`).
- `@Valid` request DTO; trả `ResponseJson.toJsonWithData(ApiCode.X, desc, data)` / `ResponseJson.toJson(...)`. Lỗi ⇒ để service ném `GlobalException`.
- Thành công khác 200 (vd. `ApiCode.CREATED`) ⇒ thêm `@ResponseStatus(HttpStatus.CREATED)`, nếu không OpenAPI ghi 200 (BUG-261007-openapi-create-account-status).
- Danh sách: phân trang, `data` = `PageResponse`.
- Xác nhận/duyệt: `POST /{id}/confirm|approve`. DTO phụ huynh riêng, chỉ field được phép (ADR-0010).
- Đổi endpoint/DTO ⇒ hỏi nhóm, cập nhật snapshot `docs/api/openapi.yaml`, nêu tác động FE/APP (`.ai/workflows/update-api.md`).
