# Feature Specification: Nhập file mục tiêu năm học

**Feature Branch**: BE `feature/G94-226-BE-FEAT-09-school-year-goals` · FE `feature/G94-178-FE-FEAT-41-lesson-assessment-parent`

**Created**: 2026-11-01

**Status**: Implemented and verified — 14/15 tasks (T015 commit/PR), BE 556 test, FE verify, Playwright 37/37 với BE thật

**Input**: Tiền (01/11/2026): "dùng vòng lặp spec tôi hay làm code cho tôi luôn tính năng nhập file vào khi tạo mục tiêu năm học, rồi commit push cả be và fe, rồi tạo pull với dev"

## Hiện trạng trước khi làm

Bước 2 đã có nút **Nhập file**, nhưng chỉ nhận CSV 6 cột (`Lĩnh vực | Loại dòng | STT | Mục tiêu | Nội dung | Chủ đề`), đọc ở Web, không có file mẫu, không xem trước, thêm thẳng vào bảng. Trường soạn kế hoạch năm bằng **Word** theo bảng mẫu MN Thái Dương (bảng A: `STT | Mục tiêu giáo dục năm | Nội dung giáo dục năm | Chủ đề thực hiện` + mỗi chủ đề một cột viết tắt), đúng bố cục CareNest đang xuất (`yearPlanDoc.js`).

## Clarifications

### Session 2026-11-01 (tự chọn theo chế độ tự chạy; mọi mục dưới đây là **Giả định – chờ Tiền duyệt**)

- Q: Nhận định dạng nào? → A: Word `.docx` (bảng theo mẫu Thái Dương), Word `.doc` do CareNest xuất (dạng HTML), Excel `.xlsx` (trang tính đầu, cùng bố cục bảng hoặc mẫu CSV cũ), CSV mẫu cũ (giữ tương thích). Word `.doc` nhị phân đời cũ không đọc được ⇒ báo "Mở file bằng Word rồi Lưu thành .docx".
- Q: Ai đọc file? → A: BE đọc và kiểm (một endpoint xem trước, **không lưu**, không đổi cấu trúc DB, không thêm thư viện: đọc zip/XML bằng JDK). Web hiện bản xem trước rồi thêm vào bảng đang soạn; vẫn chỉ ghi khi bấm Lưu (EDU-22).
- Q: Thay thế hay bổ sung? → A: Chỉ bổ sung: thêm vào cuối lĩnh vực cùng tên, bỏ qua mục tiêu trùng câu (như EDU-24 và CSV cũ). Không có chế độ thay thế/xóa bảng.
- Q: Dấu tích chủ đề? → A: Cột chủ đề khớp theo **tên viết tắt** các chủ đề đang có ở tab Chủ đề và sự kiện (không phân biệt hoa thường). Viết tắt chưa có ⇒ bỏ dấu tích đó và báo để tạo chủ đề rồi nhập lại; không tự tạo chủ đề.
- Q: Số thứ tự MT trong file? → A: Bỏ qua; số MT đánh lại theo bảng như hiện tại.
- Q: File mẫu? → A: Nút **Tải file mẫu** tải bảng A dạng Word (`.doc`) chỉ có dòng lĩnh vực và các cột chủ đề hiện tại của bản đang soạn, để điền rồi nhập lại.
- Q: Giới hạn? → A: Tối đa 2 MB; đọc bảng đầu tiên có cột Mục tiêu và Nội dung; tối đa 2.000 dòng.
- Q: Quyền? → A: Người được lập/sửa kế hoạch năm (Phó HT; Tổ trưởng) — giống quyền mở màn soạn (EDU-02/03). Endpoint không đọc/ghi kế hoạch nào nên không cần phiên sửa (EDU-10); việc thêm vào bảng vẫn cần phiên sửa như hiện tại.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Nhập bảng mục tiêu từ file Word của trường (Priority: P1)

Tổ trưởng/Phó HT đang soạn kế hoạch năm, ở bước 2 bấm **Nhập file**, chọn file Word bảng mục tiêu theo mẫu Thái Dương. Hệ thống đọc file, hiện bản xem trước (số mục tiêu, nội dung, dấu tích theo lĩnh vực; các dòng bị bỏ qua và lý do). Bấm **Thêm** thì mục tiêu vào bảng đang soạn; bấm Lưu mới ghi.

**Why this priority**: Trường đã có kế hoạch năm bằng Word; nhập lại tay hàng trăm dòng mất thời gian và dễ sai.

**Independent Test**: Nhập một file `.docx` có 2 lĩnh vực, lĩnh vực con, nhóm kết quả, mục tiêu nhiều nội dung, cột chủ đề có tích; đối chiếu bảng sau khi thêm và sau khi Lưu.

**Acceptance Scenarios**:

1. **Given** bản đang soạn có chủ đề viết tắt TMN, BT, **When** nhập `.docx` có cột TMN, BT và dấu tích, **Then** xem trước đúng số mục tiêu/nội dung/dấu tích theo lĩnh vực; Thêm xong bảng có đủ mục tiêu, nội dung, dấu tích đúng cột.
2. **Given** dòng "I. Giáo dục phát triển thể chất", "*Phát triển vận động", "- Thực hiện được…", **When** đọc, **Then** nhận đúng lĩnh vực, lĩnh vực con, nhóm kết quả mong đợi; số "1." đầu câu mục tiêu và "- " đầu nội dung được bỏ.
3. **Given** ô mục tiêu gộp nhiều dòng (mỗi dòng một nội dung), **When** đọc, **Then** các nội dung thuộc cùng mục tiêu.
4. **Given** vừa Thêm, **When** chưa bấm Lưu, **Then** là thay đổi chưa lưu (cảnh báo rời màn như hiện có); Lưu xong mở lại thấy đúng.
5. **Given** bấm Hủy ở xem trước, **When** đóng hộp, **Then** bảng không đổi.

### User Story 2 - Báo lỗi theo dòng, không làm hỏng bảng (Priority: P1)

**Acceptance Scenarios**:

1. **Given** cột chủ đề có viết tắt không có trong bản đang soạn, **When** xem trước, **Then** báo "Chưa có chủ đề X" kèm số dòng; Thêm vẫn được, các dấu tích đó bị bỏ.
2. **Given** dòng nằm trước dòng lĩnh vực đầu tiên hoặc dưới tên lĩnh vực không nhận ra, **When** xem trước, **Then** bỏ qua và báo số dòng, lý do.
3. **Given** mục tiêu đã có cùng câu trong cùng lĩnh vực, **When** Thêm, **Then** bỏ qua, báo số mục tiêu trùng.
4. **Given** file sai định dạng, quá 2 MB, rỗng, Word `.doc` nhị phân hoặc không có bảng mục tiêu, **When** chọn file, **Then** báo lỗi rõ, bảng không đổi.
5. **Given** không còn quyền sửa (mất phiên sửa, đang chờ khôi phục), **When** bấm Thêm, **Then** không thêm (như EDU-24 hiện có).

### User Story 3 - Tải file mẫu, Excel và CSV (Priority: P2)

**Acceptance Scenarios**:

1. **Given** bản đang soạn có chủ đề, **When** bấm Tải file mẫu, **Then** tải Word bảng A có 5 dòng lĩnh vực và các cột chủ đề hiện tại.
2. **Given** file `.xlsx` cùng bố cục bảng (hoặc mẫu CSV 6 cột), **When** nhập, **Then** kết quả như với Word.
3. **Given** file CSV mẫu cũ, **When** nhập, **Then** vẫn nhập được như trước.

### Edge Cases

- Ô trống ở cột chủ đề, ô có "x"/"✓" thay vì viết tắt: coi là tích chủ đề của cột đó.
- Mục tiêu không có nội dung: vẫn thêm mục tiêu (bảng hiện tại cho phép, gửi duyệt mới bắt buộc nội dung – EDU hiện có).
- Bảng có thêm cột "Nhóm kết quả mong đợi" ở đầu (màn hình CareNest): nhận như dòng nhóm.
- Zip lạ/zip bomb, XML có DOCTYPE: từ chối an toàn (giới hạn dung lượng giải nén, tắt DTD/entity).

## Requirements *(mandatory)*

- **FR-001**: BE có `POST /education-plans/goals/file-preview` (multipart `file`) trả bản xem trước: các lĩnh vực theo cấu hình (EDU), dòng `sub`/`group`/`goal` (goal có `contents[{text, themes[viết tắt]}]`), danh sách viết tắt chủ đề trong file, cảnh báo `[{row, message}]`. Không lưu, không đổi cấu trúc DB.
- **FR-002**: Nhận `.docx`, `.doc` (HTML do CareNest xuất), `.xlsx`, `.csv`; giới hạn 2 MB, ≤ 2.000 dòng; lỗi định dạng trả mã lỗi nghiệp vụ riêng.
- **FR-003**: Quyền: Phó HT hoặc giáo viên có quyền sửa kế hoạch năm (GOALS_EDIT) — Web chỉ mở được ở màn soạn.
- **FR-004**: Web: hộp **Nhập file** gồm Tải file mẫu, chọn file, xem trước (theo lĩnh vực), cảnh báo theo dòng, Thêm/Hủy; thêm theo luật bổ sung (FR Clarifications), dấu tích khớp viết tắt; chỉ ghi khi Lưu.
- **FR-005**: Điện thoại: mục "Nhập file" trong menu "Lấy mục tiêu" mở cùng hộp.
- **FR-006**: Luật ghi ở `docs/business/BUSINESS_RULES.md` EDU-46 (PROPOSED – chờ Tiền duyệt).

## Success Criteria

- **SC-001**: File Word mẫu 5 lĩnh vực × 20 mục tiêu × 2 nội dung, 9 chủ đề: xem trước ≤ 2 giây, thêm đúng 100% mục tiêu/nội dung/dấu tích.
- **SC-002**: Không có trường hợp nào nhập file làm mất hoặc đổi dữ liệu đang có trong bảng.
