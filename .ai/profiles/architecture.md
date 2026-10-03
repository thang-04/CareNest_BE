# Profile ARCHITECTURE — kiến trúc, nhiều module, security, database lớn

Mức: L3–L4. Ưu tiên đủ bằng chứng hơn tiết kiệm token.

Đọc:
1. `docs/system/MODULE_MAP.md`, `docs/system/SYSTEM_ARCHITECTURE.md`.
2. `docs/architecture/` (BACKEND_ARCHITECTURE, PACKAGE_STRUCTURE, DATA_FLOW, SECURITY).
3. Card mọi module bị chạm; `docs/knowledge/CROSS_MODULE_ISSUES.md`.
4. ADR liên quan trong `docs/decisions/`.

Kiểm tra: cycle dependency mới? thứ tự lớp trong MODULE_MAP? tác động scope campus? dữ liệu dẫn xuất? FE/APP?

Quyết định kiến trúc mới hoặc đảo quyết định cũ ⇒ viết ADR (`docs/decisions/ADR-TEMPLATE.md`), không chỉ sửa code. Không đảo ADR ACCEPTED khi chưa có bằng chứng mới hoặc quyết định của người dùng.
