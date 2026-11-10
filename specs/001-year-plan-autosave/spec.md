# Feature Specification: Tự lưu mục tiêu năm học khi soạn

**Feature Branch**: `feature/G94-226-BE-FEAT-09-school-year-goals` (nhánh hiện tại, không tạo nhánh mới)

**Created**: 2026-10-27

**Status**: Implemented — gate BE/FE và browser mock đã kiểm ngày 27/10/2026; giới hạn kiểm chứng ghi trong tasks.md

**Input**: User description: "code lại phần lưu trên theo kiểu docs"; tiếp nối yêu cầu một người sửa, thoát thì nhả quyền và bảo toàn nội dung khi mất điện/mất mạng.

> **Cập nhật 27/10/2026 (Tiền chốt):** FE không còn tự lưu lên hệ thống (bỏ nhịp 2 giây/15 giây ở US1, FR-002, SC-002); chỉ ghi khi bấm Lưu/Ctrl+S hoặc gửi/ban hành. Bản giữ trên máy vẫn chạy ẩn để khôi phục; phiên sửa, heartbeat, revision, request ID giữ nguyên.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Soạn mà không phải nhớ bấm Lưu (Priority: P1)

Tổ trưởng nhóm tuổi hoặc Phó hiệu trưởng soạn mục tiêu năm học. Nội dung được lưu trong lúc soạn và người dùng biết rõ phần nào đã được lưu trên hệ thống.

**Why this priority**: Tránh mất phần việc chỉ vì quên bấm Lưu.

**Independent Test**: Sửa một bản nháp, đợi thông báo đã lưu, mở lại và đối chiếu toàn bộ nội dung.

**Acceptance Scenarios**:

1. **Given** có quyền sửa và kết nối bình thường, **When** sửa nội dung rồi ngừng nhập 2 giây, **Then** hệ thống bắt đầu lưu và chỉ báo "Đã lưu" sau khi xác nhận nội dung được lưu thành công.
2. **Given** nhập liên tục, **When** đã có thay đổi chưa lưu trong 15 giây, **Then** hệ thống bắt đầu lưu phần đã nhập dù chưa ngừng gõ.
3. **Given** tiếp tục nhập trong lúc đang lưu, **When** lần lưu trước hoàn tất, **Then** phần mới nhập vẫn được nhận diện là chưa lưu và được lưu tiếp; không bị thay bằng nội dung cũ.
4. **Given** có thay đổi, **When** chuyển mục Mục tiêu/Chủ đề trên cùng trang, **Then** nội dung vẫn được giữ và yêu cầu lưu được thực hiện, không hỏi bỏ thay đổi đã tự lưu.
5. **Given** đang tạo mới và chưa chọn năm học/nhóm tuổi, **When** nhập nội dung, **Then** nội dung có thể được giữ tạm trên thiết bị nhưng chưa tạo kế hoạch chính thức hoặc báo đã lưu lên hệ thống.
6. **Given** đã chọn năm học/nhóm tuổi hợp lệ nhưng tiêu đề, mục tiêu hoặc chủ đề còn thiếu, **When** đến lúc tự lưu, **Then** lưu được nội dung đang soạn dở; khi gửi duyệt/ban hành phải chỉ rõ các lỗi cần hoàn thiện và chưa chuyển trạng thái.
7. **Given** dữ liệu đang soạn còn lỗi nghiệp vụ như ngày kết thúc trước ngày bắt đầu, **When** tự lưu, **Then** giữ nguyên giá trị người dùng nhập và chỉ ra lỗi; không tự sửa hay bỏ phần đó, không cho gửi duyệt/ban hành đến khi hợp lệ.

### User Story 2 - Giữ quyền sửa cho đúng một phiên (Priority: P1)

Người đang soạn được giữ quyền sửa trong suốt thời gian còn kết nối, kể cả khi đang đọc và không nhập. Người khác được xem và biết ai đang sửa.

