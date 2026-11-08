# Feature Specification: Tách Kế hoạch năm học và Kế hoạch tổ

**Feature Branch**: `feature/G94-326-BE-FEAT-09-school-year-goals-backup` (nhánh hiện tại, không tạo/chuyển nhánh)

**Created**: 2026-11-07

**Status**: Clarified — delta ngày 07/11/2026 đã chốt Q4=A/Q5=A; plan đã cập nhật/review ở trạng thái draft, chưa duyệt triển khai

**Design**: [plan.md](plan.md) và [plan làn L](../../docs/plans/active/2026-11-07-school-year-team-plans.md) đã cập nhật/review lại theo Q4=A/Q5=A ngày 07/11/2026, vẫn draft chờ user duyệt triển khai. Không tạo feature mới hoặc coi review thiết kế là quyền code/FE/API/migration.

**Input**: User yêu cầu tạo trang “Kế hoạch năm học” clone trang “Kế hoạch tổ”, đặt phía trên; tạo thông tin/chủ đề/mục tiêu/nội dung và nhánh PHT gợi ý cho GV; bỏ liên kết tick chủ đề ở trang năm; năm do PHT tạo/HT duyệt, tổ do tổ trưởng tạo/PHT duyệt; GV nhận kế hoạch tổ; đổi tên phần chủ đề; GV chỉnh lịch với nhánh 1–2 tuần, chủ đề lớn 2–4 tuần và có cấu hình thời lượng. Delta mới 07/11: GV có thể lấy nhánh PHT tạo hoặc sửa tên; chủ đề lớn được đổi thứ tự dạy nhưng không sửa thông tin nguồn.

Nguồn: user 07/11/2026, yêu cầu ban đầu và phản hồi “1. A, với chủ đề nhánh thì phó hiệu trưởng có quyền tạo và giáo viên có quyền tạo thêm; 2. A; 3. A”; [domain-spec.md](domain-spec.md), EDU-55/56. Các lựa chọn đã được user chốt cho đặc tả. Rule giữ mức PROPOSED do chưa có nguồn team/trường xác nhận; không dùng trạng thái này để phủ nhận quyết định đã trả lời hoặc hỏi lại. Chưa triển khai nên không mô tả hành vi mới như đang chạy.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Xây dựng định hướng năm học (Priority: P1)

Phó HT tạo kế hoạch năm học trên trang riêng và gửi HT duyệt, để định hướng giáo dục được kiểm tra ở cấp trường.

**Why this priority**: Tạo cấp định hướng mới và phân biệt với công việc của tổ.

**Independent Test**: Dùng hồ sơ năm riêng, soạn/lưu/mở lại/gửi/duyệt và kiểm quyền cùng bố cục.

**Acceptance Scenarios**:

1. **Given** người có quyền xem kế hoạch, **When** mở điều hướng giáo dục, **Then** thấy “Kế hoạch năm học” phía trên “Kế hoạch tổ”.
2. **Given** PHT có quyền soạn, **When** nhập thông tin, chủ đề/sự kiện, mục tiêu/nội dung và lưu, **Then** mở lại thấy đúng dữ liệu và tên phần “Dự kiến chủ đề / sự kiện năm học”.
3. **Given** bản năm hợp lệ, **When** PHT gửi và HT đúng phạm vi duyệt cuối, **Then** bản năm được ban hành theo vòng duyệt; người tham gia soạn không tự duyệt.
4. **Given** trang năm đang soạn, **When** xem phần 3, **Then** chỉ có bốn cột dữ liệu “Nhóm kết quả mong đợi · STT · Mục tiêu giáo dục · Nội dung giáo dục”, không có ô tick hay dãy chủ đề viết tắt để gán mục tiêu/nội dung vào chủ đề.
5. **Given** trang năm, **When** xem phần 2, **Then** không còn cột mục tiêu giáo dục liên kết/tự tick theo chủ đề; dữ liệu chủ đề/sự kiện vẫn nhập được.

### User Story 2 - Triển khai bằng kế hoạch tổ (Priority: P1)

