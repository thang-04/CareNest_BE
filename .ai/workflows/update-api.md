# Workflow — Thay đổi API

1. **Caller & client:** endpoint nào, FE/APP màn hình nào dùng (`docs/system/CROSS_REPO_MAP.md`). Breaking change? Nếu có ⇒ thống nhất với FE/APP trước, hoặc versioning.
2. **Contract:** theo `docs/contracts/API_CONVENTIONS.md` + `ERROR_CONTRACT.md`. OpenAPI sinh bởi springdoc (`/swagger-ui/index.html`), cập nhật cùng code; export nếu cần để ở `docs/api/`. Đổi endpoint/DTO/`ResponseJson`/`ApiCode`/prefix ⇒ hỏi nhóm trước (AGENTS.md, mục Hỏi trước khi làm).
3. **Endpoint & DTO:** controller trong `controller/<feature>`, theo `.claude/rules/controller.md`. Không trả entity; không trả field ngoài quyền (đặc biệt DTO phụ huynh — ADR-0010).
4. **Permission & scope:** permission mới ⇒ `docs/business/USER_ROLES.md`.
5. **Lỗi:** dùng `ApiCode` có sẵn, phân biệt bằng `desc`; chỉ thêm `ApiCode` khi cần HTTP status mới (hỏi nhóm) — `ERROR_CONTRACT.md`.
6. **Test:** controller test kiểm tra `{code, desc, data}` + HTTP status; service test cho bị từ chối quyền.
7. **Thông báo client:** liệt kê thay đổi FE/APP cần làm; ghi rõ phần client chưa kiểm thử.
