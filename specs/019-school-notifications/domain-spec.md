# Bổ sung xóa thông báo đã hủy và nhắc điểm danh — 17/11/2026

## 1. Domain Overview
Dọn thông báo nhà trường đã hủy; nhắc lớp chưa điểm danh trong khung giờ cấu hình.
## 2. Scope
BE+FE, API/mock, bộ lọc và xóa một/tất cả; không sửa thông báo đã gửi, không đổi lịch lặp, không sửa DB thật.
## 3. Actors & Roles
HT toàn trường, PHT phạm vi campus hoặc tin do mình gửi theo quyền hiện có; GV nhận nhắc theo phân công ngày.
## 4. Domain Concepts
SchoolAnnouncement, AnnouncementAttachment, bộ lọc q/status, giờ khóa điểm danh, khung 5 phút.
## 5. Domain Relationships
Thông báo đã hủy không sinh tin nhận; attachment thuộc thông báo, CASCADE khi xóa. Mẫu/lịch độc lập, không xóa theo.
## 6. User Requirements
Xóa một hoặc mọi tin đã hủy khớp bộ lọc trên mọi trang; không gửi cho bản thân; nhắc từ 08:00 đến trước cutoff mỗi 5 phút.
## 7. Business Rules
PAR-13 mới; PAR-07 đã loại người gửi, giữ cả preview/gửi ngay/hẹn/lặp. OBS-30 phần nhắc điểm danh thay 30 phút trước khóa bằng khung [08:00,cutoff). Ngày học/thứ/người nhận giữ cũ.
## 8. Business Processes
[B] Người dùng lọc/chọn xóa/xác nhận. [S] BE lọc quyền+trạng thái, khóa bản ghi, xóa DB rồi dọn storage sau commit; UI tải lại, điều chỉnh trang nếu trang cuối rỗng.
[S] Mỗi 5 phút kiểm ngày học, baseLocked, now>=08:00 và now<cutoff, chỉ lớp chưa điểm danh; khóa chống trùng trong link theo lớp+ngày+slot.
## 9. State / Lifecycle
Chỉ CANCELLED chuyển sang xóa. SENT/SENDING/SCHEDULED bị từ chối 409; ngoài scope 404. Không đổi status qua frontend.
## 10. Business Constraints
Xóa tất cả áp bộ lọc q/status, không phụ thuộc page/size; filter trạng thái khác CANCELLED không xóa tin nào. Không tự xóa dữ liệu môi trường thật khi triển khai.
## 11. Non-Functional Requirements
Giữ auth server-side, khóa DB chống race, cleanup sau commit, không tự retry DELETE/POST nếu timeout, giữ input lọc khi lỗi.
## 12. Acceptance Criteria
AC1 lọc q/status+phân trang đúng scope. AC2 xóa một CANCELLED, chặn trạng thái khác/ngoài scope. AC3 bulk khớp q/status toàn bộ trang, không chạm tin khác/campus khác. AC4 tệp DB xóa cùng parent, cleanup storage chỉ sau commit. AC5 không tự nhận kể cả vai trò BGH và lịch lặp. AC6 07:55 không nhắc, 08:00 nhắc, slot sau nhắc lại, tại/sau cutoff hoặc đã điểm danh/ngày nghỉ không nhắc.
## 13. Domain Terminology
Giờ quét là cron mỗi 5 phút; giờ khóa lấy cấu hình DayInfoResponse.cutoff, không hardcode 12h. Đã hủy là CANCELLED.
## 14. Assumptions & Open Questions
Đã chốt bulk theo bộ lọc trên mọi trang qua câu trả lời user; quyền tái sử dụng phạm vi hiện có. Không còn câu hỏi chặn. Nguồn: Tiền 17/11/2026 và yêu cầu tiếp tục.

## Thiết kế / contract
GET /announcements thêm q, status (optional), giữ payload phân trang; AnnouncementResponse thêm canDelete.
DELETE /announcements/{id}: xóa một, trả thành công không data.
DELETE /announcements/cancelled?q=&status=: trả data là số bản ghi đã xóa; không giới hạn theo page/size.
Dùng bảng hiện có và FK cascade attachment, không migration. Query dùng Criteria cho quyền+status+LIKE đã escape. Bulk khóa các bản ghi khớp, một transaction. FileStorage cleanup afterCommit.

