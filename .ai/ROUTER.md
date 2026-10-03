# Chọn context cho task Backend

Đọc `AGENTS.md` và mô tả task trước. Chọn một hàng chính; chỉ thêm hàng khác khi phát hiện tác động tương ứng. Mở file được chỉ tới, không đọc mọi profile/workflow.

| Task | Profile | Workflow | Mục guide | Mức đầu tiên |
| --- | --- | --- | --- | --- |
| Bug, validation, refactor cục bộ | `profiles/code.md` | `workflows/fix-bug.md` | 4, 5, 11 + mục của vùng sửa | L1 |
| Tính năng hoặc thay đổi nghiệp vụ trong BE | `profiles/feature.md` | `workflows/implement-feature.md` | 3–11, 13 | L2 |
| Review code | `profiles/code.md` | `workflows/review-code.md` | 17 (Checklist) + mục bị vi phạm | L1 |
| Đổi endpoint, DTO, auth contract hoặc lỗi API | `profiles/feature.md` | `workflows/update-api.md` | 7, 8, 9, 11 | L2; L3 nếu ảnh hưởng client |
| Đổi entity, schema, migration hoặc query contract | `profiles/feature.md` | `workflows/update-database.md` | 12, 13 | L2 |
| Tác động từ BE sang FE/APP hoặc nhiều domain | `profiles/cross-repo.md` | Workflow theo loại thay đổi | 7, 8 + theo loại thay đổi | L3 |
| Kiến trúc toàn hệ thống, quyền truy cập lớn, đa cơ sở hoặc vận hành | `profiles/cross-repo.md` | Chọn workflow hiện có nếu phù hợp | Toàn bộ guide | L4 |

"Mục guide" là số mục trong `docs/backend-coding-guide.md`. Mọi task có sửa code đều kết thúc bằng mục 17 (Checklist). Quy tắc L1–L4 ở `ESCALATION.md`. Danh sách tài liệu hiện có ở `CONTEXT_MAP.yaml`; mục `planned` không được coi là đã tồn tại.