Tổ trưởng lập kế hoạch tổ để PHT duyệt và giáo viên lấy đó làm căn cứ tạo nhánh/giáo án.

**Why this priority**: Giáo viên cần nhận đúng nguồn triển khai thay vì dùng nhầm cấp định hướng.

**Independent Test**: Chuẩn bị nguồn năm hợp lệ, lập kế hoạch tổ và kiểm nguồn xuất hiện trong luồng của giáo viên.

**Acceptance Scenarios**:

1. **Given** tổ trưởng có phân công hiệu lực, **When** tạo kế hoạch tổ trong phạm vi của mình, **Then** hồ sơ thuộc cấp tổ và chuyển PHT duyệt khi gửi.
2. **Given** kế hoạch tổ đã ban hành, **When** giáo viên lập nhánh/giáo án, **Then** nguồn trực tiếp là kế hoạch tổ đúng nhóm tuổi/lớp; kế hoạch tổ mới gửi nhưng chưa duyệt không trở thành nguồn triển khai.
3. **Given** nhánh đã được PHT đặt trước ở cấp năm và kế thừa xuống tổ, **When** giáo viên lấy nhánh cho lớp, **Then** được giữ hoặc sửa tên nhánh ở bản lớp, chỉnh lịch và tạo thêm nhánh hợp lệ; nhánh mẫu trên bản nguồn không đổi. Giáo viên được chọn lấy hoặc không lấy từng mẫu, không bắt buộc lấy đủ; bỏ chọn mẫu không làm đổi danh sách mẫu nguồn.
4. **Given** người ngoài tổ/lớp hoặc không đúng lượt, **When** tạo, sửa, duyệt hay dùng nguồn ngoài quyền, **Then** bị từ chối và hồ sơ không đổi.
5. **Given** kế hoạch năm theo nhóm tuổi đã ban hành, **When** tổ trưởng tạo kế hoạch tổ cho nhóm tuổi đó, **Then** lấy nội dung và nhánh gợi ý từ kế hoạch năm; phân biệt bản mẫu nguồn với bản nhánh GV lấy/sửa tên hoặc tạo thêm ở lớp.
6. **Given** hồ sơ cũ trước khi tách cấp, **When** mở lại sau nâng cấp, **Then** hồ sơ thuộc cấp tổ, giữ nội dung, dấu tích, lịch sử, phiên bản và tài liệu lớp liên quan; không bắt tạo ngược kế hoạch năm mới để đọc hồ sơ cũ.
7. **Given** hai cấp đã có cấu hình khởi đầu, **When** HT đổi vai trò soạn/duyệt hợp lệ của một cấp, **Then** chỉ cấu hình cấp đó thay đổi và giữ các quy tắc EDU-54 về chống tự duyệt, bản đang chờ và snapshot đã ban hành.
8. **Given** cây mục tiêu/nội dung kế thừa từ năm đã ban hành, **When** tổ trưởng soạn kế hoạch tổ, **Then** giữ cách tick từng nội dung vào chủ đề như giao diện cũ, mục tiêu tương ứng đi theo nội dung đã tick; không được thêm mới, sửa hoặc xóa mục tiêu/nội dung nguồn trong bản tổ. Thao tác ghi trái quy tắc bị từ chối và giữ nguyên dữ liệu, kể cả qua nhập file.
9. **Given** một mục tiêu có nhiều nội dung, **When** tick/bỏ tick một nội dung vào một chủ đề, **Then** chỉ liên kết nội dung đó đổi theo logic cũ, không tự tích toàn bộ nội dung của mục tiêu; câu chữ/cây nguồn và các liên kết khác giữ nguyên, lưu/mở lại giữ đúng lựa chọn.

### User Story 3 - Điều chỉnh và cấu hình thời lượng (Priority: P2)

Giáo viên điều chỉnh lịch phù hợp lớp; trường quản lý giới hạn thời lượng thay vì cố định một nhánh một tuần.

**Why this priority**: Hỗ trợ lịch linh hoạt có giới hạn kiểm chứng được.

**Independent Test**: Dùng cấu hình thời lượng và hồ sơ nháp riêng để thử các giá trị biên, lưu/mở lại và thử người không có quyền cấu hình.

