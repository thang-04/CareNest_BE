# Feature009 — Nhập đầy đủ kế hoạch năm và thống nhất màn soạn

Ngày: 02/11/2026. User duyệt plan bằng "chốt" sau ba câu trả lời nghiệp vụ. Nguồn: [domain spec](domain-spec.md), [plan quản trị](../../docs/plans/active/2026-10-22-muc-tieu-nam-hoc.md), EDU-46 mở rộng. Không tạo branch/commit/PR.

## User stories và acceptance
- US1 (P1): Phó HT/Tổ trưởng xem trước toàn bộ file, nhận metadata, chủ đề/lễ hội/lịch/ghi chú/đợt nghỉ, cây mục tiêu/nội dung/dấu tích, rồi bổ sung giữ nội dung cũ. AC-3/4/6 của domain. Hủy/lỗi/mất phiên/đổi file không đổi form. File chỉ có bảng A cũ vẫn dùng được.
- US2 (P1): người soạn giữ năm đích, xem lịch dời theo chênh năm, nhận cảnh báo lịch. Khác nhóm tuổi thì chặn; nhóm trống nhận từ file theo quyền hiện có. AC-5/7. Ngày được giữ chính xác, không tự căn lại T2/T6.
- US3 (P2): người soạn thấy một hàng nút desktop cùng kiểu/kích thước, phần thông tin chung nhập trực tiếp dạng bảng như phần2/3 (user bổ sung 02/11), cùng lề/nhịp/đường viền. AC-1/2. Màn hẹp cuộn thanh nút trong khung, body không tràn; thao tác bàn phím và label còn đủ.

## Functional requirements
FR-01: giữ luồng xem trước nhập file, giới hạn 2 MB/2.000 dòng và không ghi khi xem trước; bổ sung thông tin chung/chủ đề/đợt nghỉ, giữ đầy đủ mục tiêu/dấu tích/cảnh báo cho file cũ.
FR-02: metadata gồm tiêu đề, năm nguồn, khoảng tuổi theo tháng nếu có. Không nhập trường/tài khoản/người ký hoặc suy ra quyền từ file. Chỉ lấy phần nhận diện đầu tài liệu, không log văn bản nguồn.
FR-03: bảng mục tiêu giữ mọi lĩnh vực hợp lệ, phân cấp, nội dung và liên kết cột chủ đề; thay đổi chính tả tổ hợp/chuẩn hóa dấu/biến thể &/và phải có regression, không gộp lĩnh vực không liên quan.
FR-04: nhận bảng lịch có STT, tên chủ đề, lễ hội, thời gian từ/đến, ghi chú; mã viết tắt lấy từ cột rõ ràng hoặc thứ tự STT liên tục khớp duy nhất với cột chủ đề. Không đoán mã theo chữ cái đầu. Mơ hồ → cảnh báo, không tạo liên kết sai.
FR-05: nhận đợt nghỉ từ dòng nghỉ riêng hoặc ghi chú có từ nghỉ/ôn tập và khoảng ngày rõ. Không suy ngày từ số tuần, không suy nghỉ từ ngày lễ không có ngày đầy đủ.
FR-06: người soạn xem thông tin chung/lịch đã dời và báo cáo số mục tiêu/nội dung/dấu tích/cảnh báo trước khi xác nhận; một lần cập nhật form khi xác nhận vẫn có quyền; Lưu/Ctrl+S mới ghi DB.
FR-07: bổ sung chủ đề/đợt nghỉ theo EDU-43 (trùng tên/viết tắt bỏ qua); khớp cột nguồn sang ID đích rồi dùng merge mục tiêu EDU-24/46 hiện có, bảo toàn phân cấp/id/lineage/dấu tích và nội dung nhập tay.
FR-08: thông tin chung chỉ bổ sung trường trống; giữ năm học/phạm vi/người soạn; nhóm đã chọn khác nguồn chặn, nhóm trống nhận từ file nếu actor có quyền. File cũ không có metadata không bị bắt buộc thêm trường.
FR-09: năm nguồn khác đích dời ngày bằng số năm chênh giống EDU-43; báo lịch ngoài năm/không trọn tuần, không tự đổi lịch. Ngày nhuận theo helper dời năm hiện có.
FR-10: hàng nút dùng cùng variant outlined primary và cỡ sm trên desktop, status lưu riêng; không đổi quyền gửi/ban hành, hộp xác nhận và cảnh báo rời màn.
FR-11: file gốc chỉ dùng kiểm cục bộ; regression tổng hợp không chép tên/tài khoản/dữ liệu thật. Không AI, dependency, DB/migration hoặc APP.

## Clarifications — 02/11/2026
- User: bổ sung giữ nhập tay, preview như nhập nhánh; không thay toàn bộ.
- User: giữ năm đang mở, dời lịch sang năm đó và xem trước.
- User: chặn khác nhóm tuổi, nhóm trống lấy từ file.
- User bổ sung sau duyệt: phần1 phải nhập dạng bảng như phần2/3, giữ labels/errors/read-only.
- User: "chốt" duyệt phần bổ sung plan và mở rộng preview BE+FE; không cấp quyền commit/push/branch/deploy.

## Success criteria
SC-01: file mẫu được đối chiếu độc lập từng cấp/dòng/dấu tích và 10 chủ đề; parser không còn mất dòng hợp lệ do nhận diện lĩnh vực.
SC-02: kiểm thử tự động bao phủ nguồn tổng hợp, định dạng cũ, giới hạn, quyền và dữ liệu trao đổi đều đạt; kiểm chứng toàn ứng dụng có output thật, ghi rõ phần bỏ qua/chưa chạy.
SC-03: ở chiều rộng375/768/1440px trang không cuộn ngang ngoài khung cho phép; desktop hàng nút cùng hàng/chiều cao; nhập xong giữ dữ liệu cũ qua lưu/mở lại.

## Scope/gates
Giữ schema và stack, quyền/lease/revision/manual save. Phase BE rồi dừng review trước phase FE theo plan đã duyệt. Không gọi artifact/converge sạch khi phase FE chưa được làm.

## Acceptance scenarios
| AC | Given | When | Then |
| --- | --- | --- | --- |
| AC-1/2 | Người soạn có quyền, màn desktop hoặc màn nhỏ | Mở trang soạn | Nút cùng kiểu/cỡ/hàng desktop; phần1 nhập trong bảng như phần2/3, label/error/read-only vẫn đủ |
| AC-3 | File tham khảo đầy đủ và nguồn đối chiếu độc lập | Xem trước | Đủ83mục tiêu/5lĩnh vực và10chủ đề; nội dung/dấu tích/ngày có thể đối chiếu, thiếu/mơ hồ báo cụ thể |
| AC-4 | Form có dữ liệu nhập tay và phiên hợp lệ | Xác nhận bổ sung | Dữ liệu cũ/ID/ticks giữ nguyên; dữ liệu mới chỉ ở form đến Lưu; Hủy/lỗi/mất phiên/phản hồi muộn không đổi form |
| AC-5 | Năm nguồn khác năm đang mở | Xem trước và bổ sung | Năm đích giữ nguyên, ngày dời đúng chênh năm, lịch sai báo để sửa |
| AC-6 | File cũ chỉ bảng mục tiêu hoặc file lỗi/quá lớn | Chọn file | File cũ vẫn nhập được; file lỗi bị từ chối đúng quy tắc, không mất form |
| AC-7 | File có nhóm tuổi nhận diện được | Xác nhận nhập | Khác nhóm đích bị chặn; nhóm trống nhận từ file trong quyền người soạn |
