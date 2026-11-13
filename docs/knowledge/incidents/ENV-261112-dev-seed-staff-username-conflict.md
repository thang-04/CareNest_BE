---
id: ENV-261112-dev-seed-staff-username-conflict
type: env
modules: [identity-access, deployment]
rules: []
status: fixed
date: 2026-11-12
keywords: [flyway, R__dev_seed_thuong_hong, duplicate key, uk_user_accounts_username, th.gv.a02, 23505, repeatable, checksum, deploy dev, 18311, Restarting]
similar_to: [BUG-261101-dev-seed-class-no-age-group, ENV-261106-dev-migration-renumber-checksum]
---

# ENV-261112-dev-seed-staff-username-conflict — Deploy dev: API restart liên tục vì seed Thượng Hồng trùng username

## Symptom
CI/CD deploy dev: container `api` `Restarting (1)`, health check `curl: (7) Failed to connect to 127.0.0.1 port 18311`:
```text
FlywayMigrateException: Failed to execute script R__dev_seed_thuong_hong.sql
SQL State  : 23505
Message    : ERROR: duplicate key value violates unique constraint "uk_user_accounts_username"
  Detail: Key (username)=(th.gv.a02) already exists.
Line       : 67
```

## Điều kiện tái hiện
DB có sẵn `user_accounts.username = 'th.gv.a02'` với `keycloak_user_id` khác id seed (`00000000-0000-4000-c000-…`), rồi Flyway chạy `R__dev_seed_thuong_hong.sql`.

## Root cause
`R__dev_seed_thuong_hong.sql` mục 4 chèn 20 nhân sự với `ON CONFLICT (keycloak_user_id) DO NOTHING` — chỉ bắt trùng theo `keycloak_user_id`, không bắt `uk_user_accounts_username`. Header file ghi "idempotent theo khóa tự nhiên (username)" nhưng câu lệnh này không làm đúng thế. Lộ ra khi file đổi checksum (commit gộp bảng liên kết phụ huynh) ⇒ Flyway chạy lại repeatable trên DB dev đã có tài khoản cùng username nhưng id Keycloak khác. Vì sao DB dev có dòng đó: chưa xác minh trên VPS (giả thuyết: tài khoản được cấp thật qua ứng dụng hoặc DB clone từ môi trường khác).

## Fix
Đổi thành `ON CONFLICT DO NOTHING` (không chỉ định cột) ⇒ bỏ qua dòng trùng bất kỳ unique nào; các bước sau join theo `username` nên dùng luôn tài khoản có sẵn. Regression: `DevSeedIntegrationTest#thuongHongSeedSkipsStaffUsernameAlreadyProvisioned`.

## Phòng ngừa
Seed repeatable chèn vào bảng có nhiều unique: dùng `ON CONFLICT DO NOTHING` không cột hoặc `WHERE NOT EXISTS` theo mọi khóa tự nhiên; DB dev không phải lúc nào cũng chỉ chứa dữ liệu seed.
