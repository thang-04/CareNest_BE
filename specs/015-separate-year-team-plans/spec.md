# Feature Specification: Phân biệt kế hoạch năm học và kế hoạch tổ

> Mốc nghiệp vụ mới ghi nhận09/11/2026: [12 câu trả lời được giáo viên phía trường xác nhận theo user](../../docs/business/education-plan-confirmation-2026-11-09.md). Ưu tiên quyết định này khi nội dung/plan/tasks cũ khác phạm vi: tổ dùng chung hai điểm trường theo tuổi, một chuỗi/năm/tuổi; không ai xóa tổ kể cả nháp; TT duyệt năm/tổ theo cấu hình đúng tuổi và không tự duyệt; năm mới ban hành tự chuyển nguồn nháp/chờ và tạo cập nhật cho tổ ban hành, giữ bản cũ cho lớp; đỏ phần đổi/xóa/liên kết ảnh hưởng, mục mới riêng, chủ đề kết thúc không đỏ; phủ đủ tuần học bằng nhánh có tên. Đây là xác nhận yêu cầu, không phải delivery hoặc phê duyệt chạy migration; plan/tasks cần đối chiếu mốc này khi triển khai tiếp.

**Feature Branch**: `feature/G94-326-BE-FEAT-09-school-year-goals-backup` (nhánh hiện tại; không tạo/chuyển nhánh)

**Created**: 2026-11-09

**Status**: Artifacts refined 09/11/2026 sau analyze và lệnh user “fix”; chưa phê duyệt triển khai/API/DDL hoặc sửa repo FE/APP.

**Input**: User yêu cầu phân biệt năm/tổ, sửa nhãn hồ sơ HT chờ duyệt, phiên bản chỉ hiển thị ở năm, tổ chỉ tick mapping và rà soát phần đổi khi năm ban hành bản mới. Chỉ đạo cuối: giữ nút lập tổ, vào thẳng trang soạn như năm, không modal; nhập tên riêng và chọn liên kết năm đã ban hành; danh sách có tên tổ/nhóm tuổi/ngày gửi/người soạn/trạng thái; tách lưu trữ hai chức năng.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Nhận đúng loại và tên hồ sơ chờ duyệt (Priority: P1)

HT/PHT cần nhận đúng loại hồ sơ và đúng tên để duyệt nội dung thuộc trách nhiệm.

**Why this priority**: Tránh hiểu nhầm năm thành tổ trong danh sách chờ duyệt.

**Independent Test**: Gửi một bản năm và một bản tổ theo đúng cấu hình, kiểm loại, tên, người duyệt và liên kết mở chi tiết.

**Acceptance Scenarios**:

1. **Given** bản năm được PHT gửi HT, **When** HT mở chờ duyệt, **Then** loại là “Kế hoạch năm học”, tên là tên bản năm và liên kết mở đúng bản năm.
2. **Given** bản tổ được gửi PHT theo cấu hình, **When** mở chờ duyệt, **Then** loại là “Kế hoạch tổ”, tên là tên riêng tổ và mở đúng bản tổ.
3. **Given** người ngoài quyền/phân công, **When** truy cập bản ghi trực tiếp, **Then** bị từ chối, không thay đổi dữ liệu.
4. **Given** tổ trưởng chỉ được phân công nhóm tuổi MG nhỡ, **When** mở list/chờ duyệt năm và tổ, **Then** chỉ hiện bản của tuổi MG nhỡ; không hiện tuổi khác dù có trong DB; mất phân công thì mất scope.
5. **Given** Tổ trưởng được cấu hình duyệt năm/tổ, đúng tuổi và đúng lượt, **When** duyệt hoặc trả lại, **Then** server cho phép theo EDU-54 dù tài khoản có role TEACHER; sai tuổi/lượt, mất phân công hoặc từng tham gia soạn bị từ chối, trả lại phải có lý do.

### User Story 2 - Lập tổ trên trang soạn và chọn nguồn năm (Priority: P1)

Tổ trưởng bấm Lập kế hoạch tổ, vào trang soạn chính, nhập tên tổ theo nhóm tuổi và chọn nguồn đã ban hành để phân bổ.

**Why this priority**: Là luồng lập được user chọn cuối cùng; tên và nguồn được tách rõ ngay đầu.

**Independent Test**: Lập tổ từ nguồn hợp lệ, nhập tên khác tên năm, tick một nội dung, lưu/mở lại rồi kiểm danh sách.

**Acceptance Scenarios**:

