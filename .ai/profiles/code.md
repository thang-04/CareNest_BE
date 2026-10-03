# Profile CODE — sửa cục bộ, bug, review nhỏ

Mức: L1. Mục tiêu: tốn ít context nhất mà vẫn đúng.

Đọc:
1. Source + test gần vị trí lỗi; caller/callee trực tiếp; thay đổi gần đây (git log file).
2. `docs/knowledge/ISSUE_INDEX.md`: grep chuỗi lỗi + tên module (bug).
3. Module card `docs/modules/<module>.md` — mục Rules, Known pitfalls.
4. Mục `docs/backend-coding-guide.md` theo cột "Mục guide" của router; kết thúc bằng Checklist mục 17.

Không đọc: PROJECT_CONTEXT, DOMAIN_MODEL, ADR (trừ khi card trỏ tới).

Nâng lên L2/L3 khi sửa có thể đổi business rule, dữ liệu dẫn xuất (MealCount), scope, API contract (`.ai/ESCALATION.md`).
