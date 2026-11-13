# ADR-0026 — Ban giám hiệu dùng App ở chế độ chỉ xem

- Status: ACCEPTED (chưa có code — plan `docs/plans/active/2026-11-13-bgh-mobile-view-only.md`, Phase 2)
- Date: 2026-11-13
- Liên quan: AUTH-21, AUTH-07, AUTH-08; thay một phần ADR-0022 (dòng "BGH trên App không còn role nào")

## Bối cảnh
ADR-0022 lọc role theo client của token (`azp`). Token App (`carenest-mobile`) chỉ giữ TEACHER, KITCHEN_STAFF, PARENT, nên Ban giám hiệu gọi API từ App nhận 403 1101. Ngày 13/11/2026, user chốt: BGH vẫn vào App để xem số liệu, nhưng không có bất kỳ thao tác nào; mọi việc ghi của BGH làm trên Web.

Quyền của mọi API đọc từ authorities của `Authentication`. `CurrentUserService` dựng role của `CurrentUser` từ đó; `@PreAuthorize` và `PermissionGuard` dùng `CurrentUser`. Vì vậy đổi authorities theo từng request là đủ để áp quyết định lên mọi kiểm tra quyền.

Có API GET ghi dữ liệu:
- GET `/meal-counts` lập sổ suất của ngày lần đầu sau giờ chốt, với mọi người xem (`MealCountServiceImpl.getMealCounts` → `aggregateMissing`).
- GET đánh giá trẻ theo kỳ và nhận xét tháng tạo bản nháp, chỉ khi người gọi là GV đang dạy lớp (`ObservationDayRules.canRecord`).

## Quyết định
"BGH thuần" = token App mang PRINCIPAL và/hoặc VICE_PRINCIPAL, không mang role nào khác.

1. `ClientPlatform.MOBILE` nhận thêm PRINCIPAL, VICE_PRINCIPAL ở dạng **role chỉ xem** (`readOnlyRoles`). WEB và DEV không có role chỉ xem.
2. **Lớp 1 — theo method.** Một filter đặt sau `BearerTokenAuthenticationFilter`. Với token App và method không phải GET/HEAD/OPTIONS, filter bỏ `ROLE_PRINCIPAL`, `ROLE_VICE_PRINCIPAL` khỏi authentication của request đó. Không còn role nào thì trả 403 **1301** `MOBILE_STAFF_READ_ONLY`, controller không chạy. Tài khoản nhiều role (BGH kiêm GV/phụ huynh) giữ quyền ghi của role khác; việc cần quyền BGH bị 403 1101 như lỗi quyền thường.
3. **Lớp 3 — danh sách GET cho phép.** BGH thuần trên App chỉ gọi được GET khai báo trong `carenest.security.platform.mobile-staff-read-paths` (từng endpoint, theo bảng dưới). Path khác ⇒ 403 1301.
4. **Lớp 2 — DB chỉ đọc.** Request của BGH thuần qua được lớp 3 được bọc bằng transaction `readOnly`; `@Transactional` bên trong nhập vào transaction đó. PostgreSQL từ chối mọi lệnh ghi (SQLState `25006`) ⇒ 403 **1302** `MOBILE_STAFF_WRITE_BLOCKED`, log WARN kèm path để dev sửa.
5. **Ngoại lệ ghi:** `PUT /me/devices`, `DELETE /me/devices`, `POST /me/notifications/{notificationId}/read`, `POST /me/notifications/read-all` (đăng ký/hủy thiết bị nhận push, đánh dấu đã đọc). Khai báo ở `carenest.security.platform.mobile-staff-write-exceptions`; khớp ⇒ không bỏ role, không bọc chỉ đọc. Mọi thao tác khác của tài khoản mình (đổi mật khẩu, ảnh, chữ ký) bị chặn.
6. **Ngoại lệ lớp 2:** GET `/meal-counts` không bọc DB chỉ đọc (user chốt 13/11/2026), khai báo ở `carenest.security.platform.mobile-staff-system-write-paths`. Endpoint này lập sổ suất lần đầu sau giờ chốt (NUT-01) — việc của hệ thống, số tính từ điểm danh, giống khi PHT mở Web; BGH vẫn không xác nhận/sửa được (lớp 1).
7. Lớp 2 và lớp 3 chỉ áp cho BGH thuần. Tài khoản nhiều role chỉ qua lớp 1, vì GET của GV có thể tạo bản nháp hợp lệ.
8. Token Web không đổi.

