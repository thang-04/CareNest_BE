# Implementation Plan: Kho lưu tệp dùng chung trên S3

**Branch**: `feature/G94-283-BE-FEAT-31-s3-file-storage` (worktree `../CareNest_BE_aws`, từ `origin/dev` 965b4d24) |
**Date**: 2026-11-12 | **Spec**: [spec.md](spec.md)

**Input**: `specs/017-s3-file-storage/spec.md`

## Summary

Gom 2 lớp lưu tệp đang có (`StorageService`, `PrivateImageStorage` + 4 lớp con) thành một `FileStorage` đọc cấu hình theo loại tệp
(thư mục, giới hạn, định dạng). Tệp mới đặt tên `<code>_<id>_<thời gian>_<xxxx>.<đuôi>` trong thư mục của loại; key cũ (UUID) vẫn đọc
được. Thêm liên kết xem có hạn 15 phút cho mọi tệp và tải trực tiếp + xác nhận cho học liệu `hoclieu/`. Một migration nới cột key
(chờ duyệt). Không thêm dependency, không thêm bảng.

## Technical Context

**Language/Version**: Java 21, Spring Boot 4.1.1

**Primary Dependencies**: AWS SDK for Java v2 `s3` 2.57.3 (đã có; `S3Presigner` nằm trong artifact này)

**Storage**: S3 private (bucket hiện có trong `.env`); PostgreSQL chỉ lưu key

**Testing**: JUnit + Mockito (S3 mock), integration PostgreSQL thật có sẵn, `StorageS3Smoke` cho S3 thật (chạy tay)

**Target Platform**: Linux server (Docker/VPS) + máy dev Windows

**Project Type**: web-service (BE dùng chung cho Web + App)

**Performance Goals**: tải video 100 MB không đi qua BE; cấp liên kết < 50 ms (ký cục bộ, không gọi mạng)

**Constraints**: không public object; không trả key; không thêm dependency/bảng; giữ endpoint cũ

**Scale/Scope**: 1 trường, 2 điểm trường; 12 loại tệp; 6 service đang dùng kho

## Constitution Check

| Điều | Đánh giá |
|---|---|
| 1. Giữ stack, dependency cần duyệt | Đạt — chỉ dùng SDK đã có |
| 2. BE giữ quyền theo campus/class/child | Đạt — endpoint `.../url` và upload-url/confirm dùng lại đúng hàm kiểm quyền của endpoint gốc |
| 3. Không dữ liệu thật/secret trong test/log | Đạt — log chỉ id/loại; smoke dùng fixture tự tạo, không in key |
| 4. Dùng lại integration/storage, không public ảnh trẻ | Đạt — gom về `integration/storage`; bucket private, liên kết có hạn |
| 5. Tương thích consumer, schema qua Flyway mới | Đạt — endpoint cũ giữ; **V123 mới, chờ Tiền duyệt** (CLAUDE.md BE) |
| 6. Spec → plan → tasks → implement; plan L cần duyệt | Làn L (integration, migration, API, ≥2 module) ⇒ **chờ Tiền duyệt plan** |
| 7. Báo kiểm chứng thật, không commit/push | Giữ |

Kiểm lại sau Phase 1: không phát sinh vi phạm mới.

## Thiết kế

### Lớp mới / đổi (trong `com.carenest.integration.storage`)

- `FileCategory` (enum 12 loại, data-model) + `StorageProperties` (`@ConfigurationProperties("carenest.storage")`, kiểm khi khởi động).
- `FileTypes`: bảng đuôi→MIME + `sniff(ext, head)` — chuyển từ `LearningResourcePolicy.inspect` (giữ test cũ, đổi chỗ gọi).
- `FileStorage`:
  - `StoredFile put(FileCategory, String ownerId, InputStream|byte[], long size, String originalName)` — kiểm size/đuôi/nội dung, sinh
    key (R4), PutObject `If-None-Match:*`, SSE mặc định bucket, `Cache-Control: private, no-store`.
  - `FileUrl presignGet(FileCategory, String key, String downloadName)` — 15 phút.
  - `PresignedUpload presignPut(FileCategory, String ownerId, String fileName, String contentType, long size)` — key `tmp/...`.
  - `StoredFile confirm(FileCategory, String ownerId, String uploadId)` — Head → Range 8 KB → sniff → Copy → Delete tmp.
  - `Resource load(FileCategory, String key)`, `byte[] loadBytes(...)` (giữ hành vi stream cũ), `deleteQuietly(FileCategory, String key)`.
  - `resolve(FileCategory, key)`: UUID ⇒ legacy-prefix; key mới ⇒ kiểm mẫu + đúng folder; sai ⇒ `FILE_KEY_INVALID`.
- `EducationStorageConfig`: thêm bean `S3Presigner` cùng region/endpoint/path-style.
- Xóa `StorageService`, `PrivateImageStorage`, `AccountPhotoStorage`, `ChildPhotoStorage`, `SignatureStorage`,
  `InspectionSignatureStorage` sau khi chuyển hết chỗ gọi (6 service).

### Chuyển service (giữ nghiệp vụ, chỉ đổi lời gọi kho)

| Service | Loại | Ghi chú |
|---|---|---|
| `ParentPhotoServiceImpl` | USER_AVATAR | ownerId = userId; thêm `photoUrl` |
| `ChildPhotoServiceImpl` | CHILD_AVATAR | cả ảnh trẻ (`children`) và ảnh người giám hộ lưu ở `child_guardians` — giữ chung một loại như hiện tại |
| `UserSignatureServiceImpl` | USER_SIGNATURE | |
| `InspectionServiceImpl` | INSPECTION_SIGNATURE | URL dùng tên tệp; regex `IMAGE_KEY` nhận UUID hoặc tên mới |
| `MealPhotoServiceImpl` | MEAL | ownerId = campusId |
| `LearningResourceServiceImpl` | LEARNING_RESOURCE | ownerId = id class-doc; thêm upload-url/confirm/url |

