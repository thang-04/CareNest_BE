# Feature Specification: Phiên bản kế hoạch năm và kế hoạch điều chỉnh, bổ sung

**Feature Branch**: `feature/G94-226-BE-FEAT-09-school-year-goals` (BE) · `feature/G94-178-FE-FEAT-41-lesson-assessment-parent` (FE) — giữ nhánh hiện tại; không tạo/chuyển nhánh, chưa commit

**Created**: 2026-10-28

**Status**: Implemented, converged — BE/FE verify PASS; Playwright chưa chạy (xem plan.md Progress log)

**Input**: Tiền chốt 28/10/2026 (qua coordinator): "(A) Kế hoạch ngày có ô *Kế hoạch điều chỉnh và bổ sung* như mẫu Thái Dương, sửa được khi Nháp/Bị trả lại và cả sau khi duyệt. (B) Kế hoạch năm được sửa sau khi ban hành bằng phiên bản: bản đã ban hành không đổi; sửa ⇒ phiên bản 2 (chép từ bản 1) đi lại luồng TT gửi → PHT ban hành (PHT tự lập thì ban hành thẳng). Sau khi bản 2 ban hành, mọi ô của tài liệu lớp (chủ đề nhánh, kế hoạch chủ đề, giáo án tuần, kế hoạch ngày) gắn với MT/nội dung/chủ đề đã đổi hoặc bị bỏ hiện ĐỎ và cảnh báo giáo viên phải sửa. Giáo viên tạo bản 2 của tài liệu lớp từ bản mẫu (sinh từ kế hoạch năm mới) hoặc chép bản cũ, sửa ô đỏ; bản cũ đã duyệt giữ nguyên; bản 2 đi lại chuỗi duyệt."

## Clarifications

### Session 2026-10-28

Các mặc định do coordinator chuyển từ quyết định của Tiền (dùng làm câu trả lời, không hỏi lại):

- Q: Ai được tạo phiên bản mới của kế hoạch năm? → A: Người được sửa kế hoạch đó (Tổ trưởng nhóm tuổi, Phó HT). Mỗi (năm học, nhóm tuổi) chỉ có tối đa một phiên bản chưa ban hành.
- Q: Trong lúc phiên bản mới chưa ban hành, lớp dùng bản nào? → A: Bản đã ban hành trước đó vẫn là bản hiện hành; giáo viên tiếp tục xem/dạy theo nó.
- Q: Thế nào là "đã đổi"? → A: Câu MT đổi; nội dung đổi chữ hoặc bị xóa; dấu tích nội dung–chủ đề đổi; chủ đề đổi tên/viết tắt, đổi ngày hoặc bị xóa. MT/nội dung/chủ đề mới thêm hiện nhãn "mới" (thông tin, không đỏ).
- Q: So sánh theo gì? → A: Theo định danh dòng ổn định đi qua các phiên bản (dòng chép giữ lineage), không theo số MT.
- Q: Chủ đề đã kết thúc thì sao? → A: Chỉ đánh đỏ tài liệu của chủ đề chưa kết thúc (ngày kết thúc ≥ hôm nay); chủ đề đã kết thúc chỉ hiện ghi chú thông tin.
- Q: Đánh giá trẻ hằng ngày? → A: ~~Giữ liên kết với phiên bản đã dùng khi ghi~~ (SUPERSEDED). Tiền chốt 28/10: đánh giá trẻ luôn map theo MT của giáo án ngày đang dùng cho ngày đó; giáo án ngày đổi sang bản của phiên bản mới (đã duyệt) thì đánh giá map theo — MT đã chấm chuyển sang MT cùng lineage, MT bị bỏ không còn hiện.
- Q: Lưu gì cho mỗi phiên bản? → A: Số phiên bản, người tạo/lúc tạo, phiên bản nguồn, trạng thái; có trang lịch sử liệt kê các phiên bản chỉ xem.

Quyết định tự chọn theo chế độ tự chạy (ghi để Tiền soát, mục Assumptions A1–A8):

- Q: Tài liệu lớp gắn với phiên bản cũ sau khi bản mới ban hành? → A: Thành "đã thay thế": chỉ xem/in; không tạo mới, lưu hay gửi trên phiên bản cũ; người duyệt vẫn duyệt/trả lại được hồ sơ đang chờ (để không kẹt chuỗi).
- Q: Giáo viên "sửa xong" ô đỏ thế nào? → A: Ô đỏ hết đỏ khi tham chiếu không còn trỏ vào mục bị bỏ (chọn lại/xóa) hoặc giáo viên bấm "Đã kiểm tra" cho mục chỉ đổi chữ. Gửi duyệt bị chặn (cả BE) khi còn ô đỏ.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ghi "Kế hoạch điều chỉnh và bổ sung" trong kế hoạch ngày (Priority: P1)

