---
title: Đánh giá trẻ hằng ngày, mở lại ngày đánh giá, tổng quan lớp
status: in-progress
owner: Nhữ Đình Cần
jira: G94-236 / BE-FEAT-29
branch: feature/G94-236-BE-FEAT-29-daily-attendance
modules: [learning-observation]
rules: [OBS-15, OBS-16, OBS-17, OBS-18, OBS-19, OBS-20, OBS-21, OBS-22, EDU-19]
risk: high
created: 2026-10-28
updated: 2026-10-29
---
# Plan — Đánh giá trẻ hằng ngày, mở lại ngày đánh giá, tổng quan lớp (BE thật)

Nhánh `feature/G94-236-BE-FEAT-29-daily-attendance` · Làn L (migration, API mới, ≥2 module) · Chế độ tự chạy (CLAUDE.md) · Cần chốt 27/10/2026 "xoá demo đi bạn làm thật luôn".

## Phạm vi
- Đánh giá hằng ngày 2 view của giáo viên: Hoạt động chung (sức khỏe theo tiêu chí, ăn, ngủ trưa, cảm xúc, hành vi, 1 cờ / ngày, nhận xét) và Kiến thức kỹ năng (Đạt / Cần cố gắng / Chưa đạt theo MT của từng hoạt động trong giáo án đã duyệt). Nhận xét 1 bản / trẻ / ngày, hai view dùng chung.
- Mở lại ngày đánh giá (HT, PHT điểm trường mình), "Mở đến", kết thúc sớm, lịch sử có lọc.
- Tổng quan đánh giá lớp theo kỳ (hôm nay / tuần học T2–T6 / tháng dương lịch) + chi tiết lớp theo trẻ.
- Ngoài phạm vi (giữ mock FE): đánh giá tuần/tháng/cuối năm, phiếu bé ngoan, khen thưởng, AI.

## Thiết kế
- Feature `observation` (module card `docs/modules/learning-observation.md`): controller/service/repository/entity/dto `observation`.
- Bảng (V32, đổi số khi merge Tiền): `daily_observations` (1 dòng / trẻ / ngày: sức khỏe cô chỉnh, ăn từng bữa, ngủ, cảm xúc, hành vi, cờ, nhận xét), `daily_activity_results` (1 dòng / trẻ / ngày / dòng hoạt động của giáo án: MT đạt / cần cố gắng / chưa đạt, mức tham gia, id giáo án), `assessment_day_unlocks` (như mở lại điểm danh).
- Đọc dữ liệu module khác qua service (ADR-0001): giáo án tuần đã duyệt (`EduLessonQueryService` mới trong eduplan, chỉ đọc), điểm danh (`AttendanceService.findDayStatuses`), sức khỏe trong ngày (`DailyHealthService.findDaySymptoms`), lớp / trẻ / phân công (organization, child).
- Không lưu bản sao hoạt động / MT: kết quả tham chiếu `edu_goal_rows.id` + `edu_class_docs.id`.

## Quy tắc BE tự kiểm
EDU-19 (khóa khi chưa có giáo án duyệt), chỉ trẻ có mặt, ngày D khóa hết 23:59:59 giờ VN, sửa sau khóa chỉ khi có lần mở lại còn hiệu lực, mở lại (ngày học từ đầu năm tới hôm qua; hạn sau hiện tại, không quá cuối năm; không mở chồng; kết thúc sớm), năm đã kết thúc chỉ xem (GV xem lại lớp từng dạy), phạm vi GV / PHT / HT (AUTH-02, AUTH-03), không tự ghi Đạt cho trẻ chưa chấm.

## Progress log
- 2026-10-28: viết plan; giả định: mã lỗi dải mới 73xx "Đánh giá trẻ"; tiêu chí ăn / ngủ / cảm xúc / hành vi / tham gia theo danh mục FE hiện có (P-11 còn PENDING, đổi được bằng migration sau).

## Progress log
- 2026-10-28: V27, service, 3 controller, 5 integration test (pass); tài liệu BUSINESS_RULES OBS-15..21, DATA_DICTIONARY, ERROR_CONTRACT 73xx, module card. Chen lỗi nhãn sức khỏe ở bảng điểm danh (`health` trong ClassAttendanceResponse, TemperatureLevel). verify PASS. Còn: nối FE.

## Bổ sung đã được người dùng duyệt — Nhận xét chung lớp, 29/10/2026

Người dùng yêu cầu nhận xét tình hình chung lớp để PHT/HT nắm bắt và trả lời "bạn làm đi" sau đề xuất: GV nhập dưới bảng Hoạt động chung, lưu cùng nút Lưu đánh giá; PHT campus mình/HT toàn trường xem trong Tổng quan; tuần/tháng xem lại nhận xét từng ngày, không thêm báo cáo/AI. Đây là nguồn duyệt phạm vi bổ sung, không tự mở rộng APP.

