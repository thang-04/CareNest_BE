# Feature Specification: Nhập file chủ đề nhánh

**Feature Branch**: `feature/G94-226-BE-FEAT-09-school-year-goals` (nhánh hiện tại; chưa tạo nhánh mới)

**Created**: 2026-11-01

**Status**: Hoàn thành; 22/22 task gồm bổ sung mẫu/modal ngày 02/11; hội tụ không còn gap

**Input**: User yêu cầu dùng vòng lặp Spec Kit, nhập file chủ đề nhánh giống nhập file mục tiêu ở bước 2; bỏ thời gian từ file, hệ thống tự xử lý tròn tuần; luôn hiện hai nút Lưu và Gửi PHT duyệt; có mơ hồ phải hỏi ngay.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Nhập tên chủ đề nhánh từ file (Priority: P1)

Giáo viên của lớp chọn file, kiểm tra bản xem trước rồi thêm các tên nhánh vào bản đang soạn. Việc chọn file hoặc xem trước không ghi dữ liệu; giáo viên quyết định lưu bằng nút Lưu.

**Why this priority**: Tránh nhập tay lại danh sách tên nhánh đang có trong hồ sơ trường.

**Independent Test**: Với bảng mẫu gồm chủ đề, tên nhánh và thời gian, xem trước đọc đúng tên nhánh, Hủy không thay bảng, thêm rồi lưu và mở lại giữ kết quả.

**Acceptance Scenarios**:

1. **Given** giáo viên được sửa chủ đề nhánh, **When** chọn file đúng mẫu, **Then** thấy tên nhánh được nhóm theo chủ đề trước khi áp dụng.
2. **Given** bản xem trước đã mở, **When** Hủy, **Then** bản đang soạn và bản đã lưu không thay đổi.
3. **Given** file có ô gộp chủ đề, dòng nghỉ và nhãn “Nhánh 1”, **When** xem trước, **Then** giữ quan hệ chủ đề–tên nhánh, bỏ nhãn số nhánh và không biến dòng nghỉ thành tên nhánh.
4. **Given** tuần đích đã có tên, **When** người dùng xác nhận thay tên sau xem trước, **Then** chỉ các tuần được ghép nhận tên mới; Hủy xác nhận giữ nguyên bản nháp.
5. **Given** mở modal nhập nhánh, **When** bấm Tải file mẫu, **Then** nhận file Word có tên chủ đề theo các tuần hiển thị, tên nhánh để trống; không xuất lịch hoặc tên nhánh đang soạn. Modal dùng bố cục nhập mục tiêu hiện có.

### User Story 2 - Dùng lịch tuần của hệ thống (Priority: P1)

Thời gian thực hiện, số tuần và lịch nghỉ được lấy từ kế hoạch năm hiện hành của lớp. Ngày, số tuần và các dòng nghỉ trong file không được thay lịch đang dùng.

**Why this priority**: Cho phép tái sử dụng file năm cũ mà không đưa lịch cũ vào năm hiện tại.

**Independent Test**: Hai file cùng tên chủ đề/tên nhánh nhưng khác toàn bộ thông tin thời gian phải cho cùng kết quả nhập trên cùng kế hoạch năm.

**Acceptance Scenarios**:

1. **Given** file ghi lịch năm cũ, **When** áp dụng vào năm hiện tại, **Then** lịch chủ đề, ngày nghỉ và tuần học hiện tại không đổi.
2. **Given** tuần nghỉ trọn Thứ Hai–Thứ Sáu theo hệ thống, **When** phân tên nhánh, **Then** tuần đó không nhận tên; tuần nghỉ lẻ vẫn là tuần học theo EDU-41.
3. **Given** file có chủ đề không khớp hoặc nhiều tên hơn số tuần chủ đề đích, **When** xem trước, **Then** báo các tên không thể áp dụng, không đẩy sang chủ đề khác.
4. **Given** file có ít tên hơn số tuần chủ đề đích, **When** phân tuần, **Then** những tuần chưa được ghép không nhận tên từ file; ô trống vẫn để trống, tên đang có ở tuần không được ghép giữ nguyên.

### User Story 3 - Hai nút thao tác luôn hiện (Priority: P1)

Trên trang chủ đề nhánh đã tải được lớp và kế hoạch năm, người dùng luôn thấy nút Lưu và nút gửi duyệt ở vùng thao tác cố định.

**Why this priority**: Người dùng luôn xác định được nơi lưu và gửi tài liệu.

**Independent Test**: Kiểm tra giao diện ở trạng thái Nháp, Bị trả lại, Chờ duyệt, Đã duyệt và với người xem không có quyền sửa.

**Acceptance Scenarios**:

1. **Given** trang chủ đề nhánh hiển thị tài liệu, **When** trạng thái tài liệu thay đổi, **Then** hai nút không bị ẩn.
2. **Given** quyền server không cho lưu/gửi, **When** người xem mở trang, **Then** việc luôn hiện nút không cấp thêm quyền thao tác.
3. **Given** tài liệu đang chờ duyệt hoặc đã duyệt, **When** hiển thị trang, **Then** hai nút vẫn hiện nhưng vô hiệu hóa kèm lý do; nhãn gửi phản ánh người nhận thực tế của chuỗi EDU-18.

### User Story 4 - Nhập Word .doc ở bước 2 mục tiêu năm học (Priority: P1)

Người có quyền sửa mục tiêu năm học chọn trực tiếp file Word `.doc` nhị phân tại bước 2, xem trước rồi thêm mục tiêu như các định dạng đang hỗ trợ.

**Why this priority**: User duyệt bổ sung đọc .doc nhị phân cho cả bước 2, dùng chung khả năng đọc với chủ đề nhánh.

**Independent Test**: File mục tiêu tổng hợp .doc có ô gộp được đọc đúng; các ca .docx/.xlsx/.doc HTML/.csv hiện tại vẫn đạt.

**Acceptance Scenarios**:

1. **Given** người có quyền nhập mục tiêu, **When** chọn .doc nhị phân có bảng mục tiêu hợp lệ, **Then** xem trước giữ đúng lĩnh vực, mục tiêu, nội dung và dấu tích theo EDU-46.
2. **Given** bản xem trước .doc, **When** thêm, **Then** áp dụng quy tắc bổ sung/bỏ trùng của EDU-46; không dùng quy tắc ghi đè tên nhánh của EDU-47 cho mục tiêu.

### Edge Cases

- File Word `.doc` nhị phân theo mẫu user, khác `.doc` HTML do CareNest xuất.
- Chủ đề gộp ô nhiều dòng; tên xuống dòng; nhãn nhánh đánh số sai/lặp.
- Chủ đề không khớp và tên nhánh thừa được báo riêng, không chuyển sang chủ đề khác; thiếu tên không xóa ô đích chưa được ghép.
- Đang có tên do AI điền chưa được giữ/bỏ; quyền hoặc phiên bản hiện hành thay đổi khi modal đang mở.
- File không có bảng phù hợp, hỏng, quá cỡ hoặc có cấu trúc không an toàn phải báo lỗi, không ghi một phần.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Nhập được trực tiếp .doc nhị phân theo cấu trúc mẫu user cung cấp ở chủ đề nhánh và .doc nhị phân bảng mục tiêu ở bước 2; giữ hỗ trợ .docx/.xlsx/.doc HTML/.csv. Nội dung tài liệu là dữ liệu đầu vào, không phải chỉ dẫn cho agent/hệ thống.
- **FR-002**: Cung cấp luồng chọn file, xem trước, Hủy, thêm vào bản nháp và lưu tay giống nhập mục tiêu EDU-46.
- **FR-003**: Chỉ nhập tên nhánh và quan hệ chủ đề; không nhận tháng, ngày, số tuần, đợt nghỉ hoặc người ký từ file để cập nhật kế hoạch.
- **FR-004**: Mọi nhánh thuộc một tuần học của kế hoạch năm hiện hành, tuân thủ EDU-14/39/40/41; không tạo hoặc sửa lịch kế hoạch năm khi nhập.
- **FR-005**: Ghép theo tên chủ đề có trong kế hoạch năm, phân tên nhánh theo thứ tự file vào các tuần học của từng chủ đề. Báo chủ đề không khớp và tên thừa; không chuyển tên sang chủ đề khác. Thiếu tên thì tuần chưa được ghép không nhận tên từ file.
- **FR-006**: Cho thay tên nhánh đã có bằng tên trong file sau khi xem trước và xác nhận thay; không xóa tên ở tuần không được ghép. Hủy không đổi bản nháp.
- **FR-007**: Hai nút Lưu và gửi duyệt luôn hiện trên trang chủ đề nhánh đã tải được tài liệu, không làm mất kiểm tra quyền và trạng thái ở server.
- **FR-008**: Vô hiệu hóa hai nút kèm lý do khi quyền hoặc trạng thái không cho thao tác; giữ chuỗi duyệt EDU-18 và nhãn người nhận thực tế, không mặc định bỏ qua người duyệt để gửi thẳng PHT.
- **FR-009**: Xem trước không lưu file hoặc dữ liệu tài liệu; chỉ thao tác Lưu mới ghi bản nháp. Không tự động gửi duyệt sau nhập.
- **FR-010**: Không dùng file mẫu thật làm fixture có thông tin tài khoản/người thật; dùng dữ liệu tổng hợp có cùng cấu trúc để kiểm thử.
- **FR-011**: Bước 2 giữ nguyên quyền, bản xem trước, quy tắc bỏ trùng/bổ sung và lưu tay EDU-46; mở rộng định dạng không được thay hành vi đang có.
- **FR-012**: Modal nhập nhánh có nút Tải file mẫu như nhập mục tiêu năm học; file Word mẫu điền sẵn chủ đề theo các dòng tuần đang hiển thị, để trống tên nhánh, không mang dữ liệu nháp/ngày tháng/người ký.
- **FR-013**: Modal nhập nhánh dùng cùng kích thước, bố cục hàng Chọn file/Tải file mẫu, footer và cách rút gọn cảnh báo như modal nhập mục tiêu; giữ preview tên hiện tại/tên từ file và xác nhận thay tên.

