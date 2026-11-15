---
description: "Tasks: 017 Kho lưu tệp dùng chung trên S3"
---

# Tasks: Kho lưu tệp dùng chung trên S3

**Input**: `specs/017-s3-file-storage/` (spec, plan, research, data-model, contracts/storage-api.md, quickstart)

**Prerequisites trước T001**: Tiền duyệt plan làn L + migration V122 + chọn nhánh (plan.md › Mục mở 1–2). Chưa duyệt ⇒ không bắt đầu.

**Tests**: spec không yêu cầu TDD; mỗi phần logic mới có đúng một test nhỏ đi kèm (quy tắc repo: kiểm chứng thật). Gốc đường dẫn: `CareNest_BE/`.

## Format: `[ID] [P?] [Story] Description`

---

## Phase 1: Setup

- [x] T001 Thêm khối `carenest.storage` vào `src/main/resources/application.yml`: `bucket: ${CARENEST_EDUCATION_MEDIA_BUCKET:}`, `presign-ttl: 15m`, `tmp-prefix: tmp/`, và `categories` đủ 12 loại theo bảng `data-model.md` (folder, code, max-size, types, `max-size-by-kind` cho LEARNING_RESOURCE `image: 5MB, video: 100MB`, `legacy-prefix` dùng lại biến `CARENEST_*_MEDIA_PREFIX` hiện có); giữ khối `carenest.education-media` (region/endpoint/path-style) cho S3 client
- [x] T002 [P] Cập nhật `.env.example`: ghi chú biến prefix cũ giờ là "thư mục tệp cũ (chỉ đọc)", không thêm secret

---

## Phase 2: Foundational (chặn mọi story)

