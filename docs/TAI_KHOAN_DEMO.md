# Tài khoản demo kiểm thử kế hoạch giáo dục

Dữ liệu sau reset ngày 10/11/2026 trên `localhost:5433/carenest_merge`, năm học **2026–2027**.

**Mật khẩu của tất cả 34 tài khoản dưới đây: `demo123`.** Đây là tài khoản giả dùng kiểm thử local.

## Quản lý và tổ trưởng

| Vai trò | Email đăng nhập | Mật khẩu | Phạm vi |
|---|---|---|---|
| Hiệu trưởng | principal@gmail.com | demo123 | Toàn trường |
| Phó hiệu trưởng | viceprincipal@gmail.com | demo123 | CS-A và CS-B |
| Tổ trưởng | teamlead24@gmail.com | demo123 | 24–36 tháng |
| Tổ trưởng | teamlead34@gmail.com | demo123 | 3–4 tuổi |
| Tổ trưởng | teacher2@gmail.com | demo123 | 4–5 tuổi |
| Tổ trưởng | teacher1@gmail.com | demo123 | 5–6 tuổi |

Mỗi tổ trưởng đồng thời là chủ nhiệm lớp số 1 của nhóm tuổi tương ứng tại CS-A.

## Giáo viên theo lớp

Mỗi lớp có hai giáo viên, người ở cột Chủ nhiệm là chủ nhiệm duy nhất. Mật khẩu từng tài khoản trong bảng là `demo123`.

| Cơ sở | Nhóm tuổi | Lớp | Chủ nhiệm — email đăng nhập | Giáo viên thứ hai — email đăng nhập |
|---|---|---|---|---|
| CS-A | 24–36 tháng | 1 | teamlead24@gmail.com | teacher3@gmail.com |
| CS-A | 24–36 tháng | 2 | teacher4@gmail.com | teacher5@gmail.com |
| CS-A | 3–4 tuổi | 1 | teamlead34@gmail.com | teacher6@gmail.com |
| CS-A | 3–4 tuổi | 2 | teacher7@gmail.com | teacher8@gmail.com |
| CS-A | 4–5 tuổi | 1 | teacher2@gmail.com | teacher9@gmail.com |
| CS-A | 4–5 tuổi | 2 | teacher10@gmail.com | teacher11@gmail.com |
| CS-A | 5–6 tuổi | 1 | teacher1@gmail.com | teacher12@gmail.com |
| CS-A | 5–6 tuổi | 2 | teacher13@gmail.com | teacher14@gmail.com |
| CS-B | 24–36 tháng | 1 | teacher15@gmail.com | teacher16@gmail.com |
| CS-B | 24–36 tháng | 2 | teacher17@gmail.com | teacher18@gmail.com |
| CS-B | 3–4 tuổi | 1 | teacher19@gmail.com | teacher20@gmail.com |
| CS-B | 3–4 tuổi | 2 | teacher21@gmail.com | teacher22@gmail.com |
| CS-B | 4–5 tuổi | 1 | teacher23@gmail.com | teacher24@gmail.com |
| CS-B | 4–5 tuổi | 2 | teacher25@gmail.com | teacher26@gmail.com |
| CS-B | 5–6 tuổi | 1 | teacher27@gmail.com | teacher28@gmail.com |
| CS-B | 5–6 tuổi | 2 | teacher29@gmail.com | teacher30@gmail.com |

Tên lớp trên ứng dụng có tiền tố `Test`, ví dụ `Test CS-A - Mẫu giáo lớn (5–6 tuổi) - 1`.

## Trạng thái dữ liệu và kiểm chứng

- Có 16 lớp hoạt động, 32 giáo viên, bốn tổ trưởng, một hiệu trưởng và một phó hiệu trưởng.
- Kế hoạch năm, kế hoạch tổ và hồ sơ giáo dục đã được xóa để test từ đầu; cấu hình duyệt và chương trình nền được giữ.
- Lớp cũ chuyển đóng để giữ lịch sử của module khác. Các tài khoản admin, phụ huynh và bếp cũ được giữ; mật khẩu của chúng không được đặt lại hoặc xác minh trong đợt này.
- Đã kiểm tra 34 tài khoản trên qua Keycloak và API `/api/accounts/me` sau reset.
- Chưa kiểm trình duyệt hoặc restart BE. `/api/auth/login` ở tiến trình đang chạy trả 401; xác thực đã kiểm dùng Keycloak và Bearer token.