### Danh sách GET cho phép (user duyệt 13/11/2026)
| Màn App | Endpoint | Ghi chú |
| --- | --- | --- |
| Mọi màn | GET `/accounts/me` | roles, permissions, phân công |
| Mọi màn | GET `/me/notifications` | Tab Thông báo |
| Mọi màn | GET `/school-years` | Năm học đang hoạt động |
| PHT 01, HT 01 (trang chủ) | GET `/attendance/summary` | Có mặt / vắng |
| PHT 01, PHT 02, PHT 04, HT 01, HT 02 | GET `/meal-counts` | Số suất, trạng thái xác nhận. Ghi sổ lần đầu sau giờ chốt ⇒ ngoại lệ lớp 2 (QĐ 6) |
| PHT 04 | GET `/meal-counts/class` | Số suất theo lớp |
| PHT 06, PHT 07, HT 07 | GET `/nutrition/meal-plans`, GET `/nutrition/meal-plans/{weekStart}` | Thực đơn tuần và cân đối dinh dưỡng |
| PHT 06, HT 07 | GET `/age-groups` | Tab nhóm tuổi |
| PHT 02, PHT 12, HT 02, HT 04 | GET `/assessment/reward-lists`, GET `/assessment/reward-lists/classes/{classId}` | Khen thưởng, chỉ xem |
| HT 01 | GET `/assessment/overview` | Đánh giá trẻ theo khối |
| PHT 10, HT 06 | GET `/children`, GET `/children/{childId}` | Danh sách và hồ sơ trẻ. Chưa có tham số lọc dị ứng |
| HT 06 | GET `/school-structure` | Lớp theo cơ sở |
| HT 08 | GET `/campuses`, GET `/campuses/{campusId}`, GET `/school-years/{schoolYearId}/vice-principal-assignments` | Cơ sở và PHT phụ trách |

## Phương án đã cân nhắc
| Phương án | Ưu | Nhược |
| --- | --- | --- |
| Filter bỏ role BGH ở lệnh ghi + danh sách GET + DB chỉ đọc (chọn) | Một chỗ, áp lên mọi kiểm tra quyền; DB chặn cả GET có ghi | Không phủ `REQUIRES_NEW`; giữ danh sách khi App thêm màn |
| Rule `authorizeHttpRequests`: có role BGH + lệnh ghi ⇒ 403 | Đơn giản | Chặn cả việc GV/phụ huynh của tài khoản nhiều role |
| Thêm điều kiện vào từng `@PreAuthorize` | Rõ từng endpoint | Hơn 300 chỗ, dễ sót |
| User DB chỉ có quyền SELECT cho request BGH | Phủ cả `REQUIRES_NEW` | Thêm hạ tầng: user DB, datasource thứ hai |
| Giữ ADR-0022 (BGH không dùng App) | Không đổi code | Trái quyết định user 13/11/2026 |

## Hệ quả
- Mã lỗi khối 13xx mở cho quyền theo nền tảng (14xx dự phòng): 1301, 1302 (`docs/contracts/ERROR_CONTRACT.md`).
- App thêm màn BGH ⇒ thêm endpoint vào `mobile-staff-read-paths`, kiểm endpoint đó không ghi DB với BGH thuần.
- API GET mới không được ghi DB, trừ khi ghi đó chỉ dành cho role khác (vd. GV tạo nháp). Nếu không, BGH thuần trên App nhận 1302.
- Request của BGH thuần giữ kết nối DB suốt request (kể cả lúc ghi JSON).
- APP: màn BGH chỉ hiển thị, không có nút ghi; gặp 1301/1302 ⇒ báo "thao tác trên CareNest Web".

## Còn chờ
- Lọc trẻ theo dị ứng (màn PHT 10): `GET /children` chưa có tham số; làm khi có yêu cầu.
- Lập sổ suất bằng tác vụ chạy sau giờ chốt thay vì lúc GET (bỏ được ngoại lệ lớp 2): việc riêng của module nutrition, chưa lên kế hoạch.
