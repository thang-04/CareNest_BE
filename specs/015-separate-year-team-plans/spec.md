# Feature Specification: Phân biệt kế hoạch năm học và kế hoạch tổ

> **Quyết định mới user 10/11/2026 — EDU-72:** Bỏ thao tác lập kế hoạch tổ lần đầu. Khi kế hoạch năm được ban hành, hệ thống tự tạo một TEAM DRAFT cùng năm học/nhóm tuổi, gắn phiên bản năm đã ban hành mới nhất và giao tổ trưởng duy nhất đang phụ trách tuổi đó soạn. User làm rõ “chỉ có một tổ trưởng của một nhóm tuổi, gửi cho người đó soạn”. Quyết định này thay phần “không tự lập tổ/giữ nút lập tổ/chọn nguồn thủ công” ở EDU-62 và tài liệu09/11; các quy tắc giữ nội dung đang soạn, rà soát và giữ tổ ban hành cũ làm nguồn lớp vẫn áp dụng. Đây là chỉ đạo user, không suy là xác nhận mới từ giáo viên trường.


> **Delta mới hơn — 09/11/2026:** [spec016](../016-education-submit-validation/spec.md), EDU-65–71 PROPOSED theo user trực tiếp thay phần mâu thuẫn: tổ trưởng thêm/sửa nội dung riêng; cùng sửa giữ bản tổ và báo rà soát; điều kiện gửi năm/tổ và xử lý snapshot theo spec016; duyệt theo thứ bậc cố định, một người soạn, bỏ lease 90/20. Phần mô tả cũ ở dưới giữ lịch sử; không suy delta đã được giáo viên trường xác nhận hoặc đã triển khai. Bản tổ ban hành cũ vẫn làm nguồn lớp theo EDU-62.

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

### User Story 5 - Giáo viên triển khai mẫu nhánh cho lớp được phân công (Priority: P1)

PHT soạn kế hoạch năm học cho từng nhóm tuổi, gồm chủ đề và mẫu chủ đề nhánh. Khi kế hoạch tổ cùng tuổi đã ban hành, trang chủ đề/chủ đề nhánh của lớp dùng đúng nguồn tổ đó. Giáo viên chủ nhiệm lưu thay đổi nhánh thành bản riêng của mình và lớp mình; mọi thay đổi giữ nguyên mẫu PHT/tổ để lớp khác vẫn dùng được. Chủ đề lớn luôn chỉ đọc với giáo viên.

**Why this priority**: Tránh sửa nhầm mẫu nguồn hoặc lịch của lớp khác trong lúc vẫn cho giáo viên hoàn thành giáo án của lớp mình.

**Independent Test**: Dùng hai lớp cùng nhóm tuổi, một mẫu nhánh PHT và tổ tương ứng đã ban hành; kiểm hai giáo viên mở đúng lớp của mình, giáo viên chủ nhiệm sửa/lưu nhánh lớp mình, giáo viên còn lại chỉ xem, mẫu PHT và lớp còn lại không đổi.

**Acceptance Scenarios**:

1. **Given** PHT lập annual cho một nhóm tuổi, **When** annual được ban hành và tổ cùng tuổi được ban hành từ nguồn đó, **Then** chủ đề và mẫu nhánh có mặt trong nguồn tổ; giáo viên không xem/truy cập annual trực tiếp và không sửa nguồn PHT/tổ.
2. **Given** hai giáo viên được phân công cùng một lớp, **When** vào chủ đề/chủ đề nhánh của lớp, **Then** cả hai xem được đúng chủ đề và nhánh của lớp; chỉ giáo viên chủ nhiệm lưu được thay đổi, request ghi của giáo viên còn lại bị từ chối tại server.
3. **Given** giáo viên chủ nhiệm mở lớp có tổ APPROVED đúng năm/nhóm tuổi, **When** sửa, thêm, xóa hoặc đổi ngày nhánh, **Then** thay đổi được lưu riêng theo giáo viên chủ nhiệm/lớp/tổ; mẫu PHT/tổ và nhánh riêng của lớp khác vẫn nguyên vẹn. Chủ đề lớn hiển thị xám và server từ chối mọi thay đổi tên, nội dung, thứ tự hoặc ngày.
4. **Given** PHT đã có mẫu nhánh, **When** giáo viên chưa sửa gì, **Then** lớp dùng luôn mẫu đó, không có lựa chọn “dùng nguyên bản” hoặc bản sao hiển thị riêng. **Given** PHT chưa có nhánh cho chủ đề, **When** giáo viên bắt đầu soạn kế hoạch chủ đề, **Then** hệ thống nhắc phải tạo nhánh trước khi gửi hồ sơ chủ đề.
5. **Given** giáo viên chủ nhiệm có tổ APPROVED, **When** soạn chủ đề/nhánh/kế hoạch chủ đề/giáo án, **Then** có thể soạn cả năm ngay từ đầu năm. Một chủ đề kéo qua nhiều tháng vẫn là một hồ sơ duyệt. Giáo viên chủ động gửi kế hoạch chủ đề; từ 7 ngày trước ngày bắt đầu chủ đề, tổ trưởng được nhắc duyệt nhưng vẫn có thể duyệt sớm. Các nhánh của chủ đề được xem/duyệt trong cùng hồ sơ; nhánh không có gửi/duyệt riêng và không chặn tạo/lưu kế hoạch chủ đề khi còn nháp. **Given** nhánh, kế hoạch chủ đề hoặc giáo án đã ban hành nhưng ngày dạy chưa đến, **When** chủ nhiệm sửa, **Then** chỉ hồ sơ chủ đề chứa phần sửa về nháp và phải gửi duyệt lại; phần đã đến ngày dạy bị từ chối sửa, chủ đề khác giữ nguyên trạng thái.
6. **Given** PHT mở trang “Chủ đề / Chủ đề nhánh”, hoặc tổ trưởng mở trang trong tuổi được phân công, **When** lọc theo lớp, **Then** PHT thấy/lọc mọi nhóm tuổi, lớp; tổ trưởng chỉ thấy/lọc lớp thuộc nhóm tuổi mình. Giáo viên không dùng trang lọc đa lớp, chỉ xem dữ liệu lớp mình.
7. **Given** người dùng xem chủ đề nhánh, **When** trang được mở, **Then** dùng một bảng chung gồm Tháng, Chủ đề, Chủ đề nhánh, Tuần và Ghi chú; không hiện khối tóm tắt “Lịch chủ đề và chủ đề nhánh của lớp”.

### User Story 6 - Làm sạch dữ liệu demo local (Priority: P2)

Người demo cần bắt đầu lại luồng kế hoạch giáo dục từ dữ liệu trống mà không làm hỏng danh mục và cấu hình nền.

**Independent Test**: Sau reset local, tạo mới annual → team → chủ đề nhánh/hồ sơ lớp mà không còn dữ liệu giáo dục cũ.

**Acceptance Scenarios**:

1. **Given** database local có dữ liệu demo kế hoạch và hồ sơ lớp, **When** thực hiện reset, **Then** các kế hoạch, mapping, chủ đề, lịch sử, hồ sơ lớp và học liệu liên quan đều không còn.
2. **Given** reset hoàn tất, **When** kiểm tra danh mục chương trình Bộ, cấu trúc trường/lớp, tài khoản, phân công và cấu hình, **Then** các dữ liệu nền vẫn giữ nguyên.

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
- Tổ trưởng chỉ thấy kế hoạch tổ và trang lọc chủ đề nhánh của đúng nhóm tuổi được phân công hiệu lực; quyền xem annual APPROVED cùng tuổi để lập tổ giữ theo EDU-60. Giáo viên không truy cập annual hoặc dữ liệu lớp khác; phân công thay đổi ⇒ scope thay đổi ngay, không lưu cache scope.

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
- **FR-015**: Tổ trưởng không kiêm nhiệm quản lý MUST chỉ xem kế hoạch tổ của đúng nhóm tuổi được phân công hiệu lực; không xem kế hoạch tổ tuổi khác. Quyền xem kế hoạch năm học APPROVED cùng tuổi để chọn nguồn giữ theo EDU-60. HT/PHT có phân công hiệu lực được mọi nhóm tuổi; kiểm tại server, không tin client.
- **FR-016**: Không ai được xóa kế hoạch tổ, kể cả xóa cứng hoặc xóa mềm/ẩn khỏi danh sách, ở DRAFT, REVIEW, APPROVED và kể cả người tạo/HT/PHT/Tổ trưởng; server MUST chặn mọi đường xóa tổ, bảo toàn liên kết với năm, lịch sử/liên kết lớp (EDU-07/57; user làm rõ trực tiếp 09/11/2026). Quyền xóa năm giữ riêng theo EDU-07.
- **FR-017**: Tổ trưởng MUST được duyệt/trả lại năm/tổ khi cấu hình cho phép, đúng tuổi/phân công/lượt và không tham gia soạn; không chặn chỉ vì role TEACHER. Server MUST từ chối sai scope, tự duyệt/coauthor, mất phân công và trả lại không lý do (EDU-54).
- **FR-018**: PHT MUST là vai trò duy nhất tạo/sửa chủ đề lớn và mẫu nhánh nguồn trong annual. Giáo viên MUST NOT xem/truy cập annual; chỉ dùng TEAM APPROVED đúng năm/nhóm tuổi/lớp. Cả giáo viên của lớp xem chủ đề/nhánh lớp; chỉ giáo viên chủ nhiệm MUST tạo/sửa/xóa/đổi ngày nhánh và server MUST lưu bản nhánh riêng theo chủ nhiệm/lớp/tổ, không làm thay đổi mẫu PHT/tổ hoặc lớp khác. Chủ đề lớn MUST chỉ đọc/xám với giáo viên và server MUST chặn sửa tên, nội dung, thứ tự, ngày (EDU-63/64).
- **FR-019**: Khi reset dữ liệu demo local, hệ thống MUST chỉ xóa dữ liệu nghiệp vụ kế hoạch giáo dục và hồ sơ lớp phụ thuộc; giữ danh mục chương trình Bộ, cấu trúc trường/lớp, tài khoản, phân công và cấu hình để có thể demo lại từ đầu.
- **FR-020**: Hệ thống MUST có một trang chung tên “Chủ đề / Chủ đề nhánh”, hiển thị một bảng Tháng, Chủ đề, Chủ đề nhánh, Tuần, Ghi chú và không có khối tóm tắt lịch riêng. PHT lọc mọi lớp/nhóm tuổi; tổ trưởng lọc các lớp đúng nhóm tuổi; giáo viên chỉ xem chủ đề/nhánh lớp mình, không lọc đa lớp. Nhánh không có workflow riêng; nhánh của từng chủ đề được gửi/duyệt cùng hồ sơ kế hoạch chủ đề. Sau khi hồ sơ chủ đề ban hành, thay đổi nhánh liên quan MUST thuộc hồ sơ bị trả lại hoặc phiên kế hoạch chủ đề mới (EDU-64).
- **FR-021**: Khi phân công chủ nhiệm của lớp thay đổi trong năm học, chủ nhiệm mới MUST tiếp quản bản nhánh lớp đang dùng và quyền soạn hồ sơ lớp tương ứng; lịch sử phải giữ người soạn/thao tác trước đó, không trả lớp về mẫu nguồn hoặc làm mất hồ sơ đã ban hành (EDU-63).
- **FR-022**: Giáo viên chủ nhiệm MUST được sửa nhánh, kế hoạch chủ đề và giáo án của phần chưa đến ngày dạy. Khi sửa hồ sơ đã ban hành, server MUST chỉ đưa hồ sơ chủ đề chứa phần sửa về Nháp để gửi/duyệt lại; chủ đề khác giữ nguyên. Server MUST từ chối sửa phần đã đến ngày dạy (EDU-64).
- **FR-023**: Giáo viên chủ nhiệm MUST có thể soạn toàn bộ nhánh, kế hoạch chủ đề và giáo án của năm sau khi tổ APPROVED. Một chủ đề kéo qua nhiều tháng MUST giữ một hồ sơ duyệt duy nhất, không tách theo tháng (EDU-64).
- **FR-024**: Giáo viên chủ nhiệm MUST chủ động gửi hồ sơ kế hoạch chủ đề. Hệ thống MUST nhắc tổ trưởng từ 7 ngày trước ngày bắt đầu chủ đề; lời nhắc không thay đổi trạng thái hồ sơ và không cấm tổ trưởng duyệt sớm (EDU-64).

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
- **SC-007**: 100% ca sửa chủ đề lớn, annual, mẫu nguồn, lớp khác hoặc giáo viên không chủ nhiệm bị từ chối; 100% ca chủ nhiệm sửa nhánh lớp mình giữ nguyên mẫu PHT/tổ và nhánh lớp khác.
- **SC-008**: Reset local xóa 100% bản ghi nghiệp vụ giáo dục/hồ sơ lớp của bộ demo, đồng thời giữ 100% danh mục, cấu trúc trường/lớp, tài khoản, phân công và cấu hình nền đã kiểm trước reset.
- **SC-009**: 100% ca lọc trang chủ đề nhánh chỉ trả dữ liệu đúng scope PHT/tổ trưởng; giáo viên chỉ thấy lớp được phân công và không có kết quả lớp khác.

## Assumptions

