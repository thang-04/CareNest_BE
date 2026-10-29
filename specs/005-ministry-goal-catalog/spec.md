# Feature Specification: Lấy mục tiêu năm học từ Chương trình của Bộ

**Feature Branch**: `feature/G94-226-BE-FEAT-09-school-year-goals` (BE) · `feature/G94-178-FE-FEAT-41-lesson-assessment-parent` (FE) — giữ nhánh hiện tại; chưa commit

**Created**: 2026-10-28

**Status**: Implemented — Tiền chốt 1A, 2B, 3A, 4A, 5A, 6 OK (28/10/2026); chờ team duyệt nguyên văn dữ liệu và commit

**Input**: Tiền 28/10/2026: "Tôi muốn cập nhật theo Bộ bằng một nút là lấy mục tiêu năm học từ Bộ. Và khi lấy nó cũng phải có một trang thông tin hay link để mình có thể đối chiếu xem các trang của Bộ thế nào, để đảm bảo sự minh bạch." "Cần làm giống theo Thái Dương." "Làm luôn phần này rồi test cả với task trước ở phần cho sửa kế hoạch sai ban hành" (004-year-plan-versioning).

## Clarifications

### Session 2026-10-28

- Q: Ánh xạ dữ liệu của Bộ vào bảng mục tiêu? → A (Tiền chốt "giống Thái Dương"): tiêu đề kết quả mong đợi của Bộ (vd. "2. Thể hiện kỹ năng vận động cơ bản…") → nhóm kết quả mong đợi; câu đánh số trong ô độ tuổi (vd. "2.1. Giữ được thăng bằng cơ thể khi thực hiện vận động") → mục tiêu; các gạch đầu dòng chỉ báo bên dưới câu đó → nội dung giáo dục của mục tiêu; ô không có gạch đầu dòng → mục tiêu không kèm nội dung. 5 lĩnh vực I–V và lĩnh vực con như bảng Thái Dương (vận động / dinh dưỡng – sức khỏe; khám phá khoa học / làm quen toán / khám phá xã hội).
- Q: Nhà trẻ, Bộ gộp "Tình cảm, kỹ năng xã hội và thẩm mỹ"? → A (Tiền chốt): tách theo nhóm kết quả: nhóm 4 (hát, vận động theo nhạc, tô màu, vẽ, nặn…) vào Phát triển thẩm mỹ, nhóm 1–3 vào Phát triển tình cảm và kỹ năng xã hội; trang nguồn vẫn ghi mục gốc "IV". Giả định chưa có kế hoạch nhà trẻ của Thái Dương để đối chiếu (A3).
- Q: Văn bản nguồn? → A (Tiền chốt 1A): Chương trình GDMN hiện hành — Văn bản hợp nhất 01/VBHN-BGDĐT ngày 13/4/2021 (hợp nhất TT 17/2009, TT 28/2016, TT 51/2020), mục D "Kết quả mong đợi": nhà trẻ cột 24–36 tháng; mẫu giáo cột 3–4, 4–5, 5–6 tuổi. Lấy câu chữ đang có hiệu lực (đã gồm sửa đổi), bỏ đoạn đã bãi bỏ. Không dùng Bộ chuẩn trẻ 5 tuổi (QĐ 4222/2024) và chương trình thí điểm (QĐ 388/2026) ở bản này; thiết kế cho phép thêm nguồn khác sau.
- Q: Cách lấy? → A (Tiền chốt 3A): giống "Lấy từ kế hoạch có sẵn" (EDU-24): hộp chọn theo nhóm tuổi của kế hoạch, tích từng mục tiêu / cả nhóm / cả lĩnh vực / tất cả; thêm vào cuối, bỏ qua mục tiêu đã có; không xóa gì đang có.
- Q: Minh bạch? → A (Tiền chốt 4A): trang "Nguồn chương trình của Bộ" ghi tên văn bản, số hiệu, ngày, cơ quan, đường dẫn bản gốc chính thức, nơi đã lấy dữ liệu và ngày cập nhật; liệt kê toàn bộ mục theo nhóm tuổi kèm tham chiếu mục trong văn bản. Hộp chọn có liên kết tới trang này; mục tiêu đã lấy hiện nhãn nhỏ "Theo Bộ" bấm ra đúng mục.
- Q: Sửa câu sau khi lấy? → A (Tiền chốt 5A): sửa được như mục tiêu thường; còn giống nguyên văn thì nhãn "Theo Bộ", khác thì "Đã sửa từ Bộ" (vẫn bấm ra nguồn).