**Acceptance Scenarios**:

1. **Given** giới hạn nhánh 1–2 tuần và lớn 2–4 tuần, **When** nhập đúng các mốc biên tính theo tuần học, **Then** lịch được chấp nhận; vượt giới hạn thì báo nguyên nhân. Tuần nghỉ trọn Thứ Hai–Thứ Sáu không tính, tuần nghỉ lẻ ngày vẫn tính.
2. **Given** HT có phân công hiệu lực, **When** lưu giới hạn hợp lệ cho cả chủ đề lớn và nhánh rồi mở lại, **Then** giới hạn giữ đúng và áp dụng cho hồ sơ tạo mới sau thay đổi; hồ sơ đã tồn tại giữ giới hạn trước đó.
3. **Given** người không có quyền cấu hình hoặc giới hạn nhỏ nhất lớn hơn lớn nhất, **When** lưu, **Then** bị từ chối và cấu hình trước đó giữ nguyên.
4. **Given** một nhánh kéo dài hai tuần, **When** lập giáo án, **Then** các tuần/ngày của cả hai tuần được thể hiện, không mất tuần do giả định một nhánh một tuần.
5. **Given** giáo viên soạn lịch lớp, **When** đổi lịch chủ đề lớn/nhánh, **Then** lịch bắt đầu Thứ Hai, kết thúc Thứ Sáu; nhánh nằm trong khoảng chủ đề cha, chủ đề lớn trong năm học và các chủ đề cùng cấp không trùng nhau theo EDU-39; lịch sai bị báo rõ.
6. **Given** cấu hình nhánh 1–2 tuần, **When** giáo viên thử nhánh 1, 2, 3 tuần học, **Then** lần lượt chấp nhận, chấp nhận, từ chối; với chủ đề lớn 1, 2, 4, 5 tuần học thì lần lượt từ chối, chấp nhận, chấp nhận, từ chối.
7. **Given** nhánh có hai tuần học ngăn bởi một tuần nghỉ toàn bộ, **When** tính thời lượng, **Then** nhánh được tính là hai tuần học; các ngày nghỉ lẻ không bắt buộc có hoạt động theo EDU-41.
8. **Given** lớp có các chủ đề lớn từ kế hoạch tổ, **When** giáo viên chọn đổi thứ tự dạy, **Then** chuyển cả khối chủ đề kèm các nhánh của lớp sang thứ tự mới, giữ số tuần học của từng chủ đề/nhánh và dời ngày dạy tương ứng; tên, nội dung và liên kết mục tiêu/nhánh vẫn thuộc đúng chủ đề, không đổi bản nguồn hoặc lớp khác.

9. **Given** chủ đề A dài 2 tuần và B dài 4 tuần, mỗi chủ đề có nhánh riêng, **When** giáo viên đổi B dạy trước A, **Then** B vẫn 4 tuần và A vẫn 2 tuần; các nhánh chuyển theo đúng cha với số tuần riêng giữ nguyên, ngày được dời theo thứ tự mới; lưu/mở lại giữ đúng lịch, nhánh đổi tên/bổ sung không bị mất.

### Edge Cases

