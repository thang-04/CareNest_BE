---
id: BUG-261127-br-read-scope-parent-password
type: bug
modules: [child, account, health]
status: fixed
date: 2026-11-27
keywords: [GEN-01, CP-22, CP-21, read-scope, parent-password, smsStatus]
---

# PHT bị giới hạn phạm vi đọc và cấp lại mật khẩu PH chưa gửi SMS

Nguồn: người dùng yêu cầu thực hiện mục 31–35 trong `report/sua_sau/CODE-can-sua-theo-BR-2026-11-26.md` ngày 27/11/2026.

## Root cause

- Các danh sách và báo cáo dùng scope quản lý hoặc chỉ mở toàn trường khi đã chọn bộ lọc. Đường đọc hồ sơ PH lại gọi chung kiểm tra ghi.
- Cấp lại mật khẩu chỉ reset identity, trả mật khẩu trong DTO, không gửi SMS; hai màn FE suy kết quả thiếu trạng thái là đã gửi.

## Fix

- Tách đường đọc toàn trường của HT/PHT đang phân công khỏi scope ghi, giữ quyền ghi theo điểm trường. Bổ sung guard tổng quan tài khoản để quyền API cấp cho GV không làm lộ dữ liệu tài khoản.
- GET người nhận/tài khoản được đọc toàn trường; select/link/activate/thêm/gỡ vẫn kiểm scope quản lý riêng.
- Báo cáo tháng lấy danh sách điểm trường qua đường đọc. Mở phiếu khám bằng quyền đọc, quyền nhập/sửa giữ theo lớp.
- Reset gửi qua gateway đang cấu hình, purpose PASSWORD_RESET, chỉ trả smsStatus. FE chỉ báo thành công khi SENT.

## Attempts và kiểm tra

- Rà soát đầu phát hiện hai đường tổng quan bỏ lọc canManage có thể mở thông tin PH cho GV được cấp thêm quyền API. Đã chặn bằng role/phân công và thêm regression.
- Mock phải mở quyền đọc đợt điểm trường khác cùng BE; test ghi chéo nay trả 403 thay 404 vì đợt đã xem được nhưng không được sửa.
- Spotless tham số spotlessFiles nhận regex, không phải danh sách CSV/glob; lọc theo tên các file thay đổi bằng regex trước khi chạy formatter.
- Regression: ChildAccessPolicyTest, StaffAccessPolicyTest, ChildListServiceImplTest, ChildPhotoServiceImplTest, GuardianAccountServiceImplTest, ParentSmsSenderTest, Gen01HealthReadScopeTest, GuardianControllerTest; HealthGrowthReportIntegrationTest và HealthCheckIntegrationTest kiểm luồng DB.
- Kết quả cuối ghi tại `docs/quality/VERIFICATION.md`.
