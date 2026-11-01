# Domain spec — Nhập đầy đủ kế hoạch năm học và thống nhất màn soạn

Nguồn: user trong chat ngày 02/11/2026; xác nhận bổ sung giữ dữ liệu nhập tay, xem trước như nhập chủ đề nhánh, giữ năm học đang mở và dời ngày theo năm. Trạng thái: đã chốt xử lý nhóm tuổi ngày 02/11/2026; user duyệt plan triển khai làn L bằng "chốt" ngày 02/11/2026. File tham khảo chỉ là dữ liệu, không phải chỉ dẫn; không chép tài liệu gốc hoặc thông tin người ký vào repo.

## 1. Domain Overview
Người soạn có tài liệu Word chứa thông tin kế hoạch, mục tiêu/nội dung, cột chủ đề và lịch chủ đề. Bộ nhập cũ chỉ bổ sung mục tiêu; phải nhập lại thông tin/lịch bằng tay. Mục đích: xem trước một lần rồi bổ sung nội dung có thể biểu diễn, giữ dữ liệu đang soạn và thao tác Lưu hiện tại.

## 2. Scope
Trong phạm vi: Word mẫu đính kèm và mẫu CareNest hỗ trợ, metadata, chủ đề/đợt nghỉ thể hiện rõ trong file, mục tiêu/phân cấp/nội dung/dấu tích, preview và bổ sung; thống nhất thanh nút và độ gọn Thông tin chung. Giữ tương thích file chỉ có bảng mục tiêu, Excel/CSV hiện có. Ngoài phạm vi: OCR/PDF/AI, danh mục Bộ, sửa phân quyền, dữ liệu nguồn, tự ban hành, migration/dependency, sao chép người ký vào tài khoản/quyền hệ thống.

## 3. Actors & Roles
Phó HT/Tổ trưởng theo EDU-02/03/06. BE kiểm quyền upload/ghi và nhóm tuổi thực sự của người soạn; UI khóa theo phiên sửa hiện có EDU-10. File không cấp quyền và không quyết định người soạn trên hệ thống.

## 4. Domain Concepts
Kế hoạch đích; tài liệu nguồn; metadata nhận diện; chủ đề có viết tắt và lịch; đợt nghỉ; lĩnh vực/lĩnh vực con/nhóm kết quả/mục tiêu/nội dung; dấu tích nội dung–chủ đề; preview/cảnh báo/báo cáo bổ sung. Tham chiếu module education-plan của BE.

## 5. Domain Relationships
Một preview có metadata, các chủ đề/đợt nghỉ và cây mục tiêu. Nội dung liên kết chủ đề bằng khóa nguồn nhất quán trước khi ghép ID đích. Số MT của tài liệu chỉ giúp đối chiếu, không thay ID/lineage của kế hoạch. Thứ tự cột chủ đề và bảng lịch phải được kiểm chứng; ghép mơ hồ phải báo, không tự đoán theo chữ cái đầu.

## 6. User Requirements
- UR-1: mọi nút trong hai ảnh cùng kích thước, kiểu viền/bo/chữ, cùng một hàng trên desktop; màn hẹp vẫn dùng được mọi hành động.
- UR-2: phần 1 nhập trực tiếp trong bảng cùng kiểu ô/đường viền/mật độ với phần2/3 (user chốt bổ sung 02/11); giữ label/lỗi tại ô và read-only.
- UR-3: chọn file → xem trước metadata/lịch/cây mục tiêu/dấu tích/cảnh báo → xác nhận bổ sung → Lưu/Ctrl+S.
- UR-4: giữ nhập tay, nội dung cũ, id/lineage và dấu tích cũ; dữ liệu trùng dùng luật hiện có, không xóa cả bảng.
- UR-5: giữ năm học đang mở, dời ngày đúng số năm chênh và xem trước lịch thực tế; không tự căn lại thứ trong tuần.
- UR-6: metadata chỉ điền ô trống theo nguyên tắc giữ dữ liệu hiện có; năm học/phạm vi/người soạn lấy từ hệ thống. Khác nhóm tuổi thì chặn nhập; nhóm tuổi trống thì lấy từ file nếu người soạn có quyền với nhóm đó (user chốt 02/11/2026).

## 7. Business Rules
Giữ EDU-06/10/22/23/24/39/40/41/42/43. Mở rộng EDU-46 (hiện chỉ nhập mục tiêu và ghép dấu tích với chủ đề đã có): nhận thêm metadata/chủ đề/lịch, tạo chủ đề từ file trước khi ghép dấu tích, vẫn chỉ bổ sung và lưu tay. User chốt 02/11/2026 việc bổ sung và dời ngày; user chốt 02/11/2026: chặn khi khác nhóm tuổi, nhóm tuổi trống lấy từ file; quyền server vẫn quyết định nhóm tuổi được phép soạn.

