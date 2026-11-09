# Specification Quality Checklist: Phân biệt kế hoạch năm học và kế hoạch tổ

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-11-09
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs), ngoài ràng buộc tách lưu trữ user yêu cầu tại FR-010
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification, ngoại trừ ràng buộc lưu trữ do user chỉ định

## Notes

- Review vòng 1 ghi nhận Q1/Q2. Review vòng 2 sau trả lời user09/11: FR-013/US3.6 chốt chuyển nguồn mới nhất, giữ phần đã nhập, bản chờ về nháp; FR-014/US3.7 chốt baseline nguồn đang dùng và chủ đề đã kết thúc không đỏ. Không còn marker/câu hỏi mở; 16/16 mục checklist đạt ở mức đặc tả, sẵn sàng plan. Không phải kết quả kiểm ứng dụng.
- Đã bỏ phương án modal/tự lập tổ theo chỉ đạo cuối; giao diện là trang soạn trực tiếp. Giữ ba quyết định user đã trả lời trong cùng chat.
- `database-context.md` tách phần giải thích DB hiện tại/đích khỏi đặc tả nghiệp vụ; chưa có DDL/contract/plan được duyệt.
- Template được resolve bằng đọc trực tiếp: không có overrides/presets/extensions, dùng `.specify/templates/spec-template.md`. Constitution 2.0.0 phù hợp chỉ đạo 07/11.
- Bỏ toàn bộ extension hook trước/sau specify theo AGENTS.md chỉ đạo 07/11 và constitution V; không chạy script điều phối. Không tạo branch/commit/push.
- Refinement theo user “fix”09/11: thêm FR-016/017 và AC cấm xóa tổ/TT duyệt; FR-004 kiểm cả API/import. Đồng bộ plan/model/contract/tasks/quickstart theo context mới, xem [analysis](../analysis.md). Checklist là chất lượng đặc tả, không chứng nhận test hoặc plan đã duyệt.
- Bước tiếp theo: review/phê duyệt thiết kế sửa theo phạm vi T001/T047 trước implementation; thực thi checks runtime ở các task tương ứng, không phải tạo lại plan đã tồn tại.
