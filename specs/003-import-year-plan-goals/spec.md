# Feature Specification: Lấy mục tiêu từ kế hoạch năm học có sẵn

**Feature Branch**: `feature/G94-226-BE-FEAT-09-school-year-goals` (giữ nhánh hiện tại; chưa commit)

**Created**: 2026-10-28

**Status**: Implemented and verified — 22/22 tasks, 13/13 real-BE E2E, full FE/BE gates đạt; converged

**Input**: User: "Mục tiêu năm học: lấy mục tiêu từ kế hoạch có sẵn thuộc nhóm tuổi khác hoặc năm học khác; tích chọn từng mục tiêu hoặc chọn tất cả để thêm vào kế hoạch đang soạn; giữ luồng chỉnh sửa theo các bước hiện tại."

## Clarifications

### Session 2026-10-28

- Q: Tổ trưởng có được lấy mục tiêu từ kế hoạch của nhóm tuổi khác không? → A: Giữ quyền hiện tại; chỉ Phó hiệu trưởng lấy được từ nhóm tuổi khác, Tổ trưởng chỉ dùng nhóm tuổi mình.
- Q: Khi lấy mục tiêu, chép kèm những gì? → A: Chỉ mục tiêu và nội dung giáo dục; không chép chủ đề, đợt nghỉ hay dấu tích chủ đề.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Tìm kế hoạch nguồn khác năm học, nhóm tuổi (Priority: P1)

Người có quyền sửa kế hoạch năm mở Lấy từ kế hoạch có sẵn trong bước Mục tiêu, nội dung và chủ đề, chọn năm học và nhóm tuổi để tìm kế hoạch nguồn. Việc tìm nguồn không làm đổi năm học hay nhóm tuổi của bản đang soạn.

**Why this priority**: Tái sử dụng mục tiêu đã có, giảm nhập lại khi lập kế hoạch cho năm hoặc nhóm tuổi khác.

**Independent Test**: Chuẩn bị các kế hoạch nguồn cùng năm/khác nhóm tuổi, khác năm/cùng nhóm tuổi và khác cả hai; tìm được đúng bản mà người thao tác được phép dùng.

**Acceptance Scenarios**:

1. **Given** người thao tác có quyền sửa kế hoạch đích và dùng kế hoạch nguồn, **When** chọn năm học khác, **Then** hiển thị các nguồn phù hợp ở năm đó, kể cả khi màn chính đang chọn năm học hiện tại.
2. **Given** có nguồn khác nhóm tuổi mà người thao tác được phép dùng, **When** chọn nhóm tuổi đó hoặc Tất cả nhóm tuổi, **Then** tìm được kế hoạch nguồn tương ứng.
3. **Given** chọn đồng thời năm khác và nhóm tuổi khác, **When** có nguồn phù hợp, **Then** xem được danh sách mục tiêu để tích chọn.
4. **Given** không có nguồn phù hợp, **When** mở danh sách, **Then** thông báo rõ và cho đổi bộ lọc, không thay đổi nội dung đích.
5. **Given** bản nguồn là chính bản đích, **When** tìm nguồn, **Then** không cho chọn bản đó.
6. **Given** người thao tác là Phó hiệu trưởng có quyền sửa đích, **When** chọn nguồn khác nhóm tuổi, **Then** được dùng nguồn theo phạm vi EDU-06 hiện hành.
7. **Given** người thao tác là Tổ trưởng, **When** tìm hoặc cố dùng nguồn khác nhóm tuổi, **Then** không được xem/lấy nội dung nguồn; nguồn khác năm học nhưng cùng nhóm tuổi vẫn theo quyền xem hiện hành.

---

### User Story 2 - Chọn từng mục tiêu hoặc tất cả (Priority: P1)

Người thao tác chọn một kế hoạch nguồn, tích một hoặc nhiều mục tiêu, hoặc Chọn tất cả rồi xác nhận thêm vào bản đang soạn.

**Why this priority**: Cho phép tái sử dụng đúng phần cần thiết, không bắt buộc chép cả kế hoạch.

**Independent Test**: Với nguồn có ít nhất ba mục tiêu ở hai lĩnh vực, lần lượt chọn một mục tiêu, chọn một tập con và chọn tất cả; đối chiếu số lượng và nội dung được thêm.

**Acceptance Scenarios**:

