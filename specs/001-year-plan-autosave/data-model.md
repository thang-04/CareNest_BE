# Thiết kế dữ liệu — quyền sửa và tự lưu

Migration mới: `src/main/resources/db/migration/V24__add_edu_year_plan_edit_sessions.sql`. Không sửa migration cũ.

## edu_year_plans

| Trường | Kiểu | Ý nghĩa |
| --- | --- | --- |
| edit_session_id | UUID nullable | Phiên giữ quyền, không trả trong response đọc hoặc log |
| content_revision | BIGINT not null, mặc định 0 | Tăng sau mỗi lần ghi nội dung hoặc chuyển trạng thái; không tăng khi heartbeat |
| last_save_request_id | UUID nullable | Nhận diện lần lưu nội dung gần nhất để thử lại không ghi lặp |
| created_request_id | UUID nullable, immutable | Nhận diện lần tạo; unique với owner_id để retry không tạo kế hoạch thứ hai |
| editing_by/since/at/from | Các cột hiện có | Người sửa, lúc bắt đầu, lần liên lạc cuối và trạng thái để khôi phục khi nhả |

- Khóa rỗng: các trường phiên/người/thời gian/trạng thái gốc đều null; status không được EDITING.
- Khóa đang giữ: tất cả trường quyền sửa có giá trị. Draft/Rejected dùng EDITING và editing_from tương ứng; PENDING_VP giữ nguyên PENDING_VP và editing_from=PENDING_VP.
- Khóa hết hạn được coi là không chặn lấy quyền mới ngay, không phụ thuộc lịch cleanup. Cleanup khóa dòng trước khi nhả để không đua với heartbeat.
- Gửi/ban hành/trả lại xóa phiên và tăng revision. Release chỉ nhả khi đúng cả user/session; yêu cầu release cũ không đụng phiên mới.
- Khi nâng cấp: nhả khóa V21 không có phiên, đưa EDITING về editing_from; không sửa nội dung, ID, các tick chủ đề hoặc bản đã ban hành.
- Không nới giới hạn độ dài/link/quyền; title cho chuỗi trống khi nháp nhưng không null. Validation semantic ngày/mục tiêu/chủ đề chạy trước gửi/ban hành.

## Bản phục hồi thiết bị

- Nhận diện: user ID + khóa kế hoạch + slot của phiên; các slot cùng tài khoản/kế hoạch không ghi đè nhau.
- Nội dung: snapshot form, baseRevision và thời điểm giữ; không lưu token đăng nhập hoặc credential quyền sửa.
- Trước ID chính thức dùng khóa soạn mới theo năm; sau lần lưu đầu cập nhật ID và chuyển địa chỉ sang bản đã tạo.
- Không tự ghi đè nếu baseRevision khác bản hiện tại. Giữ bản để tải nội dung đối chiếu; chỉ người dùng chủ động phục hồi khi không có xung đột.
- Lỗi quota/lưu trữ bị cấm phải hiển thị, không báo đã lưu giả. Không hứa phục hồi phần chưa giữ xong trước mất điện.
