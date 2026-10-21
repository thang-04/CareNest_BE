---
id: BUG-261006-jwks-unavailable-returns-500
type: bug
modules: [identity-access]
rules: []
status: fixed
date: 2026-10-06
keywords: [keycloak down, JWKS, AuthenticationServiceException, 500, 503, BearerTokenAuthenticationFilter]
similar_to: []
---

# BUG-261006-jwks-unavailable-returns-500 — Keycloak tắt khi API chưa cache JWKS ⇒ 500 body mặc định của Boot (không phải ResponseJson)

## Symptom
API vừa khởi động (chưa cache JWKS), Keycloak tắt, request có Bearer token hợp lệ:
```text
HTTP/1.1 500
{"timestamp":"...","status":500,"error":"Internal Server Error","path":"/api/accounts/me"}
org.springframework.security.authentication.AuthenticationServiceException: An error occurred while attempting to decode the Jwt: I/O error on GET request for ".../protocol/openid-connect/certs": Connection refused
```

## Điều kiện tái hiện
Lấy token khi Keycloak chạy → tắt Keycloak → khởi động API mới → gọi API có token. (API đã cache JWKS trước đó thì vẫn kiểm được token — không lỗi.)

## Attempts — đã thử
| # | Cách thử | Kết quả | Vì sao không đúng / bài học |
| --- | --- | --- | --- |
| 1 | Dựa vào `authenticationEntryPoint` đã cấu hình cho `oauth2ResourceServer` | Vẫn 500 | `BearerTokenAuthenticationFilter` dùng `AuthenticationEntryPointFailureHandler` mặc định **ném lại** `AuthenticationServiceException`, không gọi entry point |

## Root cause
`JwtAuthenticationProvider` chuyển lỗi lấy JWKS (không phải `BadJwtException`) thành `AuthenticationServiceException`; failure handler mặc định của filter ném tiếp ⇒ servlet container trả 500 qua `/error` của Boot.

## Fix
`SecurityConfig`: `withObjectPostProcessor` đặt `BearerTokenAuthenticationFilter.setAuthenticationFailureHandler(restAuthenticationEntryPoint::commence)`; `RestAuthenticationEntryPoint` trả **503 `Authentication service unavailable`** cho `AuthenticationServiceException`, 401 cho lỗi token. Đã kiểm e2e: Keycloak tắt + API mới ⇒ `{"code":503,...}`.

## Regression test
`config/SecurityConfigTest#shouldReturn503WhenSigningKeysCannotBeFetched` (JwtDecoder ném `JwtException`).

## Ảnh hưởng
FE/APP: 503 khi xác thực tạm không khả dụng ⇒ báo thử lại, **không** đẩy về màn hình đăng nhập (khác 401).

## Lesson
Lỗi hạ tầng trong filter chain không đi qua entry point mặc định; kiểm cả nhánh "provider không khả dụng", không chỉ token sai.