1. **Given** chưa chọn mục tiêu, **When** mở nguồn, **Then** số đã chọn bằng 0 và chưa xác nhận thêm được.
2. **Given** nguồn có nhiều mục tiêu, **When** tích một mục tiêu, **Then** chỉ mục tiêu đó cùng nội dung giáo dục của nó được thêm khi xác nhận, không kèm dấu tích chủ đề.
3. **Given** đã chọn một tập con, **When** bấm Chọn tất cả, **Then** mọi mục tiêu của nguồn đang xem được tích và số đã chọn khớp.
4. **Given** đang chọn tất cả, **When** bấm Bỏ chọn tất cả, **Then** mọi dấu tích của nguồn bị bỏ và chưa xác nhận thêm được.
5. **Given** đã chọn vài mục tiêu, **When** đổi năm học, nhóm tuổi hoặc kế hoạch nguồn, **Then** bỏ lựa chọn của nguồn cũ để không vô tình thêm mục tiêu không còn hiển thị.
6. **Given** đã chọn mục tiêu, **When** bấm Hủy hoặc đóng hộp lấy mục tiêu, **Then** nội dung đang soạn giữ nguyên.

---

### User Story 3 - Lưu và tiếp tục soạn theo các bước cũ (Priority: P1)

Mục tiêu được thêm vào bản đang soạn để người dùng kiểm tra và chỉnh lại trước khi lưu. Kế hoạch nguồn được giữ nguyên; không chuyển sang chỉnh sửa trực tiếp trên preview.

**Why this priority**: Giữ quy trình đã chốt và tránh làm mất dữ liệu đang soạn hoặc thay đổi bản nguồn.

**Independent Test**: Mở Chỉnh sửa từ chi tiết, qua bước 2 lấy mục tiêu, lưu rồi mở lại; kiểm tra bước 1–2–3, mục tiêu mới và dữ liệu cũ.

**Acceptance Scenarios**:

1. **Given** bản đích đang có nội dung, **When** xác nhận lấy mục tiêu, **Then** giữ nguyên nội dung trước đó và thêm mục tiêu mới vào cuối lĩnh vực cùng tên theo thứ tự nguồn; giữ quan hệ lĩnh vực con, nhóm kết quả mong đợi khi cần để không gán sai ngữ cảnh.
2. **Given** mục tiêu nguồn trùng câu mục tiêu của một mục tiêu đích trong cùng lĩnh vực, **When** xác nhận, **Then** bỏ qua mục tiêu trùng, không ghi đè nội dung đích, báo số đã thêm và số đã bỏ qua. Việc so sánh bỏ khoảng trắng đầu/cuối như hành vi hiện tại.
3. **Given** vừa thêm mục tiêu, **When** chưa bấm Lưu, **Then** coi đó là thay đổi chưa lưu, không tự ghi lên hệ thống; áp dụng cảnh báo rời màn hiện có.
4. **Given** có quyền sửa và giữ phiên sửa hợp lệ, **When** bấm Lưu hoặc Ctrl+S, **Then** lưu cả nội dung cũ lẫn phần đã thêm; mở lại thấy đúng dữ liệu.
5. **Given** lưu thất bại hoặc mất quyền sửa, **When** hệ thống báo lỗi, **Then** giữ phần đang nhập để đối chiếu, không ghi đè dữ liệu mới hơn.
6. **Given** đã lấy và lưu mục tiêu, **When** kiểm tra bản nguồn, **Then** bản nguồn không bị thay đổi.
7. **Given** đang ở trang chi tiết, **When** bấm Chỉnh sửa, **Then** vẫn vào các bước Thông tin chung → Mục tiêu, nội dung và chủ đề → Xác nhận.
8. **Given** bản đích có hoặc chưa có chủ đề/đợt nghỉ, **When** lấy mục tiêu từ nguồn có chủ đề, đợt nghỉ và dấu tích, **Then** chỉ thêm mục tiêu và nội dung giáo dục, nội dung mới không có dấu tích; giữ nguyên toàn bộ chủ đề/đợt nghỉ/dấu tích có sẵn ở đích.

---

### Edge Cases

