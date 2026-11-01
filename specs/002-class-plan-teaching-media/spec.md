# Feature Specification: Hoàn thiện tài liệu lớp và học liệu trình chiếu

**Feature Branch**: `feature/G94-226-BE-FEAT-09-school-year-goals` (giữ nhánh hiện tại)
**Created**: 2026-10-27
**Status**: Implemented — code và cổng kiểm tra hoàn tất; giới hạn kiểm chứng trong quickstart.md
**Input**: User yêu cầu sau tự lưu năm tiếp tục chủ đề nhánh, kế hoạch chủ đề/tuần/ngày theo Specify → Clarify → Plan → Database → Tasks → Analyze → Implement → Converge; giữ lịch sử task; thêm ảnh/video/slide/tệp cho hoạt động học và giao diện dạy học/trình chiếu. User giao agent chọn đề xuất cho điểm mơ hồ, bám nghiệp vụ hiện hành.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Soạn đúng chuỗi tài liệu lớp hiện hành (Priority: P1)

Giáo viên của lớp tiếp tục soạn chủ đề nhánh cả năm, kế hoạch chủ đề và giáo án tuần/ngày trên kế hoạch năm đã ban hành. Các bước và người duyệt giữ đúng nghiệp vụ hiện có.

**Why this priority**: Học liệu và trình chiếu phải dùng đúng mục tiêu/hoạt động, không làm đổi kết quả soạn hay đánh giá trẻ.
**Independent Test**: Lập và duyệt chủ đề nhánh → soạn chủ đề → đủ các tuần/ngày → gửi một hồ sơ chủ đề → duyệt/trả lại theo cấu hình; đối chiếu tài liệu cũ và các liên kết MT.

**Acceptance Scenarios**:

1. **Given** kế hoạch năm chưa ban hành, **When** mở bước lập tài liệu lớp, **Then** chỉ rõ bước còn thiếu và chưa cho lập.
2. **Given** chủ đề nhánh chưa duyệt, **When** lập chủ đề/tuần, **Then** chặn đúng EDU-13; khi đủ bước trước thì mở được bước sau.
3. **Given** đủ tài liệu các nhánh, **When** giáo viên gửi hồ sơ chủ đề, **Then** gửi một lần theo chuỗi ký hiện hành; tuần không có luồng gửi riêng.
4. **Given** tài liệu đã gửi/duyệt, **When** cố sửa nội dung hoặc học liệu, **Then** từ chối; trả lại có lý do rồi sửa/gửi lại theo luồng hiện có.
5. **Given** mở dữ liệu có từ trước tính năng học liệu, **When** đọc/soạn/xuất/duyệt, **Then** nội dung cũ và số MT vẫn dùng được, không phải nhập lại.

### User Story 2 - Gắn học liệu vào từng hoạt động (Priority: P1)

Giáo viên chọn hoạt động của kế hoạch chủ đề và đính kèm hình ảnh, video, PDF/slide hoặc tệp hỗ trợ. Người xem đúng phạm vi có thể xem hoặc tải học liệu đó.

**Why this priority**: Hoạt động soạn xong có tài nguyên dùng ngay khi dạy, giữ gắn kết với MT hiện hành.
**Independent Test**: Lưu hoạt động, tải một ảnh/video/PDF/tệp, mở lại và đối chiếu đúng hoạt động; thử tệp không hợp lệ, người ngoài lớp và bản đã duyệt.

**Acceptance Scenarios**:

1. **Given** giáo viên có quyền sửa tài liệu lớp, **When** chọn tệp cho một hoạt động đang soạn, **Then** lưu nội dung cần thiết trước, gắn tệp đúng hoạt động và hiển thị tên/loại/dung lượng.
2. **Given** upload thất bại hoặc phản hồi bị mất, **When** thử lại cùng lần tải, **Then** không tạo hai bản học liệu cho cùng lần tải và không báo thành công giả.
3. **Given** tệp không nằm trong loại/kích thước hỗ trợ, **When** tải lên, **Then** từ chối với lý do rõ; nội dung kế hoạch và các tệp trước đó giữ nguyên.
4. **Given** một hoạt động bị đổi/xóa/sắp xếp lại làm liên kết không còn chắc chắn, **When** mở học liệu, **Then** giữ tệp cũ trong phần cần kiểm tra/gắn lại, không tự chuyển sang hoạt động khác.
5. **Given** tài liệu còn Nháp/Bị trả lại và giáo viên có quyền, **When** chủ động gắn lại học liệu hoặc gỡ tệp, **Then** thay đổi đúng tài liệu, xác nhận khi gỡ; không sửa bản đã gửi/duyệt.
6. **Given** người không có quyền xem lớp/tài liệu, **When** biết mã học liệu và truy cập trực tiếp, **Then** không đọc được tệp hoặc metadata ngoài phạm vi.

