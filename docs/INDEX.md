# Docs Index — CareNest_BE

Người đọc: kiến trúc tài liệu AI giải thích ở `README_AI.md` (cùng thư mục). Agent: không đọc hết. Task thường: `.ai/ROUTER.md` → module card. Status: **FULL** = dùng làm nguồn · **SKELETON** = chưa có nội dung, không dùng làm nguồn.

| Nhóm | File | Nội dung | Status |
| --- | --- | --- | --- |
| context | `context/PROJECT_CONTEXT.md` | Trường, actor, vấn đề, scope V1, exclusions, quy ước trạng thái | FULL |
| | `context/CURRENT_STATE.md` | Đã làm / đang làm / chưa làm | FULL |
| | `context/GLOSSARY.md` | Thuật ngữ VN ↔ code | FULL |
| modules | `modules/README.md` + 12 card (thêm education-plan, xóa card CSVC 29/10) | **Điểm đọc chính** theo module | FULL |
| business | `business/BUSINESS_RULES.md` | Rule có ID + status; Pending register | FULL |
| | `business/USER_ROLES.md` | Role, scope, permission | FULL |
| | `business/DOMAIN_MODEL.md` | Entity theo module, quan hệ | FULL |
| | `business/flows/*.md` | attendance, pickup, leave-request, meal-management, health-check, education-plan, child-observation | FULL |
| system | `system/SYSTEM_ARCHITECTURE.md` | BE + FE + APP, AI layer, scope | FULL |
| | `system/MODULE_MAP.md` | Dependency, thứ tự lớp, cycle đã phá | FULL |
| | `system/CROSS_REPO_MAP.md` | Ownership BE/FE/APP, điểm chạm | FULL |
| | `system/DEPLOYMENT.md` | Hạ tầng | SKELETON |
| | `system/KEYCLOAK.md` | Chạy/cấu hình Keycloak, lấy token, lỗi thường gặp | FULL |
| guide | `backend-coding-guide.md` | **Quy tắc code bắt buộc** (layer, response, logging, exception, migration, checklist) | FULL — nguồn chuẩn code |
| architecture | `architecture/BACKEND_ARCHITECTURE.md` | Layered monolith, giao tiếp feature, dẫn xuất | FULL |
| | `architecture/PACKAGE_STRUCTURE.md` | Module → feature sub-package | FULL |
| | `architecture/DATA_FLOW.md` | Luồng trong/giữa module, dữ liệu nhạy cảm | FULL |
| | `architecture/SECURITY.md` | Authz, privacy, AI data | FULL |
| contracts | `contracts/API_CONVENTIONS.md` | URL, prefix, pagination (tóm tắt guide) | FULL |
| | `contracts/ERROR_CONTRACT.md` | `{code, desc, data}` + ApiCode | FULL |
| | `contracts/AUTH_CONTRACT.md` | Auth: Keycloak, Bearer token, 401/403 | ĐANG TRIỂN KHAI |
| api | `api/openapi.yaml` | Snapshot hợp đồng OpenAPI, khóa bởi `OpenApiSnapshotTest` (đổi có chủ đích: `update-api.md`) | FULL |
| api | `api/CareNest.postman_collection.json` + `CareNest-local.postman_environment.json` | Postman: mọi API hiện có + ma trận phân quyền (kiểm bằng newman) | FULL |
| database | `database/DATABASE.md` | Nguyên tắc DB | FULL |
| | `database/ERD.md`, `database/DATA_DICTIONARY.md` | Schema | SKELETON |
| knowledge | `knowledge/ISSUE_INDEX.md` | **Search đầu tiên khi debug** | FULL (chưa có issue) |
| | `knowledge/incidents/_TEMPLATE.md` | Mẫu incident (status open/fixed, có Attempts) | FULL |
| | `knowledge/CROSS_MODULE_ISSUES.md` | Rủi ro CMR + issue liên module | FULL |
| | `knowledge/KNOWN_ISSUES.md`, `TROUBLESHOOTING.md`, `PATTERNS.md` | Giới hạn cố ý (không phải bug), lỗi môi trường/config, pattern | FULL |
| decisions | `decisions/ADR-0001..0015`, `ADR-TEMPLATE.md` | Quyết định kiến trúc | FULL |
| plans | `plans/_TEMPLATE.md`, `plans/active/`, `plans/completed/` | Plan làn L (template + đang làm + đã xong) | FULL |
| quality | `quality/DEFINITION_OF_DONE.md`, `VERIFICATION.md`, `TEST_STRATEGY.md`, `NFR.md` | Tiêu chí hoàn thành theo làn, bằng chứng kiểm chứng, test, NFR | FULL |

## ADR

| ADR | Quyết định | Status |
| --- | --- | --- |
| 0001 | Layered monolith, ranh giới theo feature | ACCEPTED |
| 0002 | Campus data scoping (không multi-tenant) | ACCEPTED |
| 0003 | Role × Permission ∩ Scope | ACCEPTED |
| 0004 | Giao tiếp feature: gọi service + event khi ngược chiều | ACCEPTED / event PROPOSED |
| 0005 | Số suất đã xác nhận: lịch sử + xác nhận lại | ACCEPTED (chi tiết chốt 28/10) |
| 0006 | Nguồn ngữ cảnh hoạt động / GoKids | SUPERSEDED by 0019 |
| 0007 | AI provider abstraction + data minimization | ACCEPTED (provider Gemini/Groq) |
| 0008 | Human-in-the-loop AI | ACCEPTED |
| 0009 | Phạm vi kho/NCC | ACCEPTED (cập nhật 28/10) |
| 0010 | Parent visibility default-deny | ACCEPTED (danh sách chốt 29/10) |
| 0011 | Keycloak + Spring Security Resource Server; scope ở CareNest | ACCEPTED |
| 0012 | Engineering harness: làn S/M/L, Iron Law, hook, cổng Maven | ACCEPTED |
| 0013 | React-owned Web auth gateway (Direct Grant qua confidential client) | ACCEPTED |
| 0014 | Service interface + Impl; enum/hằng dùng chung ở `utils/` | ACCEPTED |
| 0015 | Phiên Web trong cookie mã hóa, Keycloak giữ phiên | ACCEPTED |
| 0019 | Module kế hoạch giáo dục làm lại trên `dev` | ACCEPTED |
