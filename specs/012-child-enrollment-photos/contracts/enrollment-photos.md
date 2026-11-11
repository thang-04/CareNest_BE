# Contract — ảnh tiếp nhận

Đã được user duyệt ngày 05/11/2026 cùng plan chính `docs/plans/active/2026-11-05-child-enrollment-photos.md`.

## Ghi ảnh trong tiếp nhận
Giữ `POST /children/enrollments` với `application/json` và request EnrollChildRequest hiện có. Bổ sung cùng đường dẫn với `multipart/form-data`:

| Part | Nội dung | Bắt buộc |
| --- | --- | --- |
| payload | application/json, đúng EnrollChildRequest hiện tại | Có |
| childPhoto | Một file ảnh trẻ | Không |
| guardianPhoto0 | Ảnh child.guardians[0] | Không |
| guardianPhoto1 | Ảnh child.guardians[1] | Không |
| guardianPhoto2 | Ảnh child.guardians[2] | Không |

Từ chối part ảnh lặp, part không hỗ trợ, ảnh của guardian index không tồn tại, tệp rỗng, >2 MiB hoặc không phải JPEG/PNG/WEBP theo nội dung. Field error map về `child.photo`/`child.guardians.i.photo` hoặc map FE tương ứng theo convention INVALID_REQUEST_DATA. Kiểm quyền CHILD_ENROLL và campus bằng đường xử lý tiếp nhận hiện có; phải kiểm mọi ảnh trước khi tạo tài khoản/SMS. Không đổi ApiCode/response envelope hiện có nếu lỗi đã diễn đạt đủ.

## Đọc ảnh
- Thêm `photoUrl` nullable cho ChildViewResponse và các read model trẻ dùng avatar trong FE.
- Thêm `photoUrl` nullable cho ChildGuardianResponse; mở rộng ChildGuardianContact bằng thông tin ảnh cần cho pickup, không để attendance truy repository child trực tiếp.
- `GET /children/{childId}/photo`: trả byte ảnh trẻ sau ChildAccessPolicy; không có/ngoài scope theo convention ảnh private hiện có.
- `GET /children/{childId}/guardian-photos/{guardianId}`: trả ảnh đúng ChildGuardian của trẻ, dùng DB id ổn định để tránh nhầm với userId/position; không đổi nghĩa route `/children/{childId}/guardians/{userId}/photo` hiện có.
- PickupGuardian.photoUrl ưu tiên ảnh hồ sơ, fallback ảnh tài khoản hiện có; giữ nguyên cấu trúc response pickup và hành động xác nhận đón.
- Content-Type theo ảnh đã kiểm; Cache-Control private,no-store và X-Content-Type-Options nosniff. FE tải blob bằng axiosClient có token, thu hồi object URL; không đặt URL có bảo vệ thẳng vào img.

## Lưu trữ và vòng đời
Migration mới thêm photo_key/content_type/size_bytes/updated_at nullable cho children và child_guardians; dữ liệu cũ không backfill ảnh. Tái sử dụng PrivateImageStorage/S3Client với prefix trẻ riêng. Không sửa migration cũ, không lộ object key.

Orchestration account tiếp tục sở hữu tiếp nhận và gọi child service để lưu ảnh hồ sơ; tránh child → account. Validate quyền, form và tất cả tệp trước side effect; object mới được dọn sau rollback hoặc upload sau bị lỗi; metadata cùng giao dịch hồ sơ. Việc SMS/Keycloak có side effect ngoài DB giữ compensation của luồng hiện có, không tuyên bố giao dịch S3/Keycloak/SMS là phân tán nguyên tử.

ChildProfileUpdate phải bảo toàn ảnh theo ChildGuardian id/danh tính trước khi thay bản ghi; tuyệt đối không chuyển ảnh theo vị trí mảng khi đổi người. Nếu record bị tạo lại, cần copy metadata đúng người hoặc đổi cách đồng bộ để giữ record; kiểm bằng regression.

## Consumer cụ thể
FE: childrenApi.enrollChild, childrenService.enrollChild, childrenMockRepository.enrollChild, EnrollChildPage, ChildInfoFields, ChildProfilePage, ChildrenListPage/ChildQuickPanel, pickup API adapter và PickupVerifyFlow. APP không sửa; JSON enrollment và GET ảnh tài khoản hiện có vẫn tương thích. OpenAPI snapshot cập nhật có chủ đích cho multipart/read fields mới.

## Điều chỉnh theo source/review
GET /children/{childId}/profile (trước đây FE đã gọi nhưng BE chưa có) trả ChildPhotoProfileResponse {child}; không tạo dữ liệu khai báo hay lịch sử lớp giả. Phạm vi đọc ảnh/hồ sơ theo ChildAccessPolicy và CampusAccessPolicy AUTH-20, gồm PHT đọc trẻ chờ xếp lớp khác campus. Metadata quản trị tài khoản/SMS được chiếu theo PARENT_ACCOUNT_MANAGE và campus quản trị, tách phạm vi đọc. HTTP tạo tiếp nhận là 201 cho cả JSON và multipart; schema multipart khai rõ các binary part và payload application/json, không có query files.
