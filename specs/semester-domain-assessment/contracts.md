# Contract đề xuất
GET /assessment/semester-evaluations?classId=&semester=1|2: kỳ cấu hình, lớp, lĩnh vực, trẻ, mục tiêu/các số đếm làm căn cứ, canEdit/canConfirm, trạng thái, lưu ý, ngày xác nhận.
PUT /assessment/semester-evaluations body classId, semester, note: lưu nháp, BE tính, không nhận kết quả FE tính.
POST /assessment/semester-evaluations/confirm cùng body: kiểm đủ dữ liệu, kỳ kết thúc, quyền GV và năm còn active; khóa, tính và chốt.
Danh sách lớp dùng endpoint cuối chủ đề hiện có, cùng scope; lịch học kỳ từ school year server.
Không đổi endpoint cũ; phiếu năm chưa xác nhận chuyển nguồn kết quả sang tổng hợp hoạt động cả năm, giữ DTO/year-sheet; bản cũ xác nhận giữ snapshot.
