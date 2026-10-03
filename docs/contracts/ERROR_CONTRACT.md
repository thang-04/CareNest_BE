# Response & Error Contract

> Status: **ACCEPTED, đã implement.** Nguồn chuẩn: `src/main/java/com/carenest/utils/ApiCode.java`, `ResponseJson.java`, `exception/GlobalExceptionHandler.java`, test `src/test/java/com/carenest/exception/ResponseContractTest.java`; quy tắc: `docs/backend-coding-guide.md` mục 8, 11. File này chỉ tóm tắt cho FE/APP và agent.

Mọi response (thành công hay lỗi):

```json
{ "code": 403, "desc": "You do not have access to this class", "data": null }
```

| Trường | Ý nghĩa |
| --- | --- |
| `code` | Từ `ApiCode`, **trùng HTTP status** |
| `desc` | Thông báo ngắn, hiển thị được; lỗi cùng status phân biệt bằng `desc` |
| `data` | Dữ liệu; luôn có mặt, `null` khi không có. Validation 400: danh sách `{field, message}`. Danh sách: `PageResponse` (`items`, `page`, `size`, `totalElements`, `totalPages`) |

## ApiCode hiện có

`SUCCESSFUL 200` · `CREATED 201` · `BAD_REQUEST 400` (alias `UNSUCCESSFUL`) · `UNAUTHORIZED 401` · `FORBIDDEN 403` · `NOT_FOUND 404` · `METHOD_NOT_ALLOWED 405` · `CONFLICT 409` · `PAYLOAD_TOO_LARGE 413` · `UNSUPPORTED_MEDIA_TYPE 415` · `INTERNAL_ERROR 500` · `SERVICE_UNAVAILABLE 503` (AI/storage) · `GATEWAY_TIMEOUT 504` (AI timeout).

Không tự đặt mã số riêng; chỉ thêm `ApiCode` khi cần HTTP status chưa có — đổi contract ⇒ hỏi nhóm + nêu tác động FE/APP.

## Dùng theo tình huống nghiệp vụ

| Tình huống | ApiCode | Rule |
| --- | --- | --- |
| Ngoài phạm vi campus/lớp/trẻ | FORBIDDEN | AUTH-07 |
| Sửa trực tiếp số suất đã xác nhận / sai trạng thái | CONFLICT | NUT-03 |
| AI không khả dụng / quá thời gian | SERVICE_UNAVAILABLE / GATEWAY_TIMEOUT | AI-03 |
| Upload quá cỡ / sai loại | PAYLOAD_TOO_LARGE / UNSUPPORTED_MEDIA_TYPE | — |

Không trả stack trace, tên class, SQL. 401/403 từ Spring Security dùng handler riêng ghi cùng format (guide 8.5, khi có auth).
