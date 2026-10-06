# Escalation — mở rộng context theo mức

Không mức nào đọc toàn bộ `docs/` trừ FULL. Đọc theo thứ tự; dừng khi đủ bằng chứng.

Làn (`AGENTS.md`): S = L1 tối thiểu (file đích + test, không card/guide) · M = L1–L2 · L = L3–L4.

| Mức | Khi nào | Đọc (cộng dồn) | Ngân sách gợi ý |
| --- | --- | --- | --- |
| L1 cục bộ | Bug/sửa trong 1 file/class | Source + test gần nhất · mục guide router chỉ tới (`docs/backend-coding-guide.md`) · module card · dòng liên quan trong `docs/knowledge/ISSUE_INDEX.md` | ~8–10k token (gồm AGENTS/router) |
| L2 domain | Feature/đổi hành vi trong 1 module | + flow trong card · rule của module trong `BUSINESS_RULES.md` — grep `"| ATT-"`, không đọc cả file · `GLOSSARY.md` khi gặp thuật ngữ lạ · contract/database docs nếu đụng API/DB | ~15–20k token |
| L3 nhiều module/repo | Chạm ≥2 module, event, dữ liệu dẫn xuất, API client dùng | + card các module chạm · `docs/system/MODULE_MAP.md` · `docs/knowledge/CROSS_MODULE_ISSUES.md` · `docs/system/CROSS_REPO_MAP.md` nếu đụng client · ADR được card trỏ tới | vài card + map |
| L4 hệ thống | Kiến trúc, RBAC/scope, AI layer, module boundary | + `docs/system/SYSTEM_ARCHITECTURE.md` · `docs/architecture/*` · mọi ADR liên quan · `PROJECT_CONTEXT.md` | đầy đủ phần liên quan |
| FULL | Thiết kế lại, audit toàn hệ thống | `docs/INDEX.md` → toàn bộ docs | không tối ưu token |

## Quy tắc

- Phát hiện dependency ngoài phạm vi đang đọc ⇒ nâng 1 mức, nói rõ lý do.
- Gặp rule PENDING/OPEN ⇒ không suy đoán: nêu khoảng trống, đề xuất làm cấu hình được, hoặc hỏi.
- File có header `Status: CHƯA CÓ NỘI DUNG` ⇒ không dùng làm nguồn.
- Code, docs, yêu cầu mâu thuẫn ⇒ nêu xung đột, xác minh intended behavior trước khi sửa.
- Đường dẫn trong `CONTEXT_MAP.yaml > planned` chưa tồn tại — không viện dẫn.
