# Profile FEATURE — chức năng mới / đổi nghiệp vụ

Mức: L2. Bắt đầu từ actor và outcome, không từ bảng DB.

Đọc:
1. Module card của module sở hữu (`.ai/CONTEXT_MAP.yaml` → `keywords`).
2. Flow trong card (`docs/business/flows/`), rule ID nhóm module trong `docs/business/BUSINESS_RULES.md`.
3. `docs/business/USER_ROLES.md` nếu có permission mới.
4. `docs/contracts/API_CONVENTIONS.md` / `docs/database/DATABASE.md` khi thiết kế API/bảng.
5. `docs/backend-coding-guide.md` mục 3–11, 13 (thêm 12 khi đụng DB) + `.claude/rules/` theo loại file; kết thúc bằng Checklist mục 17.

Kiểm tra trước khi code: rule cần dùng có status CONFIRMED/ACCEPTED? Nếu PENDING/OPEN ⇒ hỏi hoặc làm cấu hình được. Một entity mới không mặc nhiên cần module mới. Feature có AI ⇒ có baseline không AI + người duyệt.
