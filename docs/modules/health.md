# Module — health

Feature: `health` (trong `controller/`, `service/`, `repository/`, ... — `docs/architecture/PACKAGE_STRUCTURE.md`) · Status: đợt khám/kỳ cân đo, số đo trong kỳ, phiếu khám Phụ lục II, mức theo dõi đã có (2026-10-31); cảnh báo ở sổ sức khỏe, mức BMI theo tuổi WHO của kỳ cân đo đã có, phiếu theo dõi đã bỏ (2026-11-04); phân loại WHO khác (hoãn), trend, AI: thiết kế

## Mục đích
Cân đo theo kỳ cân đo do HT/PHT mở (thường 3 tháng/lần, cảnh báo khi sát nhau — HLT-01), đợt khám bác sĩ 1 lần/năm học (HLT-08), cảnh báo sức khỏe lưu ở sổ sức khỏe: kết luận bác sĩ của đợt khám, mức BMI theo tuổi chuẩn WHO của kỳ cân đo (HLT-18, HLT-19), trend xác định, diễn giải AI có duyệt; kiểm tra sức khỏe đầu ngày nhập cùng màn điểm danh (HLT-07); sức khỏe trong ngày báo phụ huynh ngay (HLT-14).

## Owns
HealthRecord (mỗi lần đo; sửa kèm lý do, có lịch sử — HLT-13), phiếu khám Phụ lục II và mức theo dõi từ kết luận bác sĩ (HLT-08, HLT-10), mức BMI theo tuổi của số đo kỳ cân đo (HLT-18, bảng `src/main/resources/health/who-bmi-for-age.csv`), cảnh báo hiện tại ở sổ sức khỏe (HLT-19), đánh dấu suy dinh dưỡng/thừa cân (HLT-12), trạng thái công bố cho phụ huynh (HLT-11), đợt khám bác sĩ (HLT-08), ghi nhận sức khỏe trong ngày (triệu chứng, xử trí, tai nạn nhỏ — HLT-14), trend calculation, HealthInterpretation (DRAFT/APPROVED, source).

## KHÔNG owns
Dị ứng (child — HLT-06), chẩn đoán/điều trị (exclusion), quy trình can thiệp dinh dưỡng (HLT-12), quy trình xử lý cảnh báo (trao đổi phụ huynh, kết quả chuyên khoa — phiếu theo dõi HLT-16 đã bỏ 2026-11-04).

## Rules
HLT-01..05, HLT-07..14, HLT-17..19, AI-01..06, AI-07 (giải thích xu hướng sức khỏe). Flow: `docs/business/flows/health-check.md`. G94-180/HLT-08 (lọc đợt khám theo năm học, chốt 2026-10-28 — `BUSINESS_RULES` mục G94-180): plan `docs/plans/active/2026-10-28-child-enrollment-guardian-accounts.md` mục 9. Còn mở: P-40 (điều chỉnh tay mức BMI).

## Phụ thuộc
- Dùng: service `child` (mã trẻ, hồ sơ, ngày sinh, giới tính, lớp hiện tại; đọc/ghi tiền sử khai báo `ChildIntakeService.findMedicalHistory`/`updateMedicalHistory`), identity (scope), `AiClient` (`integration/`).
- Được dùng bởi: learning-observation (DevelopmentProfile), reporting (trẻ ốm theo lớp — RPT-01).
- Event: ghi nhận sức khỏe trong ngày, công bố kết quả cân đo ⇒ notification.

