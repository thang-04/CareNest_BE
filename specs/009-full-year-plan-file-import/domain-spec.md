# Domain spec — Nhập đầy đủ kế hoạch năm học và hai chế độ thay nội dung

Nguồn mới nhất: user02/11/2026 yêu cầu nhập file thay nội dung, trả lời “muốn có 2 lựa chọn để user chọn luôn” cho câu hỏi phần thiếu xóa/giữ, và duyệt kế hoạch/cập nhật tài liệu BE. Quyết định này thay luồng chỉ bổ sung đã chốt trước đó trong cùng ngày. Kế hoạch client: FE:docs/plans/active/2026-11-02-year-plan-file-replacement.md. Logic mới đã có implementation/verify FE phase2 sau user duyệt phase1; chờ review trước convergence cuối, giới hạn E2E BE thật ghi riêng trong quickstart.

## 1. Domain Overview
Người soạn có thể thay kế hoạch đang soạn bằng nội dung từ tài liệu, thay vì chỉ nối thêm và giữ dữ liệu cũ. Hai chế độ giúp chủ động xử lý phần file không cung cấp. Preview không ghi DB; người soạn kiểm tra trước khi áp dụng và Lưu.

## 2. Scope
Trong phạm vi: preview Word/Excel/CSV đã hỗ trợ; hai chế độ thay cây mục tiêu/nội dung/tick, chủ đề, lễ hội/lịch/ghi chú, đợt nghỉ và tiêu đề có trong preview; bảo toàn thông tin cố định và năm hệ thống. Ngoài phạm vi: OCR/PDF/AI, parser/DTO/API mới, mô tả chưa được preview đọc, migration/dependency, APP, tự lưu/tự ban hành. Giữ sửa giao diện tick xuống dòng độc lập.

## 3. Actors & Roles
Phó HT/Tổ trưởng theo EDU-02/03/06; BE quyết định quyền/phiên/revision. Phó HT chọn nhóm còn trống khi lập mới; tổ trưởng cố định nhóm được phân công; bản đã lưu không đổi nhóm. Nhập file không mở khóa trường đã cố định và không cấp quyền.

## 4. Domain Concepts
Kế hoạch đích, tài liệu/preview nguồn, chế độ áp dụng, từng phần nội dung (thông tin có thể sửa/cây mục tiêu/chủ đề/đợt nghỉ), dấu tích nội dung–chủ đề, năm đích/khoảng ngày hệ thống, phiên sửa, bản chưa lưu. Chế độ chỉ là trạng thái modal, không là thuộc tính DB.

## 5. Domain Relationships
Một preview chứa metadata và các danh sách; nội dung liên kết chủ đề qua mã viết tắt, ID đích do form/server quản lý. Thay chủ đề có thể đổi ID nên mọi tick còn giữ phải được ghép lại theo mã duy nhất; không được trỏ ID đã xóa. Kế hoạch vẫn thuộc chuỗi (năm, nhóm tuổi) và phiên bản hiện có.

## 6. User Requirements
- UR-1: hai lựa chọn trong modal: Thay toàn bộ theo file (phần thiếu trống), Chỉ thay phần có trong file (phần thiếu giữ cũ). Phần có thay nguyên phần tương ứng, không nối thêm; mặc định toàn bộ theo yêu cầu đầu của user.
- UR-2: preview mô tả phần thay/xóa/giữ, tiêu đề/ngày thực tế, cảnh báo; đổi chế độ cập nhật preview và xác nhận đúng chế độ mới nhất.
- UR-3: giữ năm học và khoảng ngày hệ thống; dời lịch nguồn theo chênh năm như hành vi đã chốt, validate theo năm đích thật, không tự căn T2/T6.
- UR-4: giữ ID kế hoạch, nhóm đã khóa, người soạn/phạm vi, trạng thái/version/history/revision/lease. Nhóm trống chỉ nhận từ file trong quyền hiện có; khác nhóm chặn.
- UR-5: apply chỉ đổi form một lần sau kiểm quyền và preview; Hủy/lỗi/mất phiên/đổi file/phản hồi muộn không đổi form. Chỉ Lưu/Ctrl+S/gửi-ban hành mới ghi theo EDU-22.
- UR-6: tick QH-DN-BH hiển thị từng thành phần trên dòng riêng; không đổi mã/tick lưu và bàn phím vẫn dùng được.

## 7. Business Rules
EDU-46 cập nhật theo quyết định mới02/11: thay nội dung theo hai chế độ; không còn mặc định bổ sung giữ nhập tay. Giữ EDU-01/02/03/06/10/22/23/25/39/40/42/43. EDU-24 tiếp tục áp riêng nút lấy từ kế hoạch có sẵn, không đổi luồng đó. Số mục tiêu được đánh lại theo dữ liệu đích, không lấy số MT trong file làm identity.