**Why this priority**: Tự lưu không được gây ghi đè giữa hai người hoặc hai trang đang mở.

**Independent Test**: Hai phiên mở cùng kế hoạch; xác nhận chỉ một phiên sửa được, rồi thoát phiên đó và nhận quyền ở phiên còn lại.

**Acceptance Scenarios**:

1. **Given** A đã nhận quyền sửa, **When** B mở cùng kế hoạch, **Then** B chỉ xem, thấy người đang sửa và không thể ghi thay đổi vào kế hoạch.
2. **Given** A vẫn kết nối trên trang soạn, **When** không nhập trong hơn 30 phút, **Then** A vẫn giữ quyền; thời gian từ lần lưu nội dung không làm mất quyền.
3. **Given** mọi nội dung đã lưu, **When** A rời trang bình thường và hệ thống nhận được yêu cầu kết thúc sửa, **Then** B có thể nhận quyền trong vòng 3 giây mà không phải chờ hết hạn khóa.
4. **Given** trang của A ngừng liên lạc đột ngột, **When** 90 giây trôi qua kể từ lần liên lạc cuối đã xác nhận, **Then** quyền sửa cũ không còn chặn B nhận quyền.
5. **Given** B đã nhận quyền sau khi A mất kết nối, **When** A quay lại với thay đổi cũ chưa đồng bộ, **Then** A không thể ghi đè nội dung của B; bản chưa đồng bộ của A vẫn có thể xem để đối chiếu.
6. **Given** cùng tài khoản mở hai tab, **When** cả hai yêu cầu sửa cùng kế hoạch, **Then** chỉ một phiên nhận quyền; đóng một tab không được nhả quyền của phiên còn lại.
7. **Given** bản chờ duyệt đang được một Phó hiệu trưởng sửa, **When** phiên thứ hai của cùng người hoặc Phó hiệu trưởng khác mở bản đó, **Then** phiên thứ hai chỉ xem; bản vẫn ở trạng thái chờ duyệt và không tự trở về Nháp khi thoát sửa.

### User Story 3 - Khôi phục khi gặp sự cố (Priority: P1)

Người soạn có thể khôi phục bản lưu gần nhất khi mất mạng, tải lại trang hoặc máy tắt đột ngột.

**Why this priority**: Đây là yêu cầu bảo toàn công việc khi sự cố, khác với việc đã lưu lên hệ thống.

**Independent Test**: Ngắt mạng sau khi sửa, mở lại trên cùng thiết bị/tài khoản và kiểm tra bản nháp; kết nối lại để thử đồng bộ trong cả trường hợp còn quyền và đã mất quyền.

**Acceptance Scenarios**:

1. **Given** kết nối bị gián đoạn nhưng thiết bị còn hoạt động và lưu tạm được, **When** tiếp tục nhập, **Then** hệ thống giữ bản nháp trên thiết bị và hiện "Đã lưu tạm trên máy — chờ đồng bộ" sau khi giữ thành công.
2. **Given** bản nháp đã được giữ trên thiết bị trước khi mất điện, **When** mở lại bằng cùng tài khoản trên thiết bị đó, **Then** người dùng khôi phục được bản nháp đó; không hứa khôi phục phần chưa kịp lưu.
3. **Given** kết nối được phục hồi, **When** người dùng còn hoặc nhận lại được quyền sửa và bản trên hệ thống chưa thay đổi, **Then** đồng bộ bản nháp và chỉ báo đã lưu sau khi thành công.
4. **Given** không thể giữ bản nháp trên thiết bị, **When** có thay đổi chưa lưu, **Then** hiện cảnh báo rõ nguy cơ mất nội dung; không báo đã lưu tạm.
5. **Given** đã có nội dung tự lưu, **When** bấm Gửi duyệt hoặc Ban hành khi đang mất mạng/chưa lưu xong, **Then** không chuyển trạng thái; chỉ thực hiện sau khi lưu và kiểm tra thành công toàn bộ nội dung hiện tại.

