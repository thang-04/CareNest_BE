---
id: BUG-261013-web-auth-session-absolute-expiry
type: bug
modules: [identity-access]
rules: [AUTH-07]
status: fixed
date: 2026-10-13
keywords: [refresh_expires_in, expiresAt, ssoSessionIdleTimeout, ProviderUnavailable, abortRefresh, 503 retry]
similar_to: [BUG-261012-web-auth-session-otp-boundaries]
---

# BUG-261013-web-auth-session-absolute-expiry — Phiên web chết sau 30 phút; Keycloak lỗi tạm xóa phiên

## Symptom
Review luồng auth React ↔ BE: phiên gateway bị từ chối refresh khoảng 30 phút sau login dù người dùng đang hoạt động và chọn remember. Keycloak tạm không khả dụng khi refresh ⇒ trả 503 nhưng phiên local đã bị xóa, lần retry nhận 401 (trái contract "503 ⇒ thử lại").

## Điều kiện tái hiện
`WebAuthServiceTest` với clock giả, provider Mockito trả `refresh_expires_in=1800` (giá trị Keycloak trả khi realm `ssoSessionIdleTimeout 1800`); provider ném `ProviderUnavailable` khi refresh. Dữ liệu giả.

## Attempts
| # | Cách thử | Kết quả | Bài học |
| --- | --- | --- | --- |
| 1 | Đọc code: `expiresAt = now + min(36000, refresh_expires_in)`, `replaceTokens` không gia hạn `expiresAt` | Xác định nguyên nhân | `refresh_expires_in` là hạn idle, không phải hạn tuyệt đối |
| 2 | Thêm 2 regression test | RED: `expiresAt` 03:30 thay vì 13:00; `abortRefresh(..., false)` | Test cũ mock 36000 nên che lỗi |
| 3 | Tách hạn tuyệt đối = `SESSION_SECONDS`; `ProviderUnavailable` ⇒ `abortRefresh(keep=true)` | GREEN | — |

## Root cause
Login lấy `refresh_expires_in` (idle của SSO session) làm hạn tuyệt đối phiên local, mà hạn tuyệt đối không bao giờ được gia hạn. Nhánh `ProviderUnavailable` coi mọi lỗi provider là rotation không chắc chắn và xóa phiên.

## Fix
`WebAuthService.login`: `expiresAt = now + SESSION_SECONDS`, idle vẫn `min(IDLE_SECONDS, refresh_expires_in)`; Keycloak `ssoSessionMaxLifespan` vẫn là trần thật (quá hạn ⇒ `invalid_grant` ⇒ xóa phiên). `refresh`: `ProviderUnavailable` giữ phiên, nhả lease. Vẫn fail-closed vì realm `revokeRefreshToken: true`, `refreshTokenMaxReuse: 0`: nếu Keycloak đã rotate trước lỗi, token cũ bị từ chối ⇒ `SessionRejected` ⇒ xóa phiên. Đổi ruling Phase 2 "uncertain rotation fails closed" ở tầng BE sang fail-closed ở tầng provider.

## Regression test
`WebAuthServiceTest.loginAbsoluteExpiryIsNotCappedByTheProviderIdleRefreshLifetime`, `WebAuthServiceTest.unreachableProviderKeepsTheLocalSessionSoTheClientCanRetry`; `WebAuthKeycloakClientTest.revokedRefreshIsSessionRejectionRatherThanProviderOutage` phủ nhánh token cũ bị từ chối.

## Ảnh hưởng
Chỉ feature gateway (mặc định tắt, chưa deploy). Không đổi API/schema. Chưa chạy E2E thật vượt mốc 30 phút.

## Lesson
Không suy hạn tuyệt đối từ `refresh_expires_in`. Test fixture provider phải dùng giá trị realm thật (1800), không giá trị lý tưởng.