- Nguồn không có mục tiêu: thông báo rõ, không cho xác nhận thêm và không coi lĩnh vực rỗng là đã chọn.
- Toàn bộ mục tiêu đã chọn bị trùng ở bản đích: không thêm bản sao; thông báo đã thêm 0 và số bỏ qua.
- Nguồn bị xóa, đổi trạng thái hoặc mất quyền dùng trong lúc chọn: không xác nhận dữ liệu không còn hợp lệ; thông báo để chọn lại, giữ nội dung đích.
- Bản đích đã ban hành, đang chờ duyệt mà người thao tác không có quyền sửa, hoặc bị phiên khác giữ quyền: áp dụng quy tắc sửa hiện hành; chức năng lấy mục tiêu không vượt qua khóa.
- Lĩnh vực có ở nguồn nhưng không có ở đích: không tự tạo danh mục mới hoặc bỏ dữ liệu âm thầm; báo rõ mục nào chưa thêm được.
- Nguồn khác năm học hoặc đích chưa có chủ đề: vẫn chỉ chép mục tiêu và nội dung; không chép lịch/chủ đề/đợt nghỉ. Chủ đề đích và ngày của chúng giữ nguyên, tiếp tục kiểm theo EDU-23.
- Lỗi tải danh sách/nội dung nguồn: báo lỗi có thể thử lại; không hiển thị nhầm thành danh sách trống và không mất nội dung đích.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Giữ thao tác Lấy từ kế hoạch có sẵn tại bước 2 của luồng lập/sửa kế hoạch năm học.
- **FR-002**: Cho chọn năm học và nhóm tuổi độc lập với năm học, nhóm tuổi của kế hoạch đích; hỗ trợ nguồn khác một hoặc cả hai thuộc tính khi được phép dùng.
- **FR-003**: Hiển thị đủ kế hoạch nguồn phù hợp trong phạm vi được phép; không bỏ sót chỉ vì nguồn nằm ngoài trang danh sách ban đầu; loại chính bản đích khỏi lựa chọn.
- **FR-004**: Hiển thị rõ tên kế hoạch, năm học, nhóm tuổi và danh sách mục tiêu theo lĩnh vực, đủ nội dung để người dùng phân biệt các mục tiêu.
- **FR-005**: Cho tích từng mục tiêu, nhiều mục tiêu, Chọn tất cả và Bỏ chọn tất cả trong nguồn đang xem; hiển thị đúng số đã chọn và chỉ cho xác nhận khi số đã chọn lớn hơn 0.
- **FR-006**: Lựa chọn chỉ áp dụng cho nguồn đang xem; đổi bộ lọc hoặc nguồn phải bỏ lựa chọn trước đó. Một lần xác nhận lấy từ một nguồn; có thể mở lại để lấy tiếp từ nguồn khác.
- **FR-007**: Chỉ người có quyền sửa đích được thêm mục tiêu. Giữ nguyên quyền nguồn theo EDU-06: chỉ Phó hiệu trưởng lấy được nguồn khác nhóm tuổi; Tổ trưởng chỉ lấy từ nhóm tuổi mình, kể cả khi chọn năm học khác. Không mở rộng quyền xem hoặc cho Tổ trưởng lấy từ nguồn khác nhóm tuổi qua chức năng này.
- **FR-008**: Chỉ chép mục tiêu đã chọn và nội dung giáo dục của chúng thành nội dung độc lập trong bản đích. Không chép chủ đề, đợt nghỉ, lịch nguồn hoặc dấu tích nội dung–chủ đề. Các chủ đề, đợt nghỉ và dấu tích đã có ở đích giữ nguyên; nội dung mới chưa tích vào chủ đề nào, kể cả khi nguồn và đích có cùng tên viết tắt chủ đề. Áp dụng cả khi đích chưa có chủ đề.
- **FR-009**: Không sửa bản nguồn; không đổi năm học, nhóm tuổi, tên, mô tả hay trạng thái của bản đích chỉ vì chọn nguồn khác.
- **FR-010**: Thêm đúng lĩnh vực và giữ cấu trúc phân cấp liên quan; không xếp mục tiêu vào nhóm kết quả mong đợi khác. Số mục tiêu đánh lại theo thứ tự bản đích, không dùng số nguồn làm định danh.
- **FR-011**: Giữ dữ liệu đích trước khi lấy; bỏ mục tiêu trùng theo User Story 3; báo số thêm, số trùng bỏ qua và mục không thêm được.
- **FR-012**: Xác nhận lấy chỉ cập nhật bản đang soạn; Lưu/Ctrl+S, gửi/ban hành, khóa phiên và cảnh báo rời màn theo EDU-03/10/22/23 hiện hành.
- **FR-013**: Có trạng thái đang tải, trống, lỗi và mất quyền; lỗi hoặc Hủy không làm mất dữ liệu đích.
- **FR-014**: Các ô tích, chọn tất cả, bộ lọc và xác nhận có nhãn rõ, thao tác được bằng bàn phím; trạng thái đã chọn nhận biết được không chỉ bằng màu.
- **FR-015**: Phạm vi tính năng là lấy mục tiêu trong kế hoạch năm học; không thêm nhập tệp, AI, tự đổi độ tuổi/nội dung giáo dục, chỉnh sửa tại preview hoặc thay đổi chuỗi duyệt.