- Q: Có lấy cả "C. Nội dung giáo dục theo độ tuổi" của Bộ không? → A (Tiền chốt 2B): có, làm danh sách GỢI Ý nội dung (không tự chèn): lọc theo nhóm tuổi + lĩnh vực/lĩnh vực con, người soạn chọn một mục tiêu đích rồi tích gợi ý để thêm thành nội dung của mục tiêu đó; nguyên văn, có tham chiếu mục; hiện cả trên trang nguồn.
- Q: Dữ liệu chép từ văn bản? → A (Tiền chốt 6): Claude tách tự động nguyên văn từ toàn văn VBHN 01/2021, nhóm soát lại trước demo.
- Q: Lĩnh vực đích đã có mục tiêu tự gõ không nằm trong lĩnh vực con nào, mà mục tiêu của Bộ có lĩnh vực con? → A (Tiền chốt A, 28/10/2026, phát hiện khi chạy Playwright): giữ luật EDU-24 — không thêm mục tiêu đó, báo "Chưa thêm được…" kèm lý do, dữ liệu đang có giữ nguyên; người soạn tự xếp lại (đưa mục tiêu cũ vào lĩnh vực con) rồi lấy lại.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Lấy mục tiêu từ Bộ vào kế hoạch đang soạn (Priority: P1)

Phó HT hoặc Tổ trưởng nhóm tuổi đang soạn kế hoạch năm (Nháp, Bị trả lại, Chờ Phó HT với Phó HT, hoặc phiên bản mới v2+ của kế hoạch đã ban hành) bấm "Lấy từ Chương trình của Bộ" ở bước Mục tiêu, nội dung và chủ đề. Hộp chọn hiện đúng nhóm tuổi của kế hoạch, chia theo lĩnh vực > lĩnh vực con > nhóm kết quả mong đợi > mục tiêu (kèm nội dung). Người dùng tích rồi bấm Thêm.

**Why this priority**: Giá trị chính: không phải gõ tay khung mục tiêu chuẩn của Bộ.

**Independent Test**: Kế hoạch MG bé trống, lấy tất cả ⇒ bảng có đủ 5 lĩnh vực, lĩnh vực con, nhóm và 84 mục tiêu đúng thứ tự văn bản; lưu, mở lại vẫn đủ.

**Acceptance Scenarios**:

1. **Given** kế hoạch MG bé đang sửa, **When** mở hộp, **Then** chỉ thấy dữ liệu 3–4 tuổi, đúng câu chữ của Bộ, có số mục (vd. 2.1) và tham chiếu mục.
2. **Given** tích "2.1 Giữ được thăng bằng…", **When** Thêm, **Then** bảng có (nếu chưa có) lĩnh vực con "Phát triển vận động", nhóm "Thể hiện kỹ năng vận động cơ bản và các tố chất trong vận động", mục tiêu 2.1 và 2 nội dung "Đi hết đoạn đường hẹp (3m x 0,2m).", "Đi kiễng gót liên tục 3m."; nội dung chưa tích chủ đề.
3. **Given** kế hoạch đã có một mục tiêu cùng câu trong cùng lĩnh vực, **When** lấy lại, **Then** bỏ qua mục đó, báo "Đã thêm X, bỏ qua Y đã có".
4. **Given** đã chọn, **When** Hủy, **Then** bảng không đổi.
5. **Given** vừa thêm, **When** chưa Lưu, **Then** là thay đổi chưa lưu (cảnh báo rời màn, Lưu/Ctrl+S như EDU-22).
6. **Given** kế hoạch đã ban hành, **When** người dùng tạo phiên bản mới (004) rồi lấy từ Bộ và gửi/ban hành, **Then** mục tiêu mới có ở phiên bản mới; phiên bản cũ không đổi; tài liệu lớp nhận nhãn "mới" theo 004.

### User Story 2 - Trang nguồn để đối chiếu (Priority: P1)

Mọi người dùng kế hoạch giáo dục mở trang "Nguồn chương trình của Bộ" (từ hộp chọn, từ nhãn "Theo Bộ" hoặc menu) để xem văn bản gốc và toàn bộ danh mục theo nhóm tuổi kèm tham chiếu mục.

**Why this priority**: Yêu cầu minh bạch của Tiền; giáo viên/người duyệt cần tự kiểm tra.

**Independent Test**: Mở trang, chọn MG nhỡ, tìm mục "2.1" lĩnh vực thể chất, bấm link văn bản gốc mở đúng văn bản 01/VBHN-BGDĐT.

**Acceptance Scenarios**:

1. **Given** mở trang, **Then** thấy tên văn bản, số hiệu 01/VBHN-BGDĐT, ngày 13/4/2021, cơ quan Bộ GDĐT, link bản gốc chính thức và link nơi đã lấy dữ liệu, ngày CareNest cập nhật, ghi chú phạm vi (mục D, độ tuổi, bỏ đoạn đã bãi bỏ).
2. **Given** chọn nhóm tuổi, **Then** thấy danh sách theo lĩnh vực > lĩnh vực con > nhóm > mục tiêu > nội dung, mỗi mục tiêu có tham chiếu mục.
3. **Given** bấm nhãn "Theo Bộ" trên một mục tiêu, **Then** mở trang ở đúng nhóm tuổi và cuộn tới mục tiêu đó.

### User Story 3 - Nhận biết mục tiêu lấy từ Bộ (Priority: P2)

Trên bảng mục tiêu (soạn, chi tiết), mục tiêu lấy từ Bộ có nhãn "Theo Bộ" (hoặc "Đã sửa từ Bộ" khi câu đã khác nguyên văn).

**Acceptance Scenarios**:

1. **Given** mục tiêu lấy từ Bộ chưa sửa, **Then** nhãn "Theo Bộ".
2. **Given** sửa câu mục tiêu, **Then** nhãn đổi "Đã sửa từ Bộ" sau khi lưu.
3. **Given** tạo phiên bản mới (004) hoặc lấy mục tiêu từ kế hoạch có sẵn (EDU-24), **Then** nguồn Bộ của mục tiêu được giữ.

### User Story 4 - Gợi ý nội dung giáo dục của Bộ (Priority: P2)

Trong hộp "Lấy từ Chương trình của Bộ", thẻ "Gợi ý nội dung" liệt kê mục C "Nội dung giáo dục theo độ tuổi" của Bộ đúng nhóm tuổi, lọc theo lĩnh vực/lĩnh vực con. Người soạn chọn một mục tiêu đích có sẵn trong bản đang soạn rồi tích các gợi ý để thêm thành nội dung của mục tiêu đó.

**Acceptance Scenarios**:

1. **Given** kế hoạch MG bé có mục tiêu thuộc lĩnh vực thể chất, **When** chọn mục tiêu đó và tích "Đi và chạy: Đi kiễng gót.", **Then** mục tiêu có thêm nội dung "Đi kiễng gót." (nhãn "Đi và chạy" chỉ để hiển thị gợi ý), chưa tích chủ đề.
2. **Given** nội dung trùng đã có ở mục tiêu, **Then** bỏ qua, báo số bỏ qua.
3. **Given** chưa chọn mục tiêu đích, **Then** không thêm được.

### Edge Cases

- Nhóm tuổi không có dữ liệu: hộp báo "Chưa có dữ liệu của Bộ cho nhóm tuổi này", không thêm gì.
- Lĩnh vực của Bộ không có/không hoạt động trong cấu hình trường: báo mục nào chưa thêm được, không tự tạo lĩnh vực.
- Lĩnh vực đích đã có mục tiêu nằm ngoài lĩnh vực con: nếu thêm lĩnh vực con của Bộ làm đổi ngữ cảnh mục tiêu cũ thì không thêm mục đó, báo "chưa thêm được" để người soạn tự đặt (cùng quy tắc EDU-24).
- Kế hoạch đã ban hành/không có quyền sửa/bị người khác giữ khóa: nút không dùng được; luật sửa hiện hành không đổi.
- Lỗi tải danh mục: báo lỗi, thử lại; không mất nội dung đang soạn.
- Danh mục của Bộ được cập nhật sau này: mục tiêu đã lấy giữ câu tại thời điểm lấy; liên kết nguồn vẫn trỏ đúng mục.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Có nút "Lấy từ Chương trình của Bộ" cạnh "Lấy từ kế hoạch có sẵn" ở bước 2 khi người dùng đang sửa được kế hoạch (cùng điều kiện EDU-03/10/24, gồm phiên bản mới của 004).
- **FR-002**: Hộp chọn chỉ hiện danh mục đúng nhóm tuổi của kế hoạch đích, theo cấu trúc 5 tầng và thứ tự trong văn bản; tích từng mục tiêu, cả nhóm, cả lĩnh vực, chọn/bỏ tất cả; hiện số đã chọn; chỉ Thêm khi đã chọn ≥1.
- **FR-003**: Thêm giữ nguyên văn câu của Bộ; tạo lĩnh vực con và nhóm kết quả mong đợi khi chưa có (so khớp theo câu, bỏ khoảng trắng thừa), nối mục tiêu vào cuối nhóm; nội dung chưa tích chủ đề; bỏ qua mục tiêu trùng câu trong cùng lĩnh vực; báo số thêm/bỏ qua/không thêm được.
- **FR-004**: Thêm chỉ đổi bản đang soạn; lưu, phiên sửa, revision, gửi/ban hành theo luật hiện hành; không ghi gì lên hệ thống khi chưa bấm Lưu.
- **FR-005**: Lưu cho mỗi mục tiêu được lấy liên kết tới mục nguồn của Bộ; liên kết đi theo khi lưu, khi tạo phiên bản mới (004) và khi chép bằng EDU-24; mất liên kết khi mục nguồn bị xóa khỏi danh mục thì vẫn giữ câu đã lấy.
- **FR-006**: Nhãn "Theo Bộ"/"Đã sửa từ Bộ" trên mục tiêu có liên kết; bấm mở trang nguồn đúng mục.
- **FR-007**: Trang "Nguồn chương trình của Bộ" (mọi vai trò có quyền xem kế hoạch giáo dục): thông tin văn bản (tên, số hiệu, ngày, cơ quan, link gốc chính thức, link nơi lấy dữ liệu, ngày cập nhật, ghi chú phạm vi) và danh mục theo nhóm tuổi kèm tham chiếu mục.
- **FR-008**: Danh mục là dữ liệu hệ thống (nạp bằng migration), chỉ đọc qua API; không có màn sửa danh mục ở bản này.
- **FR-009**: Không tự tạo/bật lĩnh vực; dùng lĩnh vực chuẩn theo tên.
- **FR-010**: Lỗi theo chuẩn mã lỗi (ApiCode/errorCode); câu tiếng Việt ngắn.
- **FR-012**: Gợi ý nội dung (mục C của Bộ) theo nhóm tuổi, lọc theo lĩnh vực/lĩnh vực con; thêm vào mục tiêu đích người dùng chọn; không tự chèn; nguyên văn; có tham chiếu mục; trang nguồn hiện cả danh sách gợi ý.
- **FR-011**: Có trạng thái đang tải/trống/lỗi; thao tác được bằng bàn phím; nhãn rõ, không chỉ dựa vào màu.

