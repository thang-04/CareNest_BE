# Contract tự lưu kế hoạch năm — thay đổi đã được duyệt phạm vi

Prefix giữ nguyên `/education-plans/goals`; wrapper ResponseJson và ApiCode hiện có giữ nguyên. Snapshot sinh từ controller là nguồn hợp đồng máy đọc.

## Write context

Các thao tác tạo, lưu, lấy/duy trì/nhả quyền, gửi, ban hành, trả lại, xóa nhận ba header:

| Header | Kiểu | Quy tắc |
| --- | --- | --- |
| X-Edit-Session | UUID | Tạo riêng cho phiên trang; server kiểm cùng user đang xác thực |
| X-Plan-Revision | Long không âm | Phiên bản nội dung mà caller đã đọc; không tự thay bằng revision mới từ lấy khóa khi caller còn nội dung cũ |
| X-Request-Id | UUID | Giữ nguyên khi retry cùng snapshot; tạo mới cho snapshot mới |

- Tạo cần năm học/nhóm tuổi hợp lệ và quyền hiện có. Replay created_request_id của cùng owner trả bản đã tạo, không lưu lại snapshot cũ.
- PUT cần đúng phiên còn hạn và đúng revision; replay last_save_request_id trả bản đã lưu, không tạo thêm dòng. Dữ liệu nhập dở được giữ, lỗi semantic chặn ở gửi/ban hành.
- POST `/{planId}/start-editing`: lấy quyền; phiên khác cùng user cũng bị 409. Trả detail hiện tại; không đồng nghĩa caller được ghi nội dung cũ với revision vừa nhận.
- POST `/{planId}/editing-heartbeat`: duy trì phiên còn hạn, không thay nội dung/revision; 409 nếu quyền đã mất/hết hạn.
- POST `/{planId}/finish-editing`: chỉ nhả phiên tương ứng; release trễ không nhả phiên mới. Không dùng response của release để thay form đang soạn.
- Các action và DELETE kiểm quyền hiện có + phiên + revision. Nếu client cũ chưa gửi context thì không được ghi.
- Detail bổ sung `revision` và `editExpiresAt`. Người xem vẫn thấy `editing` theo contract cũ, không nhận edit_session_id.
- Lỗi 400: thiếu/sai context hoặc liên kết; 403: không đủ quyền; 409: xung đột phiên/revision hoặc điều kiện nghiệp vụ. Không tự tạo mã lỗi mới.

## Consumer

CareNest_FE: `educationPlanApi.js`, `yearPlanMapper.js`, `educationPlanService.js`, `useEducationPlan.jsx`, `GoalFormPage.jsx`, hành động từ `GoalDetailPage.jsx` qua facade và thao tác xóa từ danh sách. Đồng bộ BE/FE khi triển khai; không coi client cũ tương thích ghi.
