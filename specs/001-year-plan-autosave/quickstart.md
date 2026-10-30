# Kiểm chứng tính năng tự lưu

1. Chạy Docker. Từ BE chạy `node scripts/verify.mjs`; yêu cầu full, skip 0 và snapshot khớp.
2. Từ FE chạy `npm run test:edu`, sau đó `node scripts/verify.mjs`; lint/format/design/build và mọi suite phải được báo đúng kết quả.
3. Hai tab cùng tài khoản và hai tài khoản mở cùng kế hoạch: chỉ một phiên ghi; thoát bình thường để phiên khác nhận quyền; phiên cũ không thể nhả/ghi vào phiên mới.
4. Nhập rồi nghỉ 2 giây, nhập liên tục 15 giây, gõ thêm khi request đang chạy: nội dung mới còn nguyên; ID/tick được ánh xạ đúng; retry không thêm trùng.
5. Ngắt mạng, sửa và mở lại cùng tài khoản/thiết bị: phục hồi phần đã giữ. Tab không có quyền không ghi đè bản phục hồi của tab khác. Tài khoản khác không thấy bản đó.
6. Giữ tab cũ offline quá 90 giây, sửa bản trên tab mới rồi nối mạng tab cũ: không tự ghi đè; tải bản cũ để đối chiếu.
7. Lưu thiếu tiêu đề/mục tiêu, chủ đề chưa đầy đủ hoặc ngày chưa hợp lệ: nháp giữ được; gửi duyệt/ban hành báo lỗi. Bản approved không sửa được.
8. Bấm gửi/ban hành trong lúc lưu: khóa nhập, đợi lưu xong rồi chuyển trạng thái đúng một lần; không để request cũ hồi sinh trạng thái cũ.

Không dùng dữ liệu trẻ hoặc tài khoản thật trong các bài kiểm tra. Ghi rõ lệnh/kết quả và phần trình duyệt chưa kiểm chứng trong tasks/progress; không đánh dấu hoàn tất theo suy đoán.