Giáo viên của lớp mở giáo án tuần, ở mỗi kế hoạch ngày (Thứ 2–6) có ô "*Kế hoạch điều chỉnh và bổ sung" như cuối kế hoạch ngày trong file MN Thái Dương. Ô sửa được khi giáo án Nháp/Bị trả lại và cả khi hồ sơ đã gửi/đã duyệt; nội dung giáo án đã duyệt khác vẫn khóa.

**Why this priority**: Yêu cầu nhỏ, độc lập, dùng hằng ngày; mẫu Thái Dương có mục này mà hệ thống đang để trống cho viết tay.

**Independent Test**: Với giáo án tuần đã duyệt, nhập ô điều chỉnh của Thứ 3, lưu, tải lại: ô giữ nội dung; trạng thái hồ sơ, người duyệt, phần còn lại của giáo án không đổi; bản in/Word hiện nội dung.

**Acceptance Scenarios**:

1. **Given** giáo án tuần Nháp, **When** giáo viên lớp nhập ô điều chỉnh của một ngày và bấm Lưu, **Then** lưu cùng giáo án như các ô khác.
2. **Given** hồ sơ chủ đề đã duyệt (hoặc đang chờ duyệt), **When** giáo viên lớp nhập ô điều chỉnh và bấm Lưu, **Then** chỉ phần điều chỉnh được ghi; trạng thái, người duyệt, lịch sử duyệt và nội dung khác giữ nguyên; lịch sử ghi một dòng "Cập nhật kế hoạch điều chỉnh và bổ sung".
3. **Given** người không phải giáo viên của lớp (Tổ trưởng, Phó HT, HT), **When** xem giáo án, **Then** thấy nội dung ô điều chỉnh nhưng không sửa được; BE từ chối nếu gọi trực tiếp.
4. **Given** đã nhập ô điều chỉnh, **When** xuất Word / in, **Then** mục "*Kế hoạch điều chỉnh và bổ sung:" in nội dung; để trống thì giữ dòng kẻ để viết tay.
5. **Given** rời trang khi ô điều chỉnh chưa lưu, **Then** cảnh báo rời màn và bản giữ ẩn trên máy như các ô khác (EDU-22).

---

### User Story 2 - Tạo và ban hành phiên bản mới của kế hoạch năm (Priority: P1)

Tổ trưởng nhóm tuổi hoặc Phó HT mở kế hoạch năm đã ban hành, bấm "Tạo phiên bản điều chỉnh". Hệ thống chép toàn bộ nội dung (mục tiêu, nội dung, chủ đề, đợt nghỉ, dấu tích) thành phiên bản n+1 ở trạng thái Nháp. Người soạn sửa theo luồng ba bước hiện có rồi gửi/ban hành như bản đầu. Bản đã ban hành giữ nguyên.

**Why this priority**: Nền của toàn bộ tính năng; không có phiên bản thì không đổi được kế hoạch sau ban hành.

**Independent Test**: Ban hành v1, tạo v2, đổi một MT, xóa một nội dung, Tổ trưởng gửi, PHT ban hành; mở v1 vẫn đúng như cũ; danh sách và lịch sử phiên bản hiện v1 (đã thay thế) và v2 (hiện hành).

**Acceptance Scenarios**:

