# Mô hình dữ liệu — kế hoạch năm và tổ

- **Kế hoạch năm**: năm học, nhóm tuổi, trạng thái, revision và giới hạn thời lượng đã chốt cho hồ sơ; chứa tối thiểu 9 chủ đề hợp lệ và cây mục tiêu khi gửi. Mỗi mục tiêu có STT hệ thống tự tạo và ít nhất một nội dung không rỗng; nhãn hiển thị tùy chọn, trống dùng STT mặc định. Toàn kế hoạch có ít nhất một chuỗi gồm nhóm kết quả, mục tiêu và nội dung. Quy tắc cấu trúc hiện hành khác vẫn áp dụng.
- **Chủ đề / mẫu nhánh**: chủ đề thuộc năm; mẫu nhánh tùy chọn nhưng nếu đã khai báo phải đủ ngày và trong khoảng cấu hình áp dụng, nằm trong chủ đề cha. Tuần nghỉ trọn không tính, tuần nghỉ lẻ tính; ngày/tuần hợp lệ theo EDU-39–41.
- **Kế hoạch tổ**: cùng năm/tuổi, tham chiếu đúng nguồn năm đã ban hành; chứa mục tiêu kế thừa, nội dung kế thừa và nội dung do tổ thêm/sửa. Không sửa nguồn năm. Mỗi mục tiêu có ít nhất một nội dung được tick vào chủ đề hợp lệ trước gửi.
- **Nội dung tổ**: định danh và chủ sở hữu tổ/mục tiêu; lineage nguồn khi có, text riêng và tick. Đồng bộ năm không được làm mất dữ liệu riêng. Nếu nguồn năm và tổ cùng sửa một nội dung, giữ nội dung tổ và đánh dấu để tổ trưởng rà soát.
- **Giới hạn thời lượng**: snapshot BOUNDED ghi bền cho hồ sơ mới; chỉ legacy có bằng chứng thực sự không giới hạn giữ chế độ cũ. Snapshot bị mất hoặc chưa thể phân biệt phải ở diện cần kiểm tra, bị chặn gửi; chỉ khôi phục khi có bằng chứng và giữ trạng thái hồ sơ. Phiên bản điều chỉnh ghi snapshot cấu hình hiện tại tại thời điểm tạo.
- **Phân loại lịch sử**: tối thiểu ba ý nghĩa độc lập BOUNDED, LEGACY_UNBOUNDED được chứng minh, và MISSING_OR_UNKNOWN cần kiểm tra. Migration bảo toàn bằng chứng/giá trị sẵn có; bản ghi thiếu không được mặc định thành legacy hợp lệ. Lưu dấu nguồn và thời điểm khôi phục theo bằng chứng; không tự chuyển trạng thái khi phân loại hoặc khôi phục.
- **Lifecycle**: DRAFT được lưu chưa hoàn tất; chỉ DRAFT hợp lệ chuyển REVIEW; gửi lỗi giữ trạng thái và dữ liệu. REVIEW/APPROVED cũ không tự thay đổi; bản tổ APPROVED hiện hành tiếp tục làm nguồn lớp cho tới khi bản thay thế được duyệt.
- **Phân công và duyệt**: một thành viên được chỉ định soạn mỗi kế hoạch, duyệt theo cấp bậc EDU-70; bỏ lease 90 giây/heartbeat 20 giây. Revision/chống ghi đè và cách tương thích hồ sơ/phiên cũ phải được thiết kế và kiểm thử, không suy rằng bỏ lease cho phép ghi đè dữ liệu.

Thiết kế migration/backfill cụ thể phải được duyệt và kiểm trên DB thử; không ghi lên dữ liệu thật trong bước plan.

## Nguyên tắc đánh giá bằng chứng lịch sử

- Đối chiếu bằng chứng gắn được với **đúng hồ sơ và phiên bản**: snapshot còn lưu trong bản sao lưu, bản ghi lịch sử hoặc dữ liệu xuất có nguồn và thời điểm kiểm chứng được. Chỉ khôi phục BOUNDED khi bằng chứng xác định đầy đủ các giới hạn đã áp dụng lúc tạo; kiểm tra min/max hợp lệ và đối chiếu các nguồn nếu có nhiều nguồn.
- Chỉ phân loại LEGACY_UNBOUNDED khi bằng chứng xác định đúng hồ sơ/phiên bản thuộc chính sách cũ không giới hạn. Ngày tạo đơn lẻ, giá trị null, cấu hình hiện tại hoặc suy đoán từ hồ sơ khác không đủ chứng minh chế độ hay giá trị lịch sử.
- Nếu thiếu bằng chứng, nguồn mâu thuẫn hoặc không gắn được với phiên bản: MISSING_OR_UNKNOWN, chặn gửi và báo kiểm tra/khôi phục. Không tự chọn một nguồn, suy ra giới hạn hay áp ngoại lệ legacy.
- Ghi mã hồ sơ đã ẩn danh, phiên bản, nguồn và thời điểm bằng chứng, kết quả đối chiếu, người kiểm tra, quyết định và thời điểm khôi phục; không đưa dữ liệu thật nhạy cảm vào repo. Cơ chế lưu vết cụ thể và quyền thao tác phải được xác định trong plan được duyệt, không suy đây là schema đã triển khai.
