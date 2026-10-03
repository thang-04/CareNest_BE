# Module — audit

Feature: `audit` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: thiết kế

## Mục đích
Truy vết thay đổi nhạy cảm: ai, làm gì, lúc nào, trên dữ liệu nào.

## Owns
AuditEvent (append-only): actor, action, entityType, entityId, campusId?, timestamp, diff tối thiểu.

## KHÔNG owns
Business state; không dùng audit để khôi phục dữ liệu nghiệp vụ.

## Hành động cần audit (khởi đầu)
Xác nhận/điều chỉnh MealCount · duyệt MealPlan · duyệt Summary/HealthInterpretation · sửa dữ liệu sức khỏe · đổi role/assignment/GuardianChildLink · đăng nhập thất bại lặp.

## Phụ thuộc
Domain gọi service `audit` hoặc phát event. Không import domain.

## Known pitfalls
- Không ghi toàn bộ dữ liệu sức khỏe vào diff; chỉ field thay đổi cần thiết.

## Related issues
Chưa có.
