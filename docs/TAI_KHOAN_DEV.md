# Tài khoản bộ dữ liệu dev

> File sinh tự động bởi `node scripts/dev/gen-dev-dataset.mjs` — không sửa tay. Mọi tên, SĐT, CCCD là dữ liệu giả.

- Đăng nhập bằng **SĐT** (tên đăng nhập) hoặc email (nhân sự). Mật khẩu:
  - Local (`compose.yaml`, file `docker/keycloak/dev/carenest-users-0.json`): `Carenest@2026`.
  - VPS dev/prod (`docker/keycloak/vps/carenest-users-0.json`): giá trị `KEYCLOAK_DEV_USER_PASSWORD` trong `.env` của VPS (không ghi ở repo).
- Keycloak chỉ import user khi realm còn mới: đổi bộ dữ liệu ⇒ tạo lại DB `keycloak` (local: `docker compose down -v`), xem `docs/system/KEYCLOAK.md` mục 5.
- Trường: Trường Mầm non Thượng Hồng, năm học 2026-2027, Cơ sở 1 (`CS-A`, điểm chính) và Cơ sở 2 (`CS-B`), 10 lớp, 217 trẻ, 201 tài khoản phụ huynh.

## Nhân sự (28 tài khoản)

| Họ tên | Tên đăng nhập | Email | Realm role | Chức danh | Phạm vi | Trạng thái đặc biệt |
| --- | --- | --- | --- | --- | --- | --- |
| Hoàng Công Lâm | `0861723024` | lam.hoangcong@thuonghong.carenest.edu.vn | ADMIN | Quản trị hệ thống | — |  |
| Hoàng Minh Tuyết | `0399825361` | tuyet.hoangminh@thuonghong.carenest.edu.vn | PRINCIPAL | Hiệu trưởng | Toàn trường |  |
| Lê Thị Tâm | `0392274816` | tam.lethi@thuonghong.carenest.edu.vn | VICE_PRINCIPAL | Phó hiệu trưởng phụ trách dinh dưỡng – Cơ sở 1 | Cơ sở 1 | được cấp thêm quyền NUTRITION_MANAGE; được cấp thêm quyền MENU_MANAGE; được cấp thêm quyền STOCK_MANAGE; được cấp thêm quyền FOOD_SAFETY_APPROVE |
| Bùi Thị Phương | `0376860053` | phuong.buithi@thuonghong.carenest.edu.vn | VICE_PRINCIPAL | Phó hiệu trưởng phụ trách chuyên môn – Cơ sở 2 | Cơ sở 2 | được cấp thêm quyền STOCK_MANAGE; được cấp thêm quyền FOOD_SAFETY_APPROVE |
| Kiều Thu Nga | `0343484905` | nga.kieuthu@thuonghong.carenest.edu.vn | TEACHER | Giáo viên chủ nhiệm Lớp Nhà trẻ 1, tổ trưởng chuyên môn | Lớp Nhà trẻ 1 (chủ nhiệm); Tổ trưởng ag-2 |  |
| Nguyễn Thu Mai | `0961275843` | mai.nguyenthu@thuonghong.carenest.edu.vn | TEACHER | Giáo viên Lớp Nhà trẻ 1 | Lớp Nhà trẻ 1 |  |
| Lê Mai Tuyết | `0934952716` | tuyet.lemai@thuonghong.carenest.edu.vn | TEACHER | Giáo viên chủ nhiệm Lớp Mầm 1 | Lớp Mầm 1 (chủ nhiệm) |  |
| Trần Mai Yến | `0985648263` | yen.tranmai@thuonghong.carenest.edu.vn | TEACHER | Giáo viên Lớp Mầm 1 | Lớp Mầm 1 |  |
| Nguyễn Kim Lan | `0902197238` | lan.nguyenkim@thuonghong.carenest.edu.vn | TEACHER | Giáo viên chủ nhiệm Lớp Chồi 1, tổ trưởng chuyên môn | Lớp Chồi 1 (chủ nhiệm); Tổ trưởng ag-4 |  |
| Phạm Thị Thanh | `0981293650` | thanh.phamthi@thuonghong.carenest.edu.vn | TEACHER | Giáo viên Lớp Chồi 1 | Lớp Chồi 1 |  |
| Đoàn Diệu Quyên | `0366013137` | quyen.doandieu@thuonghong.carenest.edu.vn | TEACHER | Giáo viên chủ nhiệm Lớp Lá 1 | Lớp Lá 1 (chủ nhiệm) |  |
| Trần Thanh Tâm | `0864473293` | tam.tranthanh@thuonghong.carenest.edu.vn | TEACHER | Giáo viên Lớp Lá 1 | Lớp Lá 1 (từ 02/11/2026) |  |
| Phạm Lan Uyên | `0366700036` | uyen.phamlan@thuonghong.carenest.edu.vn | TEACHER | Giáo viên chủ nhiệm Lớp Lá 2 | Lớp Lá 2 (chủ nhiệm) |  |
| Bùi Thùy Dung | `0849803594` | dung.buithuy@thuonghong.carenest.edu.vn | TEACHER | Giáo viên Lớp Lá 2 | Lớp Lá 2 |  |
| Nguyễn Hải Trang | `0974439286` | trang.nguyenhai@thuonghong.carenest.edu.vn | TEACHER | Giáo viên chủ nhiệm Lớp Nhà trẻ 2 | Lớp Nhà trẻ 2 (chủ nhiệm) |  |
| Nguyễn Thùy Huyền | `0978529818` | huyen.nguyenthuy@thuonghong.carenest.edu.vn | TEACHER | Giáo viên Lớp Nhà trẻ 2 | Lớp Nhà trẻ 2 | phải đổi mật khẩu lần đầu |
| Nguyễn Hồng Lan | `0911288423` | lan.nguyenhong@thuonghong.carenest.edu.vn | TEACHER | Giáo viên chủ nhiệm Lớp Mầm 2, tổ trưởng chuyên môn | Lớp Mầm 2 (chủ nhiệm); Tổ trưởng ag-3 |  |
| Bùi Thanh Hà | `0340732314` | ha.buithanh@thuonghong.carenest.edu.vn | TEACHER | Giáo viên Lớp Mầm 2 | Lớp Mầm 2 |  |
| Vũ Thị Liên | `0964083992` | lien.vuthi@thuonghong.carenest.edu.vn | TEACHER | Giáo viên chủ nhiệm Lớp Chồi 2 | Lớp Chồi 2 (chủ nhiệm) |  |
| Nguyễn Kim Linh | `0887441235` | linh.nguyenkim@thuonghong.carenest.edu.vn | TEACHER | Giáo viên Lớp Chồi 2 | Lớp Chồi 2 | bị bớt quyền ASSESSMENT_CONFIRM |
| Trần Kim Liên | `0867787889` | lien.trankim@thuonghong.carenest.edu.vn | TEACHER | Giáo viên chủ nhiệm Lớp Lá 3, tổ trưởng chuyên môn | Lớp Lá 3 (chủ nhiệm); Tổ trưởng ag-5 |  |
| Nguyễn Hồng Thanh | `0818139472` | thanh.nguyenhong@thuonghong.carenest.edu.vn | TEACHER | Giáo viên Lớp Lá 3 | Lớp Lá 3 |  |
| Nguyễn Thị Hoa | `0913869187` | hoa.nguyenthi@thuonghong.carenest.edu.vn | TEACHER | Giáo viên chủ nhiệm Lớp Lá 4 | Lớp Lá 4 (chủ nhiệm) | được cấp thêm quyền CHILD_PROFILE_EDIT |
| Hoàng Thị Oanh | `0932323131` | oanh.hoangthi@thuonghong.carenest.edu.vn | TEACHER | Giáo viên Lớp Lá 4 | Lớp Lá 4 |  |
| Bùi Thu Phương | `0945827330` | phuong.buithu@thuonghong.carenest.edu.vn | TEACHER | Giáo viên (đã nghỉ việc) | Lớp Lá 1 (tới 31/10/2026) | đã khóa (nghỉ việc) |
| Phạm Thị Nhàn | `0392692992` | nhan.phamthi@thuonghong.carenest.edu.vn | TEACHER | Giáo viên mới tuyển (chưa phân lớp) | Dạy thay Lớp Mầm 2 (tới 16/10/2026) |  |
| Nguyễn Thị Thơm | `0987731447` | thom.nguyenthi@thuonghong.carenest.edu.vn | KITCHEN_STAFF | Tổ bếp Cơ sở 1 | Cơ sở 1 |  |
| Nguyễn Minh Hương | `0965968997` | huong.nguyenminh@thuonghong.carenest.edu.vn | KITCHEN_STAFF | Tổ bếp Cơ sở 2 | Cơ sở 2 |  |

