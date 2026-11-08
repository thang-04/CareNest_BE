# Feature Specification: Chỉ xem năm đã ban hành và hiển thị đầy đủ mục tiêu trong tổ

**Feature Branch**: `feature/G94-326-BE-FEAT-09-school-year-goals-backup` (nhánh hiện tại; không tạo/chuyển nhánh)

**Created**: 2026-11-08

**Status**: Implemented — user08/11/2026 yêu cầu thực hiện vòng implement/converge tới sạch; evidence và giới hạn ở quickstart/VERIFICATION. Sau review: fullSpotless/native tests và2live Keycloak/server-save E2E đạt; chưa deploy.

**Design**: [plan.md](plan.md), user đã cho phép triển khai BE/FE và ưu tiên UI; code đã kiểm chứng bằng native tests/browser fixture trong phạm vi013.

**Input**: User yêu cầu người tạo kế hoạch tổ chỉ xem được kế hoạch năm học khi đã được duyệt ban hành; phần mục tiêu năm học trong kế hoạch tổ phải liệt kê hết, không scroll trong card. Bổ sung cùng ngày: chỉ xem đúng nhóm tuổi mà tài khoản đang được phân công đảm nhận.

Nguồn: user 08/11/2026; [domain-spec.md](domain-spec.md), EDU-54/55/60. Delta nối tiếp [feature012](../012-school-year-team-plans/spec.md); không thay lịch sử artifact của feature012.

## Clarifications

### Session 2026-11-08

- Q: Nếu một tài khoản vừa được lập kế hoạch tổ, vừa có quyền soạn hoặc duyệt kế hoạch năm học, tài khoản đó có được xem bản năm chưa ban hành để thực hiện nhiệm vụ soạn/duyệt không? → A: A — Giữ quyền soạn/duyệt năm độc lập theo phân công; khi chỉ có quyền lập tổ thì chỉ xem năm đã ban hành đúng nhóm tuổi được giao.

- Q: Phạm vi nhóm tuổi khi tài khoản là HT/PHT và khi là tổ trưởng/GV được áp dụng thế nào? → A: User08/11/2026 chốt HT/PHT xem mọi nhóm tuổi; tổ trưởng/GV chỉ xem đúng nhóm tuổi được phân công. Chỉ thay phạm vi nhóm tuổi, giữ điều kiện trạng thái và Q1=A.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Chỉ xem căn cứ năm học đã ban hành (Priority: P1)

Là người có quyền lập kế hoạch tổ, tôi xem căn cứ năm đã ban hành trong phạm vi tài khoản được phép: HT/PHT có phân công hiệu lực xem mọi nhóm tuổi; tổ trưởng/GV chỉ xem đúng nhóm tuổi được phân công. Quyền năm độc lập để soạn/duyệt giữ theo Q1=A.

**Why this priority**: Tránh đưa định hướng chưa hoàn tất duyệt vào luồng lập tổ và hạn chế truy cập nội dung chưa được công bố.

**Independent Test**: Dùng người chỉ có quyền lập tổ và các bản năm cùng phạm vi ở ba trạng thái; thử danh sách, mở trực tiếp và chọn nguồn. Thử riêng người có quyền soạn/duyệt năm độc lập và bản ngoài phạm vi; dùng hai tài khoản phụ trách hai nhóm tuổi khác nhau, tài khoản chưa được phân công và tài khoản đã hết phân công.

**Acceptance Scenarios**:

1. **Given** người chỉ có quyền lập tổ và có năm Nháp/Đang duyệt/Ban hành cùng phạm vi, **When** mở danh sách năm hoặc chọn nguồn năm khi lập tổ, **Then** chỉ thấy các bản đã ban hành.
2. **Given** người đó biết đường dẫn hoặc định danh bản năm chưa ban hành, **When** mở trực tiếp, **Then** không đọc được nội dung và được thông báo phù hợp.
3. **Given** bản năm mới được duyệt ở bước trung gian hoặc người dùng đổi bộ lọc, **When** xem năm bằng quyền lập tổ, **Then** nội dung chưa ban hành vẫn không được cung cấp.
4. **Given** bản năm vừa được duyệt cuối và ban hành hợp lệ, **When** người lập tổ tải lại danh sách/chọn nguồn, **Then** bản đó hiển thị và mở được nếu đúng phạm vi hiện hành.
5. **Given** chưa có bản năm ban hành đúng phạm vi, **When** mở luồng lập tổ, **Then** thấy trạng thái trống giải thích cần kế hoạch năm đã ban hành và không thể lấy bản chưa ban hành làm nguồn.
6. **Given** người dùng đồng thời có quyền soạn hoặc duyệt năm độc lập hợp lệ, **When** mở năm chưa ban hành để thực hiện nhiệm vụ đó, **Then** được xem bản năm chưa ban hành trong phạm vi quyền soạn/duyệt năm độc lập đó theo cấu hình và phân công; quyền lập tổ không làm mất quyền này (Q1=A).
7. **Given** năm đã ban hành nhưng ngoài phạm vi người dùng, **When** xem danh sách, mở trực tiếp hoặc chọn nguồn, **Then** vẫn không đọc/dùng được nguồn ngoài quyền.

