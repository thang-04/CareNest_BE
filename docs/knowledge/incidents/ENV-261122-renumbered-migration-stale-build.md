---
id: ENV-261122-renumbered-migration-stale-build
type: env
modules: [organization, observation, tooling]
rules: []
status: fixed
date: 2026-11-22
keywords: [Flyway, duplicate migration, clean, V170, V171]
---

# Migration đã đổi số còn lưu trong thư mục build

## Symptom

Sau merge dev và đổi số migration riêng của PR, ứng dụng và test báo `Found more than one migration with version 167`.

## Root cause

Source chỉ còn V167 permission, V168 work campus; catalog và nhận xét tuần đã đổi thành V170/V171 theo phê duyệt của user. Maven resources không tự xóa bản V167/V168 cũ trong target/classes khi file source đổi tên. Thư mục build chứa cả hai bản.

## Attempts

1. Chạy Maven test không clean: Spring context không khởi động được do resource cũ. Không dùng lượt này để kết luận lỗi test nghiệp vụ.
2. Dừng đúng JVM test đang thất bại, chạy `mvnw.cmd clean test` để tạo lại build từ source; đang kiểm chứng toàn bộ suite.

## Fix

- Build sạch sau khi đổi tên migration; không sửa SQL hoặc migration đã thuộc dev.
- DB UI bản sao được backup trước khi đổi hai dòng lịch sử migration riêng của PR sang V170/V171. Chỉ chạy trên `carenest_ui_20261122`; không sửa DB gốc hoặc production.
- Khi khởi động bản sao, cho phép out-of-order để áp dụng V167/V168 của dev vốn chưa có trên bản sao này. Đây là cấu hình launcher thử local, không thay cấu hình triển khai.

## Regression test

Full Maven clean test và khởi động launcher UI từ build sạch đang chạy. Kết quả cuối ghi trong báo cáo kiểm chứng.

## Bổ sung local gốc — 23/11

User gửi lỗi checksum167/168 và yêu cầu sửa trên máy để cùng test. Đã xác minh DB .env là carenest_test_20261121, lịch sử đúng tên cũ và checksum -2003043868/-2051822585. Backup pg_dump custom local4259684bytes trước khi thao tác. Transaction có kiểm current_database và đúng một dòng mỗi migration: đổi version/script sang170/171, không đổi checksum. Khởi động CareNestApplication bằng Clock thật, áp dụng các migration dev còn thiếu theo out-of-order chỉ cho tiến trình này. Không đổi SQL, không chạy repair chung, không reset DB.

Khởi động thành công8080, Flyway validate185 migration, cả167–171 success. Số dữ liệu trước/sau giữ289 trẻ,11972 attendance,11340 observation. Bản backup ngoài Git: logs/carenest-local-before-flyway-20261123.dump. UI5173 tải được hồ sơ phát triển từ DB local gốc. .env không sửa/commit; lần khởi động IntelliJ sau không cần bật out-of-order vì đã áp dụng đủ migration.

## Lesson

Đổi tên migration trong source phải kèm build sạch khi kiểm thử; resource cũ không phải migration đang tồn tại trong Git.