Thay tệp: giữ thứ tự hiện có (lưu mới → commit DB → xóa cũ; DB lỗi ⇒ xóa mới) — FR-016.

### Cấu hình (`application.yml` + `.env.example`)

`carenest.storage.{bucket,presign-ttl,tmp-prefix,categories.*}` theo data-model; biến prefix cũ đổi nghĩa thành legacy-prefix (giữ tên).
Thêm CORS origin vào tài liệu vận hành, không vào code (CORS của bucket do Console).

### Tài liệu (FR-022/FR-023)

- `docs/system/EDUCATION_MEDIA_S3.md` → viết lại thành "Kho tệp S3": bảng loại, cách dùng `FileStorage` (ví dụ ngắn), thêm loại mới,
  mã lỗi, dựng bucket dev (Block public, CORS, IAM policy, lifecycle `imports/` 7 ngày + `tmp/` 1 ngày), biến môi trường.
- `docs/modules/*` có liên quan: dòng "lưu tệp qua FileStorage loại X". `DATA_DICTIONARY.md`: độ dài cột V123. `docs/api/`: endpoint mới.

## Project Structure

### Documentation (this feature)

```text
specs/017-s3-file-storage/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/storage-api.md
└── checklists/requirements.md
```

### Source Code

```text
src/main/java/com/carenest/
├── integration/storage/    FileCategory, StorageProperties, FileTypes, FileStorage, EducationStorageConfig (sửa); xóa 6 lớp cũ
├── service/{account,child,meals,eduplan}/impl/   6 service đổi lời gọi kho; LearningResource thêm upload-url/confirm/url
├── controller/{account,child,meals,eduplan}/      thêm GET .../url; LearningResourceController thêm 2 POST
├── utils/ApiCode.java      đổi thông báo 8004/8005 có tham số
└── utils/LearningResourcePolicy.java   bỏ phần inspect (chuyển sang FileTypes)
src/main/resources/
├── application.yml         khối carenest.storage
└── db/migration/V122__widen_storage_key_columns.sql   (chờ duyệt)
src/test/java/com/carenest/integration/storage/   FileStorageTest, FileTypesTest, StoragePropertiesTest, StorageS3Smoke (mở rộng)
```

**Structure Decision**: giữ kiến trúc feature-layer hiện có; mọi gọi S3 nằm trong `integration/storage`.

## Mục mở (không chặn plan)

1. **Duyệt V122** (nới 6 cột key lên 255) — bắt buộc trước khi code phần lưu tên mới.
2. **Duyệt plan làn L** và chọn nhánh (gợi ý `feature/<mã-jira>-BE-FEAT-xx-s3-file-storage` từ `dev`).
3. Nghiệp vụ EDU (không thuộc feature này): 12/11/2026 Tiền bỏ học liệu khỏi trang Kế hoạch chủ đề, dạy học chỉ ở Kế hoạch ngày; API học
   liệu vẫn gắn theo `class-docs/theme/{docKey}`. Chuyển điểm gắn sang giáo án ngày cần chốt riêng.
4. Vận hành: thêm lifecycle `tmp/` 1 ngày (quickstart).
5. FE/APP chuyển sang `.../url` và tải trực tiếp: task riêng ở repo FE/APP.

## Complexity Tracking

Không có vi phạm cần biện minh.

## Progress log

- 2026-11-12 — Tiền duyệt plan + V122 (nới 6 cột key lên 255), làm hết US1–US5; nhánh `feature/G94-283-BE-FEAT-31-s3-file-storage`
  (worktree `../CareNest_BE_aws`, từ `origin/dev`). Migration đổi số V108 → **V122** vì `dev` đã tới V121.
- Quyết định khi làm (Tiền soát):
  - `FileCategory` đặt ở `utils/enums` (ArchitectureRulesTest: enum dùng chung ở utils/enums), không ở `integration/storage`.
  - Thông báo 8004/8005 thêm `{0}` (phần nêu định dạng/giới hạn); handler multipart truyền chuỗi rỗng. Mã số giữ nguyên.
  - Ảnh tài khoản/chữ ký/ảnh bữa: rỗng vẫn trả mã riêng (1131/1140/6067); quá cỡ/sai định dạng nay trả 8005/8004 (câu lỗi nêu giới hạn).
    FE/APP không so các mã này (đã grep). Ảnh bữa giới hạn 5 MB theo bảng của Tiền (trước 10 MB); kiểm định dạng ảnh bữa nay sau khung giờ.
  - Ảnh trẻ giữ kiểm từng ô (field error) như cũ, giới hạn đọc từ cấu hình kho.
  - Chữ ký biên bản kiểm thực lưu **tên tệp** (không thư mục) trong JSON để dùng trên URL; key UUID cũ vẫn đọc.
  - Học liệu `confirm`: Office (pptx/docx/xlsx) kiểm cấu trúc zip sau khi chép khỏi `tmp/`; sai thì xóa.
  - Không sửa `docs/modules/education-plan.md` (bản chụp nghiệp vụ EDU); tài liệu kho ở `docs/system/EDUCATION_MEDIA_S3.md`.
- Test: `mvnw test` 1630 test, 0 fail/0 lỗi; OpenApiSnapshotTest cập nhật có chủ đích (chỉ thêm endpoint/schema); StorageS3Smoke trên bucket dev đạt.