8. **Given** hai tài khoản tổ trưởng/GV chỉ có quyền lập tổ, không có HT/PHT hiệu lực: tài khoản A phụ trách nhóm tuổi X, tài khoản B phụ trách nhóm tuổi Y và cả hai nhóm đều có năm đã ban hành, **When** mỗi tài khoản mở danh sách, chi tiết hoặc chọn nguồn, **Then** mỗi tài khoản chỉ xem/dùng được nguồn thuộc nhóm tuổi mình được phân công; đổi đường dẫn hoặc bộ lọc sang nhóm kia vẫn bị chặn.
9. **Given** tài khoản không có phân công phụ trách nhóm tuổi hiệu lực hoặc đã hết/bị thu hồi phân công, **When** tải danh sách, mở nguồn bằng liên kết cũ hoặc chọn nguồn năm bằng quyền lập tổ, **Then** không được xem/dùng nguồn dựa trên phân công không còn hiệu lực.

10. **Given** tài khoản HT/PHT có phân công hiệu lực và quyền xem hợp lệ theo luồng đang dùng, **When** mở năm đã ban hành thuộc các nhóm tuổi khác nhau, **Then** xem được mọi nhóm tuổi; tổ trưởng/GV không có HT/PHT hiệu lực vẫn bị giới hạn đúng tuổi được giao. Nếu phân công HT/PHT hết hoặc bị thu hồi, không còn được dùng tư cách đó để xem mọi nhóm tuổi. Điều kiện xem năm chưa ban hành giữ theo Q1=A.

### User Story 2 - Đọc toàn bộ mục tiêu nguồn trong kế hoạch tổ (Priority: P2)

Là người làm việc với kế hoạch tổ, tôi cần thấy đầy đủ mục tiêu và nội dung năm học trong luồng đọc của trang để đối chiếu và phân bổ vào chủ đề, không phải cuộn riêng bên trong card.

**Why this priority**: Danh sách đầy đủ giúp đọc xuyên suốt và tránh bỏ sót hàng bị giấu trong vùng cuộn nhỏ.

**Independent Test**: Mở màn soạn và chi tiết tổ có 50 mục tiêu, mỗi mục tiêu ít nhất hai nội dung, nhiều nhóm và một số hàng dài/chưa tick; đối chiếu số hàng, thứ tự, nhãn, dấu tích và cách cuộn trang.

**Acceptance Scenarios**:

1. **Given** tổ có nhiều mục tiêu/nội dung nguồn, **When** mở phần mục tiêu ở màn soạn hoặc chi tiết, **Then** toàn bộ cây mục tiêu/nội dung được liệt kê theo thứ tự và phân nhóm nguồn, bao gồm các hàng chưa tick.
2. **Given** danh sách dài hơn chiều cao màn hình hoặc có nội dung dài, **When** đọc tới cuối danh sách bằng cuộn trang, **Then** đọc được mọi hàng và toàn văn nội dung; card giãn theo danh sách, không có vùng cuộn riêng trong card, không cắt bớt hàng hoặc bắt chuyển trang/xem thêm để đọc đủ.
3. **Given** đang soạn tổ và có nhiều nội dung thuộc cùng mục tiêu, **When** tick một nội dung vào chủ đề rồi lưu/mở lại, **Then** giữ đúng lựa chọn theo logic cũ, không tự tick toàn bộ mục tiêu; nhãn, thứ tự, cây nguồn chỉ đọc và đầy đủ danh sách được bảo toàn.

### Edge Cases