## 8. Business Processes
[B] Chọn năm/nhóm và file phù hợp. [S] Chọn chế độ/file, đọc preview theo giới hạn hiện có. [B] Kiểm phần thay/xóa/giữ và lịch/cảnh báo. [S] Xác nhận khi quyền/phiên còn hợp lệ, tạo patch thuần rồi đổi form một lần. [B] Sửa trường trống/cảnh báo nếu cần, Lưu hoặc gửi/ban hành. [S] BE kiểm quyền/phiên/revision và validation hiện có; không tự xóa DB lúc preview/apply.

## 9. State / Lifecycle
Modal idle → loading → ready/error; ready + chế độ mới tính lại preview; ready → áp dụng → đóng khi thành công. Form chuyển sang chưa lưu; plan status/history/version/revision do BE quản lý. Apply lỗi giữ modal/form, không áp dụng một phần.

## 10. Business Constraints
Giữ2MB/2.000dòng và file formats cũ. Phần “có” nghĩa preview có tiêu đề không trống hoặc danh sách có dữ liệu; null/thiếu/danh sách rỗng coi thiếu trong chế độ giữ phần thiếu. File không có bất cứ nội dung hợp lệ nào không được dùng để xóa sạch form. Metadata hiện không có description: toàn bộ để trống mô tả, chỉ phần có giữ mô tả; preview phải nói rõ. Trùng/mơ hồ mã nguồn chặn hoặc cảnh báo theo kiểm hiện có, không đoán ID. Không có năm/khoảng ngày cấu hình hệ thống hợp lệ thì chưa được apply lịch nhập; không đoán khoảng năm từ file. Không hứa khôi phục server sau Lưu.

## 11. Non-Functional Requirements
Không dữ liệu thật/secret trong log/test/commit. Pure transformation không mutate form/preview; không thêm thư viện hoặc gọi AI. Preview/controls có label/focus/bàn phím;375/768/1440 không tràn body. Chỉ một file mới nhất được apply; quyền/recovery/finishing/lease lỗi chặn apply.

## 12. Acceptance Criteria
- AC-1/2: header/nút/cards như UX hiện có, không đổi quyền và field cố định.
- AC-3: parser/preview hiện có giữ nguyên đủ phần có thể biểu diễn và warnings.
- AC-4: hai chế độ thay phần tương ứng, full xóa phần thiếu, present giữ phần thiếu; không nối dữ liệu cũ vào phần được thay, không mutate đầu vào; cancel/error không mất form.
- AC-5: năm và khoảng ngày lấy từ hệ thống; source year chỉ dời lịch, không đổi target year; thiếu cấu hình hoặc ngoài khoảng có thông báo đúng, kiểm gửi/ban hành vẫn giữ.
- AC-6: file cũ vẫn preview; full có thể để trống phần thiếu, present giữ phần thiếu; modal nói rõ trước confirm.
- AC-7: khác tuổi chặn, tuổi trống nhận theo quyền, cố định còn nguyên; ID/version/history/lease không bị patch từ file.
- AC-8: đổi chế độ sau đọc file cập nhật preview, confirm mới nhất, race/late response/double-apply bị chặn; form chỉ đổi lúc apply hợp lệ.
- AC-9: tick mới ghép ID chủ đề đích; mục tiêu cũ được giữ nhưng chủ đề bị thay thì remap mã duy nhất, bỏ liên kết không còn đích và báo; không tick mồ côi.
- AC-10: QH-DN-BH xuống3dòng ở sửa/chỉ xem, mã dài không bị cắt và không đổi abbr lưu.

## 13. Domain Terminology
Thay toàn bộ = toàn bộ phần có thể sửa theo preview, phần thiếu trống. Chỉ thay phần có = thay nguyên phần có, phần thiếu giữ cũ. Bổ sung = hành vi trước quyết định mới, vẫn áp nút lấy từ kế hoạch khác theo EDU-24 nhưng không mặc định cho nhập file. Năm nguồn khác năm đích hệ thống.

## 14. Assumptions & Open Questions
- Lịch sử: user đầu ngày02/11 duyệt bổ sung/giữ nhập tay, sau đó thay quyết định bằng hai chế độ, giữ năm đích/range hệ thống và field cố định.
- Q: File thiếu phần thì xóa hay giữ? A: user yêu cầu có cả hai lựa chọn. User đã duyệt kế hoạch và quyền cập nhật tài liệu BE ngày02/11/2026.
- Quyết định kỹ thuật đã nêu trong plan được duyệt: mặc định full; không thay parser/API để đọc mô tả; present dùng dữ liệu preview không rỗng làm dấu có phần; remap tick theo mã duy nhất.
- Không có câu hỏi nghiệp vụ chặn thiết kế. Cổng phase1 tài liệu/analyze cần review trước phase2 implementation theo kế hoạch; không tự đánh dấu code mới đã xong.
