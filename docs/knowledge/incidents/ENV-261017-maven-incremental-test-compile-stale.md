---
id: ENV-261017-maven-incremental-test-compile-stale
type: env
modules: [deployment]
rules: []
status: workaround
date: 2026-10-17
keywords: [maven, test-compile, incremental, constructor, cannot be applied to given types, không báo lỗi]
similar_to: [ENV-261016-flyway-duplicate-version-stale-target]
---

# ENV-261017-maven-incremental-test-compile-stale — `test-compile` không báo lỗi dù test đã vỡ

## Symptom
Đổi constructor class main (thêm dependency) rồi chạy `mvnw -q -DskipTests test-compile` ⇒ không lỗi. Chạy test thật hoặc `clean` mới lộ:
```text
constructor StaffAccessPolicy in class ... cannot be applied to given types;
```

## Điều kiện tái hiện
Sửa chữ ký constructor/method trong `src/main`, không sửa file test dùng nó, chạy `test-compile` (biên dịch tăng dần): file test không đổi nên không được biên dịch lại.

## Attempts — đã thử
| # | Cách thử | Kết quả | Vì sao không đúng / bài học |
| --- | --- | --- | --- |
| 1 | `mvnw -q -DskipTests test-compile` + grep lỗi | Không thấy lỗi | Test cũ không được biên dịch lại |
| 2 | `mvnw -q -DskipTests clean test-compile` | Hiện đủ lỗi | Đúng |

## Root cause
Biên dịch tăng dần của maven-compiler-plugin chỉ biên dịch lại file test đã đổi; không theo dõi phụ thuộc chữ ký sang file test không đổi.

## Fix
Workaround: sau khi đổi chữ ký public dùng ở test, kiểm bằng `mvnw -DskipTests clean test-compile` hoặc `node scripts/verify.mjs` (chạy toàn bộ); không coi `test-compile` tăng dần là bằng chứng.

## Regression test
Không áp dụng (môi trường build).

## Ảnh hưởng
Chỉ vòng phản hồi khi code; `scripts/verify.mjs` vẫn bắt được lỗi.

## Lesson
Đổi chữ ký ⇒ grep nơi gọi trong `src/test` hoặc build `clean`.
