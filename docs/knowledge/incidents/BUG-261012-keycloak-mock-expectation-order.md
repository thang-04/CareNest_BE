---
id: BUG-261012-keycloak-mock-expectation-order
type: bug
modules: [identity-access]
rules: []
status: fixed
date: 2026-10-12
keywords: [MockRestServiceServer, expectation order, Cannot add more expectations]
similar_to: []
---

# BUG-261012-keycloak-mock-expectation-order — Test khai báo expectation sau request

## Symptom
Test client Keycloak dừng trước assertion thứ hai:

```text
Cannot add more expectations after actual requests are made
```

## Điều kiện tái hiện
Trong cùng test, đăng ký expectation, gọi client tạo HTTP request, rồi đăng ký expectation khác trên cùng `MockRestServiceServer`.

## Attempts — đã thử
| # | Cách thử | Kết quả | Vì sao không đúng / bài học |
| --- | --- | --- | --- |
| 1 | Đăng ký response lỗi mật khẩu, gọi login, rồi mới đăng ký response lỗi client | Test harness ném `IllegalStateException` ở expectation thứ hai | Mock server khóa danh sách expectation sau request đầu tiên |
| 2 | Tách hai tình huống thành hai test độc lập | User chạy 9 tests pass; lượt tiếp theo adapter mở rộng10 tests pass | Mỗi server chỉ nhận expectation trước request của test đó |

## Root cause
`wrongPasswordIsGenericAndUnconfiguredGatewayIsNotReportedAsWrongPassword` tạo request trước khi đăng ký expectation tiếp theo, theo output Surefire ngày 2026-10-12.

## Fix
Tách tình huống sai mật khẩu và gateway chưa cấu hình thành hai test độc lập trong `WebAuthKeycloakClientTest`.

## Regression test
`WebAuthKeycloakClientTest.wrongPasswordIsGeneric` và `WebAuthKeycloakClientTest.unconfiguredGatewayIsNotReportedAsWrongPassword`; đã pass trong regression Phase 2 (adapter10 tests, 0 fail/error/skip).

## Ảnh hưởng
Chỉ test adapter Keycloak; không đổi API hay hành vi runtime.

## Lesson
Đăng ký toàn bộ expectation trước HTTP request hoặc chia mỗi tình huống thành test riêng để cô lập lifecycle của mock server.
