# Hợp đồng hành vi gửi và soạn

> **Quyết định user10/11/2026 — duyệt hồ sơ lớp:** Theo chuỗi HT cấu hình, không bắt buộc có bước TT; chỉ PHT duyệt cũng hợp lệ. Giữ cấp trên duyệt cấp dưới, chống tự duyệt, phạm vi phân công và REVIEW cấm sửa. Quyết định này thay yêu cầu TT bắt buộc trước PHT/HT trong tài liệu09/11; chưa phải bằng chứng triển khai/nghiệm thu.

- Các đường gửi năm/tổ phải cùng kiểm điều kiện tại server, kể cả gửi qua màn chi tiết; kiểm phân công một người soạn, thứ bậc EDU-70, trạng thái, revision/chống ghi đè và giao dịch. Bỏ trạng thái/nhãn Đang sửa và cơ chế lease/heartbeat 90/20 khỏi luồng nghiệp vụ; không yêu cầu client lấy, gia hạn hoặc nhả lease để soạn/lưu. Chi tiết tương thích API cũ phải được rà/duyệt, việc giữ endpoint chuyển tiếp không khôi phục cơ chế khóa. Không tự chuyển trạng thái nếu bất kỳ điều kiện nào không đạt.
- Năm: ít nhất 9 chủ đề hợp lệ; ít nhất một chuỗi kết quả/STT/mục tiêu/nội dung; mọi mục tiêu có nội dung, STT tự sinh theo EDU-09, nhãn tự nhập là tùy chọn và rỗng về mặc định theo EDU-58. Đủ ngày chủ đề/nhánh khai báo và khoảng tuần theo hồ sơ. Giới hạn bị mất hoặc chưa phân loại được: chặn gửi và báo kiểm tra/khôi phục; legacy thật có bằng chứng giữ ngoại lệ cũ. Trả lỗi xác định đối tượng, trường và ngưỡng/số tuần; tuân thủ quy trình duyệt contract.
- Tổ: mọi mục tiêu có ít nhất một nội dung thuộc mục tiêu đó tick vào chủ đề hợp lệ; lỗi chỉ rõ mục tiêu chưa phân bổ. Tổ trưởng được thêm/sửa nội dung thuộc tổ, không sửa mục tiêu hay dữ liệu năm và không mặc định xóa nội dung.
- Khi nguồn năm mới và tổ cùng sửa một nội dung, bản cập nhật giữ nội dung tổ và báo rà soát; không tự ghi đè. Phiên bản năm điều chỉnh nhận giới hạn cấu hình hiện tại khi được tạo.
- Giao diện: trường bắt buộc có dấu * đỏ và thông tin truy cập; báo lỗi gần trường và tổng hợp khi gửi; lưu nháp không bị chặn bởi các điều kiện chỉ áp dụng khi gửi.
- Giáo viên chỉ đọc phần kế hoạch tổ theo quyền, lớp và trạng thái hiện hành. Không phơi bày bản tổ chưa công bố, dữ liệu người khác hoặc bí mật.
- Thay đổi mã lỗi/DTO/API ngoài hợp đồng hiện có cần liệt kê consumer FE/APP và duyệt theo quy tắc repo; plan này chưa phê duyệt thay đổi contract.

## Ma trận nghiệp vụ để review trước duyệt plan

| Người soạn được chỉ định | Hồ sơ | Người duyệt hợp lệ | Kiểm tra bắt buộc |
|---|---|---|---|
| PHT | Năm | Chỉ HT | Đúng phạm vi, không tự duyệt; REVIEW không sửa |
| TT | Năm hoặc tổ | PHT hoặc HT | Đúng nhóm tuổi, không tự duyệt/cấp dưới duyệt cấp trên |
| GV | Hồ sơ chủ đề lớp | Theo cấu hình HT; có thể chỉ PHT, không bắt buộc TT | Đúng lớp và phạm vi, không tự duyệt; giáo án theo hồ sơ |

Theo flow EDU-70, mỗi hồ sơ có một thành viên được chỉ định soạn; người khác không được soạn. REVIEW khóa sửa. Bỏ TTL/heartbeat/author lease không bỏ revision, idempotency và kiểm đồng thời. Vai trò hệ thống không thay thế quyền nghiệp vụ. Bản nháp/phiên cũ và hồ sơ đang chờ phải được kiểm kê để đề xuất chuyển đổi giữ dữ liệu và lịch sử; không tự gán người hoặc đổi chuỗi đang chờ nếu chưa được phê duyệt cụ thể.

## Kế hoạch tương thích và phê duyệt contract

Đối chiếu `EduYearPlanController` và `EduYearPlanServiceImpl` với `FE:src/services/education-plan/api/educationPlanApi.js`, `FE:src/pages/education-plan/GoalFormPage.jsx`, `EduClassDocServiceImpl` và các consumer APP khi có repo. Giữ endpoint và header cũ tương thích trong giai đoạn chuyển đổi như phương án thiết kế; không còn lấy TTL/heartbeat làm điều kiện được soạn. Revision và idempotency vẫn được kiểm ở server. Nếu không thể giữ tương thích, trình bảng endpoint/DTO/mã lỗi trước–sau, consumer, rollout và rollback cho user duyệt trước code; không tự xóa endpoint hay tự sửa APP. Kiểm thử phiên đang mở, request cũ, xung đột revision, nháp cục bộ và tính bất biến lịch sử trên DB thử.
