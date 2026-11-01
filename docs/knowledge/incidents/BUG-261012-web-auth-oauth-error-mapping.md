---
id: BUG-261012-web-auth-oauth-error-mapping
type: bug
modules: [identity-access]
rules: []
status: fixed
date: 2026-10-12
keywords: [invalid_grant, invalid_client, expires_in, Jackson, HTTP400]
similar_to: []
---

# BUG-261012-web-auth-oauth-error-mapping — Không parse được error thiếu expires_in

## Symptom
Log adapter ghi `oauthErrorCode=unknown` cho JSON OAuth hợp lệ; malformed/invalid_client HTTP400 bị báo sai mật khẩu.

## Điều kiện tái hiện
Mock token endpoint trả `{error:invalid_grant}` hoặc `{error:invalid_client}` không có expires_in.

## Attempts
| # | Cách thử | Kết quả | Bài học |
| --- | --- | --- | --- |
| 1 | Fallback HTTP400 khi body không parse được | Test sai mật khẩu pass nhưng che lỗi parse | Không phân loại lỗi provider bằng status chung |
| 2 | Thêm malformed/error-client regression | 2 failures trong 7 tests | Test phải bao phủ lỗi cấu hình, không chỉ happy path |
| 3 | Error DTO expiresIn nullable; chỉ invalid_grant400 là sai credentials | 7 tests pass; sau mở rộng adapter 10 tests pass | Typed setup chỉ chấp nhận400, đủ trường và TTL hợp lệ |

## Root cause
Error DTO primitive long bắt buộc dù expires_in chỉ có trong typed setup; JSON mapper từ chối thiếu primitive. Fallback biến lỗi provider thành credential rejection.

## Fix
`ErrorBody.expiresIn` là Long; kiểm null cho setup challenge; malformed/invalid_client vẫn fail closed503. Provider policy/capability mapping cũng yêu cầu400 đúng mã.

## Regression test
`WebAuthKeycloakClientTest.malformedProviderErrorIsNotReportedAsWrongPassword`, `invalidClientBadRequestIsNotReportedAsWrongPassword`, cùng tests typed setup/invalid_grant.

## Ảnh hưởng
Chỉ gateway opt-in; không đổi JWT validation hoặc Mobile PKCE. Không có dữ liệu cần sửa.

## Lesson
Trường chỉ có trên một variant response phải nullable; không dùng human-readable error_description để quyết định xác thực.
