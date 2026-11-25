---
id: BUG-261122-yearly-meals-assessment-presentation
type: bug
modules: [organization, attendance, observation]
rules: [CFG-YR-02, OBS-25, OBS-28]
status: fixed
date: 2026-11-22
keywords: [bữa theo năm, chuyển lớp, mục tiêu học kỳ, bản in đã xác nhận]
---

# Cấu hình bữa và thông tin đánh giá lịch sử

## Root cause

- ClassroomService đọc danh mục nhóm tuổi chung, không đọc thông tin theo năm hoặc bản chụp năm đóng.
- Tổng hợp điểm danh lọc bữa đã ghi qua cấu hình hiện tại, làm mất số đếm khi bỏ bữa khỏi cấu hình.
- Sổ ăn chỉ duyệt bữa cấu hình; listener bỏ qua bữa bị bỏ khỏi cấu hình và không còn suất, khiến dòng sổ cũ không về 0.
- Học kỳ chỉ chọn mục tiêu theo lịch lớp hiện tại dù đã nhận kết quả lịch sử lớp cũ.
- Phiếu cuối năm chốt mục tiêu nhưng đọc lại người ký, tên nhóm và cân đo ở mỗi lần xem.

## Fix

- Dùng YearSnapshotReader cho thông tin lớp; tổng hợp hợp danh sách bữa cấu hình và bữa đã ghi.
- Sổ ăn giữ session đã lưu và tally thực tế; dòng đã tồn tại được cập nhật về 0 dù session không còn áp dụng.
- Hợp mục tiêu học kỳ với kết quả trẻ đã có cùng năm/nhóm tuổi; mục tiêu lịch sử chỉ áp dụng cho trẻ có kết quả.
- V169 thêm sheet_presentation nullable; xác nhận chốt thông tin trình bày. Phiếu cũ không tự backfill.
- Không đổi API hoặc công thức OBS-25/27/28.

## Attempts

1. Compile đạt. Regression đầu tiên: 21/22 đạt; ca đóng năm dùng JDBC đổi trạng thái nhưng persistence context còn giữ ACTIVE. Clear context sau SQL trong test để kiểm tra đúng trạng thái đã lưu.
2. Chạy lại với bộ regression rộng hơn của ba module, quyền, API và kiến trúc; kết quả ghi trong báo cáo kiểm chứng cùng ngày.
3. Đợt rộng 459 ca: 4 lỗi (DailyAssessmentIntegrationTest 3 fixture thiếu nhóm tuổi theo năm; MealCountIntegrationTest 1 ca sổ cũ bị ẩn). Bổ sung YearAgeGroupFixture trong test thuộc ba module và sửa đọc sổ cũ.
4. Review đọc phát hiện listener giữ số cũ khi tắt suất cuối cùng của bữa đã bỏ: sửa cập nhật dòng tồn tại về 0, thêm regression. Một lần testCompile thiếu injection EntityManager trong test mới, bổ sung rồi chạy lại.

## Regression

- AttendanceHistoryIntegrationTest.yearlySessionsAndRecordedMealsRemainIndependentOfGlobalCatalog
- ThemeEvaluationIntegrationTest.semesterRetainsGoalsFromPreviousClassEvenWhenNewClassHasNoSuchTheme
- ThemeEvaluationIntegrationTest.yearEndSheetAggregatesThemesThenTeacherEditsAndConfirmFreezesIt
- MealCountIntegrationTest.recordedMealSurvivesConfigurationChangeBeforeLedgerCreation
- MealCountIntegrationTest.vicePrincipalReadsClosedYearMealBookOfOwnCampus

## Giới hạn

Không thể khôi phục chính xác tên người ký/cân đo tại thời điểm xác nhận của phiếu cũ chưa lưu snapshot. Chỉ giữ đường đọc tương thích và không giả lập thông tin lịch sử.

## Kết quả cuối

506 ca BE / 64 lớp test: kết quả mới nhất không lỗi, không bỏ qua (các ca lỗi ban đầu đã chạy lại sau sửa); 652 ca FE đạt. Spotless, ESLint, Prettier file FE sửa và Vite build đạt. Xem [báo cáo kiểm chứng](../../quality/2026-11-22-three-module-continuity.md) để phân biệt tự động với UI chưa chạy.
