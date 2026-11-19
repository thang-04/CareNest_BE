---
id: ENV-261110-local-api-shared-db-read-only
type: env
modules: [health, tooling]
rules: [HLT-17]
status: workaround
date: 2026-11-10
keywords: [read-only, default_transaction_read_only, SELECT FOR NO KEY UPDATE, ssh tunnel, carenest_dev, closeExpiredHealthChecks, API local]
similar_to: [ENV-261106-dev-migration-renumber-checksum]
---

# ENV-261110-local-api-shared-db-read-only — API chạy ở máy local trỏ DB dev VPS chế độ chỉ đọc thì dừng lúc khởi động

## Symptom
API chạy ở máy local (`mvnw.cmd spring-boot:run`, profile `dev`) trỏ DB `carenest_dev` của VPS qua SSH tunnel, thêm `options=-c%20default_transaction_read_only=on` vào `DB_URL` để không ghi vào DB dùng chung. API dừng ngay sau `Started CareNestApplication`:
```text
ERROR: cannot execute SELECT FOR NO KEY UPDATE in a read-only transaction
[HealthCheckServiceImpl]|closeExpiredHealthChecks|ERROR|type=JpaSystemException
Application run failed
```

## Điều kiện tái hiện
- Tunnel `ssh -N -L 18330:127.0.0.1:18330 thangnd@<VPS>`; code nhánh `dev` (worktree riêng), `SPRING_FLYWAY_ENABLED=false`.
- `DB_URL=jdbc:postgresql://localhost:18330/carenest_dev?options=-c%20default_transaction_read_only=on`.

## Attempts — đã thử (cập nhật mỗi phiên điều tra, để phiên sau không lặp lại)
| # | Cách thử | Kết quả | Vì sao không đúng / bài học |
| --- | --- | --- | --- |
| 1 | Chạy code nhánh feature G94-180 trỏ `carenest_dev` | Không chạy | Nhánh có migration V82–V84 chưa có ở `dev`: bật Flyway thì áp vào DB chung (deploy dev sau đó lệch checksum); tắt Flyway thì Hibernate `validate` báo thiếu bảng |
| 2 | Code `origin/dev` (worktree), Flyway tắt, DB chỉ đọc (`default_transaction_read_only=on`) | API dừng lúc khởi động (lỗi ở trên) | `closeExpiredHealthChecks` chạy ở `ApplicationReadyEvent`, khóa dòng bằng `SELECT ... FOR NO KEY UPDATE`; Postgres coi là thao tác ghi; ngoại lệ ném lại nên Spring Boot dừng |
| 3 | Như cách 2 nhưng bỏ tham số chỉ đọc | API chạy; log `closeExpiredHealthChecks|count=0|END` | Job làm đúng việc server dev đã làm lúc 0h nên thường không ghi gì |

## Root cause
`HealthCheckServiceImpl.closeExpiredHealthChecks` (`@EventListener(ApplicationReadyEvent.class)` và `@Scheduled(cron = "0 0 0 * * *")`) đọc đợt/kỳ quá hạn bằng truy vấn khóa `FOR NO KEY UPDATE` rồi ném lại ngoại lệ. Vì vậy mọi cách chỉ đọc ở tầng DB (session read-only, Hikari `read-only`, role chỉ có SELECT) đều làm API dừng lúc khởi động.

## Fix
Không đổi code. Quy trình chạy API local đọc DB dev VPS ghi ở `docs/knowledge/TROUBLESHOOTING.md` (mục "API local trỏ DB dev VPS"): code `dev` trong worktree, Flyway tắt, không đặt chỉ đọc, chỉ xem, tắt API trước 23h50.

## Regression test
Không có (cấu hình chạy tay).

## Ảnh hưởng
Không ảnh hưởng code. Khi không đặt chỉ đọc, API local ghi được vào DB chung nếu người dùng bấm Lưu/Gửi/Duyệt, và job 23h50 (sổ báo ăn), 0h (đóng đợt/kỳ) chạy song song với server.

## Lesson
API có job ghi lúc khởi động thì không chạy được với DB chỉ đọc. Bảo vệ DB dùng chung bằng quy trình (code khớp schema, Flyway tắt, không thao tác ghi) thay vì cờ read-only.
