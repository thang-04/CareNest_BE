# Feature Specification: Lịch sử phiên bản khi soạn kế hoạch giáo dục

**Branch**: FE `feature/G94-178-FE-FEAT-41-lesson-assessment-parent` (chỉ Web; không đổi BE/DB)
**Created**: 2026-11-03 · **Status**: Implemented (xem plan.md Progress log)
**Domain spec**: [domain-spec.md](domain-spec.md)

## Clarifications

### Session 2026-11-03 (Tiền)
- Q: Có giữ nội dung từng lần đã Lưu trên server? → A: Không (1b) — không đổi DB; chỉ bản đã lưu hiện tại + bản chưa lưu trên máy.
- Q: Áp dụng trang nào? → A: Tất cả: Kế hoạch năm, Chủ đề nhánh, Kế hoạch chủ đề, Giáo án tuần/ngày.
- Q: Giữ bao nhiêu? → A: 2 bản (chưa lưu) mới nhất.
- Q: Ai xem? → A: Chỉ người soạn bản đó (bản giữ theo tài khoản; nút chỉ hiện khi đang sửa được).
- Q: Tô màu khác biệt? → A: Có.
- Q: Dùng bản cũ? → A: Thay toàn bộ nội dung, không ghép thêm/bớt.
- Mốc chưa lưu = một lần soạn (mở màn tới khi Lưu), cập nhật liên tục trong lần đó (thiết kế, không phải luật nghiệp vụ).

## User Scenarios

### US1 — Mở lại sau khi đóng tab chưa lưu (P1)
1. **Given** đã sửa rồi đóng tab chưa Lưu, **When** mở lại, **Then** không có thông báo chặn; form là bản trên hệ thống; nút Lịch sử phiên bản có chấm xanh.
2. **When** mở Lịch sử phiên bản, **Then** dòng đầu "Bản đã lưu hiện tại", dưới là mốc "Chưa lưu" (giờ).
3. **When** chọn mốc, **Then** thấy nội dung, dòng thêm tô xanh, dòng bỏ gạch đỏ so với bản đang mở.
4. **When** bấm Dùng bản này, **Then** toàn bộ nội dung thay bằng mốc, trạng thái "Có thay đổi chưa lưu", chưa ghi hệ thống; bấm Lưu thì ghi.

### US2 — Nhiều lần soạn (P1)
1. **Given** có 2 mốc chưa lưu, **When** lần soạn thứ 3 có thay đổi, **Then** chỉ giữ 2 mốc mới nhất.
2. **Given** Lưu thành công / gửi / ban hành / chọn Không lưu, **Then** mốc của lần soạn đó bị xóa.

### US3 — Bản hệ thống đã đổi (P2)
1. **Given** kế hoạch năm được lưu ở nơi khác sau khi giữ mốc, **When** xem mốc, **Then** chỉ xem/đối chiếu (có Tải nội dung), nút Dùng bản này khóa.
2. **Given** tài liệu lớp đã gửi/trả lại/duyệt sau khi giữ, **Then** mốc cũ bị bỏ.
3. **Given** không có quyền sửa, **Then** không hiện nút.

## Requirements
- FR-001 Bỏ DraftRecoveryNotice và khóa form ở 4 màn.
- FR-002 Nút icon lịch sử (aria-label "Lịch sử phiên bản"), chấm xanh khi có mốc chưa lưu dùng được.
- FR-003 SidePanel liệt kê bản đã lưu hiện tại + ≤2 mốc chưa lưu (mới nhất trước).
- FR-004 So khác biệt theo dòng nội dung (thêm/bỏ), không thêm dependency.
- FR-005 Dùng bản này thay toàn bộ, đánh dấu chưa lưu; khóa khi không dùng được.
- FR-006 Bộ nhớ máy: ≤2 mốc/tài liệu/tài khoản; giữ các quy tắc xóa hiện có.

## Success Criteria
- SC-001 Không còn chữ "Có nội dung chưa lưu từ lần soạn trước" ở các màn soạn.
- SC-002 Playwright BE thật: đóng tab chưa lưu ⇒ mở lại ⇒ dùng mốc ⇒ Lưu ⇒ đúng nội dung (kế hoạch năm và giáo án tuần).