1. **Given** kế hoạch đã ban hành là phiên bản mới nhất, **When** Tổ trưởng nhóm tuổi đó hoặc Phó HT bấm Tạo phiên bản điều chỉnh, **Then** có phiên bản n+1 Nháp, chép đủ nội dung, ghi người tạo, lúc tạo, phiên bản nguồn; người tạo vào trang soạn và giữ phiên sửa (EDU-10).
2. **Given** đã có một phiên bản chưa ban hành (Nháp/Chờ duyệt/Bị trả lại) của cùng năm học và nhóm tuổi, **When** tạo thêm, **Then** bị từ chối và được dẫn tới phiên bản đang soạn.
3. **Given** Giáo viên, Hiệu trưởng hoặc Tổ trưởng nhóm tuổi khác, **When** cố tạo phiên bản, **Then** bị từ chối.
4. **Given** phiên bản n+1 Nháp, **When** sửa, gửi, trả lại, ban hành, **Then** theo đúng EDU-03/04/05/10/22/23 như kế hoạch đầu; PHT tự tạo bản thì ban hành thẳng từ Nháp.
5. **Given** phiên bản n+1 chưa ban hành, **When** giáo viên mở tài liệu lớp, **Then** vẫn dùng phiên bản n đã ban hành; không thấy cảnh báo đỏ.
6. **Given** phiên bản đã ban hành (bất kỳ), **When** cố sửa/xóa/gửi, **Then** bị từ chối (bất biến).
7. **Given** phiên bản Nháp n+1 (n ≥ 1), **When** Phó HT xóa, **Then** xóa được theo EDU-07; phiên bản n vẫn hiện hành.
8. **Given** trang chi tiết kế hoạch, **When** mở Lịch sử phiên bản, **Then** liệt kê mọi phiên bản: số, trạng thái, người tạo, lúc tạo, ban hành bởi/lúc; mở bản cũ ở chế độ chỉ xem.

---

### User Story 3 - Thấy phần kế hoạch năm đã đổi (Priority: P1)

Sau khi phiên bản mới được ban hành, người soạn và giáo viên xem được so sánh với phiên bản trước: MT/nội dung/chủ đề đổi, bị bỏ, mới thêm.

**Why this priority**: Là nguồn dữ liệu cho việc đánh đỏ ở tài liệu lớp.

**Independent Test**: So sánh v2 với v1 sau một bộ thay đổi biết trước (đổi câu MT, đổi chữ nội dung, xóa nội dung, đổi dấu tích, đổi tên + ngày chủ đề, xóa chủ đề, thêm MT); danh sách thay đổi khớp 100%.

**Acceptance Scenarios**:

1. **Given** v2 sửa câu MT 5 và chèn MT mới trước nó (MT 5 đánh lại thành MT 6), **When** so sánh, **Then** chỉ MT đó là "đổi", MT chèn là "mới"; các MT bị đánh số lại nhưng không đổi chữ không bị coi là đổi.
2. **Given** v2 xóa một nội dung, đổi chữ một nội dung, bỏ dấu tích một nội dung khỏi chủ đề, **When** so sánh, **Then** ba mục tương ứng "bị bỏ", "đổi", "đổi chủ đề".
3. **Given** v2 đổi tên/ngày một chủ đề, xóa một chủ đề, **When** so sánh, **Then** chủ đề đổi/bị bỏ được liệt kê kèm phạm vi đổi (tên, viết tắt, ngày).
4. **Given** người xem không được xem một trong hai phiên bản theo EDU-06, **Then** không xem được so sánh.

---

### User Story 4 - Ô đỏ trong tài liệu lớp và tạo bản mới (Priority: P1)

Khi phiên bản mới của kế hoạch năm đã ban hành, giáo viên mở Chủ đề nhánh / Kế hoạch chủ đề / Giáo án tuần của lớp. Với tài liệu chưa có trên phiên bản mới mà có trên phiên bản cũ, trang mời "Tạo bản mới" theo hai cách: từ bản mẫu (sinh từ kế hoạch năm mới như hiện nay) hoặc chép bản cũ. Bản chép tự chuyển tham chiếu sang mục tương ứng của kế hoạch mới; ô gắn với mục đổi/bị bỏ hiện đỏ kèm lý do. Giáo viên sửa hết ô đỏ rồi gửi duyệt như thường.

**Why this priority**: Mục tiêu chính của yêu cầu B: tài liệu lớp không âm thầm lệch kế hoạch năm.

**Independent Test**: Lớp có chủ đề nhánh, kế hoạch chủ đề, giáo án tuần đã duyệt trên v1; ban hành v2 với thay đổi biết trước; chép bản cũ: đúng các ô đỏ dự kiến (kế hoạch chủ đề: dòng MT đổi/bị bỏ; giáo án tuần: ô có hoạt động gắn MT đổi/bị bỏ; kế hoạch ngày: MT trong ngày đổi/bị bỏ; chủ đề nhánh: tuần thuộc chủ đề đổi ngày/bị bỏ); sửa xong thì gửi được; bản v1 không đổi.

**Acceptance Scenarios**:

1. **Given** v2 đã ban hành và lớp chưa có chủ đề nhánh trên v2, **When** giáo viên mở Chủ đề nhánh, **Then** thấy thông báo "Kế hoạch năm đã có phiên bản n" và hai lựa chọn Tạo từ bản mẫu / Chép bản cũ; bản v1 vẫn mở được chỉ xem.
2. **Given** chọn Chép bản cũ, **When** tạo, **Then** bản mới là Nháp trên v2, nội dung chép từ bản cũ; tham chiếu MT/nội dung/chủ đề đổi sang id của v2 theo lineage; tham chiếu tới mục bị bỏ giữ lại để hiện đỏ; nhớ bản nguồn.
3. **Given** chọn Tạo từ bản mẫu, **Then** giống tạo mới hiện nay trên v2 (rỗng hoặc sinh từ kế hoạch năm), không có ô đỏ.
4. **Given** bản chép có ô gắn MT bị đổi chữ, **When** giáo viên xem, **Then** ô viền/đỏ theo token lỗi hiện có, có câu "MT n đã đổi ở kế hoạch năm phiên bản k: …", và nút "Đã kiểm tra" làm ô hết đỏ.
5. **Given** ô gắn MT/nội dung/chủ đề bị bỏ, **Then** ô đỏ với câu "đã bị bỏ ở phiên bản k"; chỉ hết đỏ khi giáo viên xóa/chọn lại tham chiếu.
6. **Given** còn ô đỏ, **When** gửi duyệt, **Then** bị chặn ở Web và BE với số ô còn phải sửa.
7. **Given** chủ đề đã kết thúc trước hôm nay, **When** xem, **Then** không đỏ; chỉ ghi chú "Chủ đề đã kết thúc theo phiên bản cũ, giữ hồ sơ đã duyệt"; không bắt tạo bản mới cho chủ đề đó.
8. **Given** đã tạo bản mới, **Then** bản cũ (mọi trạng thái) giữ nguyên, chỉ xem; không tạo mới/lưu/gửi tài liệu trên phiên bản đã bị thay thế (người duyệt vẫn duyệt/trả lại hồ sơ đang chờ).
9. **Given** MT/nội dung/chủ đề mới ở v2, **Then** hiện nhãn "mới" (không đỏ) ở kế hoạch chủ đề.
10. **Given** bản mới trên v2, **When** gửi, **Then** đi lại toàn bộ chuỗi duyệt hiện hành (EDU-13/17/18): chủ đề nhánh v2 duyệt trước, rồi hồ sơ chủ đề.

---

### Edge Cases

- Hai người cùng tạo phiên bản mới: một người thành công, người kia nhận lỗi đã có phiên bản đang soạn.
- Tạo phiên bản từ bản không phải mới nhất đã ban hành ⇒ từ chối.
- Phiên bản mới bị xóa (Nháp) ⇒ không ảnh hưởng tài liệu lớp.
- Ô chủ đề nhánh lưu theo thứ Hai của tuần: chủ đề đổi ngày làm tuần mất/thêm ⇒ tuần cũ không còn trong lịch là tham chiếu bị bỏ (đỏ, có thể xóa); tuần mới là ô trống phải đặt tên (EDU-14).
- MT được chuyển sang lĩnh vực khác nhưng giữ lineage và chữ ⇒ không đỏ.
- Bản chép có ô gõ tay không gắn MT ⇒ giữ nguyên, không đỏ.
- Giáo án tuần trên v2 có thứ Hai trùng giáo án v1 ⇒ hai bản riêng (khóa khác nhau), không ghi đè.
- Ô điều chỉnh: lưu khi phiên bản đã bị thay thế ⇒ vẫn cho (ghi chú sau dạy, không đổi nội dung giáo án).
- Đánh giá trẻ: ngày có cả giáo án v1 và v2 đã duyệt ⇒ dùng giáo án v2; MT đã chấm theo v1 hiện ở MT cùng lineage của v2, MT bị bỏ không hiện.

## Requirements *(mandatory)*

### Functional Requirements

**A. Kế hoạch điều chỉnh và bổ sung**