### Edge Cases

- Kết quả của lần lưu cũ đến sau lần lưu mới; nội dung mới không bị lùi về bản cũ.
- Máy chủ đã lưu nhưng phản hồi bị mất; thử lại không tạo trùng kế hoạch, dòng mục tiêu hay chủ đề.
- Hai người cùng tạo kế hoạch cho một năm học/nhóm tuổi; chỉ một kế hoạch chính thức tồn tại, bản nháp còn lại được giữ để đối chiếu.
- Bản đang sửa được gửi duyệt, trả lại, ban hành hoặc xóa ở nơi khác; yêu cầu lưu cũ không được phục hồi hay đổi ngược trạng thái.
- Tự lưu thất bại do lỗi hệ thống hoặc hết phiên đăng nhập; giữ thay đổi, báo lỗi, không âm thầm ghi dưới tài khoản khác.
- Quyền Tổ trưởng thay đổi trong khi soạn; kiểm tra quyền lại trước khi nhận quyền sửa và lưu.
- Đóng trình duyệt khi chưa lưu xong; có cảnh báo phù hợp và bản phục hồi nếu đã lưu tạm thành công; không coi thao tác đóng là bảo đảm đã gửi dữ liệu.
- Đăng nhập bằng tài khoản khác trên cùng máy không được đọc bản nháp tài khoản trước.
- Lưu trữ trên máy bị xóa, hỏng hoặc không được cho phép; giới hạn phục hồi phải được thông báo.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Tự lưu áp dụng cho trang mục tiêu năm học khi người dùng có quyền sửa; không thay đổi quyền theo vai trò/nhóm tuổi, không mở sửa bản đã ban hành.
- **FR-002**: Bắt đầu lưu sau khi ngừng nhập 2 giây; nếu nhập liên tục, bắt đầu lưu ít nhất mỗi 15 giây khi có thay đổi. Không lưu lặp khi không có thay đổi.
- **FR-003**: Phân biệt rõ đang lưu, đã lưu lên hệ thống, đã lưu tạm trên máy, chưa lưu được và chỉ xem do mất quyền sửa. Chỉ báo đã lưu cho phần nội dung được xác nhận thành công.
- **FR-004**: Việc lưu cũ, thử lại và lưu đồng thời với thao tác nhập không được làm mất nội dung mới hoặc tạo dữ liệu trùng.
- **FR-005**: Giữ quyền sửa theo phiên đang hoạt động, độc lập với tần suất lưu nội dung; người khác chỉ được xem khi quyền còn hiệu lực.
- **FR-006**: Rời trang bình thường kết thúc quyền sửa ngay sau khi hệ thống nhận được yêu cầu; mất liên lạc đột ngột làm quyền hết hiệu lực trong 90 giây tính từ liên lạc cuối đã xác nhận. Yêu cầu đến trễ từ phiên cũ không được nhả quyền của phiên mới.
- **FR-007**: Bản nháp giữ trên thiết bị phải gắn với tài khoản và kế hoạch đang soạn, kể cả trước lần lưu đầu tiên; người dùng khác không được truy cập bản nháp này.
- **FR-008**: Khi thiết bị hỗ trợ lưu tạm, giữ thay đổi trên thiết bị trong vòng 1 giây từ lần nhập gần nhất; chỉ bảo đảm phục hồi nội dung đã được giữ thành công trước sự cố.
- **FR-009**: Khi kết nối trở lại, kiểm tra lại quyền, trạng thái và nội dung hiện có trước khi đồng bộ. Nếu đã có thay đổi từ phiên khác, không tự ghi đè; giữ bản chưa đồng bộ để người dùng đối chiếu và chủ động chép phần cần giữ sau khi có quyền.
- **FR-010**: Tự lưu không tự gửi duyệt, tự ban hành hay đổi người sở hữu. Gửi duyệt/ban hành chỉ chạy sau khi nội dung hiện tại được lưu và đạt điều kiện nghiệp vụ tương ứng; nếu thất bại thì giữ trạng thái cũ.
- **FR-011**: Bấm Lưu/Ctrl+S vẫn có thể yêu cầu lưu ngay; không còn là điều kiện để nội dung được bảo toàn. Rời trang khi chưa bảo toàn được nội dung phải có cảnh báo; phần đã tự lưu không bị bỏ đi khi rời trang.
- **FR-012**: Sau khi có năm học/nhóm tuổi hợp lệ, cho tự lưu nội dung đang soạn dở lên hệ thống, kể cả thiếu tiêu đề, mục tiêu, tên/viết tắt chủ đề hoặc lỗi quan hệ ngày. Hiển thị lỗi để người dùng hoàn thiện; chỉ cho gửi duyệt/ban hành sau khi đạt toàn bộ điều kiện nội dung hiện hành. Không nới kiểm tra quyền, phạm vi truy cập, liên kết dữ liệu hay giới hạn dung lượng/kích thước nhập hiện tại; dữ liệu vượt giới hạn phải báo rõ chưa lưu được, không cắt âm thầm.
- **FR-013**: Mọi bản được phép chỉnh sửa đều chỉ một phiên sửa tại một thời điểm, gồm Nháp/Bị trả lại và Phó hiệu trưởng sửa bản chờ duyệt. Giữ trạng thái Chờ duyệt khi lấy/nhả quyền; việc người khác giữ quyền cũng chặn các thao tác ghi/chuyển trạng thái/xóa từ phiên khác. Nhả quyền không tự gửi duyệt, trả lại hay ban hành.
- **FR-014**: Giữ điều kiện một kế hoạch cho mỗi năm học/nhóm tuổi; không đổi năm học/nhóm tuổi của bản đã tạo và không thay đổi liên kết mục tiêu phục vụ soạn giáo án, đánh giá trẻ.
- **FR-015**: Mỗi lần tự lưu không được tạo một sự kiện duyệt/ban hành mới; giữ ý nghĩa lịch sử gửi, sửa khi chờ duyệt, trả lại và ban hành hiện tại.

