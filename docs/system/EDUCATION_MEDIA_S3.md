# Kho tệp S3 dùng chung (ảnh, tài liệu, học liệu)

Spec: `specs/017-s3-file-storage/`. Mọi tệp của CareNest (Web và App dùng chung backend) nằm trong **một bucket S3 private**, mỗi loại một
thư mục. PostgreSQL chỉ lưu **key** (đường dẫn trong bucket), không lưu URL. Gọi S3 chỉ qua `integration/storage/FileStorage`.
Không fallback local khi thiếu cấu hình hay S3 lỗi.

## 1. Loại tệp

Cấu hình ở `application.yml` › `carenest.storage.categories` (đổi giới hạn bằng biến môi trường, không sửa code). Enum `utils/enums/FileCategory`.

| Loại (`FileCategory`) | Thư mục | Mã trong tên | Tối đa | Định dạng | Thư mục tệp cũ (UUID, chỉ đọc/xóa) |
|---|---|---|---|---|---|
| USER_AVATAR | `avatars/users/` | `user` | 2 MB | jpg, png, webp | `accounts/photos/` |
| CHILD_AVATAR | `avatars/children/` | `child` | 2 MB | jpg, png, webp | `children/photos/` |
| PICKUP | `pickups/` | `pickup` | 3 MB | jpg, png, webp | — |
| PLAN_YEAR_GOAL | `plans/year-goals/` | `yeargoal` | 10 MB | pdf, docx | — |
| PLAN_THEME | `plans/themes/` | `theme` | 10 MB | pdf, docx | — |
| PLAN_LESSON | `plans/lessons/` | `lesson` | 10 MB | pdf, docx | — |
| MEAL | `meals/` | `meal` | 5 MB | jpg, png, webp | `education/learning-resources/` |
| FOOD_SAMPLE | `food-samples/` | `foodsample` | 5 MB | jpg, png, webp | — |
| IMPORT | `imports/` | `import` | 5 MB | xlsx | — |
| LEARNING_RESOURCE | `hoclieu/` | `hoclieu` | ảnh 5 MB, video 100 MB, khác 20 MB | jpg, jpeg, png, gif, webp, mp4, webm, pdf, pptx, docx, xlsx, txt, csv | `education/learning-resources/` |
| USER_SIGNATURE | `signatures/users/` | `sig` | 2 MB | jpg, png | `accounts/signatures/` |
| INSPECTION_SIGNATURE | `signatures/inspections/` | `inspsig` | 2 MB | jpg, png | `meals/inspection-signatures/` |

