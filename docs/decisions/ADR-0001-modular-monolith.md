# ADR-0001 — Backend là layered monolith với ranh giới theo feature

- Status: ACCEPTED
- Date: 2026-10-03
- Nguồn: `CareNest_Backend_Architecture_v2.docx`, `docs/backend-coding-guide.md` mục 1–4, scaffold `com.carenest` (commit "scaffold Spring Boot backend")

## Bối cảnh
Team 5 người, SEP490, thời gian giới hạn; nhiều nghiệp vụ liên kết chặt (điểm danh → suất ăn → bếp → báo cáo); 1 trường ~506 trẻ. Brief kiến trúc muốn "modular monolith" với ranh giới đủ rõ để AI/dev không tạo dependency tùy tiện.

## Quyết định
- 1 ứng dụng Spring Boot, 1 PostgreSQL, tổ chức **theo lớp** `controller → service → repository` (guide). Không DDD tactical, không package `domain/`.
- Ranh giới nghiệp vụ ("module" trong `docs/modules/`) thể hiện bằng **feature sub-package** trong từng lớp và quy tắc phụ thuộc giữa feature (`docs/system/MODULE_MAP.md`, `docs/architecture/PACKAGE_STRUCTURE.md`).

## Phương án đã cân nhắc
| Phương án | Ưu | Nhược |
| --- | --- | --- |
| Layered + feature sub-package (chọn) | Quen thuộc với nhóm, đã scaffold, đơn giản | Ranh giới feature dựa vào kỷ luật + review |
| Modular monolith package-by-module (api/application/domain) | Ranh giới mạnh | Khác guide/scaffold đã chốt, nặng cho nhóm |
| Microservices | Độc lập deploy | Quá tải vận hành |

## Hệ quả
Service feature A không dùng repository feature B; không tạo vòng phụ thuộc giữa feature. Có thể bổ sung test kiến trúc (ArchUnit) sau nếu nhóm đồng ý — không tự thêm dependency. → Đã làm: ADR-0012 (`ArchitectureRulesTest`).