- **FR-001**: Mỗi kế hoạch ngày của giáo án tuần có ô văn bản "Kế hoạch điều chỉnh và bổ sung" (nhiều dòng, tối đa 4000 ký tự).
- **FR-002**: Giáo viên được phân công vào lớp sửa ô này ở mọi trạng thái hồ sơ (Nháp, Bị trả lại, Chờ duyệt, Đã duyệt) và cả khi phiên bản kế hoạch năm đã bị thay thế; người khác chỉ xem.
- **FR-003**: Lưu ô điều chỉnh không đổi nội dung soạn khác, trạng thái, người duyệt; ghi một dòng lịch sử; trả lại dữ liệu mới.
- **FR-004**: Ô theo lưu tay EDU-22 (Lưu/Ctrl+S, cảnh báo rời màn, bản giữ ẩn) và xuất Word/in theo mẫu.

**B. Phiên bản kế hoạch năm**

- **FR-010**: Kế hoạch năm có số phiên bản (1..n), phiên bản nguồn, người tạo, lúc tạo. Mỗi (năm học, nhóm tuổi) có một chuỗi phiên bản; EDU-01 đổi thành "một chuỗi phiên bản/(năm học, nhóm tuổi), tối đa một phiên bản chưa ban hành".
- **FR-011**: Tạo phiên bản n+1 chỉ từ phiên bản ban hành mới nhất, bởi Phó HT hoặc Tổ trưởng của nhóm tuổi (quyền sửa EDU-02/03); chép đủ mục tiêu, nội dung, chủ đề, đợt nghỉ, dấu tích; mỗi dòng chép giữ định danh gốc (lineage).
- **FR-012**: Phiên bản mới theo luồng sửa/gửi/trả lại/ban hành/xóa và phiên sửa hiện hành (EDU-03..10, 22, 23); người tạo là owner của phiên bản đó (PHT tự tạo được ban hành thẳng).
- **FR-013**: Phiên bản đã ban hành bất biến. Phiên bản hiện hành = phiên bản đã ban hành có số lớn nhất; các phiên bản đã ban hành cũ là "đã thay thế".
- **FR-014**: Danh sách/chi tiết kế hoạch hiện số phiên bản và nhãn Hiện hành / Đã thay thế / đang soạn; có Lịch sử phiên bản chỉ xem theo EDU-06.
- **FR-015**: Có so sánh phiên bản với phiên bản nguồn theo lineage, trả từng MT, nội dung, chủ đề với loại thay đổi `UNCHANGED | CHANGED | REMOVED | ADDED`, trường đổi, id cũ ↔ id mới; quyền xem theo EDU-06 trên cả hai bản.
- **FR-016**: "Đổi" gồm: chữ MT; chữ nội dung; tập chủ đề được tích của nội dung; tên, viết tắt, ngày bắt đầu/kết thúc của chủ đề. Đổi số MT, thứ tự hoặc lĩnh vực mà không đổi chữ không tính là đổi.

**C. Tài liệu lớp theo phiên bản**

- **FR-020**: Tài liệu lớp gắn với đúng phiên bản kế hoạch năm khi tạo; mỗi phiên bản có bộ tài liệu riêng, không ghi đè bản của phiên bản khác.
- **FR-021**: Chỉ phiên bản hiện hành nhận tạo mới, lưu, gửi tài liệu lớp. Tài liệu trên phiên bản đã thay thế chỉ xem/in; người duyệt vẫn duyệt/trả lại hồ sơ đang chờ; ô điều chỉnh vẫn sửa được (FR-002).
- **FR-022**: Giáo viên tạo tài liệu trên phiên bản hiện hành bằng (a) bản mẫu như hiện nay, hoặc (b) chép một tài liệu cùng loại, cùng lớp của phiên bản trước; bản chép ghi tài liệu nguồn.
- **FR-023**: Khi chép, tham chiếu tới MT/nội dung/chủ đề/tuần được chuyển sang id phiên bản mới theo lineage; tham chiếu không còn tương ứng giữ nguyên để báo đỏ.
- **FR-024**: Ô đỏ khi tham chiếu (a) mục bị bỏ hoặc không thuộc phiên bản của tài liệu, hoặc (b) mục đổi chưa được giáo viên đánh dấu "Đã kiểm tra". Hiện lý do bằng chữ (không chỉ bằng màu); dùng token/lớp lỗi hiện có.
- **FR-025**: Chỉ đánh đỏ tài liệu của chủ đề có ngày kết thúc ≥ hôm nay; chủ đề đã kết thúc chỉ ghi chú.
- **FR-026**: Gửi duyệt bị chặn (Web và BE) khi tài liệu chép còn tham chiếu không thuộc phiên bản của nó hoặc còn mục đổi chưa đánh dấu; thông báo số ô còn phải sửa.
- **FR-027**: Bản mới đi lại chuỗi duyệt EDU-13/17/18 trên phiên bản mới.
- **FR-028**: Không sửa tài liệu của phiên bản cũ. Đánh giá trẻ map theo MT của giáo án ngày đang dùng: ngày có giáo án đã duyệt của phiên bản mới thì dùng bản đó, MT đã chấm trước chuyển theo lineage (Tiền 28/10).

