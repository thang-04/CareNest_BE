# Domain spec — Nhập chủ đề nhánh và Word .doc mục tiêu

Nguồn: user 01/11/2026, yêu cầu ban đầu và trả lời Q1–Q4; quy trình và mẫu Domain spec user cung cấp, giữ stack CareNest.

## 1. Domain Overview

Business Context: giáo viên có bảng tên nhánh trong Word, phải gõ lại vào tuần của lớp; bước 2 mục tiêu đã nhập bảng nhưng từ chối .doc nhị phân.
Business Goals: tái sử dụng tên, giảm nhập lại, giữ lịch kế hoạch năm và kiểm soát lưu/duyệt.
Target Users: giáo viên của lớp; PHT/Tổ trưởng có quyền nhập mục tiêu; người xem/duyệt theo quyền hiện tại.

## 2. Scope

In Scope: xem trước/nhập nhánh, xác nhận thay tên, dùng lịch hệ thống; luôn hiện Lưu/gửi duyệt; bổ sung .doc nhị phân cho cả nhánh và mục tiêu bước 2.
Out of Scope: lấy ngày/tuần/nghỉ/chữ ký từ file; đổi lịch, chuỗi duyệt, AI mới, Mobile, migration hoặc commit/push.

## 3. Actors & Roles

Giáo viên được phân công lớp: nhập/lưu/gửi nhánh khi trạng thái cho phép. Người duyệt: theo EDU-18, không được thêm quyền sửa chỉ vì thấy nút.
PHT/Tổ trưởng: nhập mục tiêu theo quyền hiện tại EDU-02/03/46. Quyền goals.edit không thay quyền giáo viên của lớp.
Server kiểm tra phân công, lớp, năm học/nhóm tuổi, kế hoạch đã ban hành hiện hành và trạng thái tài liệu.

## 4. Domain Concepts

Kế hoạch năm: nguồn chủ đề, lịch và phiên bản. Chủ đề: nhóm tuần học. Tuần học: một nhánh, Thứ Hai–Thứ Sáu, bỏ tuần nghỉ trọn tuần.
Bản xem trước: tên theo chủ đề/thứ tự, vị trí nguồn và cảnh báo, chưa lưu. Tài liệu nhánh: tên theo tuần, ghi chú và trạng thái duyệt.
Bảng mục tiêu: cây lĩnh vực/mục tiêu/nội dung/dấu tích theo EDU-46.

## 5. Domain Relationships

Lớp dùng kế hoạch đã ban hành đúng nhóm tuổi/năm học. Chủ đề chứa nhiều tuần; mỗi tuần có một tên nhánh của lớp.
Bản xem trước đề xuất tên cho các tuần được ghép, không sở hữu/sửa lịch. Tài liệu lớp tách theo phiên bản EDU-26.

## 6. User Requirements

US1 nhập nhánh; US2 giữ lịch; US3 luôn thấy Lưu/gửi; US4 nhập .doc ở bước 2.
FR-001..011 và kịch bản tại [spec.md](spec.md). Giữ nguyên ý nghĩa Thêm/Áp dụng vào nháp và Lưu ghi server.

Bổ sung 02/11/2026: FR-012/013 và SC-006 cho tải mẫu Word và đồng bộ modal với nhập mục tiêu; thuộc US1, không thay quy tắc ghép/lịch/quyền. File mẫu là bản xuất tạm phía Web, không thêm concept lưu trữ hoặc quan hệ DB.

## 7. Business Rules

EDU-12/14/18/26/39/40/41 CONFIRMED: scope lớp, một nhánh/tuần, chuỗi duyệt, phiên bản và lịch học/nghỉ.
EDU-46 CONFIRMED: mục tiêu bổ sung/bỏ trùng, lưu tay, thêm định dạng .doc theo user 01/11.
EDU-47 CONFIRMED: ghép theo chủ đề, báo không khớp/thừa, thiếu để trống, thay tên sau xác nhận; luôn hiện nút, khóa đúng điều kiện, giữ EDU-18.

