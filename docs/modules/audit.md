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

## Phạm vi Capstone (user chốt 2026-11-01)
Bảng `audit_events` append-only tối thiểu cho các hành động tài khoản nhạy cảm: khóa/mở tài khoản, cấp lại mật khẩu tạm, thu hồi vai trò, gắn/gỡ liên kết phụ huynh–trẻ, kết thúc phân công (hiện chỉ có log INFO). Làm sau nhóm P0/P1 của review E2E; phải có trước phần audit của AUTH-19. Chi tiết: `CareNest_CODE/plans/reports/final-261101-1407-carenest-e2e-business-flow-review-report.md` FLOW-029.

## Phụ thuộc
Domain gọi service `audit` hoặc phát event. Không import domain.

## Known pitfalls
- Không ghi toàn bộ dữ liệu sức khỏe vào diff; chỉ field thay đổi cần thiết.

## Related issues
Chưa có.
