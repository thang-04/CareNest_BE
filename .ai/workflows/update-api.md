# Workflow — Thay đổi API

Đổi contract = làn L (`plan-change.md`). Thêm endpoint không phá contract cũ trong 1 module = làn M.

1. **Caller & client:** grep path trong `docs/api/API_INDEX.md` (endpoint → controller#method) và `docs/system/FE_API_CALLS.md` (file Web đang gọi endpoint đó); quy ước và điểm chạm: `docs/system/CROSS_REPO_MAP.md`. Không đọc cả `openapi.yaml`. Breaking change? Nếu có ⇒ thống nhất với FE/APP trước, hoặc versioning.
2. **Contract:** theo `docs/contracts/API_CONVENTIONS.md` + `ERROR_CONTRACT.md`. OpenAPI sinh bởi springdoc; snapshot khóa ở `docs/api/openapi.yaml` (`OpenApiSnapshotTest`). Đổi có chủ đích ⇒ `mvnw.cmd test -Dtest=OpenApiSnapshotTest -Dopenapi.snapshot.update=true`, đưa `git diff docs/api/openapi.yaml` vào báo cáo; rồi `node scripts/ai-layer/api-index.mjs` để sinh lại `API_INDEX.md` (pre-commit chặn nếu lệch). Đổi endpoint/DTO/`ResponseJson`/`ApiCode`/prefix ⇒ hỏi nhóm trước (AGENTS.md, mục Hỏi trước khi làm).
3. **Endpoint & DTO:** controller trong `controller/<feature>`, theo `.claude/rules/controller.md`. Không trả entity; không trả field ngoài quyền (đặc biệt DTO phụ huynh — ADR-0010).
4. **Permission & scope:** permission mới ⇒ `docs/business/USER_ROLES.md`.
5. **Lỗi:** dùng `ApiCode` có sẵn, phân biệt bằng `desc`; chỉ thêm `ApiCode` khi cần HTTP status mới (hỏi nhóm) — `ERROR_CONTRACT.md`.
6. **Test:** controller test kiểm tra `{code, desc, data}` + HTTP status; service test cho bị từ chối quyền.
7. **Thông báo client:** liệt kê thay đổi FE/APP cần làm theo consumer cụ thể — lấy danh sách file Web từ `docs/system/FE_API_CALLS.md` (màn hình/route, không ghi "mọi caller"); ghi rõ phần client chưa kiểm thử. Có repo FE cạnh bên ⇒ chạy `node ../CareNest_FE/scripts/check-api-contract.mjs` và đưa kết quả vào báo cáo. Web: module FE nào đang `demo`/`pending` trong `integrationModules.js` (thư mục config của src, repo FE) có thể chuyển sang BE ⇒ ghi vào báo cáo để FE đồng bộ (`check-api-contract.mjs --sync`, đổi module sang `api`). Ghi chú "chưa tích hợp" không thay cổng — xem `docs/knowledge/incidents/BUG-261101-web-nonmock-unwired-screens.md`.