## Analyze trước code
Rule→AC→contract/data/test đã đối chiếu; dùng constitution hiện có. Không đổi quyền hoặc lifecycle khác. Sender exclusion đã có trong RecipientResolver và mock; cần regression thay vì thêm luật trùng. Native tests/service/controller/PostgreSQL+OpenAPI và Node mocks/API/render, lint/build; không e2e theo chỉ đạo.

## Tasks và converge
- [x] T1 Query/filter, canDelete, khóa/xóa và afterCommit cleanup (AC1–4).
- [x] T2 Controller/API/OpenAPI, phân quyền và DTO (AC1–4).
- [x] T3 FE bộ lọc, xóa một/tất cả có xác nhận, mock+API, giữ trang/lỗi (AC1–4).
- [x] T4 Nhắc điểm danh và test biên giờ/dedupe (AC6).
- [x] T5 Test không tự nhận preview/gửi/hẹn/lặp, kiểm hồi quy (AC5).
- [x] T6 Native checks, đối chiếu code/artifacts và ghi giới hạn; append gap nếu có.

## Converge cuối và kiểm chứng
T1–T6 hoàn tất. Đối chiếu AC1–6 với code/query/controller/mock/UI/test: không còn phần cần triển khai trong phạm vi đã chốt. Không đổi migration hoặc App.
BE: 47 ca trong 7 file service/controller/resolver/schedule/reminder/PostgreSQL/OpenAPI đã đạt qua các lượt targeted, 0 fail/error/skip ở kết quả cuối. PostgreSQL dùng Testcontainers dữ liệu giả: bulk 25 dòng/2 trang, scope campus, trạng thái, từ khóa/ký tự LIKE, cascade attachment và rollback không cleanup storage. Lượt đầu fixture trùng tên campus (uk_campuses_name); sửa tên giả unique rồi chạy lại đúng file integration 4/4 đạt. OpenAPI snapshot 4/4 đạt và API_INDEX sinh lại 372 endpoint.
FE: suite notifications 26/26 đạt; sau thêm guard mock HT/PHT và test teacher, chạy lại file mock 6/6 đạt, 20 ca còn lại không đổi. Lint, format, build đạt. Không chạy e2e hoặc kiểm browser/thiết bị, không xóa dữ liệu thật. Test giáo dục/auth/menu độc lập đã đạt trước không chạy lại vì không thay những luồng đó. Không commit/push.

## Delta tiếp 17/11/2026 — cấu hình thời gian và checkbox (Tiền chốt)
Tiền yêu cầu gom mặc định thời gian/cron về application.yml, comment chi tiết, bind bằng NotificationTimingProperties; giờ khóa/giờ gửi đã lưu vẫn ở DB. Giữ các key push cũ, mặc định/role/flow không đổi. Chu kỳ điểm danh dùng một giá trị cho cron và dedupe; cấu hình sai fail-fast. Deadline học liệu/giới hạn tìm ngày nội bộ không phải lịch thông báo nên không đổi.
Xóa đổi UI từ nút Xóa tất cả sang tick từng dòng hoặc tick header chọn toàn bộ CANCELLED khớp filter mọi trang (user chốt qua câu hỏi). Cho bỏ tick từng tin sau chọn tất cả; giữ lựa chọn qua phân trang, reset khi đổi q/status. BE GET thêm cancelledCount theo filter/scope; POST /announcements/delete-selection nhận {allMatching,ids,excludedIds,q,status}, trả số đã xóa. allMatching=false: tất cả ids phải tồn tại, trong quyền và CANCELLED, sai một dòng thì không xóa dòng nào; allMatching=true: đúng filter CANCELLED trừ excludedIds, scope ở BE. Endpoint xóa cũ giữ tương thích; App không dùng endpoint quản trị này.
AC7: giờ/duration/cron mặc định có một nguồn YAML, override có hiệu lực, interval đổi cả cron/dedupe; cấu hình sai bị từ chối lúc khởi động. AC8: chọn/bỏ chọn qua nhiều trang đúng số, chỉ tin canDelete được tick, xác nhận số lượng; bulk không chạm tin bỏ tick/ngoài scope/filter/status. AC9: request ids hỗn hợp SENT/ngoài scope/mất id không xóa thành công một phần.
- [x] T7 Timing properties/YAML/comments, đổi các worker/service và test caller (AC7).
- [x] T8 Request chọn dòng, count mọi trang, controller/mock/API (AC8–9).
- [x] T9 UI checkbox/selection state/confirm, bỏ nút Xóa tất cả (AC8).
- [x] T10 Test config/non-default schedule/retry/selection/DB rollback, OpenAPI, lint/build và converge.

