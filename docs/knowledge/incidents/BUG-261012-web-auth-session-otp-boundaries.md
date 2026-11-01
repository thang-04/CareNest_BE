---
id: BUG-261012-web-auth-session-otp-boundaries
type: bug
modules: [identity-access]
rules: [AUTH-07, AUTH-09]
status: fixed
date: 2026-10-12
keywords: [refresh lease, OTP resend, CSRF, JwtRequestPostProcessor, cleanup]
similar_to: []
---

# BUG-261012-web-auth-session-otp-boundaries — Regression tại ranh giới phiên/OTP

## Symptom
Draft service/state cho complete refresh sau lease/idle expiry, reset OTP attempts khi resend, thiếu logout token sau lỗi JWT và cho retry proof khi mutation chưa rõ kết quả.

## Điều kiện tái hiện
Tests với clock giả, Mockito provider và PostgreSQL thật; toàn bộ dữ liệu giả. Các regression RED ghi ở `target/phase2-regression.log` trước sửa.

## Attempts
| # | Cách thử | Kết quả | Bài học |
| --- | --- | --- | --- |
| 1 | Thêm regression expired lease/idle, resend budget, cleanup, proof uncertain, inactive first-login | 60 tests, 8 failures, 0 errors/skip | Các unit happy path ban đầu chưa phủ ranh giới |
| 2 | Fencing expiry, giữ attempts, finally cleanup, consume uncertain proof, coalesce BUSY | 1 failure + 1 fixture error còn lại | Fixture resend cần dùng API transactional mới |
| 3 | CSRF test dùng jwt() helper | Helper gọi CsrfFilter.skipRequest nên bỏ qua CSRF ngay trong test | Không dùng helper này để chứng minh CSRF |
| 4 | Bearer header thật; đúng fixture store; exact matcher tại CsrfFilter | 62 tests pass, 0 errors/skip | Kiểm cả auth POST có thêm Bearer |
| 5 | Review tìm refresh invalid_grant trả503 và cleanup không caller | Đã thêm SessionRejected401 + scheduler60s + regression unit/PostgreSQL; full196 pass | Phiên bị thu hồi khác provider outage; expiry check không thay retention cleanup |

## Root cause
Draft thiếu kiểm tra cuối lease/idle, resets attempts, đặt finally sau bước kiểm JWT, và giải phóng proof sau lỗi network không chắc chắn. Resource Server có cơ chế ignore CSRF cho Bearer; auth cookie cần matcher cuối cùng riêng. Test `jwt()` tự skip CSRF theo source Spring Security.

## Fix
Fencing owner+lease+expiry; caller BUSY chỉ chờ kết quả, không dùng lại refresh token; preserve OTP attempts và shared rate bucket; hủy provider session khi login/current-password verification thất bại; lỗi mutation uncertain tiêu hủy proof. Xác minh old password assertion dùng configured realm.

## Regression test
`WebAuthStateStoreIntegrationTest` lease/idle/concurrent claims/OTP; `WebAuthServiceTest` cleanup, inactive/proof/rate/coalescing; `WebAuthControllerTest.everyPublicMutationRequiresCsrfEvenWithBearer`. [Spring test helper source](https://github.com/spring-projects/spring-security/blob/7.1.x/test/src/main/java/org/springframework/security/test/web/servlet/request/SecurityMockMvcRequestPostProcessors.java) giải thích jwt() tự skip.

## Ảnh hưởng
Chỉ feature gateway mới chưa deploy; business Bearer APIs, scope và Mobile PKCE giữ cơ chế cũ. Không cần sửa dữ liệu.

## Lesson
Kiểm authorization tại lúc commit kết quả lẫn lúc bắt đầu; không reset abuse counters khi resend. Kiểm CSRF bằng request header/cookie thực thay vì helper tự tắt filter.