## 8. Business Processes

Main Flow: [B] chuẩn bị file → [S] chọn/xem trước → [S] ghép vào lịch, báo cảnh báo → [B] xác nhận thay tên → [S] áp dụng vào nháp → [B] Lưu → [S] kiểm quyền và lưu → [B] gửi khi đủ điều kiện.
Exception: Hủy không đổi nháp; lỗi file không ghi; chủ đề lạ/tên thừa không tràn sang chủ đề khác; mất quyền/đổi phiên bản bị chặn. Chưa đủ tên hoặc AI chưa giữ/bỏ không gửi theo EDU-14.
Outcome: bảng tên nhánh theo lịch trường, có giáo viên kiểm tra và chuỗi duyệt hiện hành.

## 9. State / Lifecycle

Preview: chưa chọn → đang đọc → sẵn sàng/lỗi → áp dụng/Hủy; không thêm trạng thái DB.
Nhánh: DRAFT/REJECTED được sửa → submit theo EDU-18 → PENDING_TL/VP/HT → APPROVED hoặc REJECTED.
Lưu/gửi luôn hiện khi tài liệu đã tải, khóa theo quyền/trạng thái. Lifecycle mục tiêu và phiên sửa giữ nguyên.

## 10. Business Constraints

File không được đổi lịch, trạng thái, quyền hay chữ ký. Không xóa tên ở tuần không được ghép.
Giữ giới hạn 2 MB/2.000 dòng như EDU-46; giữ bảo vệ XML/ZIP, giới hạn trước khi cấp phát lớn cho OLE2; không chạy macro hay cài Office trên server.
Không dùng DB production thử file; fixture tổng hợp không chứa người thật.

## 11. Non-Functional Requirements

Security: xác thực/scope trước phân tích; giới hạn đọc; không log nội dung; lỗi theo contract CareNest.
Usability: modal giống bước 2, hiển thị tuần hệ thống/thay đổi/cảnh báo, Hủy rõ, nút khóa có lý do và hỗ trợ bàn phím.
Availability: không phụ thuộc AI/Office. Performance: có trạng thái đang đọc, hữu hạn theo giới hạn file; không tự hứa SLA mới chưa đo.

## 12. Acceptance Criteria

AC-1: mẫu tổng hợp nhận 35 tên đúng nhóm/ô gộp, không nhận dòng nghỉ/chữ ký.
AC-2: thay ngày/số tuần file không đổi tuần kết quả, không ghi lịch năm.
AC-3: thay tên cũ sau xác nhận, Hủy và tuần không được ghép giữ nguyên.
AC-4: bốn trạng thái/người xem luôn thấy hai nút, trái quyền bị chặn, giữ chuỗi.
AC-5: mục tiêu .doc và .docx tương đương, các định dạng cũ/quyền/merge đạt hồi quy.
AC-6: file lỗi/quá cỡ/không bảng, sai lớp/nhóm tuổi/phiên bản hoặc tài liệu khóa không ghi dữ liệu.

## 13. Domain Terminology

Chủ đề = theme; chủ đề nhánh = branch name theo tuần lớp; tròn tuần = lịch hệ thống Thứ Hai–Thứ Sáu, không làm tròn ngày file.
.doc nhị phân khác .doc HTML CareNest xuất; xem trước/áp dụng chưa phải lưu.

## 14. Assumptions & Open Questions

Q1–Q4 và plan/API đã được user duyệt; user cho phép hoàn thành liên tục, feature đã verify và hội tụ không còn gap. User duyệt thêm thư viện cho cả hai màn.
Lựa chọn kỹ thuật trình review: ghép tên chuẩn Unicode/khoảng trắng/hoa thường, giữ dấu, không fuzzy; nhiều chủ đề đích trùng tên thì báo mơ hồ, không đoán.
Quyền/lịch/phiên bản thay đổi sau preview: Lưu kiểm lại server, preview không phải giấy cấp quyền.
