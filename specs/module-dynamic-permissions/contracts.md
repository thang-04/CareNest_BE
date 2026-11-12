# Contract impact — Module dynamic permissions

- `GET /permissions`: catalog có thêm mã quyền mới.
- `GET /accounts/me`: `permissions` có thể chứa mã mới; schema mảng chuỗi không đổi.
- Các response lớp/sổ hiện có tiếp tục dùng `canRecord`, `canUnlock`, `canConfirm`, `canAdjust` khi đã có; giá trị được tính thêm permission.
- Endpoint, request DTO và response envelope không đổi.
- FE consumers: `src/models/bePermissions.js`, `src/routes/AppRoutes.jsx`, `src/layouts/menuConfig.js`, permission utilities/pages của school/attendance/assessment. Không sửa màn hoặc logic trung tâm phân quyền/tài khoản nhân sự.
