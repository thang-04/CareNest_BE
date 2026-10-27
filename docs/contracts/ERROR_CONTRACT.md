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
| AI không khả dụng / quá thời gian | `AI_SERVICE_UNAVAILABLE` (503) / `AI_TIMEOUT` (504) | AI-03 |
| Upload quá cỡ / sai loại | `FILE_TOO_LARGE` (413) / `UNSUPPORTED_MEDIA_TYPE` (415) | — |

Không trả stack trace, tên class, SQL. Lỗi từ Spring Security ghi cùng format (guide 8.5, `docs/contracts/AUTH_CONTRACT.md`): 401 `UNAUTHENTICATED` (1002, kèm `WWW-Authenticate: Bearer`), 403 `FORBIDDEN` (1101), 503 `AUTH_SERVICE_UNAVAILABLE` (1013, không lấy được key kiểm token từ Keycloak).
