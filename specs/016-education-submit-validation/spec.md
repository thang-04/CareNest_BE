# Feature Specification: Kiểm tra điều kiện gửi duyệt kế hoạch giáo dục

> **Quyết định user10/11/2026 — duyệt hồ sơ lớp:** Theo chuỗi HT cấu hình, không bắt buộc có bước TT; chỉ PHT duyệt cũng hợp lệ. Giữ cấp trên duyệt cấp dưới, chống tự duyệt, phạm vi phân công và REVIEW cấm sửa. Quyết định này thay yêu cầu TT bắt buộc trước PHT/HT trong tài liệu09/11; chưa phải bằng chứng triển khai/nghiệm thu.

**Feature Branch**: `feature/G94-326-BE-FEAT-09-school-year-goals-backup` (nhánh hiện tại)

**Created**: 2026-11-09

**Status**: Draft — yêu cầu user trực tiếp; chưa triển khai

**Input**: User yêu cầu kiểm thời lượng, nội dung tối thiểu và số chủ đề khi gửi kế hoạch năm; kiểm phân bổ mục tiêu khi gửi kế hoạch tổ; đánh dấu bắt buộc bằng dấu * đỏ. Các câu trả lời trong chat ngày 09/11 xác định phạm vi dưới đây.

## Clarifications

### Session 2026-11-09

- Q: Khi gửi kế hoạch năm, mỗi mục tiêu có bắt buộc phải có ít nhất một nội dung không? → A: Có; user điều chỉnh câu trả lời trước: mỗi mục tiêu phải có ít nhất một nội dung khi gửi duyệt.
- Q: Tổ trưởng xử lý mục tiêu chưa có nội dung thế nào? → A: User cho biết tổ trưởng được thêm, sửa nội dung và điều chỉnh điều kiện gửi năm: mỗi mục tiêu phải có ít nhất một nội dung.
- Q: Khi tổ trưởng thêm hoặc sửa nội dung, thay đổi áp dụng ở đâu? → A: Chỉ trong kế hoạch tổ; giáo viên phía dưới thấy phần kế hoạch tổ này.
- Q: Khi gửi kế hoạch năm, mỗi chủ đề và mẫu nhánh đã khai báo có bắt buộc đủ ngày bắt đầu và kết thúc không? → A: Bắt buộc đủ ngày khi gửi duyệt.
- Q: Xử lý kế hoạch năm bị mất thông tin giới hạn thời lượng do lỗi cũ như thế nào? → A: Khôi phục giới hạn khi có bằng chứng, giữ trạng thái hiện tại.
- Q: Phiên bản điều chỉnh của kế hoạch năm dùng giới hạn thời lượng nào? → A: Dùng cấu hình hiện tại lúc tạo phiên bản mới.
- Q: Khi năm mới thay đổi cùng nội dung đã sửa riêng ở tổ, bản cập nhật tổ giữ gì? → A: Giữ nội dung tổ đã sửa và báo tổ trưởng kiểm tra lại.
- Q: Dùng quy tắc duyệt và sửa nào khi flow khác context? → A: Duyệt theo cấp bậc cố định và bỏ khóa 90 giây như tài liệu flow.
- Q: Nếu kế hoạch bị mất giới hạn thời lượng và không có bằng chứng khôi phục, khi gửi xử lý thế nào? → A: Chặn gửi, báo cần kiểm tra và khôi phục giới hạn.
- Q: Trạng thái “Đang sửa” và khóa phiên 90 giây xử lý thế nào? → A: Bỏ hẳn trạng thái, nhãn và cơ chế khóa/nhịp duy trì; vẫn bảo vệ dữ liệu khi lưu đồng thời và giữ quyền người được phân công.
- Q: Có cho xóa kế hoạch tổ không? → A: Không cho xóa, kể cả xóa mềm, vì chỉ có một chuỗi kế hoạch tổ liên kết với kế hoạch năm học; giữ dữ liệu, lịch sử và liên kết liên quan.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Kiểm tra thời lượng khi gửi năm học (Priority: P1)

Người có quyền soạn nhìn thấy lỗi thời lượng chủ đề và mẫu nhánh để điều chỉnh trước khi gửi duyệt.

**Why this priority**: Kế hoạch vượt giới hạn cấu hình đã thực tế được gửi thành công.

