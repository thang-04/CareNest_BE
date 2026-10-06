# Issue Index

Search đầu tiên khi gặp bug, lỗi môi trường, case lạ. Grep chuỗi lỗi nguyên văn, module, từ khóa VN/EN, CamelCase class. 1 issue = 1 dòng, **kể cả bug đang mở** (`open`). Chi tiết và các cách đã thử nằm trong `incidents/<ID>.md`. Cách ghi: `.ai/workflows/update-knowledge.md` T2.

ID: `BUG-` (code) · `CASE-` (edge case nghiệp vụ) · `ENV-` (môi trường/build/deploy), dạng `<loại>-YYMMDD-<slug>` (vd. `BUG-261004-meal-count-stale`). Status: `open` | `workaround` | `fixed`.

| ID | Module | Triệu chứng (từ khóa + chuỗi lỗi) | Root cause (1 câu, `?` nếu chưa rõ) | Status |
| --- | --- | --- | --- | --- |
| BUG-001 | identity-access | Keycloak tắt, API chưa cache JWKS ⇒ 500 body Boot `AuthenticationServiceException` `Couldn't retrieve remote JWK set` | Failure handler mặc định của `BearerTokenAuthenticationFilter` ném lại lỗi thay vì gọi entry point | fixed |
| ENV-001 | identity-access | Keycloak `invalid_grant` `Account is not fully set up`, user không có email/lastName | User profile mặc định Keycloak 26 bắt buộc email + lastName | fixed |