`plans/*`, `pickups/`, `food-samples/`, `imports/` mới khai báo, chưa có màn hình dùng (PLAN_*: nghiệp vụ "in ký tay, không bắt buộc tải
bản đã ký" — muốn tải bản scan cần chốt riêng).

Tên tệp mới: `<mã>_<mã đối tượng>_<yyyyMMdd-HHmmss>_<4 ký tự a-z0-9>.<đuôi>` (giờ trường), vd. `meals/meal_123_20261105-143012_a1b2.jpg`.
Không dùng tên gốc của người dùng (tên gốc, nếu cần hiển thị, lưu ở cột riêng). Ghi bằng `If-None-Match: *` nên trùng tên không ghi đè.
Tệp cũ (key UUID) vẫn xem/thay/xóa được; thay tệp cũ ⇒ tệp mới theo quy tắc mới, tệp cũ bị xóa.

## 2. Cách dùng `FileStorage` trong module

Module **tự kiểm quyền** trên đối tượng chứa tệp (lớp/trẻ/điểm trường…) rồi mới gọi; không trả key ra client.

```java
// Ghi qua backend (ảnh nhỏ): đuôi suy từ nội dung thật trong các định dạng cho phép của loại
storage.checkSize(FileCategory.MEAL, null, file.getSize());           // chặn sớm tệp quá lớn trước khi đọc
FileStorage.StoredFile f = storage.store(FileCategory.MEAL, campusId, file.getBytes());
entity.setStorageKey(f.key()); entity.setContentType(f.contentType());

// Tệp lớn dạng luồng: kiểm đuôi theo tên gốc + chữ ký đầu tệp
storage.store(FileCategory.LEARNING_RESOURCE, docId, originalName, input, size);

// Xem/tải: liên kết có hạn (15 phút), endpoint trả {url, expiresAt}
FileStorage.FileUrl url = storage.presignGet(FileCategory.MEAL, key, contentType, downloadNameOrNull);

// Tải trực tiếp từ client (video lớn): cấp liên kết PUT vào tmp/, client PUT xong thì xác nhận
var upload = storage.presignPut(FileCategory.LEARNING_RESOURCE, docId, fileName, contentType, size);
FileStorage.StoredFile f2 = storage.confirm(FileCategory.LEARNING_RESOURCE, docId, upload.uploadId());

// Thay/xóa: lưu mới → commit DB → xóa cũ; DB lỗi ⇒ xóa mới
storage.removeQuietly(FileCategory.MEAL, oldKey);
```

Thêm loại mới: thêm giá trị vào `FileCategory` + một khối trong `carenest.storage.categories` (khóa kebab-case của enum). Thiếu cấu hình
⇒ ứng dụng không khởi động (báo rõ khóa thiếu). Thêm đuôi mới: bổ sung `FileTypes` (MIME + chữ ký đầu tệp).

### Mã lỗi

| Mã | Khi nào |
|---|---|
| 8004 `UNSUPPORTED_MEDIA_TYPE` | Định dạng không thuộc loại — câu lỗi nêu định dạng được nhận |
| 8005 `FILE_TOO_LARGE` | Vượt giới hạn — câu lỗi nêu "(tối đa N MB)" |
| 8012 `FILE_EMPTY` | Tệp rỗng |
| 8013 `FILE_CONTENT_MISMATCH` | Nội dung không khớp đuôi (vd. tệp chạy đổi đuôi .jpg) |
| 8008 `FILE_NOT_FOUND` · 8009 `FILE_KEY_INVALID` | Không có tệp · key sai mẫu/sai thư mục |
| 8003 `STORAGE_UNAVAILABLE` · 8010 `STORAGE_NOT_CONFIGURED` | S3 lỗi · chưa đặt bucket |

### API (prefix `/api`)

Mỗi endpoint đọc tệp có anh em `.../url` trả `{url, expiresAt}` cùng điều kiện quyền: `/accounts/me/photo/url`,
`/children/{childId}/guardians/{userId}/photo/url`, `/children/{childId}/photo/url`, `/children/{childId}/guardian-photos/{guardianId}/url`,
`/signatures/me/{signatureId}/url`, `/nutrition/meal-photos/{photoId}/url`, `/inspections/signatures/{imageKey}/url?campusId=`,
`/education-plans/class-docs/day/{docKey}/learning-resources/{id}/url`. Endpoint stream cũ giữ nguyên.

Học liệu (EDU-81: chỉ kế hoạch giáo dục ngày `dd_…`, gắn `activityKey` = `yyyy-MM-dd#hoc|ngoai_troi|goc|chieu`) tải trực tiếp: `POST .../learning-resources/upload-url` `{fileName, contentType, size}` → `{uploadId, url, method: PUT, headers,
expiresAt}`; client `PUT` tệp lên `url` với đúng `headers`; rồi `POST .../learning-resources/confirm` (header `Idempotency-Key`)
`{uploadId, fileName, activityKey, activityLabel}` → học liệu. Chi tiết: `specs/017-s3-file-storage/contracts/storage-api.md`.

## 3. Dựng bucket trên môi trường dev (AWS Console)

1. **S3 › bucket › Permissions › Block public access:** bật cả 4 ô. Không cấp public ACL/bucket policy.
2. **Permissions › CORS:**
   ```json
   [{"AllowedOrigins":["http://localhost:5173","https://dev.carenest.edu.vn","https://web.carenest.edu.vn"],
     "AllowedMethods":["GET","PUT","HEAD"],"AllowedHeaders":["*"],"ExposeHeaders":["ETag"],"MaxAgeSeconds":3000}]
   ```
   App mobile không cần CORS. Không dùng `"*"` cho origin.
3. **Management › Lifecycle rules:** `xoa-imports-7-ngay` (prefix `imports/`, expire 7 ngày) và `xoa-tmp-1-ngay` (prefix `tmp/`,
   expire 1 ngày — dọn tệp tải trực tiếp không được xác nhận). Chọn "Limit the scope" theo prefix, **không** "Apply to all objects".
4. **IAM:** user riêng cho ứng dụng, chỉ một inline policy:
   ```json
   {"Version":"2012-10-17","Statement":[
     {"Effect":"Allow","Action":"s3:ListBucket","Resource":"arn:aws:s3:::<bucket>"},
     {"Effect":"Allow","Action":["s3:GetObject","s3:PutObject","s3:DeleteObject"],"Resource":"arn:aws:s3:::<bucket>/*"}]}
   ```
   `ListBucket` để HEAD tệp không tồn tại trả 404 (không có thì 403). CopyObject (xác nhận tải trực tiếp) dùng Get + Put.
5. **Biến môi trường** (`.env`, không commit): `CARENEST_EDUCATION_MEDIA_BUCKET`, `AWS_REGION`, `AWS_ACCESS_KEY_ID`,
   `AWS_SECRET_ACCESS_KEY` (+ `AWS_SESSION_TOKEN` nếu credential tạm). Server có thể dùng instance/container role thay khóa.
   Tùy chọn: `CARENEST_STORAGE_PRESIGN_TTL` (mặc định 15m), `CARENEST_STORAGE_MAX_*` (giới hạn từng loại, xem `application.yml`).
   S3 tương thích (SeaweedFS/MinIO): `CARENEST_EDUCATION_MEDIA_ENDPOINT`, `CARENEST_EDUCATION_MEDIA_PATH_STYLE=true`
   (xem `docs/knowledge/incidents/ENV-261103-seaweedfs-aws-chunked-photo-corrupt.md`).

Trạng thái 12/11/2026 (Tiền làm trên Console): bucket dev đã bật Block public, CORS 3 origin, lifecycle `imports/` 7 ngày, IAM inline
policy chỉ bucket. Còn thiếu lifecycle `tmp/` 1 ngày.

## 4. Kiểm chứng

- `FileStorageTest` (S3 giả trong bộ nhớ `InMemoryS3`, ký liên kết thật cục bộ): tên tệp, chống ghi đè, giới hạn theo loại/nhóm, sai định
  dạng/đổi đuôi, key cũ/khác thư mục, liên kết 15 phút, tải trực tiếp + xác nhận. `FileTypesTest`: nhận dạng theo nội dung.
- Test tích hợp module (học liệu, ảnh bữa, ảnh phụ huynh) dùng `FileStorage` thật trên `InMemoryS3`.
- S3 thật, chạy tay: `mvnw.cmd -q -Dtest=StorageS3Smoke test` với credential nạp vào môi trường của process (không in ra log). Kiểm: lưu qua
  backend, liên kết GET không cần header, URL không ký bị chặn, PUT có hạn + xác nhận, xóa xong 404; mọi tệp dưới `carenest-smoke/<uuid>/`
  và được dọn.

### Lịch sử

- 27/10/2026: User yêu cầu S3 và đồng ý AWS SDK v2 (ADR-0018). Smoke đầu với `StorageService` (đã thay bằng `FileStorage`): ảnh PNG, PDF,
  text, binary > 1 MiB round-trip, anonymous 403/404, dọn sạch.
- 12/11/2026: spec 017 — gom `StorageService` + `PrivateImageStorage` vào `FileStorage`, thư mục/tên mới, liên kết có hạn, tải trực tiếp.
  Smoke S3 thật (bucket dev, credential từ `.env` nạp vào process): `S3_SMOKE PASS | store, presigned GET, presigned PUT + confirm,
  private, cleanup`. Bộ test BE: 1630 test, 0 lỗi.