**Independent Test**: Cấu hình chủ đề 2–4 tuần, lập chủ đề 5 tuần học và thử lưu nháp, gửi; sau đó sửa còn 4 tuần và thử lại.

**Acceptance Scenarios**:

1. **Given** bản năm áp dụng giới hạn chủ đề 2–4 tuần, **When** chủ đề đủ ngày có 5 tuần học và người soạn gửi, **Then** từ chối gửi, chỉ rõ chủ đề, số tuần và khoảng cho phép, giữ nguyên bản nháp.
2. **Given** mẫu nhánh có trong kế hoạch năm, **When** số tuần học của nhánh ngoài khoảng cấu hình áp dụng cho nhánh và người soạn gửi, **Then** từ chối gửi và chỉ rõ nhánh, số tuần và khoảng cho phép.
3. **Given** không có mẫu nhánh, **When** kiểm điều kiện riêng về thời lượng nhánh, **Then** không báo thiếu nhánh; các điều kiện gửi khác vẫn được kiểm.
4. **Given** chủ đề và các nhánh có trong kế hoạch đều nằm trong khoảng bao gồm cả hai đầu mút, **When** các điều kiện gửi khác cũng đạt, **Then** cho gửi duyệt.
5. **Given** lịch chưa hợp lệ hoặc quá giới hạn, **When** người soạn lưu nháp, **Then** vẫn lưu được theo quy tắc nháp hiện hành và có cảnh báo để sửa.
6. **Given** hồ sơ có giới hạn đã gắn khi tạo và sau đó cấu hình trường thay đổi, **When** mở lại và gửi hồ sơ, **Then** kiểm theo giới hạn của hồ sơ đó.
7. **Given** người soạn không có quyền gửi hoặc hồ sơ không còn ở trạng thái được gửi, **When** thử gửi, **Then** bị từ chối theo quyền và trạng thái hiện hành.
8. **Given** chủ đề hoặc mẫu nhánh đã khai báo thiếu ngày bắt đầu hoặc ngày kết thúc, **When** gửi năm, **Then** bị chặn và chỉ rõ đối tượng cùng ngày cần nhập; lưu nháp vẫn được phép.
9. **Given** giới hạn cấu hình đã đổi sau khi ban hành phiên bản năm cũ, **When** tạo phiên bản điều chỉnh, **Then** phiên bản mới dùng giới hạn hiện tại và phiên bản cũ giữ giới hạn cũ.
10. **Given** giới hạn của hồ sơ bị mất do lỗi, **When** có bằng chứng tin cậy về giới hạn cũ, **Then** có thể khôi phục theo bằng chứng và giữ trạng thái hồ sơ; không tự suy hoặc khôi phục khi thiếu bằng chứng.
11. **Given** PHT soạn năm hoặc TT soạn tổ, **When** người không đúng cấp duyệt hoặc người soạn tự duyệt, **Then** từ chối; REVIEW không được sửa và chỉ người được phân công được soạn theo EDU-70.
12. **Given** hai lần lưu đồng thời từ phiên cũ hoặc thiết bị khác, **When** nội dung đã thay đổi từ revision người gửi, **Then** từ chối ghi đè và giữ dữ liệu đã lưu cùng bản đang soạn để xử lý xung đột.
13. **Given** giới hạn hồ sơ bị mất do lỗi và không đủ bằng chứng khôi phục, **When** gửi năm, **Then** bị chặn, được báo cần kiểm tra và khôi phục giới hạn; trạng thái và nội dung không đổi.
14. **Given** có bằng chứng hồ sơ thực sự thuộc diện cũ không có giới hạn theo chính sách cũ, **When** gửi năm, **Then** không tự áp khoảng số tuần mới; vẫn kiểm đủ ngày và các điều kiện khác.
15. **Given** người được phân công đang soạn bản nháp, **When** mở, soạn và lưu sau hơn 90 giây, **Then** không phải lấy hay gia hạn khóa phiên để tiếp tục; giao diện không hiển thị trạng thái “Đang sửa”, quyền và kiểm tra xung đột dữ liệu vẫn được áp dụng.

### User Story 2 - Kế hoạch năm đủ nội dung và chủ đề (Priority: P1)

Người soạn biết dữ liệu bắt buộc trước khi gửi kế hoạch năm học.