### Key Entities

- **Văn bản nguồn**: văn bản của Bộ dùng làm nguồn (mã, tên, số hiệu, ngày ban hành, cơ quan, link chính thức, link nơi lấy, ngày cập nhật, ghi chú phạm vi, đang dùng).
- **Mục của Bộ**: một dòng danh mục theo nhóm tuổi: lĩnh vực, lĩnh vực con, nhóm kết quả mong đợi (số + câu), mục tiêu (số + câu), danh sách nội dung, tham chiếu mục, thứ tự, văn bản nguồn.
- **Gợi ý nội dung của Bộ**: một dòng mục C theo nhóm tuổi: lĩnh vực, lĩnh vực con, mục nội dung (số + tên), nhãn nhóm (vd. "Đi và chạy"), câu nội dung, tham chiếu mục, thứ tự, văn bản nguồn.
- **Liên kết nguồn của mục tiêu**: mục tiêu trong kế hoạch năm trỏ tới mục của Bộ đã dùng để tạo.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Lập khung mục tiêu chuẩn cho một nhóm tuổi (tất cả mục tiêu của Bộ) trong dưới 1 phút, không gõ tay.
- **SC-002**: 100% câu nhóm/mục tiêu/nội dung/gợi ý nội dung được lấy khớp nguyên văn danh mục của Bộ (kiểm bằng test so sánh dữ liệu).
- **SC-003**: Từ bất kỳ mục tiêu "Theo Bộ" mở được trang nguồn đúng mục trong 1 lần bấm; từ trang nguồn mở được văn bản gốc.
- **SC-004**: Luồng 004 (ban hành → phiên bản mới → lấy từ Bộ → gửi → ban hành) chạy được đầu–cuối trên BE thật.

## Assumptions

- A1: Câu của Bộ lấy nguyên văn, không thêm chữ "Trẻ" ở đầu như cách trường hay viết; người soạn tự sửa nếu muốn (nhãn thành "Đã sửa từ Bộ").
- A2: Ô độ tuổi chỉ có gạch đầu dòng mà không có câu đánh số (hiếm) ⇒ gạch đầu dòng thuộc mục tiêu liền trước cùng nhóm (văn bản xuống hàng bảng).
- A3: Nhà trẻ: chưa có kế hoạch nhà trẻ Thái Dương để đối chiếu cách tách lĩnh vực IV.
- A4: Nguồn tải dữ liệu: bản HTML toàn văn tại caselaw.vn (ngày lấy 28/10/2026); link bản gốc chính thức trỏ Cơ sở dữ liệu quốc gia về văn bản pháp luật/moet.gov.vn. Cần nhóm soát lại trước khi demo.
- A5: Trường không thuộc diện thí điểm chương trình mới (QĐ 388/2026); nếu có, vẫn nhập tay như hiện tại.
