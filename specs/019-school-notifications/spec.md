# Feature Specification: Thông báo của trường — BGH gửi, quy tắc tự động, đẩy lên điện thoại

**Feature Branch**: `feature/G94-288-BE-FEAT-55-school-notifications`

**Created**: 2026-11-14

**Status**: Draft

**Input**: Task Jira G94-288 (BE-FEAT-55, subtask G94-289 / G94-290 / G94-291) + plan đính kèm `2026-11-08-school-notifications.md`; Tiền chốt 14/11/2026: tạm chưa dùng message broker, làm lần lượt từng subtask, chọn cách làm được dùng phổ biến và hiệu quả nhất.

## Clarifications

### Session 2026-11-15

- Q: Các loại thông báo có sẵn (~14 loại: kế hoạch giáo dục, tiếp xúc lao, đổi số điện thoại, trẻ thôi học, mở đợt khám…) có vào danh sách bật/tắt, sửa câu chữ không? → A: Danh sách hiện mọi loại; các loại có sẵn ngoài 6 loại của spec là "bắt buộc": chỉ xem, không tắt, không sửa câu chữ.
- Q: Thông báo hẹn giờ có được sửa trước giờ gửi không? → A: Sửa được mọi thứ (tiêu đề, nội dung, tệp, người nhận, giờ gửi) khi còn trạng thái hẹn; từ "đang gửi" trở đi bị từ chối.
- Q: Lịch lặp tháng chọn ngày 29–31 mà tháng không có ngày đó? → A: Lùi về ngày cuối tháng đó; nếu ngày đó là ngày nghỉ thì bỏ lần đó (SCH-05).
- Q: Vai trò BGH với phạm vi lớp/khối/điểm trường thì ai nhận? → A: HT + PHT phụ trách điểm trường chứa phạm vi đó; người gửi không nhận thông báo của chính mình.
- Q: Banner PAR-03 (cân đo, đánh giá trẻ, đánh giá chủ đề) có che như sức khỏe không? → A: Có; chỉ câu chung, không số đo/nhận xét, xem đầy đủ trong App.

## Bối cảnh nghiệp vụ

- PAR-05 (CONFIRMED): thông báo của trường chỉ BGH gửi; gửi qua thông báo đẩy và trong App; không theo dõi ai đã đọc.
- PAR-06 (CONFIRMED): một tài khoản phụ huynh nhận cho mọi con của mình.
- AUTH-02: PHT chỉ làm trong điểm trường được phân công. AUTH-22: mỗi cơ sở một tài khoản bếp dùng chung.
- SCH-05: ngày nghỉ/lễ do HT khai báo theo năm học.
- Hiện hệ thống đã tạo thông báo trong App cho khoảng 20 sự kiện (trẻ đã được đón PCK-04, xác nhận lại suất ăn NUT-03, kết quả sức khỏe PAR-03/HLT-11, tiếp xúc lao, kế hoạch giáo dục…), nhưng việc đẩy lên điện thoại là cổng giả (chỉ ghi log) và chạy ngay trong lúc xử lý thao tác của người dùng.
- Quyết định 1–9 trong plan 08/11 (người nhận theo phạm vi × vai trò, HT/PHT gửi, quy tắc và lịch lặp chỉ HT, không bước duyệt, đính kèm 5 file ≤ 10 MB JPG/PNG/PDF, lịch lặp trúng ngày nghỉ thì bỏ lần đó, PAR-03 nối cân đo / đánh giá trẻ / đánh giá chủ đề) là quyết định của user ngày 08/11/2026; sẽ ghi thành rule PAR-07..PAR-10 trong `BUSINESS_RULES.md` trước khi làm User Story 3.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Nhận thông báo đẩy trên điện thoại cho mọi thông báo (Priority: P1) — G94-289

