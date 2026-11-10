# Feature Specification: Ba bảng mục tiêu cha–con
**Feature Branch**: `feature/G94-226-BE-FEAT-09-school-year-goals`
**Created**: 2026-10-29
**Status**: Approved — user duyệt plan và yêu cầu vòng lặp Spec Kit, 29/10/2026.
**Input**: Tách lĩnh vực con, nhóm kết quả mong đợi, mục tiêu thành ba bảng cha–con; giữ cấp cha tùy chọn.

## User Scenarios & Testing
### User Story 1 — Giữ dữ liệu kế hoạch cũ (Priority: P1)
Tổ trưởng mở và tiếp tục soạn kế hoạch sau nâng cấp với đủ chữ, thứ tự, nội dung và chủ đề cũ.
**Independent Test**: Nâng cấp dữ liệu phiên bản cũ rồi đọc và lưu lại kế hoạch.
**Acceptance Scenarios**:
1. Given nhiều lĩnh vực con/nhóm/MT, When nâng cấp, Then đúng cha và giữ định danh từng dòng.
2. Given MT không có nhóm hoặc lĩnh vực con, When nâng cấp/lưu, Then không thêm tiêu đề giả, không mất MT.
### User Story 2 — Sửa và xem lại cây mục tiêu (Priority: P1)
Tổ trưởng thêm, bỏ, chuyển tiêu đề và mục tiêu mà không làm mất nội dung còn giữ lại.
**Independent Test**: Lưu, mở lại, kiểm tra quan hệ và số MT xuyên lĩnh vực.
**Acceptance Scenarios**:
1. Given nhóm/MT được chuyển, When lưu, Then đúng cha mới và giữ ID, nội dung/dấu tích.
2. Given ID dòng thuộc kế hoạch/loại khác hoặc ID lặp, When lưu, Then từ chối toàn bộ lần ghi.
### User Story 3 — Phiên bản và tài liệu lớp (Priority: P1)
Giáo viên tiếp tục dùng mục tiêu và học liệu; PHT tạo bản điều chỉnh mà không đổi bản ban hành.
**Independent Test**: Chép phiên bản, so sánh và dùng lại tham chiếu mục tiêu.
**Acceptance Scenarios**:
1. Given bản ban hành, When tạo phiên bản, Then chép đúng cha và lineage, bản cũ bất biến.
2. Given giáo án/học liệu đang tham chiếu MT, When nâng cấp, Then tham chiếu còn đúng và quyền xem vẫn kiểm tại server.

### Edge Cases
- Nhóm đứng trước lĩnh vực con đầu tiên; mục tiêu trực tiếp dưới lĩnh vực hoặc dưới lĩnh vực con.
- Xóa tiêu đề nhưng giữ/chuyển các MT trong cùng lần lưu; nhóm đổi lĩnh vực con.
- ID/lineage trên nhiều phiên bản; dữ liệu cũ có nội dung gắn tiêu đề phải bị báo lỗi khi nâng cấp.
- Tiêu đề rỗng vẫn theo hành vi lưu nháp cũ: bỏ qua, không tự tạo cấp cha.

## Requirements
### Functional Requirements
- **FR-001** (EDU-09): Lưu ba loại bằng ba bảng riêng và quan hệ cha–con bằng khóa ngoại; cấp cha tùy chọn.
- **FR-002** (EDU-09): Nâng cấp giữ 100% ID, lineage, chữ, thứ tự, số MT, nội dung và dấu tích hợp lệ.
- **FR-003** (EDU-09): Lưu/cập nhật/xóa/chuyển dòng phải toàn vẹn và không mất dữ liệu được giữ trong request.
- **FR-004** (EDU-09): Từ chối tham chiếu sai kế hoạch/lĩnh vực/loại, ID lặp và quan hệ nhóm/lĩnh vực con không khớp.
- **FR-005** (EDU-25/26): Sao chép và so sánh phiên bản giữ đúng lineage và ánh xạ ba cấp.
- **FR-006** (EDU-24/28): Giữ liên kết nguồn Bộ, MT trong giáo án và học liệu.
- **FR-007** (EDU-06/10/22): Giữ quyền, khóa sửa, revision, lưu tay và giao tiếp client hiện tại.
- **FR-008**: Tài liệu CSDL, sơ đồ và hướng dẫn kiểm chứng phải khớp source mới.
### Key Entities
Lĩnh vực con, nhóm kết quả mong đợi, mục tiêu, nội dung giáo dục, kế hoạch năm có phiên bản.
## Success Criteria
- **SC-001**: 100% dòng hợp lệ của bộ dữ liệu nâng cấp được giữ nguyên định danh và nội dung.
- **SC-002**: Các trường hợp thiếu cấp cha và chuyển nhóm lưu/mở lại thành công, không tạo tiêu đề giả.
- **SC-003**: Không có tham chiếu giáo án/học liệu hợp lệ nào mất sau nâng cấp; phiên bản cũ không đổi.
- **SC-004**: Mọi trường hợp tham chiếu sai được kiểm thử đều bị từ chối toàn bộ lần ghi.
## Clarifications
- 29/10: User chọn giữ cấp cha tùy chọn, sau đó nhắc lại đúng ba bảng cha–con.
- 29/10: User duyệt kế hoạch và yêu cầu triển khai theo vòng lặp spec.

