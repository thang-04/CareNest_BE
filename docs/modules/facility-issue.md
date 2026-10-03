# Module — facility-issue

Feature: `facilities` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: thiết kế

## Mục đích
GV báo sự cố cơ sở vật chất (hỏng/thiếu/không đủ); BGH theo dõi xử lý.

## Owns
FacilityReport (campus, location, type, description, photo?, status, history).

## KHÔNG owns
Danh mục tài sản, khấu hao, mua sắm, bảo trì định kỳ, kiểm kê (FAC-03 — ngoài V1).

## Rules
FAC-01..04. Flow: `docs/business/flows/facility-issue.md`.

## Phụ thuộc
- Dùng: service `organization`, identity (scope).
- Được dùng bởi: reporting. Event phát: `FacilityReportReported`, `FacilityReportStatusChanged` (notification).

## API & bảng
Chưa có.

## PENDING
Không có pending chặn. Trạng thái FAC-04 là PROPOSED.

## Known pitfalls
- Scope creep: yêu cầu "quản lý tài sản" ⇒ dừng, đối chiếu exclusion trong PROJECT_CONTEXT.

## Related issues
Chưa có.

## Đọc thêm khi
Không thường cần.
