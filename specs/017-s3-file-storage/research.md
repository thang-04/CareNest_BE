# Research: 017 Kho lưu tệp dùng chung trên S3

Nguồn đối chiếu: code `integration/storage/*`, `LearningResourcePolicy`, `docs/system/EDUCATION_MEDIA_S3.md`, migration V31/V61/V66/V80/V84,
nghiệp vụ chuẩn `nghiep_vu_chuan/ke_hoach_giao_duc.md` (mục Tab Kế hoạch chủ đề, Dạy học theo ngày — Tiền 12/11/2026).

## R1. Một dịch vụ chung thay cho 2 lớp đang có

- **Decision**: thêm `FileStorage` (một lớp) + `StorageProperties` (cấu hình loại tệp). `StorageService` và `PrivateImageStorage`
  (cùng 4 lớp con) chuyển thành gọi `FileStorage`, rồi xóa khi mọi service đã chuyển.
- **Rationale**: hai lớp hiện lặp nhau (put/get/delete, kiểm key UUID, requireConfigured). Gom một chỗ để áp tên tệp, giới hạn, MIME
  một lần (FR-021, SC-005).
- **Alternatives**: giữ 5 lớp + thêm presign vào từng lớp — lặp code, trái SC-005.

## R2. Giới hạn và định dạng trong cấu hình

- **Decision**: `carenest.storage.categories.<loại>` gồm `folder`, `code` (tiền tố tên tệp), `max-size` (DataSize), `types`
  (đuôi cho phép), tùy chọn `max-size-by-kind` (`image`/`video`) cho `hoclieu`, tùy chọn `legacy-prefix` (đọc key UUID cũ). Bind bằng
  `@ConfigurationProperties`, kiểm lúc khởi động (thư mục kết thúc `/`, đuôi có trong bảng MIME).
- **Rationale**: FR-002; Spring đã có, không thêm dependency.
- **Alternatives**: bảng DB cấu hình — thừa, cần migration.

## R3. Kiểm định dạng theo nội dung

- **Decision**: tách phần nhận dạng chữ ký đầu tệp (`inspect`) trong `LearningResourcePolicy` thành `FileTypes.sniff(ext, head)` dùng
  chung; bảng đuôi→MIME giữ nguyên (jpg/png/gif/webp/mp4/webm/pdf/pptx/docx/xlsx/txt/csv). Kiểm Office sâu (`validateOffice`) giữ ở
  module học liệu. Lưu `Content-Type` theo MIME suy từ nội dung, không theo client.
- **Rationale**: code đã có và đã test; FR-005.
- **Alternatives**: Apache Tika — dependency mới, cần duyệt, không cần cho 12 định dạng.

## R4. Tên tệp và chống trùng

- **Decision**: `<code>_<mã đối tượng>_<yyyyMMdd-HHmmss>_<4 ký tự [a-z0-9] SecureRandom>.<đuôi>`, giờ theo `carenest.time-zone`.
  Ghi bằng `PutObject` có `If-None-Match: *` (S3 conditional write); 412 ⇒ sinh lại, tối đa 3 lần.
- **Rationale**: FR-006/FR-007; 36^4 ≈ 1,7 triệu tổ hợp mỗi giây/đối tượng, đụng hiếm; điều kiện ghi bảo đảm không ghi đè.
- **Alternatives**: HEAD trước rồi PUT — có khe đua.

## R5. Key lưu trong DB và key cũ

- **Decision**: DB lưu key đầy đủ `<folder><tên tệp>` (vd. `meals/meal_12_20261112-101500_a1b2.jpg`, ≤ 255). Giá trị cũ là UUID
  ⇒ đọc theo `legacy-prefix + uuid` của loại (FR-018, User chốt giữ tệp cũ). Key mới phải khớp `^<folder><code>_[0-9A-Za-z-]+_\d{8}-\d{6}_[a-z0-9]{4}\.<đuôi cho phép>$`,
  không chứa `..`.
- **Hệ quả**: cột key hiện 36/64/100 ký tự không đủ ⇒ cần migration nới cột (xem data-model, **chờ Tiền duyệt**).
- **Alternatives**: chỉ lưu tên tệp, thư mục suy từ loại — vẫn vượt 36 ký tự khi id dài; trái yêu cầu "lưu đường dẫn".

## R6. Xem/tải qua liên kết có hạn

- **Decision**: `S3Presigner` (có sẵn trong artifact `software.amazon.awssdk:s3`, không thêm dependency) ký `GetObject` 15 phút
  (`carenest.storage.presign-ttl`), kèm `response-content-disposition` (tên hiển thị) và `response-content-type`. Mỗi endpoint đọc tệp
  hiện có thêm endpoint anh em `.../url` trả `{url, expiresAt}` sau cùng bước kiểm quyền. Endpoint stream cũ giữ nguyên để FE/APP chuyển dần
  (constitution 5).
- **Rationale**: FR-009; không đổi contract đang dùng.
- **Alternatives**: đổi endpoint cũ thành 302 redirect — App/axios có thể gửi kèm `Authorization` sang S3 ⇒ S3 báo lỗi hai cơ chế ký.

## R7. Tải trực tiếp + xác nhận, không thêm bảng

- **Decision**: cấp PUT có hạn vào `tmp/<folder><tên tệp>`; chữ ký gồm `Content-Type` và `Content-Length` khai báo. Xác nhận: `HeadObject`
  (đúng size khai báo, ≤ giới hạn) → đọc 8 KB đầu (`Range`) → `sniff` → `CopyObject` sang `<folder><tên tệp>` → xóa `tmp/...`. Sai ⇒ xóa
  `tmp/...`, báo lỗi. Lifecycle `tmp/` xóa sau 1 ngày dọn tệp không được xác nhận (FR-011).
- **Rationale**: không cần bảng "phiếu chờ xác nhận" và job dọn; không cần thêm quyền IAM (Copy dùng Get+Put). Key `tmp/` mang loại +
  mã đối tượng nên lúc xác nhận module kiểm lại quyền trên đối tượng đó.
- **Alternatives**: bảng `storage_pending_uploads` + scheduler — thêm migration và job; object tag `pending` — thêm quyền IAM tagging.
- **Phạm vi**: chỉ `hoclieu` dùng tải trực tiếp (video lớn). Ảnh ≤ 5 MB vẫn multipart qua BE như hiện tại.

## R8. Điểm gắn học liệu

- **Fact**: nghiệp vụ chuẩn 12/11/2026 bỏ phần học liệu khỏi trang Kế hoạch chủ đề và chỉ dạy học/trình chiếu ở Kế hoạch giáo dục ngày.
  API học liệu hiện vẫn gắn theo `class-docs/theme/{docKey}`.
- **Decision**: feature này chỉ đổi cách **lưu** học liệu (thư mục `hoclieu/`, tên, tải trực tiếp); giữ nguyên điểm gắn và quyền hiện có
  (FR-019). Chuyển học liệu sang giáo án ngày là thay đổi nghiệp vụ EDU riêng, cần Tiền chốt — ghi vào mục mở của plan.

## R9. CORS, IAM, lifecycle (vận hành, Tiền làm trên Console 12/11/2026)

- Block public: bật. CORS: GET/PUT/HEAD từ `http://localhost:5173`, `https://dev.carenest.edu.vn`, `https://web.carenest.edu.vn`.
  Lifecycle `imports/` 7 ngày: đã tạo. IAM inline policy chỉ trên bucket: đã thay quyền rộng.
- Còn thiếu: lifecycle `tmp/` 1 ngày (R7) — thêm vào quickstart.
