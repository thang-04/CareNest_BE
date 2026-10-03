---
paths:
  - "src/main/java/com/carenest/entity/**"
  - "src/main/java/com/carenest/repository/**"
  - "src/main/resources/db/migration/**"
---

# Entity / repository / migration — CareNest_BE

Nguồn chuẩn: guide mục 12; `docs/database/DATABASE.md`.

- Schema chỉ đổi qua Flyway mới `V{n}__{mo_ta_ngan}.sql`; không sửa migration đã commit (hỏi trước).
- `snake_case`; ràng buộc nghiệp vụ có ràng buộc DB (vd. unique điểm danh trẻ/ngày để upsert idempotent).
- Repository chỉ truy cập dữ liệu, không nghiệp vụ. Entity không trả qua API, không làm request body.
- Dữ liệu campus-scoped có campus; query nhận điều kiện scope từ service.
- Không lưu binary trong bảng; dùng `StorageService` + `MediaAsset`.
- Tránh N+1 (fetch có chủ đích). Quan hệ JPA xuyên feature: ưu tiên ID, không tạo vòng.
- Cập nhật `docs/database/DATA_DICTIONARY.md` (+ `ERD.md` khi đổi quan hệ) cùng thay đổi.
