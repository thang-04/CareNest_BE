---
id: BUG-261113-factor-bearer-authority
type: bug
modules: [identity-access]
rules: [AUTH-21]
status: fixed
date: 2026-11-13
keywords: [FACTOR_BEARER, FactorGrantedAuthority, authorities, role, JwtAuthenticationToken, Spring Security 7, MobileStaffReadOnlyFilter]
similar_to: []
---

# BUG-261113-factor-bearer-authority — Đếm authorities của token để biết "còn role nào" bị sai vì có `FACTOR_BEARER`

## Symptom
Filter BGH chỉ xem trên App (ADR-0026) không nhận ra tài khoản chỉ có role BGH. Kết quả: GET ngoài danh sách cho phép vẫn 200, POST của PHT thuần ra 1101 thay vì 1301.
```text
SecurityConfigTest.shouldRejectStaffReadOutsideAllowListOnMobile: Status expected:<403> but was:<200>
SecurityConfigTest.shouldRejectStaffWriteOnMobile: JSON path "$.errorCode" expected:<1301> but was:<1101>
```

## Điều kiện tái hiện
Token Bearer (`azp=carenest-mobile`) chỉ mang realm role `VICE_PRINCIPAL`; gọi `POST` qua filter chain thật (`SecurityConfigTest`).

## Attempts — đã thử
| # | Cách thử | Kết quả | Vì sao không đúng / bài học |
| --- | --- | --- | --- |
| 1 | Coi "không còn authority nào sau khi bỏ role BGH" là BGH thuần | Sai | `JwtAuthenticationToken` còn authority `FACTOR_BEARER` |
| 2 | Chỉ xét authority bắt đầu bằng `ROLE_` | Đúng | — |

## Root cause
Spring Security 7 gắn thêm authority yếu tố xác thực (`FACTOR_BEARER`) cho authentication từ token Bearer, ngoài các authority do `KeycloakRealmRoleConverter` trả. Code coi mọi authority là role nên luôn thấy "còn role khác".

## Fix
`MobileStaffReadOnlyFilter`: xác định BGH thuần bằng cách chỉ xét authority tiền tố `ROLE_`; khi bỏ role BGH cho tài khoản nhiều role vẫn giữ các authority khác.

## Regression test
- `SecurityConfigTest#shouldRejectStaffWriteOnMobile`, `#shouldRejectStaffReadOutsideAllowListOnMobile`: fail trước fix, pass sau fix.
- `MobileStaffReadOnlyFilterTest#shouldFailClosedWithoutTransactionManager`: dựng token có `FACTOR_BEARER`.

## Ảnh hưởng
Chỉ code mới của ADR-0026 (chưa phát hành). Không đổi dữ liệu.

## Lesson
Muốn biết user còn role gì thì lọc `ROLE_*` hoặc dùng `CurrentUser.roles()`, không đếm `getAuthorities()`.
