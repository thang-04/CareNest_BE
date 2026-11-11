# Contract: 017 Kho lưu tệp — API thêm mới / thay đổi

Prefix `/api` theo `carenest.api.prefix`. Mọi endpoint mới kiểm quyền **giống hệt** endpoint đọc/tải tương ứng đang có. Không endpoint nào
trả key S3. Endpoint stream cũ **giữ nguyên** (tương thích FE/APP); FE/APP chuyển sang `.../url` ở task riêng của repo đó.

## 1. Liên kết xem có hạn (FR-009)

Response chung `FileUrlResponse`:

```json
{ "url": "https://<bucket>.s3.ap-southeast-1.amazonaws.com/...&X-Amz-Expires=900...", "expiresAt": "2026-11-12T10:15:00+07:00" }
```

| Endpoint mới (GET) | Anh em của endpoint stream đang có |
|---|---|
| `/accounts/me/photo/url` | `/accounts/me/photo` |
| `/children/{childId}/guardians/{userId}/photo/url` | `/children/{childId}/guardians/{userId}/photo` |
| `/children/{childId}/photo/url` | `/children/{childId}/photo` |
| `/children/{childId}/guardian-photos/{guardianId}/url` | `/children/{childId}/guardian-photos/{guardianId}` |
| `/signatures/me/{signatureId}/url` | `/signatures/me/{signatureId}/content` |
| `/nutrition/meal-photos/{photoId}/url` | `/nutrition/meal-photos/{photoId}/content` |
| `/inspections/signatures/{imageKey}/url` (+ query đang có của endpoint gốc) | `/inspections/signatures/{imageKey}` |
| `/education-plans/class-docs/theme/{docKey}/learning-resources/{id}/url` | `.../learning-resources/{id}/content` |

- Liên kết ký `response-content-type` theo MIME đã lưu; học liệu ký thêm `response-content-disposition: inline; filename*=UTF-8''<tên gốc>`.
- Ảnh chữ ký biên bản: `{imageKey}` trên URL là **tên tệp** (không thư mục) với key mới, UUID với key cũ; service ghép thư mục rồi kiểm tồn tại
  trong biên bản như hiện tại.

## 2. Tải học liệu trực tiếp (FR-010)

### POST `/education-plans/class-docs/theme/{docKey}/learning-resources/upload-url`

Quyền: như POST multipart học liệu hiện có (`requireThemeAccess(user, docKey, true)`).

Request:
```json
{ "fileName": "bai-hat.mp4", "contentType": "video/mp4", "size": 83886080 }
```
Response 200:
```json
{
  "uploadId": "hoclieu_12_20261112-101500_a1b2.mp4",
  "url": "https://...presigned PUT...",
  "method": "PUT",
  "headers": { "Content-Type": "video/mp4" },
  "expiresAt": "2026-11-12T10:30:00+07:00"
}
```
Client PUT nguyên tệp lên `url` với đúng `headers` (trình duyệt tự đặt `Content-Length`). Lỗi trước khi cấp: `FILE_TOO_LARGE`,
`UNSUPPORTED_MEDIA_TYPE`, `FILE_EMPTY`, `FILE_NAME_INVALID`, quyền.

### POST `/education-plans/class-docs/theme/{docKey}/learning-resources/confirm`

Header `Idempotency-Key: <uuid>` (như POST multipart). Request:
```json
{ "uploadId": "hoclieu_12_20261112-101500_a1b2.mp4", "fileName": "bai-hat.mp4",
  "activityKey": "...", "activitySignature": "...", "activityLabel": "..." }
```
Response 200: `LearningResourceResponse` như POST multipart.

Kiểm: `uploadId` khớp mẫu và mã đối tượng của `docKey`; tệp `tmp/hoclieu/<uploadId>` tồn tại (không ⇒ `FILE_NOT_FOUND`); size ≤ giới hạn
theo loại; nội dung khớp đuôi (sai ⇒ `FILE_CONTENT_MISMATCH`, tệp tạm bị xóa); Office qua `validateOffice` như hiện tại. Đạt ⇒ chép sang
`hoclieu/<uploadId>`, xóa tệp tạm, ghi DB; ghi DB lỗi ⇒ xóa `hoclieu/<uploadId>`.

POST multipart học liệu hiện có giữ nguyên (tệp nhỏ, client cũ), nhưng lưu theo thư mục/tên mới.

## 3. Mã lỗi (ApiCode — đổi thông báo, giữ mã số)

| Mã | Thông báo mới |
|---|---|
| `FILE_TOO_LARGE` 8005 | `Tệp vượt quá dung lượng cho phép (tối đa {0})` |
| `UNSUPPORTED_MEDIA_TYPE` 8004 | `Định dạng tệp không được hỗ trợ, chỉ nhận: {0}` |

Các mã khác giữ nguyên (`FILE_EMPTY`, `FILE_CONTENT_MISMATCH`, `FILE_NOT_FOUND`, `FILE_KEY_INVALID`, `STORAGE_UNAVAILABLE`,
`STORAGE_NOT_CONFIGURED`). Chỗ đang ném `FILE_TOO_LARGE` không tham số (vd. ảnh phụ huynh) chuyển qua `FileStorage` để có tham số.

## Tác động FE/APP

- Không phá vỡ: endpoint cũ giữ nguyên; chỉ thông báo lỗi 8004/8005 chi tiết hơn.
- FE (học liệu): dùng upload-url + PUT + confirm cho tệp lớn; dùng `.../url` để xem. APP: dùng `.../url` cho ảnh. Làm ở repo FE/APP khi Tiền cho phép.