- Actor: GV đang dạy ghi, PHT/HT xem trong scope. Ngày/năm/khóa/mở lại theo OBS-18, OBS-20; không đổi quyền.
- Dữ liệu riêng lớp + ngày, tối đa 500 ký tự theo convention nhận xét hiện có; không dùng thuật ngữ chẩn đoán. Lưu tên người cập nhật và thời gian. Không sao chép vào nhận xét từng trẻ.
- Migration **V33** sau merge Tiền (trước đây WIP V28); bảng `class_daily_comments`, unique lớp/ngày. Giữ migration đã commit của Tiền; không chạy migration trên DB người dùng trong task.
- Contract thêm tương thích: GET daily sheet thêm `classComment`; PUT General thêm `classComment` optional (null/thiếu = giữ, chuỗi rỗng = xóa nội dung), cho `entries: []` khi chỉ lưu nhận xét lớp. Endpoint giữ nguyên, một transaction lưu trẻ + nhận xét.
- Overview và detail thêm `classComments` (date, text, recordedBy, recordedByName, updatedAt); chỉ query lớp trong phạm vi, lọc ngày trong kỳ và năm. Không làm thay đổi số trẻ đã đánh giá.
- Consumer: FE adapter, mock, màn nhập và tổng quan; APP không đổi, client cũ bỏ qua field mới.

### AC và kiểm thử

- AC-C1: GV lưu nhận xét cùng đánh giá trẻ hoặc riêng khi mọi trẻ đã lưu; tải lại không mất; request cũ bỏ field không xóa nhận xét.
- AC-C2: chỉ nhận xét không tạo đánh giá/cờ cho trẻ; cùng lớp/ngày cập nhật một bản, giữ metadata người ghi.
- AC-C3: ngày khóa/năm đóng/khác lớp không ghi được; mở lại hợp lệ cho ghi; PHT/HT không ghi thay GV.
- AC-C4: PHT chỉ đọc nhận xét campus mình; HT xem toàn trường. Ngày/tuần/tháng hiển thị ngày, người ghi và thời gian, không trộn nhận xét trẻ.
- AC-C5: lỗi không mất draft; xóa trắng được; nhập quá 500/thuật ngữ chẩn đoán bị từ chối; trạng thái loading/read-only rõ ràng.
- Phase 1 BE + mock/adapter/tests; phase 2 UI + kiểm thử/review. Người dùng đã yêu cầu triển khai toàn luồng; thực hiện liên tục trong phạm vi đã duyệt.

### Validation / Progress

- Source trước sửa: `SaveDailyGeneralRequest` bắt entries không rỗng; `DailySheetResponse`/overview/detail chưa có nhận xét lớp; FE `DailyAssessmentPage` chỉ có drafts từng trẻ. Không có tài nguyên nhận xét lớp để tái sử dụng.
- 29/10: bắt đầu phần bổ sung; giữ nguyên WIP hiện tại và lần sửa lưu mặc định, không commit/push; cổng FE có lỗi test menu cũ, DB integration hiện không khả dụng do Docker.
- Triển khai V28/entity/repo, optional field và metadata; note-only không tạo kết quả trẻ; 5 unit test mới + kiến trúc/mã lỗi 22/22 đạt. Review tĩnh không thấy lỗi scope/khóa mới; integration DB và snapshot chưa kiểm chứng.
- Schema row đánh giá tên riêng `AssessmentClassRow` tránh trùng `ClassRow` sức khỏe. Snapshot cập nhật field mới theo DTO; cần đối chiếu snapshot sinh thật khi Docker sẵn sàng.
- Kết quả sau cùng 29/10: Docker đã khả dụng; `node scripts/verify.mjs` **VERIFY PASS full | tests 403 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok**. V28 và integration nhận xét lớp chạy trên PostgreSQL 17 tạm, snapshot khớp bản sinh thật. Ghi nhận này thay cho giới hạn Docker ở các dòng tiến độ trước.
- FE dashboard + hardening 56/56; lint/design/build và format phần sửa đạt. Full FE bị lỗi menu có sẵn (97/98, trùng thực đơn tuần 2026-11-02); không thay module ngoài phạm vi. `check-ai-layer` hai repo exit 0, review FE/BE hoàn tất; lỗi serializer chuỗi rỗng đã được test và sửa.
- Không áp V28 vào DB người dùng hoặc restart BE; chưa kiểm UI trình duyệt, chưa commit/push. Chạy lại cổng verify/check-ai sau ghi log này; xem output bàn giao cuối. Plan giữ in-progress cho WIP toàn luồng và các kiểm chứng môi trường còn lại.
- Sau merge Tiền 29/10: hai migration WIP đổi từ V27/V28 thành V32/V33, SQL giữ nguyên; Tiền V27–V31 giữ nguyên. Các kết quả 403/403 phía trên là baseline trước merge; kết quả sau merge nằm ở [plan merge](2026-10-29-merge-tien-education.md).

- 30/10/2026: người dùng yêu cầu nhận xét chung không giới hạn ký tự. Bỏ maxLength/Size và guard 500 riêng classComment, entity TEXT; migration mới V34 đổi cột sang TEXT, không sửa V33 đã có. Test lưu/đọc 10000 ký tự; các giới hạn nhận xét từng trẻ không thay đổi. Chưa áp DB người dùng. Kiểm chứng cuối trong verify.log/target/verify.log.
