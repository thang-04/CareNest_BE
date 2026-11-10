---
id: ENV-261016-flyway-duplicate-version-stale-target
type: env
modules: [deployment, identity-access]
rules: []
status: workaround
date: 2026-10-16
keywords: [flyway, migration, version, target, đổi nhánh, git switch, stash, Found more than one migration]
similar_to: []
---

# ENV-261016-flyway-duplicate-version-stale-target — Flyway báo trùng version sau khi đổi nhánh

## Symptom
Mọi test `@SpringBootTest` (kể cả `OpenApiSnapshotTest`) lỗi khởi tạo context:
```text
Error creating bean with name 'flywayInitializer' ... Found more than one migration with version 7
```

## Điều kiện tái hiện
Build trên nhánh A có migration `V7__x.sql` chưa commit → stash/đổi sang nhánh B có `V7__y.sql` → chạy `mvnw test` không `clean`. `target/classes/db/migration` vẫn giữ `V7__x.sql` từ lần build trước nên Flyway thấy 2 file V7.

## Attempts — đã thử
| # | Cách thử | Kết quả | Vì sao không đúng / bài học |
| --- | --- | --- | --- |
| 1 | Kiểm `src/main/resources/db/migration` | Chỉ có 1 file V7 | Lỗi không nằm ở source mà ở output build cũ |
| 2 | `mvnw clean` rồi chạy lại | Hết lỗi | Maven không xóa resource đã bị xóa khỏi source khi build tăng dần |

## Root cause
Maven resources copy thêm/ghi đè nhưng không xóa file đã biến mất khỏi `src/main/resources`; migration của nhánh khác còn sót trong `target/classes`.

## Fix
Workaround: `mvnw clean` (hoặc `node scripts/verify.mjs` sau `clean`) mỗi khi đổi nhánh có migration khác nhau.

## Regression test
Không áp dụng (lỗi môi trường build).

## Ảnh hưởng
Chỉ máy dev/CI dùng lại `target/`. Hai nhánh G94-60 (V7 phân quyền) và G94-200 (V7–V9 AI) dễ gặp cho tới khi G94-200 đổi số migration (plan `2026-10-16-permission-management.md`, quyết định 8).

## Lesson
Lỗi "trùng version"/"migration lạ" sau khi đổi nhánh ⇒ nghi `target/` cũ trước khi sửa migration.
