---
id: BUG-261125-year-end-sheet-draft-measurement
type: bug
modules: [observation, health]
rules: [OBS-28, HLT-08, HLT-11]
status: fixed
date: 2026-11-25
keywords: [phiếu cuối năm, cân nặng, chiều cao, phiếu khám nháp, findLatestMeasurement]
similar_to: []
---

# BUG-261125-year-end-sheet-draft-measurement — phiếu cuối năm lấy số đo của phiếu khám nháp

- Module: observation (phiếu cuối năm, OBS-28) gọi health (`HealthMeasurementService.findLatestMeasurement`).
- Triệu chứng: phiếu đánh giá sự phát triển cuối năm hiện cân nặng/chiều cao của phiếu khám nháp (chưa công bố, HLT-11). Khi GV xác nhận, số sai bị chốt vào `child_evaluations.sheet_presentation` và phụ huynh xem được. Cùng ngày có phiếu khám và số đo kỳ cân đo thì lấy bản lưu sau thay vì phiếu khám (chỉ xảy ra khi có V186 của nhánh G94-180).
- Root cause: `findLatestMeasurement` lấy dòng đầu theo ngày giảm dần, id giảm dần, không lọc `isDraft()` và không ưu tiên `isExam()`.
- Fix: lọc bỏ phiếu nháp; chọn ngày gần nhất không sau mốc, cùng ngày phiếu khám thắng, rồi id lớn hơn (giống `HealthGrowth.SAME_DAY_PREFERENCE`). Phiếu đã xác nhận giữ số đã chốt; không cần sửa dữ liệu cũ.
- Test: `ThemeEvaluationIntegrationTest#yearEndSheetAggregatesThemesThenTeacherEditsAndConfirmFreezesIt` thêm phiếu khám nháp ngày gần hơn (16.9 kg) — trước sửa phiếu hiện 16.9, sau sửa 15.2. Ca cùng ngày cần V186 (G94-180), chạy thêm khi nhánh đó vào dev.
- Ghi chú: ràng buộc `ck_health_measurements_values` (V65) bắt phiếu đã kết luận và số đo kỳ cân đo luôn đủ chiều cao, cân nặng, nên không có trường hợp phiếu khám đã kết luận thiếu một ô.
- Nguồn: báo lỗi của người test ngày 25/11/2026.