### User Story 3 - Dạy học và trình chiếu từ kế hoạch đã soạn (Priority: P1)

Giáo viên mở chế độ dạy học, chọn hoạt động và trình bày nội dung/học liệu ở vùng lớn. Có thể mở toàn chủ đề hoặc các hoạt động của một ngày đã xếp trong giáo án tuần.

**Why this priority**: Giảm thao tác tìm và mở nhiều tệp trong giờ dạy, cho thấy rõ hoạt động và MT đang dùng.
**Independent Test**: Mở từ chủ đề và từ giáo án ngày, chọn hoạt động, xem ảnh/phát video/PDF, dùng trước/sau và toàn màn hình; đối chiếu MT và hoạt động ngày.

**Acceptance Scenarios**:

1. **Given** có kế hoạch chủ đề đã lưu và quyền xem, **When** mở chế độ dạy học, **Then** thấy lớp/chủ đề/trạng thái, danh sách hoạt động, số MT và vùng trình bày; bản nháp hiển thị rõ là bản nháp.
2. **Given** chọn hoạt động có ảnh/video/PDF, **When** chọn học liệu, **Then** xem được ở vùng lớn; video chỉ phát khi người dùng thao tác.
3. **Given** tệp PowerPoint/Word/Excel hoặc tệp hỗ trợ không xem trực tiếp, **When** chọn tệp, **Then** có hành động tải/mở bằng ứng dụng phù hợp, không giả rằng đã dựng lại toàn bộ slide.
4. **Given** mở từ một ngày của giáo án tuần, **When** vào chế độ dạy học, **Then** chỉ lấy các hoạt động đã xếp trong ngày đó; giữ số MT và liên kết hiện có.
5. **Given** đang trình chiếu, **When** dùng trước/sau, phím điều hướng hoặc toàn màn hình, **Then** chuyển đúng hoạt động/học liệu; thoát không sửa hay đổi trạng thái tài liệu.
6. **Given** không có tệp hoặc hoạt động trong phạm vi đã chọn, **When** mở trình chiếu, **Then** hiện nội dung hoạt động hoặc trạng thái trống rõ ràng, có đường quay lại kế hoạch.

### Edge Cases