Phụ huynh và nhân sự cài App Android nhận banner trên màn hình điện thoại (kể cả khi App đang tắt hoặc máy khóa) mỗi khi hệ thống tạo một thông báo cho họ; bấm vào banner mở đúng thông báo đó. Người thao tác (GV trả trẻ, PHT xác nhận suất ăn…) không phải chờ việc đẩy xong mới thấy kết quả. Dịch vụ đẩy lỗi thì thông báo vẫn nằm trong danh sách và được đẩy lại khi dịch vụ phục hồi.

**Why this priority**: Là nền cho mọi thông báo hiện có và cho thông báo của trường (US2, US3). Không có nó, phụ huynh chỉ thấy tin khi tự mở App.

**Independent Test**: GV xác nhận trả trẻ ⇒ phụ huynh (có App Android đã đăng ký) nhận banner; tắt dịch vụ đẩy rồi bật lại ⇒ banner tới sau khi bật, không trùng.

**Acceptance Scenarios**:

1. **Given** phụ huynh có App Android đã đăng ký thiết bị, **When** GV xác nhận trẻ đã được đón, **Then** phụ huynh nhận một banner, và thao tác của GV hoàn tất mà không chờ việc đẩy.
2. **Given** dịch vụ đẩy đang lỗi tạm thời, **When** hệ thống tạo thông báo, **Then** thông báo vẫn có trong danh sách của người nhận; banner được đẩy lại tự động khi dịch vụ phục hồi.
3. **Given** dịch vụ đẩy lỗi liên tục quá 60 phút kể từ lúc tạo thông báo, **When** hết thời hạn, **Then** hệ thống ngừng đẩy thông báo đó; thông báo vẫn còn trong danh sách.
4. **Given** máy chủ dừng đột ngột giữa lúc đang đẩy một loạt thông báo, **When** máy chủ chạy lại, **Then** các thông báo chưa đẩy được đẩy tiếp; thông báo đã đẩy xong không bị đẩy lại.
5. **Given** thiết bị đã gỡ App (dịch vụ đẩy báo thiết bị không còn hợp lệ), **When** đẩy tới thiết bị đó, **Then** thiết bị bị xóa khỏi danh sách nhận, không thử lại.
6. **Given** thông báo về sức khỏe của trẻ hoặc kết quả cân đo/đánh giá (PAR-03), **When** đẩy lên điện thoại, **Then** banner chỉ ghi câu chung ("Có thông tin sức khỏe mới"), nội dung đầy đủ chỉ xem trong App.
7. **Given** người dùng bấm vào banner, **When** App mở, **Then** App lấy được đúng thông báo đó của chính người dùng; thông báo của người khác trả "không tìm thấy".
8. **Given** thông báo đã tồn tại trước khi tính năng này được triển khai, **When** triển khai, **Then** không thông báo cũ nào bị đẩy lại.

---

### User Story 2 - BGH soạn và gửi thông báo của trường (Priority: P2) — G94-290

HT hoặc PHT soạn một thông báo (tiêu đề, nội dung, tối đa 5 tệp đính kèm), chọn người nhận theo phạm vi (toàn trường / điểm trường / khối / lớp) × vai trò (phụ huynh, giáo viên, bếp, BGH), xem trước số người sẽ nhận, rồi gửi ngay hoặc hẹn giờ. Có thể lưu và dùng lại mẫu soạn sẵn. Người nhận thấy thông báo trong chuông (Web/App) và nhận banner (US1).

**Why this priority**: Đây là mục tiêu chính của PAR-05 — kênh chính thức để trường báo tin tới phụ huynh và nhân sự.

**Independent Test**: HT gửi cho phụ huynh lớp A ⇒ mọi phụ huynh của trẻ đang học lớp A có đúng một thông báo; PHT điểm trường 1 không gửi được cho lớp của điểm trường 2.

**Acceptance Scenarios**:

1. **Given** HT chọn "phụ huynh lớp A", **When** gửi ngay, **Then** mọi phụ huynh của trẻ đang học lớp A tại thời điểm gửi nhận một thông báo; thao tác trả kết quả mà không chờ việc đẩy.
2. **Given** phụ huynh có 2 con cùng trong phạm vi, **When** gửi, **Then** phụ huynh nhận đúng 1 thông báo (PAR-06).
3. **Given** PHT phụ trách điểm trường 1, **When** gửi cho lớp thuộc điểm trường 2 hoặc toàn trường, **Then** bị từ chối (AUTH-02).
4. **Given** đã gửi, **When** BGH xem chi tiết thông báo, **Then** thấy số người nhận, không thấy ai đã đọc (PAR-05).
5. **Given** thông báo hẹn giờ, **When** hủy trước giờ gửi, **Then** không ai nhận; sau khi đã gửi thì không hủy được.
9. **Given** thông báo hẹn giờ chưa tới giờ, **When** người gửi sửa nội dung, người nhận hoặc giờ gửi, **Then** lần gửi dùng bản đã sửa; **When** sửa lúc thông báo đã sang "đang gửi", **Then** bị từ chối.
6. **Given** BGH chọn phạm vi và vai trò, **When** xem trước, **Then** thấy số người sẽ nhận theo từng vai trò trước khi gửi.
7. **Given** người không nằm trong danh sách nhận, **When** mở tệp đính kèm của thông báo, **Then** bị trả "không tìm thấy".
8. **Given** tệp đính kèm thứ 6, hoặc tệp > 10 MB, hoặc không phải JPG/PNG/PDF, **When** đính kèm, **Then** bị từ chối với thông báo lỗi rõ.

---

### User Story 3 - Quy tắc tự động theo sự kiện và lịch gửi lặp (Priority: P3) — G94-291

HT xem danh sách các loại thông báo tự động (theo sự kiện nghiệp vụ), bật/tắt những loại được phép tắt và sửa câu chữ mẫu bằng các biến được phép; một số loại bắt buộc (ví dụ trẻ đã được đón, sức khỏe trong ngày, xác nhận lại suất ăn) không tắt được. HT tạo lịch gửi lặp theo tuần/tháng cho một thông báo mẫu; lần gửi trúng ngày nghỉ thì bỏ qua lần đó. Nối thêm sự kiện: sức khỏe trong ngày (HLT-14), công bố thực đơn tuần (NUT-08), đơn nghỉ của phụ huynh báo GV lớp (ATT-05), công bố kết quả cân đo / đánh giá trẻ / đánh giá chủ đề (PAR-03).

**Why this priority**: Giảm việc soạn tay lặp lại và cho trường kiểm soát lượng thông báo; phụ thuộc US1 (đẩy) và US2 (mẫu, người nhận), và cần nhóm duyệt rule PAR-07..PAR-10.

**Independent Test**: HT tắt loại "công bố thực đơn tuần" ⇒ PHT công bố thực đơn mà phụ huynh không nhận thông báo; HT cố tắt "trẻ đã được đón" ⇒ bị từ chối.

**Acceptance Scenarios**:

1. **Given** loại thông báo bắt buộc (PCK-04, HLT-14, NUT-03), **When** HT tắt, **Then** bị từ chối; HT chỉ sửa được câu chữ mẫu.
2. **Given** HT đã tắt một loại được phép tắt (NUT-08, PAR-03, ATT-05), **When** sự kiện xảy ra, **Then** không tạo thông báo.
3. **Given** lịch lặp trúng ngày nghỉ (SCH-05), **When** tới giờ, **Then** bỏ lần đó, lịch chuyển sang lần kế tiếp.
4. **Given** mẫu câu chữ của sự kiện sức khỏe, **When** HT sửa, **Then** chỉ dùng được các biến được phép; banner đẩy lên điện thoại không chứa thông tin sức khỏe.
5. **Given** PHT, **When** mở quản lý quy tắc hoặc lịch lặp, **Then** bị từ chối (chỉ HT).

---

### Edge Cases

