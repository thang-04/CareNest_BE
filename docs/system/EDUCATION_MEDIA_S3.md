# Học liệu hoạt động trên S3

User yêu cầu S3 và đồng ý AWS SDK v2 ngày 27/10/2026. Mọi loại ảnh/video/PDF/Office/tệp hỗ trợ lưu object S3 private; PostgreSQL chỉ metadata. Không fallback local nếu thiếu cấu hình hay S3 lỗi.

## Cấu hình

- CARENEST_EDUCATION_MEDIA_BUCKET: bucket riêng đã tạo.
- AWS_REGION: region bucket, mặc định ap-southeast-1.
- CARENEST_EDUCATION_MEDIA_PREFIX: mặc định education/learning-resources/.
- CARENEST_ACCOUNT_MEDIA_PREFIX: ảnh chân dung phụ huynh (≤ 2 MB, đối chiếu người đón), cùng bucket, mặc định accounts/photos/. Đọc qua API có kiểm phạm vi trẻ.
- CARENEST_HEALTH_MEDIA_PREFIX: ảnh kết quả khám chuyên khoa của phiếu theo dõi sức khỏe (tối đa 3 ảnh/lần, ≤ 1 MB/ảnh), cùng bucket, mặc định health/follow-up-results/. Đọc qua API có kiểm phạm vi trẻ, không trả key hay URL bucket.
- CARENEST_EDUCATION_MEDIA_ENDPOINT: để trống với AWS; URL endpoint của dịch vụ tương thích S3 khi cần.
- CARENEST_EDUCATION_MEDIA_PATH_STYLE: false cho AWS, tùy nhà cung cấp.
- Credential: default credential provider chain của SDK. Local/dev cấu hình môi trường AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY và AWS_SESSION_TOKEN nếu là credential tạm; runtime có thể dùng instance/container role. Không lưu khóa vào repo, chat, FE hay log.

Giữ Block Public Access và không cấp public ACL. IAM chỉ cần s3:PutObject/GetObject/DeleteObject trong prefix của bucket (HeadObject dùng GetObject). Không cần ListBucket cho luồng ứng dụng. Bucket có default encryption theo cấu hình của tổ chức. Backup/versioning/lifecycle do người vận hành chốt; không tự tạo hay thay đổi tài nguyên cloud trong task này.

## Quyền và dữ liệu

GET content đi qua backend, kiểm scope lớp và binding trước khi mở S3 stream. Không trả storage key hay URL bucket. Browser dùng protected Blob cho resource đang chọn; không tải tất cả video khi mở trang. Range hỗ trợ qua Spring Resource (cần kiểm thử streaming thực tế trên bucket khi cấu hình xong).

Upload giữ khóa parent để không lọt qua lúc gửi/duyệt. UUID mới cho mỗi object; metadata commit thất bại thì xóa đúng object mới. Upload S3 lỗi sau khi đã nhận request cũng thử dọn UUID mới. Nếu S3 đồng thời mất kết nối, có thể còn object mồ côi: không phục vụ khi không có metadata; cần đối soát inventory/metadata trước khi xóa, không đặt lifecycle xóa toàn prefix có tài liệu đang dùng.

## Kiểm chứng

Unit tests mock SDK kiểm bucket/key/private request/body và lỗi upload/dọn object. Service integration dùng PostgreSQL thật và storage giả có kiểm bytes để test quyền/trạng thái/idempotency. Đây chưa phải kiểm chứng tài khoản S3 thật. Khi có bucket/credential trên môi trường, chạy smoke upload ảnh/video/PDF/Office, đọc lại bytes, Range và restart API; không gửi secret cho agent.

SDK được pin 2.57.3 từ [release chính thức](https://github.com/aws/aws-sdk-java-v2/releases/tag/2.57.3); credential theo [tài liệu AWS](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html).

## Live S3 smoke — 2026-10-27
User đã chỉ định project cấu hình tham chiếu ngoài repo và cho phép dùng .env để test. Chỉ nạp credential vào environment của child process Maven; không copy vào CareNest hoặc in ra log. `mvnw.cmd -q -Dtest=StorageS3Smoke test` đạt 1 test/fail0/error0/skip0; fixture tự tạo, không cần tệp riêng. Test class tên Smoke nên không tự chạy trong full CI, không tạo test skip mặc định.

Đã kiểm chính StorageService: ảnh PNG, PDF, text và binary hơn1MiB → PutObject → HEAD/contentLength → GET/readAllBytes khớp → anonymous HEAD bị403/404 → DELETE → signed HEAD404. Prefix carenest-smoke/<random UUID>/, mỗi lần4object và đã dọn toàn bộ. Không sửa bucket/IAM/ACL. Test log kiểm không chứa access-key/secret-key values.

Đây là live storage integration; chưa phải toàn bộ browser→BE→PostgreSQL→S3 E2E, chưa mô phỏng mất mạng S3 thật hoặc kiểm lifecycle/backup. Runtime CareNest vẫn nạp cấu hình theo phần trên; không đọc tự động .env của project tham chiếu.