- Duyệt trung gian không đủ điều kiện cho người chỉ có quyền lập tổ xem năm.
- Bản năm đã ban hành có phiên bản nháp mới: không lộ bản nháp qua bản ban hành; cơ chế chọn phiên bản nguồn hiện có được giữ.
- Đổi đường dẫn/bộ lọc hoặc mở liên kết cũ không vượt được giới hạn trạng thái, nhóm tuổi và phân công hiệu lực của tài khoản đăng nhập.
- Tổ trưởng/GV không có HT/PHT hiệu lực, dù cùng vai trò lập tổ nhưng phụ trách hai nhóm tuổi khác nhau, không được xem chéo nguồn năm. HT/PHT hiệu lực được xem mọi nhóm tuổi; tài khoản kiêm nhiệm được xét quyền HT/PHT khi phân công đó còn hiệu lực. Không có phân công hoặc phân công hết hiệu lực thì không có phạm vi xem năm từ quyền lập tổ; khi phân công đổi, lần đọc tiếp theo áp dụng phân công mới.
- Người kiêm nhiệm soạn/duyệt năm vẫn cần quyền năm độc lập để hoàn thành công việc.
- Tổ cũ không có liên kết nguồn năm giữ khả năng đọc theo feature012; không bắt tạo nguồn hồi tố.
- Danh sách không có mục tiêu hiển thị trạng thái trống; không tạo mục tiêu giả.
- Danh sách nhiều nhóm, hàng dài và hàng chưa tick vẫn đầy đủ; không biến yêu cầu liệt kê hết thành quyền sửa nội dung kế thừa.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Tư cách lập kế hoạch tổ chỉ cấp quyền xem năm đã duyệt cuối và ban hành. Phạm vi nhóm tuổi: HT/PHT có phân công hiệu lực được xem mọi nhóm tuổi; tổ trưởng/GV không có tư cách HT/PHT hiệu lực chỉ xem đúng nhóm tuổi được phân công cho chính tài khoản đăng nhập. Vai trò danh nghĩa không có phân công hiệu lực không cấp quyền. Việc xem năm chưa ban hành giữ theo FR-003/Q1=A. Kiểm chứng US1.1/3/4/7/8/9/10.
- **FR-002**: Quy tắc FR-001 MUST áp dụng đồng nhất cho danh sách, chi tiết, mở trực tiếp và chọn nguồn; hệ thống MUST kiểm tra quyền ở phía cung cấp dữ liệu để thao tác đường dẫn/bộ lọc không làm lộ nội dung chưa ban hành hoặc nội dung nhóm tuổi không được giao. Phạm vi MUST được xác định từ tài khoản đăng nhập và phân công hiệu lực, không tin tài khoản/nhóm tuổi do người dùng tự truyền; không có phân công hợp lệ thì không được cấp quyền xem nguồn năm từ tư cách lập tổ. Kiểm chứng US1.1/2/3/7/8/9.
- **FR-003**: Theo Q1=A, hệ thống MUST giữ quyền xem năm chưa ban hành để soạn/duyệt khi tài khoản có quyền năm độc lập hợp lệ theo cấu hình và phân công; đồng thời có quyền lập tổ không làm mất quyền năm này. Giới hạn đã ban hành/đúng nhóm tuổi được giao của FR-001 áp dụng cho quyền xem cấp từ tư cách lập tổ, không mở rộng hoặc thu hẹp phạm vi riêng của quyền năm độc lập. Kiểm chứng US1.6/7.
- **FR-004**: Khi không có nguồn năm ban hành hợp lệ, hệ thống MUST giải thích trạng thái trống và không cho dùng năm chưa ban hành làm nguồn kế hoạch tổ mới. Kiểm chứng US1.5.
- **FR-005**: Phần mục tiêu năm học trong màn soạn và chi tiết kế hoạch tổ MUST liệt kê toàn bộ mục tiêu và nội dung kế thừa, gồm hàng chưa tick, giữ thứ tự, phân nhóm, nhãn và liên kết chủ đề. Kiểm chứng US2.1/3.
- **FR-006**: Card mục tiêu MUST giãn để hiển thị đầy đủ danh sách, không cuộn riêng trong card, cắt bớt hàng/nội dung, phân trang hoặc yêu cầu xem thêm để đọc đủ; người dùng được cuộn toàn trang. Kiểm chứng US2.2.
- **FR-007**: Cách trình bày mới MUST giữ cây nguồn chỉ đọc và cơ chế tick/bỏ tick từng nội dung vào chủ đề, lưu/mở lại theo EDU-55; không tự chọn mọi nội dung của một mục tiêu. Kiểm chứng US2.3.

- **FR-008**: Trong kế hoạch tổ, bốn cột kế thừa ở phần2 (nhóm kết quả mong đợi/STT/mục tiêu/nội dung), gồm header, nền xám như các ô chỉ đọc; giữ màu phân nhóm lĩnh vực hiện có và ô tick thao tác được.
- **FR-009**: Phần3 kế hoạch tổ thay chuỗi MT1/MT2 bằng đúng một select cho mỗi chủ đề với nhãn ngoài “Xem mục tiêu”; danh sách từ tick phần2, chọn mục tiêu xem toàn văn mục tiêu và các nội dung đã tick vào chủ đề đó, không sửa dữ liệu qua select. Có trạng thái không mục tiêu và dùng bàn phím được.
- **FR-010**: Phần3 kế hoạch tổ có cả cột chủ đề nhánh/dự án kế thừa như năm và cột xem mục tiêu. Toàn bộ bảng chủ đề/đợt nghỉ gồm header nền xám, dữ liệu chỉ đọc; trong bảng chỉ select xem mục tiêu là control thao tác được. Không đổi khả năng biên soạn kế hoạch năm.