- Người nhận chưa có thiết bị đăng ký ⇒ vẫn có thông báo trong danh sách, không đẩy, không lỗi.
- Một người có nhiều thiết bị ⇒ mỗi thiết bị nhận banner; một thiết bị hỏng không chặn thiết bị khác.
- iOS chưa có khóa Apple ⇒ không có banner trên iOS; thiết bị iOS giữ nguyên để dùng khi có khóa; thông báo vẫn xem được trong App.
- Dịch vụ đẩy báo quá tải (yêu cầu chờ) ⇒ đẩy lại sau đúng thời gian được yêu cầu.
- Chưa cấu hình dịch vụ đẩy (máy dev, test) ⇒ hệ thống chạy bình thường, chỉ ghi log việc đẩy.
- Hai máy chủ cùng chạy ⇒ mỗi thông báo chỉ được một máy đẩy.
- Gửi cho "khối" mà khối không có lớp nào đang hoạt động ⇒ xem trước báo 0 người nhận, không cho gửi.
- Trẻ chuyển lớp sau khi hẹn giờ ⇒ danh sách người nhận chốt tại thời điểm gửi thực tế.
- Tài khoản bếp của điểm trường ⇒ nhận 1 lần dù chọn nhiều lớp cùng điểm trường (AUTH-22).
- Thiết bị không cập nhật quá 270 ngày ⇒ tự xóa khỏi danh sách nhận.

## Requirements *(mandatory)*

### Functional Requirements

**Đẩy lên điện thoại (US1)**

- **FR-001**: Mọi thông báo được tạo cho một người MUST được lưu trước, rồi mới đẩy lên các thiết bị của người đó; thao tác nghiệp vụ tạo ra thông báo MUST NOT chờ việc đẩy.
- **FR-002**: Hệ thống MUST đẩy lại thông báo khi dịch vụ đẩy lỗi tạm thời, với khoảng chờ tăng dần (bắt đầu 1 phút, gấp đôi mỗi lần, có ngẫu nhiên) và tôn trọng thời gian chờ do dịch vụ đẩy yêu cầu; MUST ngừng đẩy sau 60 phút kể từ khi tạo.
- **FR-003**: Hệ thống MUST NOT gửi lại thiết bị đã ghi nhận gửi thành công chỉ vì thiết bị khác lỗi; thiết bị lỗi tạm thời phải được thử lại riêng trong 60 phút từ lúc tạo tin (PAR-11, Tiền 15/11/2026, clarify spec 902 Q1=A). Ngoại lệ: backend dừng sau Firebase nhận trước lưu kết quả thì được thử lại trong hạn, chấp nhận khả năng banner trùng hiếm gặp riêng cửa sổ này; App bắt buộc chống xử lý trùng theo notificationId ở phần do App quản lý (PAR-12, Q2=A). Nhiều máy chủ không được xử lý đồng thời cùng lượt gửi.
- **FR-004**: Thiết bị bị dịch vụ đẩy báo không còn hợp lệ MUST bị xóa khỏi danh sách nhận; lỗi do thiếu khóa Apple MUST NOT xóa thiết bị.
- **FR-005**: Thiết bị không cập nhật quá 270 ngày MUST bị xóa định kỳ hằng ngày.
- **FR-006**: Banner của thông báo sức khỏe và của thông báo công bố kết quả cân đo / đánh giá trẻ / đánh giá chủ đề (PAR-03) MUST chỉ chứa câu chung, không tên bệnh/triệu chứng/số đo/nhận xét; nội dung đầy đủ chỉ xem trong App.
- **FR-007**: Người dùng MUST xem được một thông báo của chính mình theo mã (để mở từ banner); thông báo của người khác trả "không tìm thấy".
- **FR-008**: Thông báo đã có trước khi triển khai MUST NOT bị đẩy.
- **FR-009**: Khi chưa cấu hình dịch vụ đẩy, hệ thống MUST chạy bình thường và chỉ ghi log việc đẩy (không ghi token, không ghi nội dung có tên trẻ).

**BGH gửi thông báo (US2)**