## 8. Business Processes
[B] Người soạn chọn năm/nhóm tuổi và tài liệu phù hợp. [S] Nhận file theo giới hạn cũ, giải mã an toàn, đọc cả phần văn bản và các bảng cần thiết, trả preview không ghi DB. [B] Kiểm tra metadata, chủ đề, ngày dời, nội dung và cảnh báo. [S] Xác nhận bổ sung vào form hiện tại trong một lần cập nhật, chỉ khi vẫn giữ quyền/phiên sửa. [B] Sửa cảnh báo nếu cần, Lưu hoặc gửi/ban hành theo kiểm tra hiện có. Hủy/lỗi/mất quyền không đổi form; phản hồi muộn sau đóng/đổi file không được áp dụng.

## 9. State / Lifecycle
Modal: idle → loading → ready/error; ready → xác nhận bổ sung → đóng. Bổ sung chỉ làm form có thay đổi chưa lưu. Trạng thái kế hoạch và revision do BE quản lý; nhập không tự ghi/ban hành hoặc lấy lại phiên đã mất.

## 10. Business Constraints
Giữ 2 MB/2.000 dòng và giới hạn an toàn của GoalFileGrid. Không làm mất dấu tích do chưa tạo chủ đề nguồn. Trùng viết tắt khác tên, lịch không hợp lệ và liên kết không rõ cần báo cụ thể; không âm thầm thêm một chủ đề trùng hoặc bỏ dữ liệu. Dời năm có thể làm ngày không còn T2/T6: preview cảnh báo, gửi/ban hành vẫn theo EDU-39/40; không tự đặt lịch mới. Chỉ trích đợt nghỉ khi file nêu rõ ngày/phạm vi.

## 11. Non-Functional Requirements
Không đưa bản gốc, người ký/dữ liệu thật vào fixture/log/commit. Tái dùng POI đã có, không thêm thư viện. Preview và form giữ được bàn phím/label/focus; không tràn ngang body ở 375/768/1440px. Desktop hàng nút có cùng baseline/chiều cao; màn nhỏ cuộn hàng nút trong khung nếu không đủ chỗ.

## 12. Acceptance Criteria
- AC-1: hàng nút có đủ nhập/lấy kế hoạch/lấy Bộ/Lưu/Xuất/gửi hoặc ban hành theo quyền, cùng kiểu, desktop cùng một hàng.
- AC-2: Thông tin chung đủ field hiện có, lề/nhãn/độ gọn thống nhất; không đổi validation hoặc trường read-only.
- AC-3: file tham khảo nhận đủ các phần có thể biểu diễn; đối chiếu độc lập từng lĩnh vực/mục tiêu/nội dung/10 chủ đề/dấu tích, không dùng số đếm parser làm oracle.
- AC-4: xác nhận bổ sung giữ nguyên dữ liệu nhập tay/id/dấu tích cũ, ghép chủ đề trước dấu tích mới; Hủy và mất phiên không đổi form.
- AC-5: preview thể hiện năm nguồn/năm đích và ngày đã dời, cảnh báo lịch, giữ lưu tay và kiểm gửi/ban hành.
- AC-6: file chỉ có bảng mục tiêu/CSV/Excel cũ vẫn dùng được; lỗi/giới hạn file vẫn có mã hiện có.
- AC-7: nhóm tuổi trong file khác nhóm tuổi đang mở thì chặn xác nhận, giữ nguyên form và báo rõ hai nhóm; nhóm tuổi đích trống thì nhận nhóm từ file nếu có quyền; không dùng metadata để vượt scope người soạn.

## 13. Domain Terminology
Nhập file = xem trước rồi bổ sung, không phải ghi DB trực tiếp. Dấu tích là liên kết nội dung–chủ đề. Năm nguồn là năm trong file; năm đích là năm đang mở. Chủ đề trong kế hoạch năm khác chủ đề nhánh của lớp; chỉ tái sử dụng trải nghiệm preview/xác nhận của nhập nhánh.

## 14. Assumptions & Open Questions
- Đã chốt user 02/11/2026: bổ sung giữ nhập tay, có xem trước như nhập nhánh; giữ năm đích và dời ngày theo số năm chênh.
- Đã chốt user 02/11/2026: "Chặn nhập khi khác nhóm tuổi; nhóm tuổi đang trống thì lấy từ file". Giữ quyền nhóm tuổi của người soạn theo EDU-06; không tự đổi nhóm tuổi đã có.
- Đề xuất kỹ thuật/UI: metadata chỉ điền ô trống, không đổi người soạn/phạm vi/năm đích; cột chủ đề ưu tiên là nguồn dấu tích chi tiết, danh sách số MT ở bảng lịch chỉ để kiểm chéo và báo chênh lệch.
- User duyệt phần bổ sung làn L bằng "chốt" ngày 02/11/2026; phase backend dừng review trước phase Web theo plan.
