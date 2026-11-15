# Data model đề xuất
Bảng semester_assessments: id, classroom_id, school_year_id, semester_no (1/2), period_start/end, note, status, result_snapshot JSON text, confirmed_by/at, created/updated. Unique classroom/year/semester. Không seed bản xác nhận.
Snapshot chốt tên lĩnh vực, danh sách mục tiêu, trẻ và kết quả; không phụ thuộc kế hoạch sửa sau. Bản nháp tính live; ngày kỳ đã có bảng không đổi qua cấu hình theo cơ chế khóa usage hiện có, cần nối usage check.
Đọc daily_activity_results, attendance_records, enrollment history, approved year plan + theme goal mapping; không copy lịch sử hoạt động.