- Tệp có tên chứa dấu, khoảng trắng, đường dẫn giả hoặc ký tự điều khiển; không dùng tên người tải gửi để xác định nơi lưu.
- Sai loại thật dù đổi phần mở rộng; tệp quá lớn, tệp rỗng, lỗi ghi tệp hoặc lỗi lưu metadata.
- Upload đang hoàn tất trong lúc người khác gửi/duyệt hồ sơ; không cho lọt thay đổi vào bản đã khóa.
- Hoạt động dùng ID theo vị trí hiện hành; đổi thứ tự/dòng không được âm thầm đưa tệp sang hoạt động khác.
- Nhiều giáo viên xem các lớp khác nhau; URL tệp không trở thành đường công khai ngoài kiểm tra quyền.
- Học liệu của hoạt động cũ còn lưu nhưng không còn khớp; không xóa dữ liệu cũ để làm sạch giao diện.
- Chuyển nhanh ảnh/video khiến phản hồi đến muộn; không hiển thị tệp trước vào hoạt động mới và giải phóng dữ liệu xem khi rời.
- Dữ liệu cũ chưa có bất kỳ học liệu nào; màn soạn/duyệt/xuất vẫn hoạt động như trước.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Giữ nghiệp vụ EDU-12..19: tiền đề, quyền soạn, quan hệ MT, hồ sơ chủ đề gửi một lần, chuỗi ký, trả lại và khóa sau gửi/duyệt.
- **FR-002**: Tài liệu cũ phải đọc/soạn/xuất/duyệt được mà không nhập lại; không đánh số lại hay tạo lại mục tiêu năm để thêm học liệu.
- **FR-003**: Cho giáo viên được phân công vào lớp thêm học liệu cho từng hoạt động của kế hoạch chủ đề còn được sửa; lưu hoạt động trước khi tạo liên kết học liệu.
- **FR-004**: Hỗ trợ ảnh JPEG/PNG/GIF/WebP, video MP4/WebM, PDF và các tệp PPTX/DOCX/XLSX/TXT/CSV; kiểm nội dung thực, phần mở rộng, dung lượng và tệp rỗng. Loại chưa hỗ trợ phải báo rõ.
- **FR-005**: Giới hạn mặc định: ảnh 5 MB, video 100 MB, tệp khác 20 MB; một lần tải một tệp. Không làm mất nội dung hoặc học liệu đã có khi bị từ chối.
- **FR-006**: Lưu metadata và tệp tách biệt; không nhúng tệp lớn vào nội dung kế hoạch hoặc bản dữ liệu demo; không trả đường dẫn lưu vật lý cho người xem.
- **FR-007**: Danh sách, xem và tải tệp đều kiểm quyền theo tài liệu/lớp trên hệ thống; biết mã tệp không đủ để truy cập. Chỉ giáo viên của lớp được thay đổi học liệu khi tài liệu còn được sửa.
- **FR-008**: Upload và các thao tác thay đổi học liệu phải kiểm lại trạng thái tài liệu để không đua với gửi/duyệt; các bản đã khóa không thay học liệu.
- **FR-009**: Mỗi lần tải có nhận diện để thử lại không thêm trùng; lỗi lưu giữa chừng phải xử lý phần tệp/metadata chưa hoàn tất, không báo đã tải thành công giả.
- **FR-010**: Lưu dấu nhận diện nội dung hoạt động khi gắn tệp; khi nội dung/vị trí không còn khớp thì giữ học liệu ở phần cần kiểm tra, không trình chiếu như đang thuộc hoạt động khác.
- **FR-011**: Giáo viên có thể gắn lại học liệu vào hoạt động hiện tại hoặc gỡ khi còn quyền sửa; gỡ có xác nhận, dữ liệu đã có không bị tự xóa trong lúc đồng bộ.
- **FR-012**: Có chế độ dạy học từ kế hoạch chủ đề, gồm lớp/chủ đề/trạng thái, hoạt động, MT, học liệu và vùng trình bày lớn.
- **FR-013**: Trình bày trực tiếp ảnh/video/PDF; tệp khác có tải/mở, không dùng dịch vụ công khai bên ngoài để chuyển đổi hoặc xem tệp riêng tư.
- **FR-014**: Có trước/sau hoạt động/học liệu, phím điều hướng phù hợp, nút toàn màn hình và đường quay lại; video không tự phát ngoài thao tác của người dùng.
- **FR-015**: Mở dạy học từ một ngày trong giáo án tuần phải lọc đúng các hoạt động đã xếp cho ngày đó và giữ liên kết MT hiện có.
- **FR-016**: Trạng thái tải/lỗi/trống/không có quyền phải hiển thị rõ; giữ được thao tác bàn phím, không tải đồng thời toàn bộ video lớn chỉ để mở trang.
- **FR-017**: Spec/plan/tasks giữ lịch sử; Analyze chỉ đọc để tìm lỗi, sửa theo task rồi kiểm lại; Converge bổ sung task còn thiếu bằng ID mới ở cuối, không thay file bằng bản rút gọn làm mất dữ liệu cũ.

### Key Entities *(include if feature involves data)*

