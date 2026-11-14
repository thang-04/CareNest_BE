# Data model — Ảnh đại diện nhân sự (019)

Không migration. Dùng lại cột có sẵn của `user_accounts` (`UserAccount.java:61-67`): `photo_key`, `photo_content_type`, `photo_size_bytes`; `UserAccount#hasPhoto` (`UserAccount.java:164`).

- Ảnh gắn tài khoản, không gắn role ⇒ tài khoản đa vai trò (multi-role phone identity) có một ảnh.
- Tệp: S3 loại `USER_AVATAR`, thư mục `avatars/users/`, tên `user_<userId>_<yyyyMMdd-HHmmss>_<4 ký tự>.<đuôi>`; tệp cũ `accounts/photos/` (key UUID) chỉ đọc/xóa (spec 017).
- Xóa ảnh: đặt ba cột về null rồi `removeQuietly` sau commit.
- Dữ liệu dẫn xuất cho quyền xem (không lưu): campus nhân sự từ `staff_assignments` còn hiệu lực hôm nay; lớp đang học của trẻ từ enrollment; GV của lớp từ phân công.