- Một người có cả vai trò soạn và duyệt: giữ chống tự duyệt và nguyên tắc EDU-54.
- Nhánh PHT gợi ý được đổi tên tại lớp, không sửa nhánh mẫu nguồn; nguồn gốc của nhánh không bị mất chỉ vì tên lớp khác tên mẫu. Giáo viên được không lấy/bỏ chọn mẫu; lịch lớp vẫn phải đủ nhánh/nội dung theo điều kiện gửi hiện có, không bắt buộc đủ mọi mẫu PHT.
- Đổi thứ tự chủ đề lớn là đổi lịch dạy của lớp, không chỉ sắp xếp hiển thị và không sửa tên/nội dung chủ đề nguồn. Khi đổi thứ tự, chuyển cả khối kèm nhánh, giữ số tuần học từng chủ đề/nhánh và dời ngày theo vị trí mới; không tự di chuyển giáo án/học liệu đã ban hành.
- Chủ đề qua tuần nghỉ trọn tuần hoặc có ngày nghỉ lẻ: chỉ bỏ tuần nghỉ trọn Thứ Hai–Thứ Sáu khỏi số tuần học, giữ EDU-40/41.
- Đổi giới hạn khi đã có hồ sơ nháp/đang duyệt/ban hành: không hồi tố; hồ sơ đã tồn tại giữ giới hạn đã áp dụng, chỉ hồ sơ tạo mới nhận cấu hình mới.
- Bản năm có phiên bản mới: giữ nguyên nguồn/lịch sử của hồ sơ tổ và lớp đã ban hành; kế thừa quy tắc phiên bản hiện có, không tự đồng bộ ngược nội dung mới vào hồ sơ cũ.
- Hồ sơ cũ có dấu tích, viết tắt, nhánh theo từng tuần: giữ ở cấp tổ cùng liên kết tài liệu lớp, không xóa dữ liệu để khớp trang năm mới.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Hệ thống MUST có hai trang phân biệt “Kế hoạch năm học” và “Kế hoạch tổ”, trang năm nằm trên trang tổ trong điều hướng.
- **FR-002**: Trang năm MUST cho tạo thông tin chung, chủ đề/sự kiện, mục tiêu và nội dung giáo dục, dựa trên cấu trúc trang tổ hiện có.
- **FR-003**: Phần chủ đề/sự kiện của trang năm MUST có tên “Dự kiến chủ đề / sự kiện năm học”.
- **FR-004**: Phần 2 trang năm MUST thay cột mục tiêu giáo dục liên kết/tự tick bằng cột “Chủ đề nhánh / dự án” để PHT nhập nhiều nhánh trực tiếp theo chủ đề; Enter chuyển sang dòng nhánh mới như các phần hiện có. Không có khối nhập nhánh riêng trùng lặp; trang tổ giữ nguyên cột mục tiêu map. Mẫu có thể chỉ có tên, GV chọn lịch hợp lệ khi lấy về lớp.
- **FR-005**: Phần 3 trang năm MUST có đúng bốn cột dữ liệu “Nhóm kết quả mong đợi · STT · Mục tiêu giáo dục · Nội dung giáo dục”, không yêu cầu tick chủ đề và không hiển thị dãy viết tắt để tick; trang tổ giữ cơ chế gán nội dung vào chủ đề hiện có.
- **FR-006**: Luồng khởi đầu của kế hoạch năm MUST là PHT tạo → HT duyệt; kế hoạch tổ MUST là tổ trưởng tạo → PHT duyệt. Đây là mặc định riêng từng loại, HT được cấu hình lại theo EDU-54.
- **FR-007**: Mỗi cấp MUST phân biệt Nháp/Đang duyệt/Ban hành, giữ kiểm tra phạm vi và chống tự duyệt theo EDU-54.
- **FR-008**: Giáo viên MUST lấy kế hoạch tổ đã ban hành làm nguồn trực tiếp để tạo chủ đề nhánh và giáo án; chỉ gửi duyệt chưa đủ điều kiện cung cấp làm nguồn triển khai.
- **FR-009**: PHT MUST được tạo trước chủ đề nhánh ở kế hoạch năm; tổ kế thừa mẫu này. Giáo viên MUST được lấy mẫu cho lớp, giữ hoặc sửa tên nhánh trên bản lớp, tạo thêm nhánh và chỉnh lịch trong giới hạn FR-010/011/015; tên mẫu/bản nguồn không đổi. Giáo viên MUST được chọn lấy hoặc không lấy từng mẫu, không bắt buộc lấy đủ các nhánh PHT; vẫn phải hoàn thiện lịch lớp hợp lệ trước gửi. Không mở quyền sửa bản đã ban hành hoặc bỏ vòng duyệt hiện có.
- **FR-010**: Giáo viên MUST được chỉnh thời gian chủ đề lớn và nhánh cho lớp trong giới hạn; yêu cầu ban đầu nhánh tối thiểu 1/tối đa 2 tuần, chủ đề lớn tối thiểu 2/tối đa 4 tuần.
- **FR-011**: Chỉ HT có phân công hiệu lực MUST được cấu hình tối thiểu/tối đa số tuần học của cả chủ đề lớn và nhánh, mặc định lần lượt 2–4 và 1–2 tuần. Giới hạn là số tuần nguyên dương, nhỏ nhất không vượt lớn nhất; lưu/mở lại giữ đúng giá trị. Cấu hình mới chỉ áp dụng hồ sơ tạo mới; hồ sơ đã tồn tại, kể cả nháp/đang duyệt/ban hành, không bị đổi giới hạn hồi tố.
- **FR-012**: Một chủ đề nhánh hai tuần MUST hỗ trợ đầy đủ giáo án các tuần/ngày tương ứng; không gộp mất tuần hoặc hoạt động.
- **FR-013**: Kế hoạch cũ MUST được giữ ở cấp tổ, bảo toàn phiên bản, trạng thái, lịch sử, nội dung/dấu tích, lịch và liên kết tài liệu lớp. Không tự tạo nội dung kế hoạch năm mới hoặc buộc hồ sơ cũ có nguồn năm mới để đọc lại. Hồ sơ đã ban hành không tự thay đổi khi nguồn có phiên bản mới.
- **FR-014**: Kế hoạch năm MUST theo từng (năm học, nhóm tuổi), áp dụng toàn trường trong nhóm tuổi đó; kế hoạch tổ đúng nhóm tuổi MUST kế thừa nội dung và nhánh định sẵn của kế hoạch năm đã ban hành. Giữ nguyên quy tắc một chuỗi phiên bản và tối đa một phiên bản chưa ban hành của mỗi loại trong phạm vi đó theo EDU-01/25.
- **FR-015**: Lịch chủ đề/nhánh MUST tính bằng tuần học, bắt đầu Thứ Hai và kết thúc Thứ Sáu; bỏ tuần nghỉ trọn tuần, vẫn tính tuần có nghỉ lẻ ngày theo EDU-40/41. Nhánh MUST nằm trong khoảng chủ đề cha; chủ đề lớn trong năm học; các chủ đề cùng cấp không trùng theo EDU-39. Giáo viên chỉ chỉnh lịch tài liệu lớp được phép soạn, không đổi lịch nguồn đã ban hành.
- **FR-016**: Tổ trưởng MUST phân bổ bằng cách tick/bỏ tick từng nội dung giáo dục vào chủ đề như giao diện và logic cũ; mục tiêu tương ứng đi theo nội dung đã tick, không tự chọn toàn bộ nội dung của mục tiêu. Cây mục tiêu/nội dung kế thừa là cố định: không được thêm mới, sửa hoặc xóa mục tiêu/nội dung nguồn trong bản tổ. Hệ thống MUST giữ bảo vệ này khi ghi và khi nhập file, không chỉ khóa ô trên màn hình. Giữ logic cũ về liên kết, lưu/mở lại, kiểm tra nội dung trống và phiên bản ngoài các thay đổi đã nêu ở feature này.
- **FR-017**: Giáo viên MUST được đổi thứ tự dạy các chủ đề lớn trong lịch của lớp; không sửa tên/nội dung chủ đề lớn hoặc bản nguồn. Liên kết mục tiêu/nội dung/nhánh đi theo đúng chủ đề, không gán lại bằng tên/ô hiển thị. Khi đổi thứ tự MUST chuyển cả khối chủ đề kèm các nhánh đã lập/chọn của lớp, giữ số tuần học của từng chủ đề và nhánh, dời ngày theo thứ tự mới; không hoán đổi vào khoảng ngày cố định làm thay thời lượng. Lịch mới vẫn theo cấu hình, trọn tuần học, nhánh trong cha, không trùng và trong năm học. Chỉ thao tác trên hồ sơ được phép soạn, không dời/sửa trực tiếp hồ sơ đã ban hành.

