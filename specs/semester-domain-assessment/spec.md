# Specification
Nguồn: Cần duyệt sau khi giải thích phạm vi và trả lời hai câu hỏi ngày 05/11/2026.
US1 GV chọn lớp/kỳ từ cấu hình HT để xem bảng trẻ × lĩnh vực, mở mục tiêu làm căn cứ, lưu lưu ý và xác nhận cả lớp.
US2 HT/PHT/tổ trưởng xem trong phạm vi BE, không ghi thay GV; in/xuất đúng bảng toàn bộ lớp, không chỉ trang tìm kiếm.
US3 Phiếu năm chưa chốt tính >=50% hoạt động cả năm cho mỗi MT, có dữ liệu lớp cũ cùng năm/nhóm tuổi; bản đã xác nhận giữ snapshot.
FR1 Nguồn mục tiêu kỳ là mục tiêu gắn chủ đề trong kế hoạch năm duyệt có giao với học kỳ. Tính theo hoạt động đã chấm trong kỳ, không đếm lại lịch sử sửa, dedup lineage.
FR2 Lĩnh vực >=50% MT đạt, nhưng khi còn MT thiếu kết quả hiển thị pending và không xác nhận. Không có MT hoặc không có trẻ không xác nhận.
FR3 Ngày vắng hoặc chưa chấm không vào mẫu số hoạt động. Chuyển lớp cùng nhóm tuổi giữ lịch sử theo từng đoạn học.
FR4 Ngày học kỳ khóa khi có bảng nháp/xác nhận; chỉ GV lớp/năm active lưu; xác nhận từ ngày kết thúc kỳ. Phản hồi cũ không gắn sang phạm vi mới, rời có sửa mới hỏi.
FR5 Lưu/xác nhận bị khóa kép ở server, UI khóa khi gửi; snapshot có tên lĩnh vực/MT/ngày/danh sách trẻ/kết quả.
Success: test AC1..8 qua integration PostgreSQL; kiểm UI/loading/error/empty/readonly; không đổi dữ liệu xác nhận cũ.