## Phụ huynh đại diện

Mọi trẻ đang học đều có 1 phụ huynh nhận tài khoản (tên đăng nhập = SĐT của người đó trên hồ sơ trẻ). Đại diện để thử:

| Trường hợp | Họ tên | Tên đăng nhập | Con | Trạng thái đặc biệt |
| --- | --- | --- | --- | --- |
| Phụ huynh 1 con (id cố định …0006) | Trần Thị Hà | `0335869197` | Nguyễn Hải Tùng – Lớp Chồi 1 |  |
| Phụ huynh 2 con khác cơ sở (id cố định …0007) | Nguyễn Thị Thơm | `0984066510` | Lê Tuấn Sơn – Lớp Mầm 1; Lê Minh An – Lớp Lá 3 |  |
| Phụ huynh 2 con | Lê Phương Liên | `0335474254` | Nguyễn Văn Long – Lớp Nhà trẻ 1; Nguyễn Phúc Vũ – Lớp Chồi 1 |  |
| Phụ huynh 2 con | Lê Thị Mai | `0384234102` | Nguyễn Minh An – Lớp Nhà trẻ 1; Nguyễn Tú Mỹ – Lớp Lá 1 |  |
| Bà giám hộ | Phạm Hồng Trinh | `0372766108` | Nguyễn Diệu Hương – Lớp Mầm 1 |  |
| Bà giám hộ | Nguyễn Thị Uyên | `0903280617` | Trần Hoài Nga – Lớp Lá 1 |  |
| Phụ huynh chưa đổi mật khẩu lần đầu | Nguyễn Thùy Linh | `0985950454` | Vũ Gia Dũng – Lớp Nhà trẻ 1 | phải đổi mật khẩu lần đầu |
| Phụ huynh bị hệ thống khóa (con đã nghỉ học) | Nguyễn Mạnh Trung | `0829120778` | Nguyễn Tuệ Nga – Lớp Mầm 1 | hệ thống khóa (không còn con đang học) |

Danh sách đầy đủ: `select u.username, u.full_name, c.full_name from user_accounts u join child_guardians g on g.user_id = u.id join children c on c.id = g.child_id order by 1;`