- [x] T003 Tạo `src/main/java/com/carenest/utils/enums/FileCategory.java` (ArchitectureRulesTest: enum dùng chung ở utils/enums): enum USER_AVATAR, CHILD_AVATAR, PICKUP, PLAN_YEAR_GOAL, PLAN_THEME, PLAN_LESSON, MEAL, FOOD_SAMPLE, IMPORT, LEARNING_RESOURCE, USER_SIGNATURE, INSPECTION_SIGNATURE; mỗi giá trị map tới khóa cấu hình kebab-case (vd. `learning-resource`)
- [x] T004 Tạo `src/main/java/com/carenest/integration/storage/StorageProperties.java` (`@ConfigurationProperties("carenest.storage")`, record): `bucket`, `Duration presignTtl`, `tmpPrefix`, `Map<String, Category> categories`; `Category(folder, code, DataSize maxSize, Set<String> types, Map<String, DataSize> maxSizeByKind, String legacyPrefix)`; kiểm khi khởi động: mọi `FileCategory` có cấu hình, `folder` khớp `[a-z0-9/-]+/`, `code` khớp `[a-z]+`, mọi đuôi có trong `FileTypes`; đăng ký bằng `@EnableConfigurationProperties` trong `EducationStorageConfig`
- [x] T005 [P] Tạo `src/main/java/com/carenest/integration/storage/FileTypes.java`: chuyển bảng đuôi→MIME và phần nhận dạng chữ ký đầu tệp từ `LearningResourcePolicy.inspect` (`src/main/java/com/carenest/utils/LearningResourcePolicy.java`) thành `static String mime(String ext)`, `static String kind(String ext)` (`image`/`video`/`other`), `static boolean matches(String ext, byte[] head)`; `LearningResourcePolicy.inspect` gọi lại `FileTypes` (giữ hành vi cũ tới T021)
- [x] T006 [P] Test `src/test/java/com/carenest/integration/storage/FileTypesTest.java`: jpg/png/webp/gif/pdf/mp4/webm/zip-Office đúng chữ ký ⇒ true; `.exe` (MZ) đổi đuôi `.jpg` ⇒ false; png đổi đuôi jpg ⇒ false
- [x] T007 Thêm bean `S3Presigner` vào `src/main/java/com/carenest/integration/storage/EducationStorageConfig.java` cùng region/endpoint/path-style với `S3Client` (`destroyMethod = "close"`, credential default chain)
- [x] T008 Đổi thông báo trong `src/main/java/com/carenest/utils/ApiCode.java`: `FILE_TOO_LARGE` 8005 ⇒ "Tệp vượt quá dung lượng cho phép (tối đa {0})", `UNSUPPORTED_MEDIA_TYPE` 8004 ⇒ "Định dạng tệp không được hỗ trợ, chỉ nhận: {0}"; giữ mã số/HTTP; chạy lại `ApiCodeTest`
- [x] T009 Tạo `src/main/java/com/carenest/integration/storage/FileStorage.java` (`@Component`), phần lõi: `requireConfigured()` (bucket trống ⇒ `STORAGE_NOT_CONFIGURED`); `newKey(FileCategory, String ownerId, String ext)` = `folder + code + "_" + ownerId + "_" + yyyyMMdd-HHmmss (ZoneId từ carenest.time-zone) + "_" + 4 ký tự [a-z0-9] SecureRandom + "." + ext`, ownerId phải khớp `[0-9A-Za-z-]{1,40}`; `resolve(FileCategory, String key)`: UUID `[a-f0-9-]{36}` ⇒ `legacyPrefix + key` (loại không có legacy ⇒ `FILE_KEY_INVALID`), key mới phải bắt đầu bằng `folder`, khớp `^<folder><code>_[0-9A-Za-z-]{1,40}_\d{8}-\d{6}_[a-z0-9]{4}\.(<types>)$`, không chứa `..`; `validate(FileCategory, String fileName, long size, byte[] head)`: size ≤ 0 ⇒ `FILE_EMPTY`; đuôi không thuộc `types` ⇒ `UNSUPPORTED_MEDIA_TYPE` (tham số: danh sách đuôi); size > giới hạn (`maxSizeByKind[kind]` nếu có, ngược lại `maxSize`) ⇒ `FILE_TOO_LARGE` (tham số "5 MB"); head không khớp ⇒ `FILE_CONTENT_MISMATCH`
- [x] T010 Trong `FileStorage.java` thêm `StoredFile put(FileCategory, String ownerId, InputStream, long size, String fileName)` và bản `byte[]`: validate (đọc 8 KB đầu bằng `BufferedInputStream.mark/reset`), sinh key, `PutObject` với `ifNoneMatch("*")`, `contentType` = MIME theo đuôi đã kiểm, `cacheControl("private, no-store")`; 412 ⇒ sinh key mới, tối đa 3 lần; `SdkException` ⇒ xóa đúng key mới rồi `STORAGE_UNAVAILABLE` (như `StorageService.store` hiện tại); `record StoredFile(String key, String contentType, long size)`
- [x] T011 Trong `FileStorage.java` thêm `Resource load(FileCategory, String key)` (HEAD lấy size + stream GET, 404 ⇒ `FILE_NOT_FOUND`, giống `StorageService.load`), `byte[] loadBytes(FileCategory, String key)`, `void delete(FileCategory, String key)` (lỗi ⇒ `STORAGE_UNAVAILABLE`), `void deleteQuietly(FileCategory, String key)` (nuốt `SdkException`)
- [x] T012 Test `src/test/java/com/carenest/integration/storage/FileStorageTest.java` (S3Client/S3Presigner mock, cấu hình dựng tay): key mới đúng mẫu `meal_123_\d{8}-\d{6}_[a-z0-9]{4}\.jpg` trong `meals/`; 412 lần đầu ⇒ thử key khác; vượt 5 MB ⇒ `FILE_TOO_LARGE`; sai chữ ký ⇒ `FILE_CONTENT_MISMATCH` và không gọi PutObject; `resolve` UUID ⇒ legacy-prefix; `resolve` key thư mục khác hoặc có `..` ⇒ `FILE_KEY_INVALID`; bucket trống ⇒ `STORAGE_NOT_CONFIGURED`
- [x] T013 Tạo migration `src/main/resources/db/migration/V122__widen_storage_key_columns.sql` (**chỉ sau khi Tiền duyệt**): `ALTER COLUMN ... TYPE VARCHAR(255)` cho `user_accounts.photo_key`, `children.photo_key`, `child_guardians.photo_key`, `user_signatures.storage_key`, `meal_photos.storage_key`, `edu_learning_resources.storage_key`; comment 1 dòng lý do; đổi `@Column(length = 255)` ở entity tương ứng (grep `photoKey`/`storageKey` trong `src/main/java/com/carenest/entity`)

**Checkpoint**: `./mvnw -q -Dtest='FileTypesTest,FileStorageTest,ApiCodeTest' test` đạt.

---

## Phase 3: US1 — Tải tệp lên đúng chỗ, đúng giới hạn (P1) 🎯 MVP