- **FR-018**: PHT MUST NOT xóa kế hoạch tổ, kể cả kế hoạch nháp và khi được cấu hình là người soạn. Chặn ở UI/capability và server; các quyền khác, kế hoạch năm học và chức năng khác giữ nguyên. Nguồn: user 07/11/2026 xác nhận “kế hoạch tổ, còn lại giữ nguyên”.
- **FR-019**: Giữ UI bảng chủ đề nhánh ban đầu, không thêm bảng nhập nhánh trùng. Trong kế hoạch năm học/kế hoạch tổ/chủ đề nhánh, ô và cột cố định MUST màu xám và không sửa; ô được nhập/chọn MUST màu trắng. Cột nhánh/dự án annual là ô nhập trắng; TEAM giữ cột map mục tiêu. Nguồn: user duyệt FE và bổ sung ngày 07/11/2026.

### Key Entities *(include if feature involves data)*

- **Kế hoạch năm học**: định hướng theo năm học/nhóm tuổi, người soạn/duyệt, trạng thái, chủ đề, mục tiêu/nội dung, nhánh PHT định sẵn nếu có.
- **Kế hoạch tổ**: kế hoạch triển khai thuộc tổ/nhóm tuổi, liên hệ nguồn năm, người soạn/duyệt, trạng thái; giáo viên lấy làm nguồn.
- **Chủ đề / chủ đề nhánh**: nội dung, lịch, quan hệ cha–con và nguồn ràng buộc; một nhánh có thể bao phủ nhiều tuần.
- **Cấu hình thời lượng**: giới hạn tuần học tối thiểu/tối đa cho cả chủ đề lớn và nhánh của trường, HT quản lý; giá trị áp dụng được giữ theo hồ sơ khi tạo để không hồi tố.
- **Tài liệu lớp**: nhánh, kế hoạch chủ đề, giáo án tuần/ngày có nguồn từ tổ và lịch sử riêng.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% ca nghiệm thu hai cấp phân biệt được trang, loại kế hoạch, người tạo và người duyệt; không gửi nhầm cấp.
- **SC-002**: 100% ca tạo nhánh/giáo án mới dùng đúng kế hoạch tổ đã ban hành làm nguồn; lấy/sửa tên nhánh mẫu và thêm nhánh hợp lệ không làm đổi mẫu nguồn hoặc mất liên hệ chủ đề.
- **SC-003**: 100% ca biên thời lượng, kể cả tuần nghỉ và cấu hình đổi, chấp nhận giá trị trong giới hạn áp dụng cho hồ sơ và từ chối giá trị ngoài giới hạn với lý do rõ ràng.
- **SC-004**: 100% ca sai vai trò/phạm vi hoặc tự duyệt bị từ chối; lưu/mở lại không mất nội dung hoặc thay lịch sử đã ban hành.
- **SC-005**: Người nghiệm thu hoàn tất được một chu trình năm → tổ → nhánh → giáo án hai tuần mà không phải tạo bản sao nhánh chỉ để chứa tuần thứ hai.
- **SC-006**: 100% ca đổi thứ tự chủ đề lớn giữ số tuần học từng chủ đề/nhánh, nguyên tên/nội dung chủ đề lớn và liên kết mục tiêu/nhánh, chỉ dời ngày dạy ở lớp tương ứng; lưu/mở lại không mất thứ tự và không đổi nguồn/lớp khác.

