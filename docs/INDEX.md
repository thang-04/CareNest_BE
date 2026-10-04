# Docs Index — CareNest_BE

Người đọc: kiến trúc tài liệu AI giải thích ở `README_AI.md` (cùng thư mục). Agent: không đọc hết. Task thường: `.ai/ROUTER.md` → module card. Status: **FULL** = dùng làm nguồn · **SKELETON** = chưa có nội dung, không dùng làm nguồn.

| Nhóm | File | Nội dung | Status |
| --- | --- | --- | --- |
| context | `context/PROJECT_CONTEXT.md` | Trường, actor, vấn đề, scope V1, exclusions, quy ước trạng thái | FULL |
| | `context/CURRENT_STATE.md` | Đã làm / đang làm / chưa làm | FULL |
| | `context/GLOSSARY.md` | Thuật ngữ VN ↔ code | FULL |
| modules | `modules/README.md` + 12 card | **Điểm đọc chính** theo module | FULL |
| business | `business/BUSINESS_RULES.md` | Rule có ID + status; Pending register | FULL |
| | `business/USER_ROLES.md` | Role, scope, permission | FULL |
| | `business/DOMAIN_MODEL.md` | Entity theo module, quan hệ | FULL |
| | `business/flows/*.md` | attendance, leave-request, meal-management, health-check, facility-issue, child-observation | FULL |
| system | `system/SYSTEM_ARCHITECTURE.md` | BE + FE + APP, AI layer, scope | FULL |
| | `system/MODULE_MAP.md` | Dependency, thứ tự lớp, cycle đã phá | FULL |
| | `system/CROSS_REPO_MAP.md` | Ownership BE/FE/APP, điểm chạm | FULL |
| | `system/DEPLOYMENT.md` | Hạ tầng | SKELETON |
| guide | `backend-coding-guide.md` | **Quy tắc code bắt buộc** (layer, response, logging, exception, migration, checklist) | FULL — nguồn chuẩn code |
| architecture | `architecture/BACKEND_ARCHITECTURE.md` | Layered monolith, giao tiếp feature, dẫn xuất | FULL |
| | `architecture/PACKAGE_STRUCTURE.md` | Module → feature sub-package | FULL |
| | `architecture/DATA_FLOW.md` | Luồng trong/giữa module, dữ liệu nhạy cảm | FULL |
| | `architecture/SECURITY.md` | Authz, privacy, AI data | FULL |
| contracts | `contracts/API_CONVENTIONS.md` | URL, prefix, pagination (tóm tắt guide) | FULL |
| | `contracts/ERROR_CONTRACT.md` | `{code, desc, data}` + ApiCode | FULL |
| | `contracts/AUTH_CONTRACT.md` | Auth | SKELETON |
| api | `api/` | OpenAPI export (springdoc) | chưa có |
| database | `database/DATABASE.md` | Nguyên tắc DB | FULL |
| | `database/ERD.md`, `database/DATA_DICTIONARY.md` | Schema | SKELETON |
| knowledge | `knowledge/ISSUE_INDEX.md` | **Search đầu tiên khi debug** | FULL (chưa có issue) |
| | `knowledge/incidents/_TEMPLATE.md` | Mẫu incident (status open/fixed, có Attempts) | FULL |
| | `knowledge/CROSS_MODULE_ISSUES.md` | Rủi ro CMR + issue liên module | FULL |
| | `knowledge/KNOWN_ISSUES.md`, `TROUBLESHOOTING.md`, `PATTERNS.md` | Giới hạn cố ý (không phải bug), lỗi môi trường/config, pattern | FULL |
| decisions | `decisions/ADR-0001..0010`, `ADR-TEMPLATE.md` | Quyết định kiến trúc | FULL |
| plans | `plans/active/`, `plans/completed/` | Kế hoạch thay đổi lớn | — |
| quality | `quality/DEFINITION_OF_DONE.md`, `TEST_STRATEGY.md`, `NFR.md` | Tiêu chí hoàn thành, test, NFR | FULL |

## ADR

| ADR | Quyết định | Status |
| --- | --- | --- |
| 0001 | Layered monolith, ranh giới theo feature | ACCEPTED |
| 0002 | Campus data scoping (không multi-tenant) | ACCEPTED |
| 0003 | Role × Permission ∩ Scope | ACCEPTED |
| 0004 | Giao tiếp feature: gọi service + event khi ngược chiều | ACCEPTED / event PROPOSED |
| 0005 | Số suất đã xác nhận: lịch sử + xác nhận lại | ACCEPTED (P-04 pending) |
| 0006 | Nguồn ngữ cảnh hoạt động / GoKids | **OPEN** |
| 0007 | AI provider abstraction + data minimization | ACCEPTED / provider OPEN |
| 0008 | Human-in-the-loop AI | ACCEPTED |
| 0009 | Phạm vi kho/NCC | **OPEN** |
| 0010 | Parent visibility default-deny | ACCEPTED (field pending) |
