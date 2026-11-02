# Data model — Feature 008

## Dữ liệu hiện có

Giữ EduYearPlan, EduTheme, EduBreak và EduClassDoc JSONB. Không thêm bảng, cột, trạng thái, migration hoặc backfill.
Giữ payload nhánh `names: {weekStart: name}`, ghi chú và lịch sử. Lịch lấy từ teachingWeeks của kế hoạch hiện hành, không nhận từ file.

Bổ sung mẫu/modal 02/11: Word mẫu được tạo phía Web từ danh sách tuần đã hiển thị, chỉ xuất tên chủ đề và ô tên nhánh trống. Không lưu file trên server, không đổi schema/API.

## Dữ liệu transient

- Parsed branch row: số dòng bảng, tên chủ đề, tên nhánh; tên chủ đề tiếp diễn từ ô gộp được giữ đúng.
- Match: row, themeId, themeName, weekStart (ISO date từ hệ thống), name; không chứa thông tin người ký hay ngày file.
- Warning: row (dòng bảng), message; không trả nguyên file hoặc nội dung ngoài bảng.
- Preview: matches[], warnings[]; không persisted ID, không tạo EduClassDoc khi preview.

## Ràng buộc

Kiểm file <=2 MB và bảng <=2.000 dòng trước xử lý không giới hạn; hỏng/mã hóa/định dạng sai bị từ chối. Cap kỹ thuật 256 cột ở decoder dùng chung, kiểm tọa độ/span trước cấp phát (T019), không nới POI allocation limit toàn cục.
Mapping theo tên chủ đề chuẩn hóa; nhiều đích cùng tên là mơ hồ, không chọn tùy tiện.
Thứ tự nhánh theo dòng file, tuần theo lịch server; bỏ tuần nghỉ trọn, giữ tuần nghỉ lẻ như EDU-41.
FE so matches với nháp để xác nhận thay tên, không xóa tuần ngoài matches; lưu dùng API hiện có và kiểm lại server.

## State transitions

Preview là trạng thái UI tạm; không đổi lifecycle tài liệu. Save chỉ DRAFT/REJECTED; submit/approve/reject theo EDU-18 hiện tại.
Không sửa bất kỳ dữ liệu mục tiêu khi đọc .doc; merge bước 2 giữ EDU-46 hiện hữu.