## Assumptions

- Tái sử dụng nghiệp vụ lưu tay, phân công hiệu lực, lịch sử và phiên bản hiện có ngoài phần yêu cầu đổi; không tự mở quyền sửa bản ban hành.
- Cấu hình áp dụng cho một trường đang vận hành; không thêm quản trị nhiều trường.
- Đổi phần 2/3 áp dụng trang năm mới; trang tổ dùng cơ chế hiện có ngoài các thay đổi nguồn, vai trò và lịch đã nêu.
- Phụ thuộc nghiệp vụ phiên bản, lưu tay và duyệt hiện có; kế hoạch cũ cấp tổ là trường hợp chuyển tiếp không có nguồn năm mới. Cách lưu quan hệ/giá trị cấu hình và chuyển dữ liệu vật lý thuộc bước plan/data model.
- User nhấn mạnh giữ logic cũ, chỉ cố định thông tin nguồn (07/11/2026): không tự thêm cơ chế phân bổ hay đồng bộ phiên bản mới ngoài các thay đổi cấp kế hoạch/nhánh/lịch đã chốt; áp dụng các quy tắc phiên bản hiện hành EDU-25/26. Phương án lưu quan hệ hai cấp và chuyển dữ liệu cần đối chiếu ở bước plan.
- Tác động Web cần ghi nhận và xin quyền riêng trước khi sửa repo FE; không có quyền sửa FE/APP trong lượt này. APP chưa có bằng chứng consumer mới.