1. **Given** người có quyền lập tổ, **When** bấm nút lập, **Then** mở thẳng trang soạn giống cách vào trang lập năm, không modal, có tên tổ và mục chọn nguồn năm.
2. **Given** danh sách nguồn, **When** chọn liên kết, **Then** chỉ chọn đúng một bản năm ban hành hiện hành, cùng năm/tuổi và trong quyền; không liên kết nhiều bản cùng lúc.
3. **Given** nguồn được chọn, **When** soạn tổ, **Then** nguồn chỉ đọc; chỉ phân bổ từng nội dung vào chủ đề, mục tiêu tương ứng đi theo; không thêm/sửa/xóa cây nguồn hoặc tự tick cả mục tiêu.
4. **Given** tên năm “Giáo dục 4–5 tuổi”, **When** lưu tổ tên “Kế hoạch tổ nhỡ”, **Then** hai tên được giữ riêng, năm học vẫn là danh mục thời gian, nguồn không đổi tên.
5. **Given** tổ đã lưu/gửi, **When** mở list, **Then** có cột tên kế hoạch tổ, nhóm tuổi ánh xạ, ngày gửi, người soạn và trạng thái; chưa gửi hiện “—”, không dùng ngày tạo thay ngày gửi.
6. **Given** không có nguồn hợp lệ hoặc nguồn bị thay thế/mất quyền trước khi lưu, **When** xác nhận tạo, **Then** không tạo tổ với nguồn sai, giải thích và cho người dùng chọn lại, không mất nội dung đang nhập vì lỗi.
7. **Given** tổ kế thừa cây nguồn, **When** gửi request giả thêm/sửa/xóa mục tiêu, nội dung, nhãn hoặc dùng nhập file thay cây, **Then** server từ chối không ghi dữ liệu; tick/bỏ tick từng nội dung hợp lệ vẫn lưu được và không đổi nguồn năm.

### User Story 3 - Rà soát tổ khi năm ban hành phiên bản mới (Priority: P1)

Tổ trưởng thấy rõ phần thay đổi để tick lại mapping, trong khi lớp vẫn có bản tổ ban hành làm nguồn.

**Why this priority**: Thay đổi năm phải được người soạn tổ kiểm tra trước khi triển khai.

**Independent Test**: Tạo năm mới nháp rồi ban hành; mở cập nhật tổ, sửa phần đỏ và gửi duyệt, kiểm nguồn lớp trước/sau.

**Acceptance Scenarios**:

1. **Given** màn năm và tổ, **When** xem chi tiết, **Then** bảng phiên bản/so sánh phiên bản và thao tác tạo phiên bản chỉ hiện ở năm; tổ vẫn giữ lịch sử nội bộ và thao tác cập nhật theo nguồn khi có quyền.
2. **Given** phiên bản năm mới còn nháp/chờ duyệt, **When** mở tổ hiện hành, **Then** tổ chưa bị đổi nguồn/mapping theo bản đó.
3. **Given** năm mới đã ban hành, **When** rà soát tổ liên quan, **Then** bản cập nhật nhận nội dung mới và đánh dấu phần ảnh hưởng để người soạn kiểm mapping; phần không đổi giữ liên kết hợp lệ.
4. **Given** phần đỏ do thay đổi/loại bỏ tham chiếu, **When** sửa/tick lại hoặc xử lý tham chiếu như hồ sơ nhánh/giáo án hiện có, **Then** chỉ gỡ cảnh báo khi xử lý hợp lệ; lưu/mở lại giữ tiến độ; chưa xử lý hết thì không gửi duyệt.
5. **Given** tổ cũ đã ban hành và bản cập nhật chưa duyệt cuối, **When** lớp dùng nguồn, **Then** bản tổ cũ tiếp tục làm nguồn; không sửa trực tiếp hoặc xóa lịch sử/lớp cũ. Bản cập nhật đi lại luồng PHT duyệt theo cấu hình.