## Delta02/11 — approved nghiệp vụ và plan
User đã chọn “Duyệt kế hoạch và triển khai tới khi sạch” sau khi chấp nhận xóa toàn nhánh và ô Lĩnh vực ellipsis. Delta thuộc D2 của plan quản trị, không dùng evidence DB29/10 cho code mới.
### Clarifications
Xóa lĩnh vực con ở Web phải xóa cả nhóm, goal trực tiếp/thuộc nhóm, content/tick thuộc nhánh; có xác nhận số lượng, Hủy giữ form, các nhánh/chủ đề khác giữ nguyên. PUT vẫn cho giữ/chuyển cha có chủ đích. Ô Lĩnh vực trong lấy từ Bộ dùng một dòng/ellipsis như ô Thêm vào mục tiêu, đủ title/value/option.
### User Story 4 — Xóa cả nhánh (P1)
Người soạn có quyền xóa nhánh sau khi xem ảnh hưởng. Independent test: nhiều nhánh/cùng tên/goal trực tiếp/nhóm rỗng; mở, Hủy, xác nhận, normalize, đánh số/tick và lưu/mở lại.
### User Story 5 — Tên lĩnh vực dài (P2)
Người soạn chọn lĩnh vực dài tại360/390/768/1440px, ô một dòng/ellipsis nhưng title/option/value đầy đủ, bàn phím và bộ lọc hoạt động.
### Functional Requirements mới
- **FR-009** (EDU-48): Sau xác nhận, xóa đúng nhánh lĩnh vực con gồm nhóm/goal trực tiếp hoặc thuộc nhóm/content/tick; giữ nhánh khác.
- **FR-010** (EDU-48): Xác nhận nêu tên và số nhóm/goal/content/tick sẽ xóa; mở/Hủy không đổi form.
- **FR-011** (EDU-09/48): Đúng nhánh đầu/giữa/cuối/duy nhất/rỗng, ID không có/cùng tên khác ID và cha tùy chọn; không mutate hoặc đổi cha phần giữ sau normalize.
- **FR-012** (EDU-06/10/22): Giữ quyền/read-only/lease/revision/lưu tay; chỉ ghi khi Lưu; chủ đề/đợt nghỉ/nguồn Bộ không bị xóa.
- **FR-013**: Ô Lĩnh vực trong lấy từ Bộ một dòng/ellipsis khi dài, title đủ nhãn, không cắt dữ liệu value/option.
- **FR-014**: Dùng cơ chế cắt chữ của ô Thêm vào mục tiêu, giữ target/filter/keyboard, không thư viện mới; không tràn body360/390/768/1440.
- **FR-015**: Tách evidence docs/analyze/RED-GREEN/browser/verify/converge; không nhận sạch khi còn task/gap hoặc persistence chưa kiểm.
### Success Criteria mới
- **SC-005**: Fixture xóa nhánh giữ100%ID/text/tick/thứ tự ngoài nhánh; con bị xóa không quay lại sau normalize.
- **SC-006**: Counts xác nhận đúng; Hủy giữ100%form; read-only không có thùng rác.
- **SC-007**: Select lĩnh vực một dòng/ellipsis/title/value/option/filter đúng ở4viewport, target và keyboard không hồi quy.
- **SC-008**: Analyze không blocker trước code; converge cuối giữ tasks byte-for-byte và không gap; giữ lịch sử T001..T011.
### Edge cases
Nhánh cuối không có mốc kết thúc; nhánh rỗng/duy nhất; goal không nhóm; nhóm không goal; ID mất; cùng tên khác ID; content nhiều tick; chủ đề giữ nguyên; lựa chọn mặc định theo target.