**Why this priority**: Kế hoạch cần có nội dung giáo dục và hệ thống chủ đề tối thiểu để tổ sử dụng.

**Independent Test**: Gửi lần lượt kế hoạch có 8 rồi 9 chủ đề và bổ sung từng phần của một chuỗi kết quả mong đợi → STT → mục tiêu → nội dung.

**Acceptance Scenarios**:

1. **Given** năm học có dưới 9 chủ đề hợp lệ, **When** gửi duyệt, **Then** bị chặn và thấy số hiện có cùng mức tối thiểu 9.
2. **Given** năm học có ít nhất 9 chủ đề hợp lệ nhưng chưa có một chuỗi gồm kết quả mong đợi, STT, mục tiêu và nội dung, **When** gửi duyệt, **Then** bị chặn và được chỉ rõ phần còn thiếu.
3. **Given** năm học đủ ít nhất 9 chủ đề hợp lệ và một chuỗi có đủ bốn thành phần, **When** các điều kiện khác đạt, **Then** cho gửi duyệt.
4. **Given** màn soạn năm học, **When** người soạn xem các trường bắt buộc để gửi duyệt, **Then** mỗi trường đó có dấu * đỏ kèm nhãn dễ hiểu; dấu và mô tả không chỉ phụ thuộc màu sắc.
5. **Given** đã có một chuỗi đủ kết quả mong đợi, STT, mục tiêu và nội dung nhưng mục tiêu khác chưa có nội dung, **When** gửi năm, **Then** bị chặn và được chỉ rõ mục tiêu còn thiếu nội dung.

### User Story 3 - Phân bổ mọi mục tiêu trong kế hoạch tổ (Priority: P1)

Tổ trưởng xác định rõ mục tiêu chưa được gắn vào chủ đề trước khi gửi kế hoạch tổ.

**Why this priority**: Mục tiêu chưa phân bổ không có chủ đề để triển khai.

**Independent Test**: Tạo tổ có hai mục tiêu, chỉ tick nội dung của một mục tiêu; gửi rồi tick thêm nội dung của mục tiêu còn lại và gửi lại.

**Acceptance Scenarios**:

1. **Given** một mục tiêu nguồn chưa có nội dung nào được tick vào chủ đề, **When** gửi kế hoạch tổ, **Then** bị chặn, xác định mục tiêu chưa phân bổ và giữ các lựa chọn đã nhập.
2. **Given** mỗi mục tiêu có ít nhất một nội dung của chính mục tiêu đó được tick vào ít nhất một chủ đề hợp lệ, **When** các điều kiện gửi khác đạt, **Then** cho gửi; các nội dung khác không bắt buộc đều được tick theo yêu cầu này.
3. **Given** thông tin phân bổ còn thiếu, **When** lưu nháp, **Then** giữ được tiến độ và nhắc người soạn trước khi gửi.
4. **Given** tổ trưởng có quyền soạn, **When** thêm hoặc sửa nội dung trong kế hoạch tổ, **Then** thay đổi thuộc kế hoạch tổ đó, không thay đổi kế hoạch năm nguồn; giáo viên thấy nội dung tổ theo quyền và trạng thái công bố hiện hành.
5. **Given** tổ đã sửa nội dung riêng và năm mới ban hành cũng thay đổi nội dung nguồn đó, **When** cập nhật kế hoạch tổ, **Then** giữ nội dung tổ đã sửa và đánh dấu để tổ trưởng rà soát.
6. **Given** kế hoạch tổ đang nháp, chờ duyệt hoặc đã ban hành, **When** bất kỳ người dùng nào thử xóa hoặc ẩn kế hoạch bằng chức năng xóa, **Then** hệ thống từ chối và giữ kế hoạch, nguồn năm, lịch sử cùng liên kết lớp.

### Edge Cases