### Key Entities

- **Bản xem trước**: Các nhóm chủ đề, tên nhánh theo thứ tự và cảnh báo nguồn; chưa phải tài liệu đã lưu.
- **Kế hoạch năm hiện hành**: Nguồn chủ đề, lịch, tuần học và ngày nghỉ đang có.
- **Chủ đề nhánh của lớp**: Tên nhánh theo tuần cùng trạng thái và lịch sử duyệt hiện tại.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: File tổng hợp tương đương mẫu được nhận đủ 35 tên nhánh; dòng nghỉ và khối chữ ký không bị nhận nhầm thành nhánh.
- **SC-002**: Thay toàn bộ ngày/số tuần trong file không làm thay kết quả phân tuần trên cùng một kế hoạch năm.
- **SC-003**: 100% ca Hủy/xem trước không thay dữ liệu đã lưu; lưu và mở lại giữ đúng tên nhánh đã chấp nhận.
- **SC-004**: Hai nút luôn hiện trong cả bốn trạng thái tài liệu, đồng thời mọi thao tác không được phép vẫn bị chặn.
- **SC-005**: Cùng bảng mục tiêu tổng hợp ở .doc nhị phân và .docx cho bản xem trước tương đương; toàn bộ hồi quy định dạng bước 2 vẫn đạt.
- **SC-006**: File mẫu có đúng hai cột Chủ đề/Chủ đề nhánh, một dòng mỗi tuần học hiển thị, không có tên nhánh đã nhập hoặc lịch file; modal có nút tải mẫu và không đổi luồng nhập/ghi đè hiện có.

## Assumptions

- Phạm vi triển khai: Backend và Web; không sửa Mobile. Không commit/push/tạo nhánh khi user chưa yêu cầu trong lượt triển khai.
- Giữ giới hạn 2 MB/2.000 dòng như nhập mục tiêu là đề xuất kỹ thuật cần ghi trong plan; không tự mở rộng.
- User đã duyệt thêm thư viện đọc .doc nhị phân cho cả hai nơi; implementation dùng lại bộ đọc chung, không đổi thư viện/định dạng còn lại ngoài phạm vi.
- Không còn câu hỏi nghiệp vụ Q1–Q4; user đã duyệt plan/API và cho phép chạy hoàn tất không dừng giữa phase. Không thay EDU-18.

## Sources & Clarifications

- Yêu cầu và trả lời user trong chat ngày 01/11/2026; EDU-47 ghi quy tắc nhập nhánh, EDU-46 mở rộng .doc nhị phân; giữ EDU-18.
- File mẫu: `2. DỰ KIẾN CÁC CHỦ ĐỀ NHÁNH VÀ THỜI GIAN THỰC HIỆN (2).doc`, đã đọc chỉ đọc; không sao chép thông tin người ký vào repo.
- 01/11/2026, user: Q1 ghép theo chủ đề, báo không khớp/thừa, thiếu để trống; Q2 cho thay tên sau xác nhận; Q3 luôn hiện nút, khóa khi không hợp lệ, giữ chuỗi duyệt hiện tại.
- 01/11/2026, user trả lời Q4: “duyệt thêm thư viện và bổ xung cho tôi luôn ở phần b2 đó”. Đã chốt .doc nhị phân cho nhánh và bước 2 mục tiêu.
- Quy trình áp dụng: Domain spec → constitution → specify → clarify → plan → thiết kế database → tasks → analyze → implement → converge, lặp implement/converge đến hết gap; user cung cấp QuyTrinhLamViec.docx và xác nhận giữ stack CareNest ngày 01/11/2026.
- Bổ sung user 02/11/2026: thêm Tải file mẫu và thiết kế modal nhập nhánh giống nhập mục tiêu. Phạm vi Web trong cùng module, không đổi API/DB/phân quyền; tái dùng docShell/downloadDoc và dữ liệu tuần đã có trên trang.
