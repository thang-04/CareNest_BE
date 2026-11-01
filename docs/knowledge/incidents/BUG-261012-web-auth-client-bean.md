---
id: BUG-261012-web-auth-client-bean
type: bug
modules: [identity-access]
rules: []
status: fixed
date: 2026-10-12
keywords: [ApplicationContext, WebAuthKeycloakClient, No qualifying bean]
similar_to: []
---

# BUG-261012-web-auth-client-bean — Gateway client chưa được đăng ký bean

## Symptom
Full verify có 25 context errors; nguyên nhân đầu tiên: `No qualifying bean of type 'com.carenest.integration.keycloak.WebAuthKeycloakClient'`.

## Điều kiện tái hiện
Chạy full verify hoặc `CareNestApplicationTests` với PostgreSQL Testcontainers; service draft đã là Spring bean nhưng dependency client chưa đăng ký.

## Attempts
| # | Cách thử | Kết quả | Bài học |
| --- | --- | --- | --- |
| 1 | Chỉ chạy unit client/service | Pass, không tạo Spring context | Unit test không kiểm component scanning |
| 2 | Full verify | 25 errors cùng thiếu bean | Đọc Caused by đầu tiên thay vì sửa từng context failure |
| 3 | Thêm Component và Autowired cho constructor production | Context + OpenAPI regression 3 tests pass, skip 0 | Constructor test giữ package-private |

## Root cause
`WebAuthKeycloakClient` có hai constructor nhưng chưa có component/injection metadata. Chỉ lộ khi khởi động toàn context; không phải lỗi database hay nhiều lỗi snapshot độc lập.

## Fix
Đăng ký adapter như `KeycloakIdentityProviderClient` hiện có, không nới test hoặc thay dependency.

## Regression test
`CareNestApplicationTests.contextLoads` và `OpenApiSnapshotTest`; log `target/context-regression.log`: 3 tests, 0 fail/error/skip.

## Ảnh hưởng
Backend không khởi động với bản draft lỗi, kể cả feature off; chưa deploy và không đổi dữ liệu người dùng.

## Lesson
Adapter được service inject phải được test trong context bên cạnh unit test.