### Kết quả delta timing/selection
Đối chiếu AC7–9: không còn gap triển khai. BE 64 test trong 9 file đạt, gồm custom timing, validation, push retry, selection atomic, scope/loại trừ và PostgreSQL/OpenAPI. Sau chặn backoff nhỏ xuống dưới 1ms, rerun đúng PushOutboxWorkerTest/PushDeliveryServiceImplTest: 18/18, các ca khác giữ kết quả đạt. FE 29/29 notifications đạt; lint/format/build đạt, BE Spotless đạt. Không e2e/browser hoặc DB thật, không commit/push. Test sai generic Map ở fixture cấu hình được sửa trước khi chạy suite đạt; không sửa điều kiện test để che lỗi.

## Delta tiếp — trang lịch soạn trực tiếp, chọn bật/tắt và toast (Tiền 17/11/2026)
Nguồn: user yêu cầu làm task timing/checkbox trước rồi làm task này; task trước đã hoàn tất. Tạo/sửa lịch dùng trang riêng, dùng lại form soạn nội dung/người nhận/tệp và thêm cấu hình tuần/tháng, không tạo bằng modal. Mẫu là điền nhanh giống form soạn thông báo hiện có; snapshot nội dung riêng, sửa mẫu không đổi nội dung lịch. JSON template-only cũ vẫn được nhận; migration backfill nội dung lịch cũ, hỗ trợ fallback fixture cũ. Tệp lịch có parent riêng và mỗi lần gửi gắn bản metadata cho tin đã gửi; không xóa file mà tin cũ còn dùng.
AC10: tạo lịch không mẫu với tiêu đề/body/target/tệp, preview và thời gian hợp lệ; GET/sửa ở route riêng, HT-only. AC11: chọn nhiều/tất cả lịch (list hiện không phân trang) bật/tắt nguyên tử, không thay nội dung/tệp; bật lại tính lần kế tiếp, không gửi bù. AC12: success dùng toast chung 3.5s, lỗi giữ cụ thể; bỏ helper thừa; không modal tạo/sửa lịch. AC13: xóa/sửa lịch không làm hỏng tệp thông báo đã gửi; loại/tổng/scope tải tệp và rollback giữ đúng.
Data: V144 thêm title/body optional snapshot, template nullable; announcement_attachments có đúng một parent announcement hoặc schedule, CASCADE từng parent. Giữ FK template/provenance và guard xóa mẫu như cũ. API GET schedule/{id}, POST/PUT hỗ trợ multipart data/files và JSON cũ; POST activation {ids,active}; response có title/body/attachments. Kế hoạch: shared file lifecycle → model/service/controller → form riêng/route/list checkbox/toast → native tests/OpenAPI/lint/build/converge. Không migration sửa lại, không DB thật/App/dependency/e2e, không commit/push.
- [x] T11 Migration/model/content/tệp/lifecycle và worker (AC10/13).
- [x] T12 API detail/multipart/activation và kiểm scope/lock (AC10/11).
- [x] T13 Form tái sử dụng, route riêng/list checkbox/toast, bỏ helper (AC10–12).
- [ ] T14 Test dữ liệu cũ/mới/file tham chiếu/quyền/lịch, OpenAPI, lint/build và converge.

Bổ sung Tiền 17/11/2026: trang lịch tick như trang thông báo để xóa đã chọn (POST /announcement-schedules/delete-selection {ids}, nguyên tử, dọn tệp sau commit khi không còn tham chiếu); công tắc Bật lịch gửi mặc định bật, có thể tắt. T14 còn mở: chưa e2e/browser.
Bổ sung tiếp Tiền 17/11/2026: tick được mọi lịch để bật/tắt hàng loạt, chỉ lịch đã tắt mới xóa được (ApiCode 1215 ANNOUNCEMENT_SCHEDULE_ACTIVE, nguyên tử); mọi bảng module thông báo có cột Thao tác (Xem chi tiết, Sửa theo quyền, menu khác); bấm dòng không mở chi tiết để tránh trùng tick; thêm trang chi tiết lịch /announcement-schedules/:id.
