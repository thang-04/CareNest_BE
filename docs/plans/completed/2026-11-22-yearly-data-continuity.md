# Đồng bộ lịch sử cấu hình và đánh giá

status: done
branch: feature/G94-236-BE-FEAT-29-daily-attendance

## Phạm vi đã duyệt

User yêu cầu ngày 22/11/2026: sửa ba lỗi đã báo rồi kiểm tra bao phủ luồng của ba module phụ trách.
Không sửa kế hoạch giáo dục, tài khoản/phân quyền của thành viên khác, lớp ghép hoặc tối ưu DB.
Không thêm lịch chuyển lớp/nghỉ học đặt trước trong đợt sửa lỗi dữ liệu này.

## Acceptance và kiểm chứng

- AC-1 (CFG-YR-02, ATT): lớp đọc bữa theo năm; báo cáo giữ mọi bữa đã ghi dù cấu hình đổi.
- AC-2 (OBS-25, OBS-28): bảng học kỳ không bỏ kết quả mục tiêu lớp cũ cùng năm/nhóm tuổi; mục tiêu chưa có kết quả để trống.
- AC-3 (OBS-28): xác nhận phiếu cuối năm chốt thông tin nhóm tuổi, giáo viên, HT và cân đo cùng kết quả; bản cũ không bị tự ghi đè.
- AC-4: giữ contract; khóa phiếu đã xác nhận, phạm vi truy cập và công thức hiện hành không đổi.

## Triển khai

1. ClassroomService dùng YearSnapshotReader; tổng hợp suất ăn giữ bữa có trong bản ghi.
   Sổ ăn giữ session đã lưu/tally thực tế và cập nhật về 0 khi bữa không còn suất.
2. Học kỳ hợp danh sách mục tiêu lịch lớp với mục tiêu có kết quả theo lịch sử học.
3. Thêm migration mới cho thông tin trình bày phiếu cuối năm, đọc tương thích bản cũ.
4. Regression ba lỗi và chạy test hiện có cho cấu hình, attendance/meals, observation, quyền và mốc thời gian; FE kiểm consumer hiện có.

## Validation log

- ClassroomServiceImpl.toInfo đọc AgeGroup chung; AttendanceServiceImpl lọc bữa lưu qua danh sách hiện tại.
- SemesterAssessmentServiceImpl.build lọc YearGoalRow trước khi nhận kết quả lịch sử học.
- YearEndSheetServiceImpl.freeze chỉ lưu goal_results; view đọc cân đo và người ký mỗi lần.
- Không đổi endpoint hoặc DTO công khai; migration chỉ thêm cột nullable, không suy diễn snapshot phiếu cũ.

## Progress

- 22/11/2026: user duyệt phạm vi sửa và kiểm chứng trong chat; bắt đầu triển khai.
- 22/11/2026: triển khai ba lỗi và sửa bổ sung ở sổ ăn theo regression/review; không đổi nghiệp vụ hoặc contract. Báo cáo: [kiểm chứng](../../quality/2026-11-22-three-module-continuity.md).
- 22/11/2026: kết quả mới nhất 506 BE/64 lớp test và 652 FE đạt, không bỏ qua; Spotless/lint/build đạt. UI bốn vai trò và giao thông báo đầu cuối chưa chạy, nêu rõ trong báo cáo.