- **Tài liệu lớp**: Chủ đề nhánh, kế hoạch chủ đề, giáo án tuần/ngày hiện có, trạng thái và chuỗi ký theo EDU-12..18.
- **Hoạt động giáo dục**: Hoạt động của từng MT trong kế hoạch chủ đề, được xếp vào tuần/ngày; nhận diện và nội dung đang có được giữ.
- **Học liệu hoạt động**: Tên, loại, kích thước, người/thời điểm tải, tài liệu sở hữu, hoạt động và dấu nội dung lúc gắn; phần tệp lưu riêng, trạng thái gỡ có kiểm soát.
- **Phiên trình chiếu**: Hoạt động/học liệu đang chọn và phạm vi chủ đề/ngày; không phải trạng thái duyệt mới của tài liệu.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Các ca hồi quy chuỗi chủ đề nhánh → chủ đề → tuần/ngày → hồ sơ duyệt vẫn đạt; không mất dữ liệu cũ hoặc đổi ID/số MT trong các ca kiểm.
- **SC-002**: Toàn bộ ca kiểm tệp sai loại/quá cỡ/rỗng, ngoài lớp và thay đổi bản đã khóa bị từ chối; không có tệp ngoài phạm vi đọc được qua mã trực tiếp.
- **SC-003**: Thử lại cùng lần upload tạo đúng một học liệu hoàn tất; sau mở lại thấy đúng hoạt động và tệp.
- **SC-004**: Giáo viên mở chế độ dạy học từ chủ đề/ngày trong tối đa hai thao tác sau khi vào trang kế hoạch; chọn ảnh/video/PDF và trước/sau/toàn màn hình hoạt động trong các ca kiểm trình duyệt.
- **SC-005**: Đổi/xóa/sắp xếp lại hoạt động không làm tệp cũ xuất hiện nhầm ở hoạt động khác trong toàn bộ ca kiểm liên kết.
- **SC-006**: Mỗi lượt Analyze/Converge có kết quả và task theo dõi; các task có ID cũ vẫn còn nguyên sau khi bổ sung thiếu sót.

## Assumptions

- Nguồn chính: EDU-12..23 trong [business rules](../../docs/business/BUSINESS_RULES.md), [flow giáo án](../../docs/business/flows/education-plan.md), source EduClassDocServiceImpl và các trang lớp hiện có. User giao agent chọn đề xuất cho điểm mơ hồ ngày 27/10/2026; không coi lựa chọn kỹ thuật là quy định mới từ trường.
- Các màn tài liệu lớp tiếp tục cơ chế lưu hiện hành theo EDU-22; quy trình Spec Kit ở đây dùng để kiểm tra/hoàn thiện và thêm học liệu/trình chiếu. Phần tự lưu mục tiêu năm là feature 001 đã hoàn tất riêng.
- Dùng PDF như slide xem trực tiếp trong trình duyệt; giữ PPTX/Word/Excel để tải/mở bằng ứng dụng phù hợp. Không cam kết tái hiện hiệu ứng PowerPoint trong trình duyệt.
- Học liệu gắn vào hoạt động kế hoạch chủ đề và được dùng lại khi hoạt động đó được xếp vào tuần/ngày, không tạo bản sao tệp cho từng ngày.
- Chế độ dạy học cho người có quyền xem tài liệu; bản nháp có nhãn rõ, không tự gửi/duyệt hoặc mở khóa tài liệu khi trình chiếu.
- Các giới hạn loại/dung lượng và lựa chọn xem trực tiếp là đề xuất triển khai trong phạm vi user đã giao; giữ stack hiện có, không thêm dịch vụ bên ngoài hay dependency.

## Clarifications

### Session 2026-10-27

- User: "nếu có mơ hồ và thay đổi thì làm theo đề xuất của bạn, nhưng vẫn cần bám chính xác nghiệp vụ hiện tại của sơ đồ và code". Agent chọn giữ chuỗi duyệt/quyền/cách lưu tài liệu lớp đang có, thêm học liệu theo hoạt động, không sửa mục tiêu năm đã ban hành.
- Đề xuất của agent đã nêu trong chat: ảnh/video/PDF trình bày trực tiếp; PPTX/Word và tệp khác đính kèm để tải/mở; giữ dữ liệu cũ và lịch sử task qua các vòng lặp.

- User chốt bổ sung: mọi tệp lưu S3; đồng ý AWS SDK v2. User cung cấp project cấu hình ngoài repo để testS3 thật, chỉ dùng credential trong environment của tiến trình test. Live storage smoke đạt, không tự thay bucket/IAM.