- Chủ đề trống hoặc chỉ có khoảng trắng không được tính vào mức 9; chủ đề được tính phải đáp ứng điều kiện hợp lệ hiện hành.
- Thời lượng tính theo tuần học; tuần nghỉ trọn tuần không tính, nghỉ lẻ vẫn tính. Ngày không đủ hoặc không đúng điều kiện lịch không được coi là hợp lệ chỉ vì không tính được số tuần.
- Nhánh không có trong năm là tùy chọn; nhánh đã khai báo phải hợp lệ theo giới hạn và điều kiện lịch áp dụng.
- Mục tiêu có nhiều nội dung chỉ cần ít nhất một nội dung được tick; tick của mục tiêu khác hoặc chủ đề không hợp lệ không thỏa điều kiện.
- Không tự thay đổi hồ sơ đã gửi/ban hành hoặc tự áp cấu hình mới vào hồ sơ cũ; trường hợp mất giới hạn lịch sử phải được xử lý có kiểm chứng.
- Từ chối gửi không thay đổi trạng thái và không làm mất nội dung đã lưu; kiểm tra phải áp dụng cho mọi đường gửi hợp lệ.
- Không cho xóa hoặc ẩn kế hoạch tổ bằng xóa mềm, kể cả với người tạo hoặc người quản lý; quyền xóa kế hoạch năm là quy định riêng theo EDU-07 và EDU-70.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Khi gửi năm, hệ thống MUST yêu cầu đủ ngày bắt đầu và kết thúc cho từng chủ đề và mẫu nhánh đã khai báo, rồi kiểm thời lượng theo khoảng tối thiểu–tối đa áp dụng cho hồ sơ; hai đầu mút hợp lệ.
- **FR-002**: Hệ thống MUST báo ngay khi dữ liệu lịch đủ để xác định lỗi và kiểm lại tại thời điểm gửi; thông báo nêu đối tượng và giới hạn/số tuần thực tế.
- **FR-003**: Năm gửi duyệt MUST có tối thiểu 9 chủ đề hợp lệ, ít nhất một chuỗi có kết quả mong đợi, STT, mục tiêu và nội dung không rỗng; mỗi mục tiêu phải có ít nhất một nội dung không rỗng.
- **FR-004**: Trường bắt buộc gửi duyệt MUST có dấu * đỏ, gắn với nhãn và được thông báo bằng văn bản cho công nghệ hỗ trợ.
- **FR-005**: Khi gửi tổ, mỗi mục tiêu trong cây nguồn MUST có ít nhất một nội dung của nó được tick vào ít nhất một chủ đề hợp lệ; không bắt buộc mọi nội dung của mục tiêu đều được tick.
- **FR-006**: Kiểm tra MUST thực thi tại nơi quyết định chuyển trạng thái cho mọi đường gửi; giao diện hiển thị lỗi tương ứng và giúp tìm phần cần sửa.
- **FR-007**: Dữ liệu chưa đủ điều kiện vẫn được lưu nháp theo quy tắc hiện hành; gửi lỗi không chuyển trạng thái hoặc xóa nội dung.
- **FR-008**: Giữ giới hạn đã gắn với hồ sơ; cấu hình thay đổi chỉ áp cho hồ sơ mới theo EDU-56. Không tự suy giới hạn lịch sử đã mất.
- **FR-009**: Duyệt theo thứ bậc cố định và phân công một người soạn theo EDU-70; bỏ hẳn trạng thái/nhãn “Đang sửa”, khóa phiên 90 giây và heartbeat 20 giây khỏi luồng nghiệp vụ và giao diện. Giữ REVIEW không được sửa, không tự duyệt và cấp dưới không duyệt cấp trên; vẫn bảo vệ revision/chống ghi đè. Không tạo luồng gửi riêng cho nhánh lớp; nhánh tại FR-001 là mẫu nhánh trong năm.
- **FR-010**: Tổ trưởng có quyền soạn MUST được thêm, sửa nội dung trong kế hoạch tổ của mình; thay đổi không sửa nguồn năm. Giáo viên thuộc phạm vi được phép MUST thấy nội dung kế hoạch tổ theo trạng thái công bố hiện hành.
- **FR-011**: Kế hoạch năm phiên bản điều chỉnh MUST ghi nhận cấu hình giới hạn hiện tại tại thời điểm tạo; các phiên bản trước giữ giới hạn của mình. Giới hạn đã mất do lỗi chỉ khôi phục khi có bằng chứng và không tự đổi trạng thái hồ sơ.
- **FR-012**: Khi cập nhật tổ theo năm mới, nếu năm và tổ cùng sửa một nội dung, hệ thống MUST giữ nội dung tổ đã sửa và báo tổ trưởng rà soát; không ghi đè hoặc mất tick đã nhập.
- **FR-013**: Từ chối soạn/gửi/duyệt ngoài phân công, phạm vi hoặc thứ bậc hợp lệ; bảo vệ nội dung trước ghi đè đồng thời và giữ trạng thái REVIEW không sửa dù bỏ khóa phiên.
- **FR-014**: Hồ sơ mất giới hạn do lỗi, hoặc chưa thể phân biệt với hồ sơ cũ thực sự không giới hạn, MUST bị chặn gửi và báo cần kiểm tra/khôi phục; chỉ hồ sơ có bằng chứng thuộc diện legacy không giới hạn mới áp dụng ngoại lệ đó.
- **FR-015**: Hệ thống MUST từ chối mọi yêu cầu xóa kế hoạch tổ, kể cả xóa mềm hoặc ẩn bằng chức năng xóa, với mọi vai trò và mọi trạng thái; giữ kế hoạch, liên kết với năm, lịch sử và liên kết lớp theo EDU-57. Quyền xóa kế hoạch năm không thuộc thay đổi này.

