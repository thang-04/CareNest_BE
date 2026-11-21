---
id: BUG-261122-transfer-week-year-meals
type: bug
modules: [attendance, nutrition, learning-observation]
rules: [OBS-29, ATT-06, NUT-03]
status: fixed
date: 2026-11-22
keywords: [chuyển lớp, nhận xét tuần, năm học, sổ suất ăn]
similar_to: []
---
# Nhận xét tuần khi chuyển lớp và thời gian sổ suất ăn

Status: fixed (2026-11-22)

## Nguyên nhân
- `weekly_comments` duy nhất theo trẻ/tuần; lớp mới tìm thấy bản lớp cũ nhưng không được sửa.
  Roster theo khoảng tuần cũng cho phép lớp cũ ghi sau khi trẻ rời lớp.
- Sổ ăn đọc day-info với classId null nên dùng năm ACTIVE, và chỉ kiểm ngày bắt đầu năm.
- Chi tiết suất ăn lớp dùng `year(null)`, không tìm được lớp năm đã đóng.

## Cách sửa
- V168 thay khóa duy nhất thành trẻ/lớp/tuần, giữ nguyên dữ liệu; query nhận xét gắn lớp.
- Ghi/gửi và tạo nháp tự động chỉ cho trẻ còn thuộc lớp; dòng cũ vẫn xem được, có nhãn lịch sử.
- Tổng cần gửi/nhắc quá hạn không tính trẻ đã rời lớp.
- Sổ ăn dùng hàm nội bộ đọc day-info theo năm được chọn; kiểm cả hai biên ngày.
- Chi tiết lớp tìm đúng năm từ classroomId; nhãn cuối chủ đề không thay kết quả snapshot.

## Attempts
1. Dùng getDayInfo theo lớp đại diện: test phát hiện tài khoản có quyền sổ ăn nhưng không có quyền lớp bị 403.
2. Tách getYearDayInfo dùng nội bộ sau kiểm quyền sổ ăn: 21 test sổ ăn đạt.

## Regression
- WeeklyCommentIntegrationTest: chuyển lớp giữ nháp cũ, lớp mới lưu riêng; lớp cũ không sửa/gửi; nghỉ hẳn khóa ghi.
- MealCountIntegrationTest: năm cũ dùng giờ chốt riêng, mở đúng lớp cũ; qua ngày kết thúc không lập sổ.
- ThemeEvaluationIntegrationTest: giữ kết quả bản chốt khi đổi kế hoạch.