6. **Given** tổ đang nháp hoặc chờ duyệt theo nguồn cũ, **When** năm mới hơn ban hành, **Then** chuyển sang nguồn mới nhất, giữ phần đã nhập để rà soát; bản đang chờ về Nháp và phải gửi duyệt lại sau xử lý, bản tổ ban hành cũ vẫn giữ nguyên.
7. **Given** thay đổi nguồn ảnh hưởng chủ đề đã kết thúc và chưa kết thúc, **When** tính phần đỏ so với nguồn tổ đang dùng trước lần chuyển, **Then** chủ đề đã kết thúc không đỏ; phần bị ảnh hưởng ở chủ đề chưa kết thúc được đánh dấu và lưu/mở lại giữ tiến độ xử lý.
8. **Given** đã có bản tổ nháp/chờ song song bản ban hành, **When** năm mới ban hành hoặc xử lý lặp, **Then** cập nhật chính bản đang mở, không sinh bản mở thứ hai; không có tổ thì không tự lập lần đầu. Năm v3 so với nguồn tổ dùng ngay trước chuyển v3; tiến độ cũ chưa xử lý vẫn được kiểm tính hợp lệ, không tự mất cảnh báo khi lỗi còn tồn tại.
9. **Given** mục rà soát thuộc nguồn hiện tại, **When** xác nhận, **Then** server chỉ resolve nếu mapping đã hợp lệ; sai tổ, nguồn/revision cũ hoặc còn tham chiếu mất bị từ chối; lưu/mở lại giữ baseline và tiến độ. Mục mới có nhãn riêng, không tự thành mục đỏ bắt buộc.

### User Story 4 - Tách dữ liệu hai chức năng và bảo toàn hồ sơ (Priority: P1)

Nhà trường cần hai loại kế hoạch có lưu trữ riêng, tên riêng và liên kết nguồn rõ ràng, tránh nhầm lẫn trong vận hành.

**Why this priority**: User yêu cầu tách DB; phải giữ được lịch sử và tài liệu đang dùng.

**Independent Test**: Kiểm tra sau chuyển đổi trên bộ dữ liệu tổng hợp gồm năm/tổ nhiều lần sửa, mapping và tài liệu lớp.

**Acceptance Scenarios**:

1. **Given** hai loại kế hoạch, **When** lưu và đọc lại, **Then** năm/tổ thuộc hai bảng nghiệp vụ riêng, tổ trỏ đúng nguồn năm và mỗi loại giữ tên riêng.
2. **Given** dữ liệu cũ gồm tổ legacy không có nguồn năm, **When** chuyển đổi, **Then** bảo toàn nội dung, tick, lịch sử, người soạn/gửi/duyệt, trạng thái và liên kết lớp; không tạo năm giả hoặc gán nhầm nguồn.
3. **Given** tổ ở DRAFT/REVIEW/APPROVED, **When** bất kỳ người tạo/HT/PHT/Tổ trưởng gọi xóa trực tiếp hoặc qua UI, **Then** server từ chối, không xóa tổ hay dữ liệu con; quyền xóa năm vẫn theo EDU-07.

### Edge Cases