**Goal**: mọi chỗ tải lên hiện có lưu theo thư mục + tên mới, kiểm giới hạn/MIME qua `FileStorage`; tệp cũ vẫn đọc được.
**Independent Test**: quickstart bước 1–2 và 7.

- [x] T014 [P] [US1] `src/main/java/com/carenest/service/account/impl/ParentPhotoServiceImpl.java`: thay `AccountPhotoStorage` bằng `FileStorage` loại `USER_AVATAR`, ownerId = userId; bỏ kiểm size/MIME riêng trùng với `FileStorage.validate`; đọc/xóa ảnh cũ qua `resolve` (UUID vẫn chạy); giữ thứ tự lưu mới → commit → xóa cũ
- [x] T015 [P] [US1] `src/main/java/com/carenest/service/child/impl/ChildPhotoServiceImpl.java`: thay `ChildPhotoStorage` bằng `FileStorage` loại `CHILD_AVATAR` cho ảnh trẻ (ownerId = childId) và ảnh người giám hộ trong `child_guardians` (ownerId = childId); như T014
- [x] T016 [P] [US1] `src/main/java/com/carenest/service/account/impl/UserSignatureServiceImpl.java`: thay `SignatureStorage` bằng `FileStorage` loại `USER_SIGNATURE`, ownerId = userId
- [x] T017 [P] [US1] `src/main/java/com/carenest/service/meals/impl/InspectionServiceImpl.java`: thay `InspectionSignatureStorage` bằng `FileStorage` loại `INSPECTION_SIGNATURE`, ownerId = sheetId; JSON biên bản lưu key đầy đủ; `getSignatureImage` nhận `imageKey` là UUID (cũ) hoặc tên tệp (mới) — ghép `folder` trước khi so trong JSON và tải; regex `IMAGE_KEY` nhận cả hai dạng; API trả ra chỉ tên tệp (phần sau `/` cuối) cho key mới
- [x] T018 [P] [US1] `src/main/java/com/carenest/service/meals/impl/MealPhotoServiceImpl.java`: thay `StorageService` bằng `FileStorage` loại `MEAL`, ownerId = campusId
- [x] T019 [US1] `src/main/java/com/carenest/service/eduplan/impl/LearningResourceServiceImpl.java`: thay `StorageService` bằng `FileStorage` loại `LEARNING_RESOURCE`, ownerId = id class-doc; `LearningResourcePolicy.inspect` → `FileStorage.validate`; giữ `validateOffice`, idempotency và dọn tệp khi rollback
- [x] T020 [US1] Cập nhật test hiện có bị ảnh hưởng (grep `AccountPhotoStorage|ChildPhotoStorage|SignatureStorage|InspectionSignatureStorage|StorageService` trong `src/test/java`): đổi mock sang `FileStorage`, thêm 1 case key UUID cũ vẫn tải được ở `LearningResourceServiceImplTest`
- [x] T021 [US1] Xóa `StorageService.java`, `PrivateImageStorage.java`, `AccountPhotoStorage.java`, `ChildPhotoStorage.java`, `SignatureStorage.java`, `InspectionSignatureStorage.java` trong `src/main/java/com/carenest/integration/storage/` và `StorageServiceTest.java`; bỏ `inspect` khỏi `LearningResourcePolicy.java` (đã chuyển sang `FileTypes`); `ArchitectureRulesTest` vẫn đạt

**Checkpoint**: test các service trên + `ArchitectureRulesTest` đạt.

---

## Phase 4: US2 — Xem/tải qua liên kết có hạn (P1)

**Goal**: mỗi endpoint đọc tệp có anh em `.../url` trả `{url, expiresAt}` sau cùng bước kiểm quyền.
**Independent Test**: quickstart bước 3–4.

