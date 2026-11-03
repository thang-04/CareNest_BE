# Data model — Feature010

Không migration, không bảng/cột mới.

## Dùng lại
- `health_measurements`: chiều cao, cân nặng, ngày đo, `exam`/`exam_status` (phiếu khám), `campaign_id`.
- `health_check_campaigns.kind`: `EXAM` / `MEASUREMENT`.
- `children.date_of_birth`, `children.gender` (qua `ChildService.findChildBriefs`, `ChildIntakeService.getChildView`).

## Dữ liệu dẫn xuất (tính khi đọc)
- Mức BMI = f(chiều cao, cân nặng, tuổi tháng tròn tại ngày đo, giới tính, bảng WHO) — chỉ lần đo không có phiếu khám. Không lưu: sửa số đo, sửa ngày sinh/giới tính ⇒ mức đổi theo.
- `latestExam` = lần đo đã công bố gần nhất có mức theo dõi khác UNAVAILABLE. `latestBmi` = lần đo kỳ cân đo gần nhất xếp được mức.
- Đếm theo mức BMI, trẻ cần chú ý: theo hàng danh sách lớp của đợt/kỳ (`HealthCheckRules.rosterOf`).

## Ngừng dùng
- `health_follow_ups`, `health_follow_up_events`, `health_follow_up_images` (V65): giữ dữ liệu, không entity/repository. Xóa bằng migration mới khi team chốt. Ảnh S3 prefix `health/follow-up-results/` giữ nguyên.
- `child_health_declaration_history.action = UPDATED_FROM_FOLLOW_UP`: không ghi mới, enum vẫn đọc được dòng cũ.