- Nguồn rỗng, nháp/chờ duyệt, khác năm/tuổi, ngoài quyền hoặc vừa bị thay thế.
- Bấm lập/lưu lặp; đã có chuỗi tổ hoặc bản cập nhật chưa ban hành: giữ giới hạn hiện có, không nhân bản âm thầm.
- Nguồn đổi tên, đổi nội dung, xóa mục đang tick, đổi chủ đề/lịch hoặc chỉ đánh lại số: phân biệt thay đổi thực với đổi số hiển thị.
- Mục mới chưa từng mapping: đối chiếu logic nhánh/giáo án có nhãn mới; không mặc định coi mọi dòng mới là lỗi đỏ.
- Tổ đang soạn/chờ duyệt gặp năm mới ban hành: chuyển sang nguồn mới nhất, giữ phần đã nhập để rà soát lại; bản đang chờ quay về nháp. Không ghi đè bản tổ đã ban hành.
- Rà soát sau nhiều phiên bản: so với bản năm tổ đang dùng ngay trước lần chuyển nguồn; giữ phần đã nhập, tính lại phần cần rà soát trên nguồn mới nhất. Chủ đề đã kết thúc không đánh đỏ như nhánh/giáo án hiện có.
- Tổ legacy không có nguồn năm xác định: bảo toàn theo FR-011, không tự gán năm/baseline theo tên hoặc tuổi khi năm mới ban hành; không suy là tổ có liên kết nguồn cho cập nhật tự động.
- Người soạn giữ form/lease cũ khi hệ thống chuyển nguồn: request cũ bị từ chối do revision/session đã thay đổi, giữ phần nhập để đối chiếu rồi mở phiên hợp lệ trên nguồn mới; không ghi đè dữ liệu đã chuyển. Lặp xử lý không hủy phiên thêm nếu nguồn không đổi.
- Không chỉ dựa vào màu; tên/nhãn trạng thái/giải thích phải giúp nhận biết phần cần xử lý.
- Tổ trưởng/GV không kiêm nhiệm quản lý chỉ thấy năm/tổ của đúng tuổi phân công hiệu lực (FR-015, EDU-60); hai tài khoản khác tuổi không thấy hồ sơ của nhau; phân công thay đổi ⇒ scope thay đổi ngay, không lưu cache scope.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Tên loại, tên riêng và đích mở ở list/chờ duyệt/chi tiết MUST đúng năm hoặc tổ, không suy loại từ tên riêng.
- **FR-002**: Lập tổ MUST mở trực tiếp trang soạn, không modal, không tự lập tổ lần đầu khi năm ban hành; bản cập nhật cho tổ đã tồn tại theo FR-008/013.
- **FR-003**: Trang lập tổ MUST có tên riêng và lựa chọn liên kết đúng một kế hoạch năm đã ban hành hiện hành, cùng năm/tuổi và trong quyền; nguồn không hợp lệ bị chặn tại server.
- **FR-004**: Tổ MUST chỉ phân bổ từng nội dung nguồn vào chủ đề; cây nguồn chỉ đọc, mục tiêu đi theo nội dung được tick như EDU-55. Server MUST chặn thêm/sửa/xóa cây và nhãn nguồn, kể cả request giả hoặc nhập file; không chỉ khóa UI.
- **FR-005**: Tên năm, tên tổ và tên năm học MUST là thông tin độc lập; không đổi tên nguồn khi đổi tên tổ, không tự tạo loại kế hoạch thứ ba.
- **FR-006**: List tổ MUST hiển thị tên tổ, nhóm tuổi, ngày gửi, người soạn, trạng thái; ngày chưa gửi là “—”, hành động theo quyền.
- **FR-007**: Giao diện bảng/nút tạo phiên bản MUST chỉ ở năm; lịch sử nội bộ tổ và nguồn của lớp MUST được giữ.
- **FR-008**: Chỉ khi năm mới ban hành MUST phát sinh nhu cầu cập nhật tổ; tổ ban hành cũ tiếp tục dùng đến khi bản cập nhật được duyệt cuối, bản cập nhật đi lại workflow tổ.
- **FR-009**: Bản cập nhật MUST đánh dấu phần thay đổi cần rà soát mapping, giữ phần không đổi; áp dụng cách xử lý phần đỏ như nhánh/giáo án, giữ tiến độ khi lưu lại, chặn gửi nếu còn phần chưa xử lý.
- **FR-010**: Năm và tổ MUST có hai bảng nghiệp vụ riêng; liên kết nguồn năm và lịch sử cùng loại phải phân biệt, không chỉ dùng cờ loại trong cùng một bảng như hiện tại.
- **FR-011**: Chuyển đổi MUST giữ dữ liệu và tham chiếu cũ, cả tổ legacy không nguồn; không sửa migration đã commit hoặc tự tạo dữ liệu năm giả.
- **FR-012**: Quyền, phạm vi, chống tự duyệt, trạng thái và một chuỗi/mỗi loại/năm/tuổi MUST giữ theo quy tắc hiện có; thay UI không cấp thêm quyền.
- **FR-013**: Khi năm mới ban hành, tổ đang nháp/chờ duyệt MUST chuyển sang nguồn năm mới nhất và giữ phần đã nhập để rà soát lại; bản đang chờ duyệt MUST về Nháp, sau xử lý gửi duyệt lại theo workflow tổ. Không đổi trực tiếp bản tổ đã ban hành hoặc nguồn lớp đang dùng. Tổ legacy không có liên kết nguồn xác định được bảo toàn theo FR-011, không tự gán nguồn để đưa vào cập nhật tự động.
- **FR-014**: Rà soát MUST so với bản năm mà tổ đang dùng trước khi chuyển nguồn; chủ đề đã kết thúc MUST không đánh đỏ, giữ ngoại lệ như chủ đề nhánh/giáo án hiện có. Các phần chưa kết thúc bị ảnh hưởng vẫn phải được xử lý trước gửi theo FR-009.
- **FR-015**: Tổ trưởng và GV không kiêm nhiệm quản lý MUST chỉ xem kế hoạch năm học và kế hoạch tổ của đúng nhóm tuổi được phân công hiệu lực cho tài khoản đăng nhập; không mở rộng scope khi tách bảng năm/tổ. HT/PHT có phân công hiệu lực được mọi nhóm tuổi; kiểm tại server theo EDU-60, không tin client.
- **FR-016**: Không ai được xóa kế hoạch tổ, kể cả DRAFT, REVIEW, APPROVED và kể cả người tạo/HT/PHT/Tổ trưởng; server MUST chặn mọi đường xóa tổ, bảo toàn lịch sử/liên kết lớp (EDU-07/57). Quyền xóa năm giữ riêng theo EDU-07.
- **FR-017**: Tổ trưởng MUST được duyệt/trả lại năm/tổ khi cấu hình cho phép, đúng tuổi/phân công/lượt và không tham gia soạn; không chặn chỉ vì role TEACHER. Server MUST từ chối sai scope, tự duyệt/coauthor, mất phân công và trả lại không lý do (EDU-54).