- [x] T022 [US2] Trong `FileStorage.java` thêm `FileUrl presignGet(FileCategory, String key, String contentType, String downloadName)`: `resolve` key, `S3Presigner.presignGetObject` hạn `presignTtl`, đặt `responseContentType`, và `responseContentDisposition("inline; filename*=UTF-8''" + URLEncoder(downloadName))` khi downloadName khác null; `record FileUrl(String url, OffsetDateTime expiresAt)`; test thêm vào `FileStorageTest` (hạn 15 phút, key cũ resolve legacy)
- [x] T023 [P] [US2] Tạo DTO `src/main/java/com/carenest/dto/response/FileUrlResponse.java` (`url`, `expiresAt`) theo vị trí DTO response hiện có của repo (grep thư mục `dto`)
- [x] T024 [P] [US2] `AccountPhotoController` + `ParentPhotoServiceImpl`: GET `/accounts/me/photo/url`, GET `/children/{childId}/guardians/{userId}/photo/url` — dùng lại đúng hàm kiểm quyền của endpoint stream anh em; log START/END theo quy ước
- [x] T025 [P] [US2] `ChildPhotoController` + `ChildPhotoServiceImpl`: GET `/children/{childId}/photo/url`, GET `/children/{childId}/guardian-photos/{guardianId}/url`
- [x] T026 [P] [US2] `UserSignatureController` + `UserSignatureServiceImpl`: GET `/signatures/me/{signatureId}/url`
- [x] T027 [P] [US2] `KitchenRecordsController` + `MealPhotoServiceImpl`: GET `/nutrition/meal-photos/{photoId}/url`
- [x] T028 [P] [US2] `InspectionController` + `InspectionServiceImpl`: GET `/inspections/signatures/{imageKey}/url` (cùng query param và kiểm quyền với endpoint gốc)
- [x] T029 [P] [US2] `LearningResourceController` + `LearningResourceServiceImpl`: GET `.../learning-resources/{id}/url`, downloadName = tên gốc
- [x] T030 [US2] Test quyền cho `.../url`: 1 case "có quyền ⇒ trả url", 1 case "không quyền ⇒ lỗi quyền, không gọi presigner" ở `LearningResourceServiceImplTest` và test service ảnh trẻ hiện có

---

## Phase 5: US3 — Tải học liệu trực tiếp (P2)

**Goal**: upload-url → PUT S3 → confirm cho `hoclieu/`.
**Independent Test**: quickstart bước 5–6.

- [x] T031 [US3] Trong `FileStorage.java` thêm `PresignedUpload presignPut(FileCategory, String ownerId, String fileName, String contentType, long size)`: `validate` trước phần đuôi/size (chưa có nội dung), contentType phải bằng `FileTypes.mime(ext)`; key `tmpPrefix + newKey(...)`; `presignPutObject` với `contentType` và `contentLength(size)`, hạn `presignTtl`; trả `uploadId` = tên tệp (không thư mục), `url`, `headers {Content-Type}`, `expiresAt`
- [x] T032 [US3] Trong `FileStorage.java` thêm `StoredFile confirm(FileCategory, String ownerId, String uploadId)`: `uploadId` khớp mẫu tên của loại và chứa đúng `ownerId`; `HeadObject tmp/<folder><uploadId>` (404 ⇒ `FILE_NOT_FOUND`); validate size theo loại; `GetObject` `Range bytes=0-8191` ⇒ `FileTypes.matches` (sai ⇒ xóa tệp tạm, `FILE_CONTENT_MISMATCH`); `CopyObject` sang `<folder><uploadId>` với `contentType` đúng và `cacheControl` private; xóa tệp tạm; trả `StoredFile`
- [x] T033 [US3] Test thêm vào `FileStorageTest`: presignPut 150 MB video ⇒ `FILE_TOO_LARGE` trước khi ký; confirm tệp PDF mang uploadId `.mp4` ⇒ xóa tmp + `FILE_CONTENT_MISMATCH`; confirm ownerId khác ⇒ `FILE_KEY_INVALID`; confirm hợp lệ ⇒ Copy rồi Delete tmp
- [x] T034 [US3] DTO trong thư mục DTO của repo: `LearningResourceUploadUrlRequest(fileName, contentType, size)` (`@NotBlank`, `@Positive`), `PresignedUploadResponse(uploadId, url, method, headers, expiresAt)`, `LearningResourceConfirmRequest(uploadId, fileName, activityKey, activitySignature, activityLabel)`
- [x] T035 [US3] `LearningResourceServiceImpl`: `uploadUrl(user, docKey, req)` — `requireThemeAccess(user, docKey, true)` rồi `presignPut`; `confirm(user, docKey, req, requestId)` — cùng nhánh idempotency/validateBinding như `upload`, lấy tệp bằng `FileStorage.confirm`, với Office tải nội dung về kiểm `validateOffice`, ghi DB; ghi DB lỗi ⇒ `deleteQuietly` key thật. Tách phần chung với `upload` thành một hàm private, không lặp
- [x] T036 [US3] `LearningResourceController`: POST `.../learning-resources/upload-url`, POST `.../learning-resources/confirm` (header `Idempotency-Key`), theo `contracts/storage-api.md` §2; log START/END
- [x] T037 [US3] Test `LearningResourceServiceImplTest`: confirm replay cùng `Idempotency-Key` trả cùng kết quả, không copy lần hai