- **FR-010**: HT và PHT MUST soạn được thông báo gồm tiêu đề, nội dung và tối đa 5 tệp đính kèm (mỗi tệp ≤ 10 MB, JPG/PNG/PDF).
- **FR-011**: Người gửi MUST chọn người nhận theo phạm vi (toàn trường / điểm trường / khối / lớp) × vai trò (phụ huynh, giáo viên, bếp, BGH); hệ thống MUST xem trước số người nhận theo vai trò. Vai trò BGH với phạm vi lớp/khối/điểm trường MUST gồm HT và PHT phụ trách điểm trường chứa phạm vi đó; người gửi MUST NOT nằm trong danh sách nhận.
- **FR-012**: Phụ huynh MUST được xác định qua trẻ đang học trong phạm vi tại thời điểm gửi; mỗi người MUST nhận đúng 1 thông báo dù khớp nhiều lần.
- **FR-013**: PHT MUST chỉ gửi trong điểm trường được phân công; toàn trường chỉ HT.
- **FR-014**: Người gửi MUST gửi ngay hoặc hẹn giờ; MUST sửa được (tiêu đề, nội dung, tệp đính kèm, phạm vi × vai trò, giờ gửi) và hủy được thông báo hẹn giờ khi còn ở trạng thái hẹn; sửa/hủy khi đã sang "đang gửi" MUST bị từ chối; MUST NOT hủy hay sửa sau khi đã gửi.
- **FR-015**: Chi tiết thông báo đã gửi MUST hiện người gửi, thời điểm, phạm vi, số người nhận; MUST NOT hiện ai đã đọc.
- **FR-016**: Tệp đính kèm MUST chỉ tải được bởi người gửi, BGH trong phạm vi và người nằm trong danh sách nhận.
- **FR-017**: HT, PHT MUST tạo/sửa/xóa mẫu soạn sẵn; PHT chỉ thấy mẫu của điểm trường mình và mẫu toàn trường.

**Quy tắc tự động và lịch lặp (US3)**

- **FR-018**: HT MUST bật/tắt được các loại thông báo tự động được phép tắt; loại bắt buộc (PCK-04, HLT-14, NUT-03) MUST NOT tắt được. Danh sách MUST hiện cả các loại thông báo có sẵn khác (kế hoạch giáo dục, tiếp xúc lao, đổi số điện thoại, trẻ thôi học, mở đợt khám…) dưới dạng bắt buộc, chỉ xem: không tắt, không sửa câu chữ.
- **FR-019**: HT MUST sửa được câu chữ mẫu của từng loại trong 6 loại của spec (PCK-04, HLT-14, NUT-03, NUT-08, PAR-03, ATT-05) chỉ bằng các biến được phép của loại đó.
- **FR-020**: Hệ thống MUST tạo thông báo cho: sức khỏe trong ngày (phụ huynh, HLT-14), công bố thực đơn tuần (phụ huynh, NUT-08), đơn nghỉ mới (GV lớp, ATT-05), công bố kết quả cân đo / đánh giá trẻ / đánh giá chủ đề (phụ huynh, PAR-03) — trừ khi loại đó đang tắt.
- **FR-021**: HT MUST tạo lịch gửi lặp theo tuần/tháng từ một mẫu với phạm vi người nhận; lần trúng ngày nghỉ (SCH-05) MUST bị bỏ qua. Lịch tháng chọn ngày 29–31 mà tháng không có ngày đó MUST chạy vào ngày cuối của tháng đó (vẫn bỏ nếu là ngày nghỉ).
- **FR-022**: Chỉ HT MUST quản lý quy tắc tự động và lịch lặp.

### Key Entities