### Key Entities *(include if feature involves data)*

- **Kế hoạch năm học**: nguồn định hướng theo năm học/nhóm tuổi; có trạng thái và phiên bản.
- **Kế hoạch tổ**: kế hoạch triển khai kế thừa nguồn năm theo cơ chế đã có.
- **Quyền lập tổ và quyền năm độc lập**: các quyền được xác định bởi cấu hình/phân công và phạm vi hiện hành.
- **Cây mục tiêu/nội dung**: các nhóm, mục tiêu, nhãn, nội dung và liên kết nội dung–chủ đề của kế hoạch tổ.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% ca nghiệm thu với người chỉ có quyền lập tổ không xem/dùng được năm chưa ban hành qua danh sách, chi tiết, đường dẫn trực tiếp hoặc chọn nguồn.
- **SC-002**: 100% ca HT/PHT hiệu lực có quyền xem năm đã ban hành đọc được mọi nhóm tuổi; 100% ca tổ trưởng/GV đọc đúng nhóm tuổi được giao và bị chặn khi xem chéo tuổi. Phân công hết/thu hồi không còn cấp phạm vi tương ứng ở danh sách/chi tiết/chọn nguồn, kể cả liên kết cũ. Quyền năm độc lập và điều kiện trạng thái giữ theo Q1=A.
- **SC-003**: Với bộ mẫu 50 mục tiêu, mỗi mục tiêu ít nhất hai nội dung, 100% hàng và toàn văn nội dung đọc được bằng cuộn trang ở màn soạn/chi tiết tổ; có 0 vùng cuộn riêng trong card mục tiêu.
- **SC-004**: 100% ca đối chiếu nhãn, thứ tự, phân nhóm, hàng chưa tick và tick–lưu–mở lại giữ đúng dữ liệu; người dùng đọc được mục tiêu cuối cùng mà không phải chuyển trang hoặc bấm xem thêm trong card.

## Assumptions

- “Được duyệt ban hành” là trạng thái Ban hành sau duyệt cuối theo EDU-54, không phải duyệt trung gian.
- User làm rõ sau analyze08/11/2026: HT/PHT được mọi nhóm tuổi; tổ trưởng/GV đúng nhóm tuổi phân công. Phạm vi từ quyền lập tổ vẫn phải có quyền tạo hiệu lực và phân công của CurrentUser; phân công HT/PHT hợp lệ mang phạm vi mọi nhóm tuổi, không cần phân công tổ trưởng từng tuổi. Không đồng nhất tài khoản phụ trách với tác giả bản năm; không thay điều kiện ban hành/Q1=A.
- “Người tạo kế hoạch tổ” được xác định theo quyền tạo hiệu lực; tổ trưởng chỉ là mặc định cấu hình, không cố định vai trò trong yêu cầu.
- Phạm vi giao diện là phần mục tiêu nguồn ở màn soạn và chi tiết kế hoạch tổ. Cuộn toàn trang được phép; không yêu cầu mọi hàng cùng nằm trong một màn hình.
- Cây nguồn chỉ đọc, phạm vi truy cập, cách chọn/nâng phiên bản và đọc tổ cũ kế thừa feature012.
- Không đổi quy trình duyệt, lịch, quyền biên soạn nguồn, nhãn hoặc dữ liệu lưu chỉ để đáp ứng thay đổi hiển thị.
- Phạm vi triển khai hiện hành: BE/giáo dục FE được user cho phép và đã thực hiện; không sửa APP, không deploy/Git integration. Xem evidence và giới hạn ở quickstart.

## UI delta — user08/11/2026

User yêu cầu thực hiện UI trước rồi implement→converge tới sạch, cho phép tiếp tục cả BE/FE phạm vi013. Đánh giá UX: nền xám biểu thị nguồn chỉ đọc nhưng chữ phải đủ tương phản; select có label ngoài và chi tiết toàn văn bên dưới để tránh option dài bị cắt. Không thêm thư viện/abstraction, giữ native select và component/table hiện có. FR008–010 bổ sung US2; không đổi phạm vi quyền/status đã chốt.