---

## Phase 6: US4 — Thay/xóa không để rác (P2)

**Independent Test**: quickstart bước 1 (ảnh cũ bị xóa) + lifecycle trên Console.

- [x] T038 [US4] Rà 6 service ở Phase 3: mọi đường thay/xóa gọi `FileStorage.deleteQuietly` sau commit (thay) hoặc sau xóa bản ghi (xóa); DB lỗi ⇒ xóa tệp mới. Sửa chỗ thiếu, ghi danh sách đã rà vào Progress log
- [x] T039 [US4] Mở rộng `src/test/java/com/carenest/integration/storage/StorageS3Smoke.java` (chạy tay, S3 thật, prefix `carenest-smoke/<uuid>/` hoặc loại thật rồi dọn): put ảnh qua `FileStorage` → presignGet tải được bằng HTTP không header → presignPut + PUT HTTP → confirm → Copy/Delete tmp → delete; anonymous GET ⇒ 403; không in key/secret

---

## Phase 7: US5 — Dịch vụ dùng chung có ghi chú (P3)

- [x] T040 [P] [US5] Viết lại `docs/system/EDUCATION_MEDIA_S3.md` thành "Kho tệp S3": bảng 12 loại; cách dùng `FileStorage` (put/presignGet/presignPut+confirm/deleteQuietly, ví dụ 10 dòng cho một module); thêm loại mới (enum + yml); mã lỗi 8003–8013; dựng bucket dev (Block public, CORS 3 origin, IAM inline policy, lifecycle `imports/` 7 ngày + `tmp/` 1 ngày); biến môi trường; giữ mục lịch sử smoke cũ
- [x] T041 [P] [US5] `docs/database/DATA_DICTIONARY.md`: độ dài 255 của 6 cột (V122); `docs/api/`: thêm endpoint `.../url`, upload-url, confirm vào Postman collection nếu repo đang duy trì
- [x] T042 [P] [US5] Module card liên quan (đã sửa child, nutrition, identity-access; **không** sửa `docs/modules/education-plan.md` vì là bản chụp nghiệp vụ EDU — học liệu ghi ở EDUCATION_MEDIA_S3.md): một dòng "tệp lưu qua `FileStorage` loại X, thư mục Y"; `docs/system/CROSS_REPO_MAP.md`: ghi tác động FE/APP (endpoint mới, thông báo 8004/8005)

---

## Phase 8: Polish

- [x] T043 Chạy `./mvnw -q spotless:apply` (nếu repo dùng) và `./mvnw -q test` cho các test đã đổi + `ArchitectureRulesTest`; ghi lệnh và kết quả thật vào báo cáo
- [x] T044 Chạy tay `./mvnw -q -Dtest=StorageS3Smoke test` với `.env` (credential chỉ nạp vào process, không in); ghi kết quả vào `docs/system/EDUCATION_MEDIA_S3.md`
- [x] T045 Báo bàn giao: rule đã đối chiếu (FR-019 không đổi EDU-12..23; nghiệp vụ học liệu 12/11 để mở), phần liên quan đã kiểm (6 service, test), phần bị ảnh hưởng + kết quả test, việc vận hành còn lại (lifecycle `tmp/`), việc FE/APP

---

## Dependencies

- Duyệt plan/V122/nhánh → Phase 1 → Phase 2 (T003–T013) → US1 (T014–T021).
- US2 cần US1 (service đã dùng `FileStorage`). US3 cần T019 + T022 không bắt buộc. US4 sau US1. US5 sau US1–US3 (ghi tài liệu theo code thật). Polish cuối.
- T021 (xóa lớp cũ) chỉ sau T014–T020.

## Parallel

- Phase 2: T005/T006 song song với T003/T004; T007, T008 song song.
- US1: T014–T018 song song (khác file); T019 riêng (file lớn).
- US2: T023–T029 song song sau T022.
- US5: T040–T042 song song.

## Implementation Strategy

1. MVP = Phase 1–3 (US1): mọi tệp mới đúng thư mục/tên/giới hạn, tệp cũ còn đọc được. Dừng, kiểm quickstart 1–2, 7.
2. + US2 (liên kết xem) — FE/APP có thể bắt đầu chuyển.
3. + US3 (tải trực tiếp học liệu) — cần lifecycle `tmp/` trên Console.
4. + US4/US5, Polish.
