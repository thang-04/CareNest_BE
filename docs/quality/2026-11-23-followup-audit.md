# Rà tiếp ba module của Cần — 23/11/2026

Phạm vi: cấu hình trường, điểm danh/suất ăn, đánh giá trẻ. Lượt này rà và kiểm chứng, không đổi source của module người khác, không ghi dữ liệu test vào DB local gốc.

## Phát hiện

1. **High, xác nhận bằng audit guard:** GV dạy chính hết phân công trong năm ACTIVE không đọc được ngày từng dạy; lớp cũ còn bị loại khỏi danh sách. AttendanceDayRules.canViewDay và ObservationDayRules.canViewDay chỉ có ngoại lệ lịch sử theo ngày cho người dạy thay. Xem incident BUG-261123-former-teacher-read-interval.
2. **High, xác nhận bằng audit guard:** năm CLOSED, từng dạy một đợt được coi là có quyền đọc cả lớp/năm, bỏ qua ngày yêu cầu ngoài đợt. Tổng hợp điểm danh đọc khoảng rộng sau kiểm cấp lớp; nhận xét tuần/phiếu cần rà theo khoảng khi sửa. Cùng incident trên.
3. **Low, tái hiện UI:** PHT mở Lớp Chồi1 ngày Chủ nhật22/11/2026: màn ghi Không tải được dữ liệu rồi Ngày đã chọn không phải ngày học. BE chặn ngày nghỉ đúng; trạng thái này nên hiện thông tin ngày không học. Dashboard ClassTodayPanel vẫn gửi ngày cụ thể và bắt lỗi thànhnull nên dễ hiện lỗi tải. Không sửa dashboard dùng chung trong lượt rà.
4. **Chặn CI, xác nhận log GitHub:** BE head24b0ddf5 dừng ngay job plan trước test vì base dev mới có V169__add_applied_structure_to_meal_configs và V170__nutrition_group_status trùng V169 freeze/V170 catalog của nhánh. Đây là collision mới sau khi dev tiến lên, không phải checksum lỗi local vừa sửa. FE head1ae31f01 còn Git conflict với dev. Chưa merge dev mới hoặc sửa migration trong lượt audit này.

## Ca không tái hiện

BUG-027 hồ sơ phát triển: PHT mở đúng trẻ49 trên local hiện tại, Năm học/HKI/tháng11 tải được số liệu. Chưa kiểm cùng điều kiện GV/HT và cùng ngày trong ảnh; không đánh dấu lỗi này đã sửa dứt điểm chỉ từ ca PHT.

## Bằng chứng

- logs/former-teacher-read-audit.log: Attendance và Observation ACTIVE expectedtrue/actualfalse; CLOSED expectedfalse/actualtrue; tổng4 sai khác. Mock input, không phải full regression suite.
- FE:logs/ui-proof/audit-non-school-day.jpg: ảnh UI ngày nghỉ.
- logs/ci-current-plan-failure.log: GitHub job137496834845, run45115356301 báo trùng migration V169/V170, chưa chạy api-test.

## Giới hạn và ưu tiên

Ưu tiên quyền đọc lịch sử trước, sau đó đồng bộ migration/merge PR và hiển thị ngày nghỉ. Không suy báo cáo này thành mọi luồng/phân quyền/thông báo/chuyển lớp đã phủ đủ. Test trước61/61 vẫn là bằng chứng cho bộ ca cũ, chưa bao gồm hai lỗi quyền mới phát hiện.
