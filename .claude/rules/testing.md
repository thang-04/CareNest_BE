---
paths:
  - "src/test/**"
---

# Testing — CareNest_BE

Nguồn chuẩn: guide mục 14; `docs/quality/TEST_STRATEGY.md`.

- Service: JUnit + Mockito cho hành vi chính, lỗi nghiệp vụ, bị từ chối quyền.
- Repository/migration/tích hợp: Testcontainers PostgreSQL thật (tự skip khi Docker không chạy).
- Controller: kiểm tra `{code, desc, data}` + HTTP status; thêm handler exception ⇒ thêm test vào `ResponseContractTest`.
- Bug fix ⇒ regression test fail trước/pass sau; ghi tên test vào incident.
- Kịch bản bắt buộc khi chạm vùng tương ứng: trẻ đến muộn sau khi suất đã xác nhận; truy cập chéo campus/lớp; AI tắt/timeout; input AI không chứa định danh trẻ.
- Dữ liệu giả; không dữ liệu trẻ/phụ huynh thật. Không xóa/skip test fail để qua build — báo rõ.
- Chạy: `./mvnw test` (Windows `mvnw.cmd test`).
