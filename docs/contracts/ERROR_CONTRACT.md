# Response & Error Contract

> Status: **ACCEPTED, đã implement.** Bổ sung 27/10/2026 (Cần): mã lỗi nghiệp vụ tập trung trong `ApiCode` + trường `errorCode`. Nguồn chuẩn: `src/main/java/com/carenest/utils/ApiCode.java`, `ResponseJson.java`, `exception/GlobalException.java`, `exception/GlobalExceptionHandler.java`; test `src/test/java/com/carenest/exception/ResponseContractTest.java`, `src/test/java/com/carenest/utils/ApiCodeTest.java`; quy tắc: `docs/backend-coding-guide.md` mục 8, 11. File này chỉ tóm tắt cho FE/APP và agent.

Mọi response (thành công hay lỗi):

```json
{ "code": 409, "errorCode": 4007, "desc": "Điểm danh và báo ăn đã khóa sau 08:30", "data": null }
```

| Trường | Ý nghĩa |
| --- | --- |
| `code` | Từ `ApiCode`, **trùng HTTP status** |
| `errorCode` | Mã lỗi nghiệp vụ 4 số của `ApiCode`; `null` khi thành công. FE/APP phân biệt lỗi bằng trường này, không so `desc` |
| `desc` | Câu tiếng Việt hiển thị được cho người dùng (lỗi lấy từ `ApiCode`, có giá trị thay vào `{0}`) |
| `data` | Dữ liệu; luôn có mặt, `null` khi không có. Validation 400 (`errorCode` 9001): danh sách `{field, message}`. Danh sách: `PageResponse` (`items`, `page`, `size`, `totalElements`, `totalPages`) |

## Mã lỗi theo phân hệ

Mỗi lỗi là một dòng trong `ApiCode`: mã 4 số + HTTP status + câu tiếng Việt. Service ném `GlobalException.of(ApiCode.X, args...)` (bọc lỗi: `GlobalException.withCause(ApiCode.X, ex, args...)`), không tự viết câu lỗi. Lỗi mới thêm vào cuối dải của phân hệ; mã đã dùng không đổi số, không dùng lại. Danh sách đầy đủ: `ApiCode.java` (bản Word "CareNest_Danh_sach_ma_loi" của Cần).

Bổ sung 29/10/2026 (G94-180, khi gộp G94-236): tài khoản & phân quyền 1112–1127, phân công nhân sự 2308–2320, liên kết phụ huynh – trẻ 3004–3006 (thay các câu lỗi tiếng Anh cũ; bảng theo tình huống: `docs/contracts/AUTH_CONTRACT.md`); tiếp nhận trẻ 3008–3010 (bảng dưới; trước khi gộp `dev` 31/10 là 3007–3009 — 3007 nay là `GUARDIAN_ACCOUNT_CONFIRMATION_REQUIRED` của G94-60), SĐT tài khoản nhân sự ở form tiếp nhận 1130 `PHONE_OF_STAFF_ACCOUNT`. Số 11xx/23xx/30xx theo bảng của `dev` (G94-60 đánh lại). Cần thêm các mã này vào bản Word của Cần.

Bổ sung 31/10/2026 (G94-180, đợt khám và kỳ cân đo): sức khỏe 5009–5026 — đợt/kỳ không tìm thấy 5009, không quản lý được 5010, điểm trường đang có đợt/kỳ chưa đóng 5011, đã đóng/hủy 5012, có kết quả không hủy được 5013, đóng/mở lại sai trạng thái 5014–5015, đổi ngày làm kết quả ra ngoài 5016, số đo ngoài kỳ cân đo 5017–5018, trùng số đo 5019–5020, ngày đo ngoài kỳ 5021, lần đo không tìm thấy 5022, không phải GV lớp 5023, thiếu lý do sửa 5024, không xem được sổ sức khỏe 5025, ngày kết thúc trước ngày bắt đầu 5026. Cần thêm vào bản Word của Cần.

Bổ sung 31/10/2026 (G94-180, phiếu khám Phụ lục II và phiếu theo dõi): 5027–5039 — phiếu khám không tìm thấy hoặc ngoài phạm vi 5027, kỳ cân đo không dùng phiếu khám 5028, không phải GV lớp của trẻ 5029, đợt khám không còn diễn ra 5030, lưu nháp phiếu đã kết luận 5031, mẫu kết luận đổi do phiếu theo dõi vừa đổi 5032, phiếu chưa đủ thông tin 5033 (400 kèm `data: [{ field, message }]`, khóa ô theo phiếu: `exam.vitals.temperatureC`...), sửa phiếu khám qua API số đo 5034, phiếu theo dõi không tìm thấy hoặc ngoài phạm vi 5035, không phải GV lớp của trẻ 5036, phiếu đã đóng 5037, chưa trao đổi phụ huynh mà ghi kết quả chuyên khoa 5038, ảnh không tìm thấy 5039. Lỗi theo ô của trao đổi, kết quả chuyên khoa, đóng phiếu dùng 9001 `INVALID_REQUEST_DATA`. Cần thêm vào bản Word của Cần.

