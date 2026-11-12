# Data model — Module dynamic permissions

Không thêm bảng. Dùng lại `role_permissions`, `user_permission_overrides`, `permission_change_log` và `scope` hiện có.

Thêm các code vào enum/catalog và migration insert grant role tương thích dữ liệu cũ. Permission chỉ biểu diễn hành động; campus/class/child tiếp tục lấy từ staff assignment và policy hiện có. Rollback dữ liệu thực hiện bằng migration mới nếu cần, không sửa migration đã commit.