### Key Entities

- **Phiên bản kế hoạch năm**: bản kế hoạch năm có số phiên bản, phiên bản nguồn, trạng thái, người tạo/lúc tạo, ban hành bởi/lúc; chuỗi theo (năm học, nhóm tuổi).
- **Định danh gốc (lineage)**: định danh của dòng MT, nội dung, chủ đề, đợt nghỉ không đổi qua các phiên bản chép.
- **Thay đổi phiên bản**: mục (MT/nội dung/chủ đề), loại thay đổi, trường đổi, id cũ/mới.
- **Tài liệu lớp theo phiên bản**: tài liệu gắn phiên bản kế hoạch năm, tài liệu nguồn khi chép, danh sách mục đổi đã được đánh dấu kiểm tra.
- **Kế hoạch điều chỉnh và bổ sung**: văn bản theo từng ngày của giáo án tuần, tách khỏi nội dung đã duyệt.

## Success Criteria *(mandatory)*

- **SC-001**: Ô điều chỉnh lưu được ở cả 4 trạng thái hồ sơ; 100% ca kiểm thử giữ nguyên trạng thái, người duyệt và nội dung khác.
- **SC-002**: Tạo phiên bản n+1 chép đủ 100% MT, nội dung, chủ đề, đợt nghỉ, dấu tích của bản nguồn (đối chiếu số lượng và chữ).
- **SC-003**: Với bộ thay đổi mẫu ≥10 mục, so sánh phiên bản phân loại đúng 100% (không coi đánh số lại là đổi).
- **SC-004**: Với tài liệu chép mẫu, ô đỏ khớp 100% danh sách dự kiến; không có ô đỏ ở chủ đề đã kết thúc.
- **SC-005**: Không ca kiểm thử nào làm đổi bản kế hoạch năm hoặc tài liệu lớp đã duyệt của phiên bản cũ.
- **SC-006**: Gửi duyệt bị chặn ở BE trong 100% ca còn ô đỏ, kể cả khi gọi API trực tiếp.

## Assumptions

- A1 (tự chọn): Tài liệu trên phiên bản đã thay thế chỉ xem/in, không tạo/lưu/gửi; người duyệt vẫn xử lý hồ sơ đang chờ; ô điều chỉnh vẫn ghi được.
- A2 (tự chọn): "Sửa xong" = tham chiếu thuộc phiên bản mới và mục đổi được đánh dấu "Đã kiểm tra"; danh sách đánh dấu lưu trong tài liệu lớp.
- A3 (tự chọn): Ô điều chỉnh lưu tách khỏi nội dung soạn (cột riêng) để sửa sau duyệt không làm đổi bản đã duyệt; ghi một dòng lịch sử mỗi lần lưu.
- A4 (tự chọn): Mã kế hoạch phiên bản n ≥ 2 là mã gốc + `-V<n>`; khóa Web giáo án tuần của phiên bản n ≥ 2 thêm `_<id kế hoạch năm>` để không trùng tuần của phiên bản cũ; khóa chủ đề nhánh, kế hoạch chủ đề đã khác nhau theo id.
- A5: Dòng/nội dung/chủ đề thêm sau khi chép có lineage = chính nó; dữ liệu cũ (trước migration) coi lineage = id.
- A6: So sánh luôn với phiên bản nguồn trực tiếp (n với n−1); tài liệu chép từ phiên bản cũ hơn dùng chuỗi lineage nên vẫn ánh xạ được.
- A7: Đánh giá trẻ hiện chỉ có ở mock Web; quy tắc FR-028 áp dụng cho mock, BE learning-observation chưa có.
- A8: Không thêm ký điện tử (EDU-21), AI, Mobile; không đổi chuỗi người ký; không thêm dependency.
- Nguồn: EDU-01..24 trong [Business Rules](../../docs/business/BUSINESS_RULES.md) (ghi trao đổi PROPOSED 26/10 về phiên bản — nay Tiền chốt 28/10), [module education-plan](../../docs/modules/education-plan.md), [flow](../../docs/business/flows/education-plan.md).