## Clarifications

### Session 2026-11-07

- Q1 → A (lịch sử trước delta): năm theo nhóm tuổi, tổ lấy nội dung từ năm; ban đầu nhánh định sẵn khóa tên nhưng GV chỉnh lịch và tạo thêm. Phần khóa tên nhánh đã bị thay bằng quyền GV sửa tên tại lớp theo cập nhật mới 07/11/2026; các quyết định khác giữ. PHT được tạo nhánh cấp năm; hai luồng duyệt mặc định cấu hình được, GV nhận tổ sau ban hành, kế hoạch cũ cấp tổ.
- Q2 → A: phần 2 bỏ cột mục tiêu liên kết/tự tick; phần 3 bốn cột “Nhóm kết quả mong đợi · STT · Mục tiêu giáo dục · Nội dung giáo dục”, không tick/viết tắt chủ đề.
- Q3 → A: chỉ HT cấu hình cả hai loại; đếm tuần học, bỏ tuần nghỉ trọn tuần, giữ Thứ Hai–Thứ Sáu; nhánh trong chủ đề cha; cấu hình đổi chỉ áp dụng hồ sơ tạo mới.
- Nguồn là câu trả lời trực tiếp của user ngày 07/11/2026. Không còn câu hỏi Q1–Q3 chặn lập plan. Hoàn thành specify không thay thế phê duyệt plan làn L hoặc quyền sửa FE/APP.
- Q: Khi lập Kế hoạch tổ từ Kế hoạch năm học, tổ trưởng được thay đổi mục tiêu và nội dung giáo dục đến mức nào? → A: User chọn A và nói “không được sửa chỉ được thêm mục tiêu vào chủ đề”; tổ trưởng chỉ phân bổ mục tiêu nguồn vào chủ đề, không biên soạn lại mục tiêu/nội dung (07/11/2026, câu 1 của vòng clarify).
- Q: Khi đưa một mục tiêu vào chủ đề, có đưa theo toàn bộ nội dung giáo dục của mục tiêu đó không? → A: User yêu cầu “tick như giao diện cũ đi theo mục tiêu đó”, sau đó nhấn mạnh “logic như cũ chỉ làm các thông tin nó cố định là được”; giữ tick từng nội dung vào chủ đề, mục tiêu tương ứng đi theo, không tự tích cả mục tiêu; chỉ khóa biên soạn cây mục tiêu/nội dung nguồn (07/11/2026, câu 2 của vòng clarify).
- Delta mới 07/11/2026, nguồn user gọi lại speckit-specify: “khi phó hiệu trưởng tạo chủ đề nhánh thì giáo viên có thể lấy hoặc vẫn sửa tên bình thường”, “chủ đề to ... lựa chọn đổi cho nhau để dạy chủ đề nào trước nhưng không cho sửa”. Thay quyết định cũ khóa tên nhánh trên lớp; quyền biên soạn mục tiêu/nội dung nguồn vẫn cố định. Q4=A và Q5=A đã được trả lời ở vòng clarify tiếp theo; quyền lấy mẫu tùy chọn/sửa tên và chuyển cả khối giữ thời lượng đã rõ, không còn câu hỏi chặn đặc tả delta.

- Q: Giáo viên có được chọn không lấy một số nhánh PHT tạo sẵn không? → A: A — được chọn lấy hoặc không lấy từng mẫu, được sửa tên bản lớp và tạo thêm; mẫu PHT là tham khảo, không bắt buộc lấy đủ (user, 07/11/2026, Q4 của delta).

