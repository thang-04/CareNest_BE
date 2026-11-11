# Feature010 — Cảnh báo sức khỏe ở sổ sức khỏe, mức BMI kỳ cân đo, bỏ phiếu theo dõi

Nguồn: `domain-spec.md`, user 04/11/2026. Plan quản trị: `docs/plans/active/2026-11-04-health-warnings-book.md`.

## User stories và acceptance
- US1 (P1): GV lưu số đo kỳ cân đo, thấy mức BMI theo tuổi; sửa số đo kèm lý do thì mức tính lại. AC-1, AC-2.
- US2 (P1): GV, HT/PHT mở sổ sức khỏe thấy cảnh báo hiện tại (bác sĩ, BMI) và cảnh báo từng lần đo. AC-1, AC-3.
- US3 (P2): HT/PHT xem đợt/kỳ thấy số trẻ theo mức BMI, trẻ cần chú ý. AC-4.
- US4 (P1): GV không còn phiếu theo dõi phải xử lý. AC-3, AC-5.

## Functional requirements
- FR-01: Số đo kỳ cân đo trả `bmiStatus`, `bmiZScore` theo bảng WHO (domain spec mục 10); phiếu khám trả null.
- FR-02: Sổ sức khỏe trả `latestExam` (lần khám gần nhất có kết luận) và `latestBmi` (lần đo kỳ cân đo gần nhất có mức BMI).
- FR-03: Đợt/kỳ trả `progress.bmi`; chi tiết trả `classes[].bmiWarning`, `watchList` theo loại đợt; dòng trẻ trả `bmi`, `bmiStatus`.
- FR-04: Kết luận phiếu khám không tạo phiếu theo dõi; bỏ API `/health-follow-ups/**`, trường liên quan, mã lỗi 5035–5039.
- FR-05: Phiếu đã kết luận theo mẫu FOLLOW_UP cũ giữ mẫu đó; phiếu mới/nháp dùng STANDARD.
- FR-06: Không migration; bảng phiếu cũ giữ dữ liệu, không entity.

## Clarifications
### Session 2026-11-04
- Q: Cảnh báo lưu ở đâu, ai xử lý? → A: user: lưu ở sổ sức khỏe, giảm việc GV; bỏ phiếu theo dõi.
- Q: Kỳ cân đo cảnh báo gì? → A: user: BMI; ngưỡng theo WHO (HLT-03).
- Q: GV chỉnh tay mức BMI? → chưa có nguồn chốt; không làm, ghi P-40.

## Success criteria
- Không còn endpoint/màn phiếu theo dõi; test hiện có và test mới pass; OpenAPI snapshot cập nhật có chủ đích.
- Mức BMI đối chiếu đúng giá trị SD WHO công bố (test).
