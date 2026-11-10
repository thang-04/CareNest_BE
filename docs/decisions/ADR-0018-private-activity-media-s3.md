# ADR-0018 — Học liệu hoạt động trên S3 private

- Status: ACCEPTED
- Date: 2026-10-27
- Liên quan: education-plan, EDU-12/18; user yêu cầu S3 và đồng ý thêm AWS SDK v2 trong chat.

## Bối cảnh
Học liệu ảnh/video/slide/tệp có dung lượng lớn, phải giữ quyền tài liệu lớp và liên kết MT. Không nhúng binary JSONB/localStorage.

## Quyết định
S3 private cho tất cả binary, metadata edu_learning_resources trong PostgreSQL. AWS SDK Java v2 tại integration/storage; credential môi trường/runtime role. Backend proxy sau scope check; không public URL/ACL. Frontend demo dùng IndexedDB Blob độc lập, không phải S3 thật.

## Phương án đã cân nhắc
| Phương án | Ưu | Nhược |
| --- | --- | --- |
| S3 private | Theo yêu cầu user, tệp không phụ thuộc máy API | Cần bucket/credential và đối soát rollback phân tán |
| Local volume | Ít cấu hình | Không phù hợp chỉ đạo S3 mới nhất |
| Public link/Office viewer | Dễ nhúng | Không giữ scope truy cập, không chọn |

## Hệ quả
Không transaction phân tán DB/S3; rollback cleanup best effort, object mồ côi không có metadata không đọc được. Không thay ID hoạt động cũ; fingerprint đổi thì gắn lại thủ công. Cấu hình và kiểm chứng thật: docs/system/EDUCATION_MEDIA_S3.md.

## Còn chờ
Cấu hình tham chiếu ngoài repo đã được user cung cấp cho ca kiểm S3 thật và ca kiểm đạt; credential chỉ nạp trong process test. Chưa cấu hình runtime/deploy CareNest và chưa kiểm sự cố mạng cloud thật. Không tự tạo/chỉnh bucket hay IAM.