- Q: Khi đổi thứ tự hai chủ đề lớn, bạn muốn giữ số tuần của từng chủ đề hay hoán đổi các khoảng ngày có sẵn? → A: A — chuyển cả chủ đề kèm các nhánh, giữ số tuần từng chủ đề/nhánh, dời ngày dạy theo thứ tự mới; tên/nội dung chủ đề lớn và nguồn không đổi (user, 07/11/2026, Q5 của delta).

- Delta FE review user 07/11/2026: nhánh/dự án nhập ngay cột phần 2 của năm thay cột map, Enter thêm dòng; tổ giữ cột map. Cấm PHT xóa bản kế hoạch tổ, không mở rộng lệnh cấm ra chức năng khác.

### User review UI 07/11/2026 (quyết định mới nhất)

- TEAM giữ UI Kế hoạch năm học cũ theo FE commit ba5c6ec; chỉ lúc chỉnh sửa tô xám phần cố định. Giữ lĩnh vực xanh, lĩnh vực con vàng; xám các dòng kết quả/mục tiêu/nội dung và cột tên chủ đề/ngày lễ hội/số tuần. Thông tin chung TEAM chỉnh sửa xám/khóa; cả annual/TEAM không highlight khi hover thông tin chung.
- Annual: Lấy kế hoạch năm và chỉ liệt kê SCHOOL_YEAR làm nguồn nhập, TEAM giữ nguồn TEAM.
- Nhánh mẫu được chọn từ/đến ngày trong khoảng chủ đề lớn; class branch cũng giới hạn theo parent; không thêm bảng nhánh thứ hai, Enter vẫn thêm nhánh. Cho chọn ngày không đồng nghĩa bắt buộc ngày cho mẫu name-only.
- Mọi nút gửi và xác nhận gửi trong module Kế hoạch giáo dục chỉ ghi Gửi duyệt, kể cả khi lưu trước gửi; không đổi workflow.
- Lượt chỉnh UI kiểm FE, không test lại BE theo user.

### Delta đã duyệt 07/11/2026 — nhãn và tách UI hai cấp

FR-019 / AC-13 (EDU-58): Người được sửa SCHOOL_YEAR đổi displayLabel của goal và kéo tăng số theo mẫu; nhãn lưu qua tải lại, copy/version và annual→TEAM. TEAM chỉ xem nhãn. ID và goalNo liên tục theo EDU-09 giữ nguyên. Null/omitted giữ nhãn; blank clear; max100. Legacy fallback MTn. Chuỗi kéo tăng cụm số cuối/giữ padding, không số thì copy, hỗ trợ bàn phím/Escape. Snapshot đã ban hành bất biến.
FR-020: Hai trang dùng thứ tự info → goals → themes; TEAM khóa các bảng nguồn, bỏ thêm/xóa/sắp xếp, giữ tick nội dung; xám đồng nhất STT và subdomain vàng. Lịch nhánh tái sử dụng DatePickerCell của chủ đề lớn.
SC-007: lưu/tải nhãn và giữ ID/số; kéo range không đổi ngoài range; bản tổ/phiên bản giữ nhãn; client cũ không xóa nhãn; legacy vẫn chạy. Nguồn user07/11: đã duyệt plan và BE, duyệt toàn bộ không hỏi lại từng phase.

### FR-021 / AC-14 — lịch nhánh user07/11/2026
Hai endpoint ngày luôn hiện inline dd/mm/yy - dd/mm/yy, placeholder kể cả lúc chưa nhập; mỗi đầu mở lịch độc lập. Nhánh cùng cha có cảnh báo trùng interval inclusive hoặc không bắt đầu Thứ Hai/kết thúc Thứ Sáu; calendar tô ngày sibling đã dùng. Draft vẫn lưu; submit/publication server từ chối khoảng đầy đủ không hợp lệ. Name-only/partial giữ semantics cũ; bounds và duration policy cũ giữ. Không đổi DTO/endpoint/migration/dependency. User đã cho phép thực hiện toàn bộ liên quan không hỏi từng bước.