### Key Entities *(include if feature involves data)*

- **Kế hoạch năm học**: tên, năm học, nhóm tuổi, nội dung nguồn, phiên bản và duyệt/ban hành.
- **Kế hoạch tổ**: tên riêng, năm/tuổi, nguồn năm, mapping, workflow và lịch sử cập nhật nội bộ.
- **Liên kết nội dung–chủ đề**: từng nội dung được chọn vào từng chủ đề, không thay đổi văn bản nguồn.
- **Mục cần rà soát**: lý do đổi/mất tham chiếu và tiến độ xử lý tương ứng nguồn.
- **Năm học / nhóm tuổi / tài liệu lớp**: danh mục và hồ sơ liên quan phải giữ đúng tham chiếu.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% hồ sơ năm/tổ trong bộ ca duyệt hiển thị đúng loại, tên và mở đúng hồ sơ.
- **SC-002**: Người soạn vào trang lập tổ bằng một lần bấm từ danh sách; không có bước modal trung gian.
- **SC-003**: 100% ca nguồn không hợp lệ bị từ chối; tên/mapping hợp lệ giữ nguyên sau lưu/mở lại.
- **SC-004**: List tổ có đủ năm cột user yêu cầu; không có bảng/nút phiên bản tại tổ và không mất lịch sử cũ.
- **SC-005**: 100% phần cần rà soát trong bộ ca thay đổi được nhận biết và phải xử lý trước gửi; bản tổ/lớp đã ban hành giữ nguyên trong lúc chờ duyệt cập nhật.
- **SC-006**: Kiểm đối chiếu trước/sau chuyển đổi đạt 100% số hồ sơ, nội dung, mapping và tham chiếu hợp lệ trong bộ kiểm thử, không tạo hồ sơ năm giả cho legacy.

## Assumptions

- Nguồn: user ngày 09/11/2026; quyết định cuối về trang soạn trực tiếp và tách bảng thay các ý modal/tự xuất hiện trước đó. Ba câu trả lời đã chốt về thời điểm ban hành, giữ bản tổ cũ, ẩn phiên bản tổ vẫn áp dụng.
- Cấu hình mặc định năm PHT → HT, tổ tổ trưởng → PHT; vẫn cấu hình được theo EDU-54.
- “Tên khác nhau” nghĩa là trường tên độc lập, chưa yêu cầu cấm hai bản có chuỗi tên trùng nhau.
- Phần lưu trữ vật lý là ràng buộc user yêu cầu, không phải lựa chọn công nghệ mới; tên bảng/cột, bảng con, thiết kế chuyển đổi và contract thuộc phase plan.
- [Domain spec](domain-spec.md), EDU-62 và [giải thích DB hiện tại/đích](database-context.md) là nguồn kèm theo. Feature hoàn thành gồm BE + FE; task FE trong tài liệu không phải quyền sửa repo FE. Lượt fix này chỉ sửa artifact. Làn L, cần plan/API/DDL và quyền client theo phạm vi trước triển khai.

## Clarifications

### Session 2026-11-09

- Q1: Tổ đang nháp/chờ duyệt gặp năm nguồn mới hơn đã ban hành? → User chọn chuyển sang nguồn mới nhất, giữ phần đã nhập và rà soát lại; bản đang chờ phải về nháp.
- Q2: Chủ đề đã kết thúc có bỏ qua đánh đỏ không? → User chọn có, giữ logic chủ đề nhánh/giáo án hiện có; so với nguồn năm tổ đang dùng trước lần chuyển.
- Q3: Phạm vi xem của tổ trưởng? → User cập nhật: tổ trưởng và GV chỉ xem kế hoạch năm + tổ của đúng nhóm tuổi được phân công hiệu lực, không phải mọi nhóm tuổi (FR-015, EDU-60); HT/PHT phân công hiệu lực giữ scope mọi tuổi.
- Đây là quyết định nghiệp vụ đích, chưa phải bằng chứng code đã có hoặc phê duyệt plan/migration.