- Nguồn: user ngày 09/11/2026; quyết định cuối về trang soạn trực tiếp và tách bảng thay các ý modal/tự xuất hiện trước đó. Ba câu trả lời đã chốt về thời điểm ban hành, giữ bản tổ cũ, ẩn phiên bản tổ vẫn áp dụng.
- Cấu hình mặc định năm PHT → HT, tổ tổ trưởng → PHT; vẫn cấu hình được theo EDU-54.
- “Tên khác nhau” nghĩa là trường tên độc lập, chưa yêu cầu cấm hai bản có chuỗi tên trùng nhau.
- Phần lưu trữ vật lý là ràng buộc user yêu cầu, không phải lựa chọn công nghệ mới; tên bảng/cột, bảng con, thiết kế chuyển đổi và contract thuộc phase plan.
- Reset demo chỉ áp dụng database local do user chỉ định ngày 09/11/2026; không là migration, không áp dụng staging/production và không xóa danh mục hay dữ liệu định danh nền.
- Một lớp có tối đa hai giáo viên và đúng một giáo viên chủ nhiệm theo phân công hiệu lực; quyền ghi nhánh dùng cờ chủ nhiệm đó, giáo viên còn lại chỉ đọc.
- Đổi chủ nhiệm chuyển quyền soạn bản nhánh lớp hiện có sang chủ nhiệm mới; không tạo lại bản nguồn và không thay lịch sử cũ.
- “Chưa đến ngày dạy” dùng ngày bắt đầu dạy của phần nhánh/chủ đề/giáo án; phần có ngày bắt đầu bằng hoặc trước ngày hiện tại là đã đến ngày dạy.
- Giáo viên có thể soạn trước cả năm; mốc 7 ngày trước hạn chỉ điều phối thời điểm duyệt, không buộc tách hồ sơ theo tháng.
- [Domain spec](domain-spec.md), EDU-62 và [giải thích DB hiện tại/đích](database-context.md) là nguồn kèm theo. Feature hoàn thành gồm BE + FE; task FE trong tài liệu không phải quyền sửa repo FE. Lượt fix này chỉ sửa artifact. Làn L, cần plan/API/DDL và quyền client theo phạm vi trước triển khai.

## Clarifications

### Session 2026-11-09

- Q1: Tổ đang nháp/chờ duyệt gặp năm nguồn mới hơn đã ban hành? → User chọn chuyển sang nguồn mới nhất, giữ phần đã nhập và rà soát lại; bản đang chờ phải về nháp.
- Q2: Chủ đề đã kết thúc có bỏ qua đánh đỏ không? → User chọn có, giữ logic chủ đề nhánh/giáo án hiện có; so với nguồn năm tổ đang dùng trước lần chuyển.
- Q3: Phạm vi xem của tổ trưởng? → User cập nhật: tổ trưởng chỉ xem kế hoạch tổ của đúng nhóm tuổi được phân công hiệu lực; annual APPROVED cùng tuổi vẫn xem làm nguồn (FR-015, EDU-60). Phạm vi nguồn nhánh của GV theo phân công lớp/nhóm tuổi được quy định riêng tại FR-018; HT/PHT phân công hiệu lực giữ scope mọi tuổi.
- Q4: Trang chủ đề/chủ đề nhánh và duyệt? → GV không xem annual; cả hai GV xem dữ liệu đúng lớp, chỉ chủ nhiệm lưu nhánh riêng theo chủ nhiệm/lớp/tổ. Chủ đề lớn xám/chỉ đọc. PHT lọc mọi lớp/tuổi, tổ trưởng lọc lớp đúng tuổi, GV không lọc đa lớp. Nhánh không duyệt riêng; nhánh đi cùng hồ sơ kế hoạch chủ đề và sau ban hành chỉ sửa qua hồ sơ bị trả lại/phiên mới (FR-018/020, EDU-63/64).
- Q5: Khi đổi chủ nhiệm trong năm học? → Chủ nhiệm mới tiếp quản bản nhánh/hồ sơ lớp đang dùng; lịch sử người soạn cũ giữ nguyên, không trả về mẫu nguồn (FR-021, EDU-63).
- Q6: Hồ sơ đã ban hành nhưng chưa đến ngày dạy có sửa được không? → Có; khi sửa, phần hồ sơ liên quan về Nháp và phải gửi duyệt lại. Phần đã đến ngày dạy không sửa (FR-022, EDU-64).
- Q7: Sửa một phần chưa đến ngày dạy ảnh hưởng hồ sơ nào? → Chỉ hồ sơ kế hoạch chủ đề có phần sửa về Nháp/gửi duyệt lại; chủ đề khác giữ nguyên (FR-022, EDU-64).
- Q8: Chủ đề kéo qua nhiều tháng có tách hồ sơ không? → Không; một chủ đề một hồ sơ duyệt, giáo viên có thể soạn cả năm ngay từ đầu năm (FR-023, EDU-64).
- Q9: Hồ sơ soạn trước cả năm đến tổ trưởng duyệt thế nào? → Giáo viên chủ động gửi; hệ thống nhắc tổ trưởng từ 7 ngày trước ngày bắt đầu chủ đề, vẫn duyệt sớm được (FR-024, EDU-64).
- Đây là quyết định nghiệp vụ đích, chưa phải bằng chứng code đã có hoặc phê duyệt plan/migration.