### Key Entities *(include if feature involves data)*

- **Kế hoạch năm**: Hồ sơ theo năm học/nhóm tuổi, chứa cây nội dung, các chủ đề và mẫu nhánh tùy chọn.
- **Chủ đề / mẫu nhánh**: Khoảng tuần học và giới hạn thời lượng áp dụng cho từng loại.
- **Kế hoạch tổ**: Hồ sơ kế thừa cây năm; người soạn phân bổ từng nội dung vào chủ đề.
- **Mục tiêu và nội dung**: Mục tiêu thuộc nhóm kết quả mong đợi; mỗi nội dung thuộc một mục tiêu và có thể được tick vào chủ đề.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% tình huống thử chủ đề hoặc nhánh thiếu ngày hoặc dưới/vượt ngưỡng bị chặn gửi; đúng ngưỡng được chấp nhận khi điều kiện khác đạt.
- **SC-002**: 100% tình huống thử với 0–8 chủ đề hoặc thiếu một trong bốn thành phần bị chặn; trường hợp đủ 9 và đủ thành phần được thông qua kiểm tra tương ứng.
- **SC-003**: 100% mục tiêu chưa có nội dung phân bổ được chỉ ra; trường hợp mỗi mục tiêu có ít nhất một nội dung tick vượt qua kiểm tra phân bổ.
- **SC-004**: 100% trường bắt buộc trong phạm vi soạn năm có dấu * đỏ và tên truy cập được; mọi lần gửi lỗi giữ nguyên dữ liệu đã lưu và trạng thái nháp.
- **SC-005**: Trong mọi tình huống thử ở trạng thái nháp, chờ duyệt và ban hành, yêu cầu xóa kế hoạch tổ đều bị từ chối và các liên kết, lịch sử được giữ; người có quyền soạn không mất khả năng lưu chỉ vì đã qua 90 giây.

## Assumptions

- Các yêu cầu và ba câu trả lời là chỉ đạo user ngày 09/11/2026; chưa có xác nhận mới của giáo viên trường cho delta này.
- Mỗi mục tiêu có STT liên tục do hệ thống tạo theo EDU-09; nhãn hiển thị tùy chọn theo EDU-58, để trống dùng STT mặc định, không bắt buộc nhập nhãn riêng. Bốn thành phần tối thiểu thuộc cùng một chuỗi để có ý nghĩa nghiệp vụ.
- Đủ 9 chủ đề là ngưỡng cho kế hoạch năm, không tự đặt ngưỡng mới cho kế hoạch tổ hay hồ sơ lớp.
- User điều chỉnh: mỗi mục tiêu năm phải có ít nhất một nội dung trước khi gửi; điều kiện tick kế hoạch tổ áp dụng cho từng mục tiêu.
- User xác nhận tổ trưởng thêm, sửa nội dung chỉ trong kế hoạch tổ; giáo viên phía dưới nhìn thấy nội dung kế hoạch tổ theo phạm vi và trạng thái hiện hành.
- Kế hoạch có bằng chứng thực sự thuộc LEGACY_UNBOUNDED giữ xử lý hiện hành; hồ sơ mất giới hạn hoặc chưa phân biệt được phải chặn gửi tới khi kiểm tra/khôi phục theo bằng chứng. Không tự thay trạng thái hiện tại. Phiên bản điều chỉnh mới dùng cấu hình hiện tại lúc tạo.