## API & bảng
Plan: `docs/plans/completed/2026-10-31-health-check-rounds.md` (phạm vi B1, mặc định B2–B8), `docs/plans/completed/2026-10-31-health-exam-follow-ups.md` (mặc định C1–C10), `docs/plans/active/2026-11-04-health-warnings-book.md` (cảnh báo ở sổ sức khỏe, bỏ phiếu theo dõi).
- Bảng (V64): `health_check_campaigns`, `health_check_campaign_history`, `health_check_absences`, `health_measurements`, `health_measurement_edits`; (V65) cột phiếu khám ở `health_measurements`; V67 cho phép người đóng/người ghi lịch sử trống khi hệ thống tự đóng (`docs/database/DATA_DICTIONARY.md`). Bảng phiếu theo dõi (V65: `health_follow_ups`, `health_follow_up_events`, `health_follow_up_images`) ngừng dùng từ 2026-11-04, giữ dữ liệu cũ, không entity/API.
- `/health-checks` (GET danh sách lọc `campusId`, `kind`, `status`, `schoolYear`; POST tạo), `/health-checks/schedule-warnings`, `/health-checks/{id}` (GET, PUT), `/{id}/classes/{classId}/children`, `/{id}/cancel|close|reopen` — HT, PHT quản lý theo điểm trường; GV xem điểm trường của lớp mình. `progress.bmi` đếm trẻ theo mức BMI; chi tiết có `classes[].bmiWarning`, `watchList` = trẻ cần chú ý (đợt khám: theo dõi/theo dõi sát; kỳ cân đo: BMI khác bình thường).
- `/{id}/reopen`: nhận `reason` và `endDate` tùy chọn; `EXAM` đã hết hạn bắt buộc ngày kết thúc mới từ hôm nay (HLT-17). Request chỉ có `reason` giữ tương thích cho đợt chưa hết hạn/kỳ cân đo.
- `/children/{childId}/health` (GET sổ sức khỏe: lần đo kèm cảnh báo, `latestExam`, `latestBmi`), `/children/{childId}/health/measurements` (POST), `/{measurementId}` (PUT, bắt buộc lý do) — GV phụ trách lớp nhập trong kỳ cân đo đang diễn ra (HLT-09); phản hồi có `bmiStatus`, `bmiZScore`.
- `/health-checks/{id}/children/{childId}/exam` (GET; PUT lưu nháp/kết luận — GV lớp; POST `/review-preview`) — phiếu khám Phụ lục II của đợt khám.
- Mã lỗi 5009–5034 (`docs/contracts/ERROR_CONTRACT.md`); 5035–5039 của phiếu theo dõi đã bỏ, không dùng lại.
- Chưa có: phân loại WHO khác BMI (hoãn), điều chỉnh tay mức BMI (P-40), báo cáo 5.1, 5.3 (hoãn), trend, AI, thông báo (mở đợt, cập nhật kết quả — chưa có module thông báo), API phụ huynh xem kết quả.

## PENDING
P-21 (triệu chứng chọn nhanh), P-22 (trẻ bất thường lúc nhận), P-23 (ưu tiên sức khỏe đầu ngày), P-40 (điều chỉnh tay mức BMI).

