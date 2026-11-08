# Feature Specification: Lọc phân cấp và gợi ý từ chương trình Bộ

**Feature Branch**: feature/G94-326-BE-FEAT-09-school-year-goals-backup (nhánh hiện có)

**Created**: 2026-11-08

**Status**: Ready for planning

**Input**: User báo mục tiêu đích vẫn còn sau đổi lĩnh vực con khi lấy nội dung Bộ; yêu cầu lọc đúng cha và gợi ý ngay khi bấm ô nhập.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Lọc đúng mục tiêu đích (Priority: P1)

Người có quyền soạn chọn Lĩnh vực → Lĩnh vực con → Kết quả mong đợi → Mục tiêu để thêm đúng nội dung vào đúng mục tiêu.

**Why this priority**: Tránh thêm sai đích vì lựa chọn cũ còn tồn tại.

**Independent Test**: Dùng hai lĩnh vực, mỗi lĩnh vực hai lĩnh vực con có nhóm/mục tiêu riêng; đổi bộ lọc và kiểm toàn bộ mục tiêu còn chọn được.

**Acceptance Scenarios**:

1. **Given** chưa chọn lĩnh vực con, **When** chọn lĩnh vực A, **Then** chỉ còn lĩnh vực con, nhóm và mục tiêu của A, đúng nhóm tuổi kế hoạch.
2. **Given** A có A1/A2, **When** chọn A1, **Then** chỉ còn mục tiêu A1; chọn nhóm R1 thì chỉ còn mục tiêu R1.
3. **Given** đang chọn đích G1 thuộc A1, **When** đổi sang A2, **Then** bỏ lựa chọn G1 và các lựa chọn phụ thuộc không phù hợp, không cho thêm khi chưa có đích hợp lệ.
4. **Given** đã thêm nội dung vào bản soạn, **When** đổi bộ lọc, **Then** giữ nguyên dữ liệu đã thêm; không tự lưu hoặc xóa.
5. **Given** lựa chọn con vẫn phù hợp sau đổi cha, **When** lọc lại, **Then** giữ lựa chọn hợp lệ, không tự chọn mục tiêu khác.

### User Story 2 - Gợi ý ngay tại ô nhập (Priority: P1)

Người soạn bấm ô kết quả mong đợi, mục tiêu hoặc nội dung để xem gợi ý từ Bộ rồi gõ để lọc tiếp.

**Why this priority**: Giảm gõ lại và thể hiện quan hệ giữa các cấp.

**Independent Test**: Với hai nhóm R1/R2 trong cùng nhánh, bấm từng ô khi chưa gõ, gõ rồi chọn; đối chiếu quan hệ nguồn của mọi gợi ý.

**Acceptance Scenarios**:

1. **Given** đã xác định lĩnh vực/lĩnh vực con, **When** bấm ô kết quả mong đợi, **Then** hiện ngay nhóm của đúng nhánh dù chưa gõ; gõ chỉ thu hẹp trong phạm vi đó.
2. **Given** chọn R1, **When** bấm ô mục tiêu, **Then** chỉ gợi ý mục tiêu thuộc R1, kể cả khi R2 có câu chữ trùng.
3. **Given** chọn R1, **When** bấm ô nội dung, **Then** gợi ý nội dung kèm các mục tiêu thuộc R1 ở mục D của Bộ; không suy quan hệ nhóm cho nội dung độc lập mục C.
4. **Given** đang xem/gõ tìm gợi ý, **When** chưa chọn/thêm, **Then** không tự thay dữ liệu trong bảng; khi chọn chỉ áp vào vị trí đích được chỉ định.
5. **Given** thiếu ngữ cảnh nguồn cấp cha, **When** bấm ô con, **Then** hướng dẫn chọn cha để dùng gợi ý; nhập tay tiếp tục theo quyền/quy tắc hiện có.

### User Story 3 - Giữ bản soạn khi thiếu dữ liệu hoặc tải lỗi (Priority: P2)

