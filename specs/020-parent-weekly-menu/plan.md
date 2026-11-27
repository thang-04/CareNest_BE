# Implementation Plan — thực đơn phụ huynh

**Spec**: [spec.md](spec.md) · **Date**: 2026-11-26 · Plan quản trị (duyệt, phase, rủi ro): `docs/plans/active/2026-11-26-parent-weekly-menu.md`.

## Summary
Ba endpoint chỉ đọc trong module meals cho phụ huynh; một luật hiển thị dùng chung cho tuần và ảnh.

## Technical context
Java 21, Spring Boot 4.1.1, JPA, PostgreSQL 17; không dependency, không migration. Test: integration (Testcontainers), `@WebMvcTest`, OpenAPI snapshot.

## Constitution
`.specify/memory/constitution.md` 1.0.0 dùng lại, không sửa. Nguyên tắc 2, 4, 5 áp dụng: kiểm quyền ở server, ảnh qua `FileStorage`, không đổi contract cũ.

## Research
- Giai đoạn học: `findChildStays` đủ cho tuần bất kỳ; phải lọc theo năm học của lớp.
- Đối chiếu dị ứng: dùng lại `KitchenAllergyWarnings.match` (cùng package).
- Ảnh: giữ cặp `url` + `content` như API ảnh hiện có.

## Analyze (26/11/2026)
| Kiểm | Kết quả |
| --- | --- |
| FR-1…FR-8 đều có AC và task | đạt |
| AC-1…AC-9 đều có task test | đạt |
| Quyết định PAR-14 xuất hiện ở spec, contract, tasks | đạt |
| Dữ liệu contract cần đều có nguồn trong data-model | đạt |
| Khác Jira G94-497 | ngày ngoài đợt học trả 200 thay vì từ chối; không món thay thế — user chốt |
| Còn chặn | chờ user duyệt plan; chưa có nhánh và mã công việc |
