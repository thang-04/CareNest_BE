# Research — Tự lưu mục tiêu năm học

Ngày: 27/10/2026. Nguồn: source hiện tại và yêu cầu user trong spec; không giả định thuật toán nội bộ của Google Docs.

| Quyết định | Chọn | Phương án loại | Căn cứ |
| --- | --- | --- | --- |
| Sửa đồng thời | Một phiên giữ quyền + kiểm tra revision | Cùng gõ/cùng sửa nhiều người | User yêu cầu một người sửa, khác mô hình cộng tác Docs |
| Hoạt động khi không nhập | Heartbeat 20s, TTL 90s | Gia hạn chỉ khi Save | Người dùng còn đọc không được tự mất quyền |
| Lưu lặp sau mất phản hồi | Một request ID cho một snapshot, thử lại cùng ID | Tạo request mới cho mỗi lần retry | Ngăn thêm trùng dòng/chủ đề khi phản hồi bị mất |
| Nhập trong lúc lưu | Tuần tự, đổi ID cũ→mới vào bản đang nhập | Thay cả form bằng phản hồi | Giữ keystroke phát sinh sau snapshot gửi đi |
| Phục hồi hai tab | Bản riêng cho mỗi tài khoản/kế hoạch/phiên | Một khóa lưu trữ cho cả tài khoản/kế hoạch | Tab cũ ngoại tuyến không ghi đè bản phục hồi của tab mới |
| Gửi duyệt/ban hành | Khóa nhập, flush xong, gửi phiên bản đã xác nhận | Gửi cùng lúc autosave | Không bỏ lại nội dung đang gõ hoặc chuyển trạng thái sai revision |
| Nội dung dở | Lưu nháp được, kiểm tra đầy đủ ở bước gửi/ban hành | Chặn autosave khi thiếu | User đã chọn sau clarify |

Ràng buộc dữ liệu cũ đã kiểm: V12 đã bỏ ràng buộc khoảng ngày của themes/breaks; không cần sửa migration đó. V24 gắn trạng thái EDITING với khóa cũ, V30 thay bằng invariant nhận diện phiên và giữ PENDING_VP độc lập.
