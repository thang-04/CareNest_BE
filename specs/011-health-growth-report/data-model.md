# Data model — Feature011

Không migration, không bảng/cột mới.

## Dùng lại
- `health_check_campaigns` (kind, campus_id, start_date, end_date, cancelled_at, closed_at): chọn đợt/kỳ của tháng.
- `health_measurements` (child_id, campaign_id, classroom_id, date, height_cm, weight_kg, exam_status): lần đo được tính.
- `health_check_absences`: trẻ vắng lúc đóng (qua `HealthCheckRules.rosterOf`).
- `classrooms.age_group_id`, `age_groups` (`age_from_months`, `code`, `sort_order`, `name`): cấp học, thứ tự lớp.
- `children.date_of_birth`, `children.gender` (qua `ChildService.findChildBriefs`).

## Dữ liệu dẫn xuất (tính khi đọc)
- Mức W/A, H/A, BMI = f(chiều cao, cân nặng, tuổi tháng tròn tại ngày đo, giới tính, bảng WHO).
- Số đếm lớp/MG/NT/điểm trường/toàn trường từ danh sách lớp của đợt/kỳ trong tháng.
- Bảng WHO: `src/main/resources/health/who-weight-for-age.csv`, `who-height-for-age.csv` (cùng định dạng `who-bmi-for-age.csv`).
