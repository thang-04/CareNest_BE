# ADR-0023 — Phạm vi quyền sửa theo mảng lưu trên ngoại lệ quyền

- Status: ACCEPTED
- Date: 2026-10-31
- Liên quan: AUTH-02, AUTH-20, NUT-02, P-36 (đóng 31/10); ADR-0017 (phân quyền cấu hình được), ADR-0003 (scope); module identity-access; plan `2026-10-31-vp-domain-scope.md`

## Bối cảnh
User chốt 31/10: HT giao mảng (vd. dinh dưỡng) cho PHT kèm phạm vi **một điểm trường** (điểm trường PHT đang phân công) hoặc **cả trường**; mảng không được giao thì PHT chỉ xem, xem cả trường. ADR-0017 chỉ có quyền bật/tắt (ma trận role + GRANT/REVOKE từng người), không có phạm vi. Scope hiện tại = campus phân công PHT (ADR-0003).

## Quyết định
- Mã quyền sửa theo mảng có cờ `campusScoped` trong enum `Permission` (đầu tiên: `MEAL_COUNT_CONFIRM`).
- `user_permission_overrides.scope` (`CAMPUS`/`SCHOOL`, chỉ GRANT, CHECK DB — V40). Ô ma trận bật và GRANT không ghi phạm vi = `CAMPUS`. `SCHOOL` chỉ nhận cho mã `campusScoped` (API trả 400 `INVALID_VALUE`).
- `CurrentUser.schoolWidePermissions` = mã được GRANT `SCHOOL` (tính mỗi request cùng quyền hiệu lực); `/accounts/me` trả `schoolWidePermissions`.
- Kiểm sửa: `CampusAccessPolicy.canEditCampus(user, permission, campusId)` = có mã ∧ (HT có phân công ∨ PHT + `SCHOOL` + còn phân công PHT ∨ PHT + phân công tại campus đó). Module mới (thực đơn, kho) dùng hàm này, không tự kiểm.
- Đọc tách khỏi sửa: PHT còn phân công đọc mọi campus (`canReadAllCampuses`), không cần mã quyền; `ALL_CAMPUS_VIEW` bỏ (V39).
- Lịch sử: `permission_change_log.before_scope/after_scope`; đổi chỉ phạm vi ghi một dòng `GRANT → GRANT`.

## Phương án đã cân nhắc
| Phương án | Ưu | Nhược |
| --- | --- | --- |
| Cột `scope` trên ngoại lệ (chọn) | Dùng lại API, màn ngoại lệ, lịch sử; một nơi cấu hình mỗi quyền | Phạm vi theo từng mã, không theo cả mảng; ô ma trận không có phạm vi (luôn điểm trường) |
| Bảng mới "phân công mảng" (user, mảng, phạm vi) | Khớp cách nói "giao mảng" | Hai nơi cấu hình cùng một quyền (mã + mảng), dễ lệch; thêm API/màn mới |
| Thêm cột phạm vi vào `staff_assignments` | Gắn với phân công PHT | Một phân công không mang được nhiều mảng với phạm vi khác nhau |

## Hệ quả
- Mã sửa theo mảng mới ⇒ khai `campusScoped = true`, seed ma trận nếu PHT mặc định bật, kiểm bằng `canEditCampus`.
- Muốn PHT sửa cả trường ⇒ HT tạo GRANT `SCHOOL` cho đúng người (kể cả khi ô ma trận đã bật).
- FE: màn ngoại lệ chọn phạm vi khi GRANT mã `campusScoped`; menu/nút theo `permissions` + `schoolWidePermissions` + `assignments`.

## Còn chờ
- Kế hoạch giáo dục (EDU-08) chưa vào ma trận; khi vào thì dùng cùng cơ chế.