Lỗi theo ô do service kiểm (C-5, từ 29/10/2026): service ném `GlobalException.withFieldErrors(ApiCode.X, [{field, message}])` ⇒ `data` là danh sách như validation, `field` theo đường dẫn ô FE (`guardians.0.phone`, `profile.address.ward`, `declaration.medicalHistory.tbContactDate`; không tiền tố `child.`). Dùng cho 400 `INVALID_REQUEST_DATA` (9001) và 409 có ô (`GUARDIAN_IDENTITY_CONFLICT` 3008). Lỗi khác `data = null` như trước.

| Dải | Phân hệ |
| --- | --- |
| 10xx | Xác thực |
| 11xx | Tài khoản & phân quyền |
| 20xx | Năm học |
| 21xx | Điểm trường & phòng |
| 22xx | Nhóm tuổi & lớp |
| 23xx | Phân công giáo viên / tổ trưởng |
| 24xx | Xếp lớp & chuyển năm |
| 30xx | Hồ sơ trẻ |
| 40xx | Điểm danh |
| 50xx | Sức khỏe |
| 60xx | Dinh dưỡng (báo ăn & sổ báo ăn) |
| 70xx–72xx | Kế hoạch giáo dục (kế hoạch năm, tài liệu lớp, cấu hình) |
| 80xx | AI & tệp |
| 90xx | Chung & hệ thống (validation 9001, sai định dạng 9002, không tìm thấy 9009, lỗi hệ thống 9010…) |

Mã chung không có `errorCode` (`SUCCESSFUL 200`, `CREATED 201`, `BAD_REQUEST`, `UNAUTHORIZED`, `NOT_FOUND`, `CONFLICT`, `PRECONDITION_REQUIRED`, `PAYLOAD_TOO_LARGE`, `SERVICE_UNAVAILABLE`, `GATEWAY_TIMEOUT`) chỉ dùng cho response thành công hoặc chỗ chưa gắn mã nghiệp vụ.

ADR-0013: `PASSWORD_CHANGE_REQUIRED` (1010, HTTP 428) cho Web gateway: mật khẩu tạm đã xác minh cần đổi lần đầu; `data` chỉ chứa challenge opaque và hạn dùng, không có access/refresh token. FE xử lý 428 bằng màn hình đổi mật khẩu; Mobile PKCE không dùng endpoint này.

## Dùng theo tình huống nghiệp vụ

| Tình huống | ApiCode (errorCode) | Rule |
| --- | --- | --- |
| Ngoài phạm vi campus/lớp/trẻ | `CAMPUS_ACCESS_DENIED` / `CLASS_ACCESS_DENIED` / `CHILD_ACCESS_DENIED` (403) | AUTH-07 |
| Năm học đã kết thúc | `SCHOOL_YEAR_CLOSED` (409) | CFG-YR-01 |
| Điểm danh sau giờ chốt | `ATTENDANCE_LOCKED` (409) | ATT-08 |
| Sổ báo ăn đã xác nhận | `MEAL_COUNT_ALREADY_CONFIRMED` (409) | NUT-03 |
| Tiếp nhận: form sai, lớp/điểm trường không nhận được (`classId`, `campusId`) | `INVALID_REQUEST_DATA` (9001) + ô | CHD-02, CHD-10 |
| Tiếp nhận trùng trẻ đang học / chờ xếp lớp | `CHILD_DUPLICATE` (3008, 409, không có ô) | CHD-04 |
| CCCD / SĐT phụ huynh đã thuộc người khác | `GUARDIAN_IDENTITY_CONFLICT` (3009, 409) + ô `guardians.{i}.idNumber` / `phone` | AUTH-18 |
| Mở lại hồ sơ trẻ chưa nghỉ học | `CHILD_NOT_LEFT` (3010, 409) | CHD-03 |
| AI không khả dụng / quá thời gian | `AI_SERVICE_UNAVAILABLE` (503) / `AI_TIMEOUT` (504) | AI-03 |
| Upload quá cỡ / sai loại | `FILE_TOO_LARGE` (413) / `UNSUPPORTED_MEDIA_TYPE` (415) | — |
| Tạo phiên bản kế hoạch năm sai nguồn / còn bản đang soạn | `YEAR_PLAN_VERSION_SOURCE_INVALID` (7027, 409) / `YEAR_PLAN_OPEN_VERSION_EXISTS` (7028, 409) | EDU-25 |
| Ghi tài liệu lớp trên phiên bản đã thay thế / gửi bản chép còn ô đỏ | `YEAR_PLAN_SUPERSEDED` (7122, 409) / `CLASS_DOC_VERSION_FIX_REQUIRED` (7121, 409) | EDU-26 |
| Kế hoạch điều chỉnh sai ngày / quá dài | `CLASS_DOC_ADJUSTMENT_INVALID` (7123, 400) | EDU-27 |

Không trả stack trace, tên class, SQL. Lỗi từ Spring Security ghi cùng format (guide 8.5, `docs/contracts/AUTH_CONTRACT.md`): 401 `UNAUTHENTICATED` (1002, kèm `WWW-Authenticate: Bearer`), 403 `FORBIDDEN` (1101), 503 `AUTH_SERVICE_UNAVAILABLE` (1013, không lấy được key kiểm token từ Keycloak).
