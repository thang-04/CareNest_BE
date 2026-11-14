# Data model: 017 Kho lưu tệp dùng chung trên S3

## Loại tệp (cấu hình, không phải bảng)

`carenest.storage.categories.<loại>` trong `application.yml`, mỗi giá trị đọc được từ biến môi trường.

| Loại (enum `FileCategory`) | folder | code | max-size | types | legacy-prefix |
|---|---|---|---|---|---|
| USER_AVATAR | `avatars/users/` | `user` | 2MB | jpg,png,webp | `accounts/photos/` (`CARENEST_ACCOUNT_MEDIA_PREFIX`) |
| CHILD_AVATAR | `avatars/children/` | `child` | 2MB | jpg,png,webp | `children/photos/` (`CARENEST_CHILD_MEDIA_PREFIX`) |
| PICKUP | `pickups/` | `pickup` | 3MB | jpg,png,webp | — |
| PLAN_YEAR_GOAL | `plans/year-goals/` | `yeargoal` | 10MB | pdf,docx | — |
| PLAN_THEME | `plans/themes/` | `theme` | 10MB | pdf,docx | — |
| PLAN_LESSON | `plans/lessons/` | `lesson` | 10MB | pdf,docx | — |
| MEAL | `meals/` | `meal` | 5MB | jpg,png,webp | `education/learning-resources/` (ảnh món ăn cũ đang dùng chung `StorageService`) |
| FOOD_SAMPLE | `food-samples/` | `foodsample` | 5MB | jpg,png,webp | — |
| IMPORT | `imports/` | `import` | 5MB | xlsx | — |
| LEARNING_RESOURCE | `hoclieu/` | `hoclieu` | 20MB; image 5MB; video 100MB | jpg,png,gif,webp,mp4,webm,pdf,pptx,docx,xlsx,txt,csv | `education/learning-resources/` (`CARENEST_EDUCATION_MEDIA_PREFIX`) |
| USER_SIGNATURE | `signatures/users/` | `sig` | 2MB | jpg,png,webp | `accounts/signatures/` |
| INSPECTION_SIGNATURE | `signatures/inspections/` | `inspsig` | 2MB | jpg,png,webp | `meals/inspection-signatures/` |

Chung: `carenest.storage.bucket` (= `CARENEST_EDUCATION_MEDIA_BUCKET`, giữ tên biến cũ), `presign-ttl: 15m`, `tmp-prefix: tmp/`.
Enum giữ danh sách loại (kiểm tại biên dịch); cấu hình giữ số liệu. Loại có trong enum mà thiếu cấu hình ⇒ khởi động lỗi rõ.

## Key tệp

- Mới: `<folder><code>_<mã đối tượng>_<yyyyMMdd-HHmmss>_<xxxx>.<đuôi>`; mã đối tượng `[0-9A-Za-z-]{1,40}` (id số, hoặc docKey đã lọc).
- Cũ: UUID 36 ký tự ⇒ `legacy-prefix + uuid`. Chỉ đọc/xóa; không sinh mới.
- Tạm: `tmp/<key mới>`; chỉ tồn tại giữa cấp liên kết PUT và xác nhận; lifecycle xóa sau 1 ngày.

## Thay đổi schema — **CẦN TIỀN DUYỆT TRƯỚC KHI LÀM** (CLAUDE.md BE, chốt 31/10/2026)

Migration mới `V123__widen_storage_key_columns.sql`, chỉ nới độ dài, không đổi dữ liệu, không đổi ràng buộc khác:

| Bảng.cột | Hiện tại | Mới |
|---|---|---|
| `user_accounts.photo_key` | VARCHAR(36) | VARCHAR(255) |
| `children.photo_key` | VARCHAR(36) | VARCHAR(255) |
| `child_guardians.photo_key` | VARCHAR(36) | VARCHAR(255) |
| `user_signatures.storage_key` | VARCHAR(36) | VARCHAR(255) |
| `meal_photos.storage_key` | VARCHAR(64) | VARCHAR(255) |
| `edu_learning_resources.storage_key` | VARCHAR(100) | VARCHAR(255) |

- Ràng buộc UNIQUE trên các cột giữ nguyên (PostgreSQL đổi độ dài VARCHAR tăng lên không rebuild index).
- Ảnh chữ ký biên bản kiểm thực nằm trong JSON của biên bản (`imageKey`) ⇒ không đổi schema.
- Entity JPA tương ứng đổi `@Column(length = 255)`; DATA_DICTIONARY cập nhật.
- `edu_year_plans.attachment_key` đã VARCHAR(255) ⇒ không đổi.
- Không bảng mới (tải trực tiếp dùng `tmp/` + lifecycle, research R7).