- **Thông báo (của một người nhận)**: đã có; bổ sung trạng thái đẩy (chờ / đã đẩy / ngừng / bỏ qua), số lần thử, thời điểm thử lại.
- **Thiết bị nhận**: đã có; thời điểm cập nhật dùng để dọn thiết bị cũ.
- **Thông báo của trường**: người gửi, tiêu đề, nội dung, phạm vi × vai trò, thời điểm hẹn, trạng thái (hẹn / đang gửi / đã gửi / đã hủy), số người nhận; sinh ra các Thông báo cho từng người.
- **Tệp đính kèm**: thuộc một Thông báo của trường; tối đa 5.
- **Mẫu soạn sẵn**: tiêu đề, nội dung, phạm vi sở hữu (toàn trường / điểm trường).
- **Quy tắc tự động**: loại sự kiện, bật/tắt, bắt buộc hay không, sửa câu chữ được hay không, câu chữ mẫu, danh sách biến được phép.
- **Lịch gửi lặp**: mẫu, phạm vi × vai trò, chu kỳ tuần/tháng, lần chạy kế tiếp.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Thao tác nghiệp vụ tạo thông báo (trả trẻ, công bố, gửi thông báo trường) không chậm thêm khi dịch vụ đẩy chậm hoặc lỗi.
- **SC-002**: Khi dịch vụ đẩy hoạt động, người nhận có App Android nhận banner trong vòng 30 giây kể từ khi thông báo được tạo.
- **SC-003**: 0 thông báo bị mất khỏi danh sách khi dịch vụ đẩy lỗi; 0 lần gửi lại thiết bị đã ghi thành công vì lỗi thiết bị khác. Riêng backend dừng sau Firebase nhận trước lưu kết quả: cho retry trong hạn 60 phút, chấp nhận khả năng banner OS trùng hiếm gặp; phần do App quản lý không xử lý trùng theo notificationId (PAR-11/12, Tiền 15/11/2026, clarify spec 902 Q1/Q2=A). Đây là nghiệp vụ đích cập nhật, chưa chứng nhận code 019 đã đáp ứng.
- **SC-004**: BGH gửi một thông báo cho cả lớp (soạn → xem trước → gửi) trong dưới 2 phút.
- **SC-005**: 100% trường hợp gửi ngoài phạm vi (PHT khác điểm trường) bị chặn.
- **SC-006**: Lần lặp trúng ngày nghỉ không tạo thông báo nào.

## Assumptions

- Chưa dùng message broker (Tiền chốt 14/11/2026): hàng chờ đẩy nằm trong cơ sở dữ liệu; thiết kế giữ được để sau thay bằng broker mà không đổi dữ liệu.
- Dịch vụ đẩy là Firebase Cloud Messaging (quyết định 08/11); Android trước, iOS khi có Apple Developer.
- Ngưỡng thử lại 60 phút và dọn thiết bị 270 ngày theo khuyến nghị của Firebase (thay ngưỡng 24 giờ của plan 08/11).
- Web không nhận thông báo đẩy; chuông trên Web đọc danh sách như hiện tại.
- App đăng ký lại thiết bị mỗi lần mở (task APP riêng); Web/App màn soạn, mẫu, quy tắc, lịch là task FE-FEAT riêng.
- Thông báo của trường không có bước duyệt; sai thì gửi thông báo đính chính.
- Hẹn giờ tối đa 30 ngày tới (mặc định hợp lý, chỉnh được bằng cấu hình).
- SMS và AI ngoài phạm vi.

## Delta Tiền chốt 17/11/2026 — PAR-13, OBS-30

Yêu cầu/14 mục domain, AC, contract, dữ liệu không migration, kế hoạch, tasks và analyze/converge tại [bổ sung dọn thông báo đã hủy](domain-spec.md). Bộ lọc q/status, DELETE một hoặc mọi CANCELLED khớp filter+scope trên mọi trang, canDelete; PAR-07 không tự nhận giữ nguyên. Nhắc điểm danh mỗi 5 phút từ 08:00 đến trước giờ khóa cấu hình, thay một lần trong 30 phút trước khóa.

### Điều chỉnh tiếp — timing tập trung và chọn dòng
Theo domain-spec.md AC7–9/T7–T10: giữ DB hiện có, timing defaults/cron về YAML + properties, GET cancelledCount, POST delete-selection (ids hoặc allMatching/excludedIds/filter), UI checkbox thay nút xóa tất cả. User chốt chọn tất cả mọi trang; không đổi scope và mặc định giờ.
