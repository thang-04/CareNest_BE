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
3. CI toàn bộ phát hiện child gọi trực tiếp repository attendance/observation và ticket gọi repository child.
   Tách port ChildDayUsageCheck, các module tự đọc repository của mình; mã trẻ đọc qua ChildService.
4. CI phát hiện fixture xếp lớp thiếu nhóm tuổi của năm; bổ sung dữ liệu nhóm 4–5 phù hợp trẻ trong test xếp lớp.
5. FE check-design chặn AgeGroupFormModal: chuyển các ô chỉ đọc sang TextField, không tăng baseline.
6. Seed nhận xét tuần còn trỏ khóa cũ sau V168; chỉ sửa ON CONFLICT của weekly_comments trong R__dev_05.

## Kiểm chứng CI trong phạm vi (22/11/2026)
- ArchitectureRulesTest 14/14, ChildRecordServiceImplTest 21/21, AssessmentContinuityIntegrationTest 4/4.
- ChildPlacementListIntegrationTest 5/5, OpenApiSnapshotTest 4/4 (chạy lại không có cờ update).
- DevSeedIntegrationTest 5/5; nạp lại repeatable seeds thành công. Spotless check đạt.
- FE lint, format, check-design, check-api và build đạt; test children 194/194.

## Giới hạn phạm vi CI
- User chốt chỉ sửa ba phần cấu hình trường, điểm danh/suất ăn, đánh giá trẻ.
- Không sửa test/seed kế hoạch giáo dục hoặc tài khoản nhân sự của module khác, dù CI còn báo lỗi tương thích nhóm tuổi ở đó.

## Ca ra trường theo tuổi
- CI `YearTransitionIntegrationTest.autoPlaceGraduatesOnlyChildrenWhoCompletedTheProgram` dùng trẻ sinh 2023,
  nhưng kỳ vọng tốt nghiệp ở năm 2027. `graduatingCompletion` chỉ xét trẻ từ 72 tháng tại năm mới.
- Sửa riêng DOB của hai trẻ trong ca tốt nghiệp thành 2021; giữ nguyên kiểm tra một trẻ hoàn thành và một trẻ chưa có kết quả.
- Lượt local 22/11 ban ngày: Docker engine không sẵn sàng, 17/17 test bị skip. Chưa coi là pass; xác minh trên CI sau push.

## Regression
- WeeklyCommentIntegrationTest: chuyển lớp giữ nháp cũ, lớp mới lưu riêng; lớp cũ không sửa/gửi; nghỉ hẳn khóa ghi.
- MealCountIntegrationTest: năm cũ dùng giờ chốt riêng, mở đúng lớp cũ; qua ngày kết thúc không lập sổ.
- ThemeEvaluationIntegrationTest: giữ kết quả bản chốt khi đổi kế hoạch.