Người soạn hiểu vì sao không có gợi ý và thử lại mà không mất dữ liệu.

**Why this priority**: Tránh nhầm lẫn bộ lọc hỏng hoặc dùng nhầm kết quả cũ.

**Independent Test**: Dùng nhánh rỗng, tình huống tải lỗi và kết quả nhánh cũ đến muộn.

**Acceptance Scenarios**:

1. **Given** nhánh rỗng, **When** mở gợi ý, **Then** thông báo không có dữ liệu trong phạm vi đó, không tự mở rộng sang nhánh khác.
2. **Given** đã chuyển A1 sang A2, **When** kết quả A1 đến muộn, **Then** vẫn giữ ngữ cảnh A2.
3. **Given** tải lỗi, **When** thử lại, **Then** giữ bản soạn và không hồi sinh lựa chọn ngoài phạm vi.
4. **Given** chỉ có quyền xem hoặc đang soạn tổ với cây năm kế thừa, **When** xem dữ liệu, **Then** không mở quyền sửa/thêm cây nguồn.

### Edge Cases

- Lĩnh vực không có lĩnh vực con: dùng nhánh trực tiếp, không tạo cấp giả; lưu/gửi giữ EDU-53.
- Mục tiêu cũ không có nhóm: giữ dữ liệu, không giả liên kết nhóm; chỉ xuất hiện khi quan hệ thực phù hợp bộ lọc.
- Tên nhóm/mục tiêu trùng ở các nhánh: phân biệt bằng quan hệ nguồn, không ghép chỉ theo tên.
- Xóa bộ lọc cha/đổi ngữ cảnh nhóm tuổi hoặc kế hoạch: đánh giá lại mọi lựa chọn con trước khi thêm.
- Tên cha nhập tay chưa gắn nguồn Bộ: hướng dẫn chọn ngữ cảnh nguồn, không tự đoán theo tên.
- Không có nguồn cho nhóm tuổi: báo rõ và giữ nhập tay theo quyền.
- Nội dung đã thêm và dấu tích chủ đề giữ nguyên khi đổi bộ lọc.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001** (EDU-09/28/61): Thứ tự chọn/hiển thị là Lĩnh vực → Lĩnh vực con → Kết quả mong đợi → Mục tiêu; nội dung nằm dưới mục tiêu.
- **FR-002** (EDU-61): Mọi danh sách thỏa nhóm tuổi kế hoạch và các cha đang chọn; lĩnh vực giới hạn toàn lĩnh vực, lĩnh vực con thu hẹp đúng nhánh, nhóm thu hẹp đúng nhóm.
- **FR-003** (EDU-61): Danh sách mục tiêu đích nhận nội dung phải theo cùng ngữ cảnh cha, bao gồm luồng gợi ý nội dung mục C hiện có.
- **FR-004** (EDU-61): Đổi ngữ cảnh phải bỏ lựa chọn con không hợp lệ, giữ lựa chọn còn hợp lệ; không tự thay bằng mục đầu.
- **FR-005** (EDU-22/61): Chặn thêm khi chưa có đích hợp lệ; đổi bộ lọc không xóa/thay nội dung đã thêm hoặc tự lưu.
- **FR-006** (EDU-61): Bấm ô kết quả mong đợi mở gợi ý ngay theo lĩnh vực/con; gõ để lọc tiếp trong phạm vi đó.
- **FR-007** (EDU-61): Bấm ô mục tiêu mở gợi ý ngay theo nhóm kết quả mong đợi nguồn đang chọn; gõ để lọc tiếp.
- **FR-008** (EDU-28/61): Bấm ô nội dung mở gợi ý ngay từ nội dung kèm các mục tiêu thuộc nhóm nguồn đang chọn ở mục D; gõ để lọc tiếp. Không tự gắn mục C với nhóm; luồng mục C giữ phạm vi lĩnh vực/con và tuân thủ FR-003..005.
- **FR-009** (EDU-61): Phân biệt thiếu cha, rỗng, đang tải, lỗi tải; không mở rộng phạm vi ngầm; dữ liệu tải cũ không được thay ngữ cảnh mới.
- **FR-010** (EDU-22/28): Chọn/thêm có chủ đích mới áp gợi ý vào đích; giữ nguồn, nhãn, bỏ trùng và lưu tay theo cơ chế hiện có; không tự tích chủ đề hoặc sửa dòng khác.
- **FR-011** (EDU-54/55/60): Giữ quyền, phân công, phạm vi và trạng thái hồ sơ; không mở sửa cây năm kế thừa ở tổ hoặc bản chỉ xem.
- **FR-012** (EDU-09/53/61): Hỗ trợ cấp cha tùy chọn trong dữ liệu hiện có; phân biệt chưa lọc với nhánh không có cấp đó; không tạo cấp giả hoặc ép sửa dữ liệu cũ.