### Key Entities *(include if feature involves data)*

- **Kế hoạch năm học**: Nội dung mục tiêu, chủ đề, đợt nghỉ, liên kết nội dung/chủ đề; năm học, nhóm tuổi và trạng thái nghiệp vụ.
- **Phiên chỉnh sửa**: Người sửa, phiên đang giữ quyền, thời điểm hoạt động cuối và thời điểm quyền hết hiệu lực; phân biệt hai tab của cùng người.
- **Bản nháp khôi phục**: Nội dung chưa đồng bộ của một tài khoản/kế hoạch trên thiết bị; bản trên hệ thống tại thời điểm bắt đầu sửa và thời điểm giữ gần nhất.
- **Trạng thái lưu**: Phần thay đổi đang chờ, đang lưu, đã lưu hoặc thất bại; thời điểm xác nhận lưu gần nhất.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Với kết nối ổn định, 95% lượt sửa rồi ngừng nhập được xác nhận đã lưu trong 5 giây; lượt thất bại hiển thị đúng tình trạng chưa lưu.
- **SC-002**: Khi nhập liên tục và các lần lưu hoàn tất bình thường, không có khoảng chờ bắt đầu lưu thay đổi nào vượt 15 giây.
- **SC-003**: Trong toàn bộ tình huống kiểm tra hai người/hai tab, đúng một phiên được sửa tại một thời điểm thuộc phạm vi khóa đã chốt; không có trường hợp ghi đè từ phiên hết quyền.
- **SC-004**: Sau khi nhận yêu cầu thoát bình thường, người khác có thể nhận quyền trong 3 giây; sau mất liên lạc, quyền cũ hết hiệu lực trong 90 giây.
- **SC-005**: Trong các thử nghiệm mất điện/mất mạng, khôi phục đúng toàn bộ phần đã được xác nhận giữ trên thiết bị; không có phần chỉ lưu trên máy bị hiển thị là đã lưu lên hệ thống.
- **SC-006**: Trong toàn bộ thử nghiệm lưu chậm, thử lại, kết nối lại và chuyển trạng thái, không tạo trùng kế hoạch, không lùi nội dung và không làm đổi bản đã ban hành.