## Known pitfalls
- Mức theo dõi chỉ từ kết luận bác sĩ của phiếu đã kết luận (`HealthExamOutcome`); số đo của kỳ cân đo, phiếu nháp luôn `UNAVAILABLE`. Kỳ cân đo cảnh báo bằng `bmiStatus` (HLT-18); phiếu khám không có `bmiStatus` (bác sĩ kết luận).
- Mức BMI tính khi đọc (`WhoBmiForAge`, `HealthRecords.bmiOf`) từ số đo, ngày sinh, giới tính hiện tại: không lưu cột; sửa số đo hoặc ngày sinh ⇒ mức đổi theo. Tuổi = tháng tròn (`ChronoUnit.MONTHS`); 24 tháng dùng bảng đo đứng; từ 61 tháng ngưỡng WHO 2007 (+1SD thừa cân, +2SD béo phì); ngoài 0–84 tháng hoặc thiếu giới tính ⇒ `null`. Z-score tính từ BMI chưa làm tròn, làm tròn 2 chữ số rồi mới xếp mức (mức luôn khớp z hiển thị); BMI trả API làm tròn 1 chữ số.
- Phiếu không còn câu "vấn đề mới hay đã biết" (user bỏ 2026-11-04): có vấn đề sức khỏe ⇒ WATCH. `issueKind` chỉ giữ khi sửa phiếu cũ đã kết luận có câu trả lời (`HealthExamNormalizer` tham số `keepsIssueKind`), nên mức KNOWN_STABLE của phiếu cũ không đổi khi sửa. API không còn `knownCondition`.
- Bảng `who-bmi-for-age.csv` chép nguyên từ bảng z-score WHO; không sửa tay. `WhoBmiForAgeTest` đối chiếu vài giá trị SD đã công bố.
- Phiếu khám nháp không phải kết quả: không vào sổ sức khỏe, lần khám trước, cảnh báo hiện tại; danh sách lớp hiện `IN_PROGRESS` (đợt đã đóng: `NOT_CONCLUDED`). Đọc lần đo ở chỗ mới phải lọc `isDraft()`.
- Nháp vẫn chiếm ngày đo (UNIQUE `(child_id, date)`) và vẫn kiểm khoảng chiều cao/cân nặng/vòng đầu để không vi phạm CHECK.
- Phiếu đã kết luận theo mẫu FOLLOW_UP cũ (có `followUpFinding`) giữ mẫu đó khi xem/sửa; phiếu mới, phiếu nháp luôn STANDARD. Mức theo dõi hiện tại chỉ từ lần khám gần nhất, không còn xét phiếu theo dõi đã đóng.
- Đọc nhiều trẻ một lần qua `HealthRecords.load` (snapshot), tránh truy vấn theo từng trẻ.
- Vòng đầu đo khi trẻ chưa quá 60 tháng: tới trước ngày đủ 61 tháng (`HealthRecords.headCircumferenceUntil`).
- Suy dinh dưỡng/thừa cân chỉ đánh dấu; không làm quy trình theo dõi/can thiệp (HLT-12).
- Lưu là công bố (HLT-11): số đo khi lưu, phiếu khám khi kết luận; sức khỏe trong ngày thì báo ngay (HLT-14).
- Sửa số đo không ghi đè mất dấu: lưu lịch sử + lý do (HLT-13).
- Prompt + kiểm tra output AI phải chặn ngôn ngữ chẩn đoán (HLT-04).
- Không có chu kỳ đo cấu hình: HT/PHT mở kỳ cân đo, hệ thống chỉ cảnh báo khi ngày bắt đầu cách đợt/kỳ khác dưới 2 tháng 15 ngày (HLT-01, sửa 2026-10-31).
- Mỗi điểm trường một đợt/kỳ chưa đóng, tính chung hai loại (index UNIQUE có điều kiện).
- Tạo đợt khám/kỳ cân đo chỉ nhận ngày bắt đầu và kết thúc từ hôm nay theo giờ trường; không áp giới hạn này khi sửa đợt đã bắt đầu (user yêu cầu 2026-11-01, `HealthCheckServiceImpl.checkCreationDates`).
- Tự đóng `EXAM` theo HLT-17: chạy lúc 00:00 theo `carenest.time-zone` và khi ứng dụng khởi động để đóng bù đợt quá hạn; ghi `closed_at` và lịch sử `CLOSED` với actor trống/"Hệ thống". Không chỉ suy diễn nhãn trạng thái; mở lại cập nhật ngày kết thúc mới rồi tiếp tục tự đóng khi hết hạn (user yêu cầu 2026-11-01).
- Đợt/kỳ thuộc năm học của ngày bắt đầu; lớp của đợt = lớp điểm trường trong năm đó (`findYearClassrooms(yearId)`, không truyền null vì null = năm đang hoạt động).
- Tên record lồng trong DTO phải duy nhất toàn hệ thống: springdoc đặt tên schema theo tên đơn giản, trùng (vd. `History`) sẽ ghi đè schema của module khác trong `docs/api/openapi.yaml`.

## Related issues
Chưa có.

## Đọc thêm khi
Đụng AI ⇒ card ai-assistance. Đụng hiển thị phụ huynh ⇒ ADR-0010.