### Key Entities *(include if feature involves data)*

- **Ngữ cảnh lựa chọn**: nhóm tuổi, lĩnh vực, lĩnh vực con, nhóm nguồn và mục tiêu đích hợp lệ.
- **Lĩnh vực/lĩnh vực con**: phạm vi phân loại của nguồn và kế hoạch; cấp con có thể vắng.
- **Kết quả mong đợi**: nhóm mục tiêu trong nhánh nguồn, khác với câu nhập tay chưa gắn nguồn.
- **Mục tiêu**: mục tiêu nguồn của nhóm hoặc mục tiêu đích trong bản soạn.
- **Nội dung giáo dục**: nội dung kèm mục tiêu mục D dùng cho gợi ý theo nhóm; mục C độc lập theo lĩnh vực/con.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% ca đổi cha không còn mục tiêu ngoài phạm vi có thể chọn làm đích.
- **SC-002**: 100% ca đổi bộ lọc giữ nguyên số dòng, câu chữ và dấu tích đã có trong bản soạn.
- **SC-003**: Cả ba ô nhập mở gợi ý bằng một lần bấm khi đủ ngữ cảnh, không cần gõ; 100% gợi ý đúng cha/nhóm tuổi.
- **SC-004**: Hoàn thành chuỗi chọn nhóm → mục tiêu → nội dung từ nguồn có sẵn mà không chép/gõ lại câu nguồn, kiểm với ít nhất hai nhánh độc lập.

## Assumptions

- Phạm vi là luồng lấy từ Bộ và ô nhập của bản kế hoạch được phép sửa cây, dùng lại nguồn/nhóm tuổi EDU-28; không thêm AI/nguồn/quyền mới.
- Tìm chữ chỉ thu hẹp phạm vi cha; cách chuẩn hóa chữ theo convention sản phẩm khi planning.
- Tác động Web là rõ; Mobile cần khảo sát consumer ở plan, chưa khẳng định có cùng màn hình.
- Làn L khi triển khai do tác động client; cần plan được duyệt và đánh giá tác động trước đổi contract. User đã cho phép sửa giáo dục CareNest_FE cho014 ngày08/11/2026; đã duyệt triển khai plan014 qua vòng implement→converge ngày08/11/2026; chưa cấp quyền APP, delta sửa catalog nguồn có plan riêng chờ duyệt.

## Clarifications

### Session 2026-11-08

- Q1: Đổi cha làm mục tiêu đã chọn không phù hợp? → User: Bỏ lựa chọn không phù hợp, giữ dữ liệu đã thêm.
- Q2: Nguồn nội dung theo kết quả mong đợi? → User: Lấy nội dung kèm các mục tiêu thuộc kết quả mong đợi đó.
- Q3: “Khi ấn nhập”? → User: Bấm ô là hiện gợi ý rồi gõ để lọc.

Nguồn: yêu cầu và ba câu trả lời trực tiếp user ngày 08/11/2026. EDU-61 PROPOSED theo quy ước chưa có nguồn team/trường; ba câu trả lời đã chốt cho đặc tả, không còn câu hỏi nghiệp vụ chặn planning.
