# Implementation plan — 014

Java 21/Spring/PostgreSQL/Flyway sẵn có, không dependency mới. Plan quản trị: `docs/plans/active/2026-11-10-twilio-sms-otp.md` Phase 2. Kiến trúc: ADR-0026.

1. Migration `V83__parent_activation_codes.sql`, entity + repository (khóa dòng khi kiểm).
2. `ParentActivationCodes` (impl, package-private): sinh mã `SecureRandom`, hash SHA-256 gắn userId, lưu/thay mã trong transaction tạo tài khoản. `ParentSmsSender.sendActivationCode`.
3. `ChildEnrollmentServiceImpl`, `SelectedParentAccountServiceImpl`: khi `SmsProperties.usesRealProvider()` tạo user Keycloak không mật khẩu, lưu mã trong callback, gửi mã sau commit.
4. `IdentityProviderClient.setPassword` (Keycloak reset-password `temporary=false`).
5. `ParentActivationService` + `ParentActivationController` `POST /parent-activation` (`permitAll`, đăng ký trong `SecurityConfig`). `GuardianAccountService.resendActivationCode` + `POST /parent-accounts/{userId}/activation-code`.
6. ApiCode mới `ACTIVATION_NOT_PENDING`; dùng lại `OTP_INVALID_OR_EXPIRED`, `OTP_RESEND_TOO_SOON`, `PASSWORD_POLICY_VIOLATION`.
7. Test unit/controller/Testcontainers, cập nhật snapshot OpenAPI có chủ đích, docs (AUTH_CONTRACT, DATA_DICTIONARY, CROSS_REPO_MAP, module card).

Kiểm chứng: `node scripts/verify.mjs` full.