## Assumptions

- Phạm vi là mục tiêu năm học trên Web, gồm cả kế hoạch năm do Phó hiệu trưởng tự lập; không phải toàn bộ giáo án tuần/ngày/chủ đề hoặc ứng dụng Mobile. Backend và Web đều cần thay đổi; user đã cho phép sửa CareNest_FE ngày 27/10/2026.
- "Theo kiểu Docs" được hiểu là trải nghiệm tự lưu và phục hồi, vẫn giữ yêu cầu trước đó của user: một người sửa tại một thời điểm; không có cộng tác nhiều người cùng gõ.
- Các mốc 1/2/15/90 giây và mục tiêu 3/5 giây là đề xuất để duyệt đặc tả, chưa phải quy định trường đã chốt hoặc hành vi đang có trong code.
- Chỉ bắt đầu bản chính thức sau khi đã xác định năm học/nhóm tuổi. Nội dung trước đó chỉ là bản nháp trên thiết bị; FR-012 áp dụng cho cả lần lưu đầu tiên và các lần tiếp theo.
- Tiếp tục soạn khi mất mạng chỉ áp dụng cho nội dung đã mở và bản nháp trên thiết bị; gửi duyệt, ban hành, nhận quyền mới cần kết nối.
- Dữ liệu đã tự lưu là thay đổi thật của bản đang soạn; không bổ sung tính năng hoàn tác toàn bộ phiên. Khi có xung đột chỉ hỗ trợ đối chiếu/chủ động chép, không tự trộn nội dung.
- Nguồn quy tắc hiện tại: [BUSINESS_RULES.md](../../docs/business/BUSINESS_RULES.md), EDU-03/10/22; user ngày 27/10/2026 yêu cầu thay lưu tay bằng tự lưu và chọn lưu nội dung dở, khóa mọi phiên được sửa. Đây là thay đổi được yêu cầu cho tính năng mới; quy tắc CONFIRMED cũ còn mô tả hành vi hiện hành, cần cập nhật nhất quán trong kế hoạch triển khai.
- Constitution Spec Kit hiện chỉ là mẫu chưa điền, không phải chính sách mới. [AGENTS.md](../../AGENTS.md) và quy trình CareNest tiếp tục áp dụng; đây là đặc tả, không thay cho kế hoạch làn L đã được duyệt.

## Clarifications

### Session 2026-10-27

- Q: Nội dung còn thiếu/chưa hợp lệ được lưu thế nào? → A: User chọn "Lưu tạm phần đang soạn; kiểm tra đầy đủ khi gửi duyệt/ban hành". Đã đưa vào FR-012 và kịch bản 1.6–1.7.
- Q: Cùng tài khoản mở lại khi phiên cũ (vd. mất điện) còn giữ quyền thì sao? → A: Tiền chốt 27/10: phiên mới nhận quyền ngay, phiên cũ bị từ chối ở lần tín hiệu/lưu kế tiếp (409 "Đã mất quyền sửa…") và giữ bản giữ; người khác vẫn chờ 90 giây. Thay cho kịch bản 2.6 ("chỉ một phiên nhận quyền" vẫn đúng: phiên cũ mất quyền). Kiểm: `EduYearPlanServiceIntegrationTest.sameAccountNewSessionTakesOverAndOldSessionLosesLease`, `otherUserStillWaitsForLeaseExpiry`; Playwright IT-05b, ST-03b.
- Q: Bản chờ duyệt có khóa theo phiên không? → A: User chọn "Có, mọi bản được phép sửa đều chỉ một phiên sửa tại một thời điểm". Đã đưa vào FR-013 và kịch bản 2.7.