### Key Entities *(include if feature involves data)*

- **Kế hoạch nguồn**: Bản năm học có tên, năm học, nhóm tuổi, trạng thái và phạm vi người dùng được phép lấy; chứa các lĩnh vực, nhóm và mục tiêu tham khảo.
- **Kế hoạch đích**: Bản đang soạn được người thao tác phép sửa; năm học và nhóm tuổi giữ nguyên khi lấy từ nguồn khác.
- **Mục tiêu được chọn**: Mục tiêu nguồn được đánh dấu để thêm, cùng ngữ cảnh phân cấp và nội dung giáo dục, không kèm dấu tích chủ đề; bản chép độc lập với bản nguồn.
- **Lựa chọn hiện tại**: Tập mục tiêu của một nguồn đang mở, có số lượng và trạng thái chọn tất cả; chưa phải dữ liệu đã lưu.
- **Kết quả lấy mục tiêu**: Số thêm thành công, số bỏ qua vì trùng và các mục chưa thêm được để người thao tác đối chiếu.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Kiểm thử thành công cả ba trường hợp nguồn khác năm, khác nhóm tuổi và khác cả hai, với người được phép dùng nguồn đó.
- **SC-002**: Với nguồn thử có ít nhất 10 mục tiêu ở ít nhất hai lĩnh vực, chọn một, chọn tập con và chọn tất cả đều cho số đã chọn và số thêm/bỏ qua khớp 100% dữ liệu đối chiếu.
- **SC-003**: Chọn tất cả hoặc Bỏ chọn tất cả hoàn tất bằng một thao tác, không cần tích từng mục tiêu.
- **SC-004**: Trong các ca Hủy, lỗi tải, lỗi lưu và mất quyền, dữ liệu đích trước thao tác không bị mất; không có bản nguồn nào bị sửa trong toàn bộ bộ ca kiểm thử.
- **SC-005**: Lấy → Lưu → mở lại giữ đủ nội dung đã thêm, thứ tự và quan hệ phân cấp; mọi điểm vào Chỉnh sửa vẫn dùng luồng ba bước.
- **SC-006**: Với nguồn kiểm thử 200 mục tiêu, thao tác tích một mục tiêu hoặc Chọn tất cả phản ánh trạng thái và số lượng trong vòng một giây sau khi nội dung nguồn đã tải xong.

## Assumptions

- Người dùng tiếp tục là Phó hiệu trưởng và Tổ trưởng có quyền sửa bản đích; quyền EDU-02/03/06/08/10 giữ nguyên. Lựa chọn A cho FR-007 đã xác nhận không mở rộng phạm vi nguồn của Tổ trưởng.
- Mỗi lần xác nhận chỉ lấy từ một kế hoạch nguồn, phù hợp hộp chọn hiện có; lấy từ nhiều nguồn bằng cách mở lại và thực hiện tiếp.
- Bộ lọc thay đổi không làm đổi năm học đang chọn ở màn chính.
- Giữ hỗ trợ chọn theo lĩnh vực hiện có; yêu cầu mới không bắt buộc chọn riêng từng nội dung con của mục tiêu.
- Đối chiếu với quy tắc EDU-01/03/06/09/10/22/23/24 trong [Business Rules](../../docs/business/BUSINESS_RULES.md) và [module education-plan](../../docs/modules/education-plan.md). Quyết định lấy mục tiêu được ghi tại EDU-24, không mở rộng quyền xem kế hoạch.
- Phụ thuộc dữ liệu năm học, nhóm tuổi, kế hoạch nguồn và quyền người thao tác; không yêu cầu thay đổi các module giáo án lớp hoặc Mobile.
- Đặc tả được lập trước triển khai; tiến độ và bằng chứng nằm trong tasks.md/quickstart.md. Không tạo nhánh, commit hoặc đổi quyền/HTTP contract trong tính năng này.
