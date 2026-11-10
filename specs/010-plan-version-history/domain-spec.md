# Domain spec — Lịch sử phiên bản khi soạn kế hoạch giáo dục

Nguồn: Tiền 03/11/2026 (ảnh nút đồng hồ có chấm xanh, "giống Docs"); trả lời 6 câu cùng ngày: 1b không đổi DB · 2 cả 4 màn soạn · 3 giữ 2 bản mới nhất · 4 chỉ người soạn xem · 5 tô màu khác biệt · 6 dùng bản cũ là thay toàn bộ.

## 1. Domain Overview
Các màn soạn kế hoạch giáo dục chỉ ghi lên hệ thống khi bấm Lưu (EDU-22). Bản đang soạn chưa lưu được giữ ẩn trên máy để phòng mất điện/đóng tab. Thay thông báo "Có nội dung chưa lưu… Khôi phục/Bỏ" bằng nút **Lịch sử phiên bản**: xem bản đã lưu hiện tại và tối đa 2 bản chưa lưu mới nhất trên máy, so khác biệt, chọn dùng lại.

## 2. Scope
- Trong: Kế hoạch năm học (tạo/sửa), Chủ đề nhánh, Kế hoạch chủ đề, Giáo án tuần/ngày. Chỉ Web.
- Ngoài: lưu nhiều bản đã lưu trên server (Tiền chọn 1b — không đổi DB); Cấu hình kế hoạch; các màn chỉ xem; module khác; so sánh giữa hai bản cũ với nhau.

## 3. Actors & Roles
- Người soạn tài liệu (Phó HT/Tổ trưởng với kế hoạch năm; giáo viên của lớp với tài liệu lớp) đang có quyền sửa: thấy nút, xem, dùng bản cũ.
- Người chỉ xem: không thấy nút. Bản giữ trên máy theo từng tài khoản ⇒ mỗi người chỉ thấy bản mình soạn.

## 4. Domain Concepts
- **Bản đã lưu hiện tại**: nội dung tài liệu trên hệ thống lúc mở/vừa lưu.
- **Mốc chưa lưu**: một lần soạn (một lần mở màn soạn, tới khi Lưu) chưa ghi lên hệ thống, giữ trên máy, cập nhật liên tục trong lần soạn đó.
- **Khác biệt**: các dòng nội dung thêm/bỏ giữa mốc được chọn và bản đang mở.

## 5. Domain Relationships
Một tài liệu + một tài khoản ⇒ 0..2 mốc chưa lưu trên một máy. Mốc gắn "mốc phiên bản" của bản trên hệ thống lúc giữ (tài liệu lớp: id/trạng thái/số lịch sử/revision; kế hoạch năm: revision).

## 6. User Requirements
- UR1 Không còn thông báo chặn form khi mở lại; form mở bản trên hệ thống.
- UR2 Nút đồng hồ cạnh trạng thái lưu, chấm xanh khi có mốc chưa lưu dùng được.
- UR3 Danh sách: Bản đã lưu hiện tại + tối đa 2 mốc chưa lưu (giờ, nhãn Đã lưu/Chưa lưu).
- UR4 Chọn mốc: xem nội dung, tô màu thêm (xanh) / bỏ (đỏ gạch) so với bản đang mở.
- UR5 "Dùng bản này" thay toàn bộ nội dung đang soạn; vẫn phải bấm Lưu.

## 7. Business Rules
- Giữ EDU-22 (lưu tay) và các quy tắc xóa bản giữ hiện có: Lưu thành công, gửi/ban hành, "Không lưu" khi rời màn ⇒ xóa mốc của lần soạn đó; tài liệu lớp có mốc phiên bản trên hệ thống đổi ⇒ bỏ mốc cũ.
- Kế hoạch năm: mốc giữ khi revision trên hệ thống khác hiện tại ⇒ chỉ xem/đối chiếu, không dùng được (giữ hành vi hiện tại, EDU-10).
- Không ghi gì lên hệ thống khi xem/dùng mốc.

## 8. Business Processes
Mở màn soạn ⇒ (ngoài hệ thống: người dùng soạn) ⇒ hệ thống giữ mốc 750 ms/lần và khi rời/đóng tab ⇒ mở lại ⇒ bấm đồng hồ ⇒ chọn mốc ⇒ xem khác biệt ⇒ Dùng bản này ⇒ Lưu.

## 9. State / Lifecycle
Mốc: tạo khi lần soạn có thay đổi chưa lưu ⇒ cập nhật trong lần soạn ⇒ xóa khi Lưu/gửi/Không lưu, hoặc bị đẩy ra khi có mốc thứ 3, hoặc bỏ khi bản trên hệ thống đổi (tài liệu lớp).

## 10. Business Constraints
Tối đa 2 mốc mỗi tài liệu/tài khoản/máy; ghi bộ nhớ máy lỗi (đầy, chế độ riêng tư) thì bỏ qua, vẫn lưu tay được.

## 11. Non-Functional Requirements
Không đổi DB/API/dependency; không chữ nào nói "tự lưu"; panel dùng được ở 375 px; so khác biệt bảng 200 dòng ≤ 300 ms.

## 12. Acceptance Criteria
Xem spec.md (AC theo User Story).
