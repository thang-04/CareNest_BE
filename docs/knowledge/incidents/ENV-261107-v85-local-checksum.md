---
id: ENV-261107-v85-local-checksum
type: env
modules: [deployment, education-plan]
rules: []
status: workaround
date: 2026-11-07
keywords: [Flyway, V92, checksum mismatch, presentation_snapshot]
similar_to: [ENV-261030-flyway-renumbered-stale-db]
---

# ENV-261107-v85-local-checksum — Backend dừng khi validate V92

## Symptom
`Migration checksum mismatch for migration version 85`: applied `-449248398`, resolved `-969916`.

## Điều kiện tái hiện
Chạy backend profile `dev` với DB local đã áp V92 trước phiên bản file hiện tại. V92 còn untracked tại thời điểm điều tra; source và target/classes hiện giống nhau.

## Attempts
| # | Cách thử | Kết quả | Bài học |
| --- | --- | --- | --- |
| 1 | Đối chiếu log, source, target/classes và lịch sử DB | V92 success=true nhưng checksum cũ; cột presentation_snapshot đã tồn tại (jsonb); cells là text | Không phải resource cũ trong target; không chạy lại ALTER TABLE |
| 2 | Kiểm tra dữ liệu đánh giá chỉ bằng count | 0 bản ghi, 0 confirmed, 0 confirmed thiếu snapshot | Không có snapshot cũ cần backfill trên DB local này |
| 3 | Maven spring-boot:run profile dev trước xử lý | Exit 1, tái hiện đúng checksum -449248398 / -969916 | Lịch sử DB chặn source hiện tại |
| 4 | Sao lưu metadata; transaction sửa riêng checksum V92 với điều kiện lịch sử cũ và bảng đánh giá rỗng | Commit thành công, checksum -969916, success=true | Không chạy lại SQL migration hoặc thay schema |
| 5 | Maven spring-boot:run profile dev sau xử lý, gọi actuator/health | Validate 89 migrations, ứng dụng start và health UP | Dừng riêng process chạy thử sau kiểm chứng, nhường cổng IDE |

## Root cause
Nội dung V92 hiện tại khác bản đã áp vào DB local. Flyway chặn trước khi khởi tạo entityManagerFactory. Chỉ xuất hiện sau khi file đã áp thay đổi; thêm migration mới không tự giải quyết validation của V92.

## Fix
Đã sao lưu metadata tại `target/flyway-v85-history-before.json`, đồng bộ riêng checksum local với điều kiện checksum cũ, success=true và bảng đánh giá rỗng, trong transaction có khóa bảng. Không sửa migration/schema/API, không tắt validate. Không áp dụng cách này cho DB khác khi chưa đối chiếu schema và dữ liệu. Checksum trước xử lý là -449248398; hoàn tác metadata sẽ khiến source hiện tại bị validate chặn trở lại.

## Regression test
`.\mvnw.cmd -DskipTests -Dspring-boot.run.profiles=dev spring-boot:run`: trước xử lý exit 1, mismatch V92 (`target/flyway-v85-before.log`); sau xử lý validate 89 migrations, schema up to date, `Started CareNestApplication` (`target/flyway-v85-after.log`), HTTP health `status=UP`. Phiên chạy thử đã dừng có chủ đích sau kiểm chứng. Không chạy suite unit/integration vì không đổi source/schema.

## Ảnh hưởng
Khởi động backend trên DB local này; FE/APP không đổi contract. Không có bản ghi đánh giá bị ảnh hưởng. Các thay đổi đang có trong working tree được giữ nguyên.

## Lesson
Migration đã áp phải giữ nội dung ổn định. Checksum repair chỉ hợp lệ sau khi đối chiếu tác động SQL; không dùng repair để che schema hoặc dữ liệu chưa cập nhật.
