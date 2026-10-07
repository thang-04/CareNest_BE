---
id: BUG-261007-openapi-create-account-status
type: bug
modules: [identity-access]
rules: []
status: fixed
date: 2026-10-07
keywords: [OpenAPI, createAccount, 200, 201, FE handoff]
similar_to: []
---

# BUG-261007-openapi-create-account-status — OpenAPI lệch HTTP status tạo tài khoản

## Symptom
Snapshot `docs/api/openapi.yaml` mô tả `POST /api/accounts` trả 200; source controller dùng `ApiCode.CREATED` (201). Phát hiện tĩnh khi rà soát bàn giao FE; chưa gọi runtime trong lượt này.

## Điều kiện tái hiện
Đối chiếu `paths./api/accounts.post.responses` trong snapshot với `AccountController.createAccount`, `ApiCode.CREATED` và `ResponseJson.toJsonWithData`.

## Attempts — đã thử
| # | Cách thử | Kết quả | Vì sao không đúng / bài học |
| --- | --- | --- | --- |
| 1 | Đọc snapshot và lần theo source status | Xác nhận snapshot 200, source trả 201 | Snapshot tồn tại chưa chứng minh mô tả status đúng |
| 2 | `node scripts/verify.mjs` | FAIL, tests 0; Maven wrapper báo `Cannot index into a null array`, `Cannot start maven from wrapper` | Chưa chạy test/snapshot; không kết luận code test fail |
| 3 | Test đọc `/v3/api-docs` đòi `responses.201` cho `POST /api/accounts` | Fail trước sửa (`No value at JSON path ...responses['201']`), pass sau sửa | Lỗi ở cách springdoc suy status, không phải ở hành vi API |

## Root cause
Lệch tài liệu/source đã chứng minh: `docs/api/openapi.yaml:22` khai báo 200; `AccountController.java:46` dùng CREATED; `ApiCode.java:16` ánh xạ CREATED sang HTTP 201; `ResponseJson.toJsonWithData` dùng status đó. Springdoc chỉ đọc status từ `@ResponseStatus`/`@ApiResponse`; method trả `ResponseEntity` không có annotation ⇒ mặc định 200, dù runtime `ResponseJson` đặt 201.

## Fix
`@ResponseStatus(HttpStatus.CREATED)` trên `AccountController.createAccount` (hành vi runtime vẫn 201, không đổi), cập nhật snapshot `docs/api/openapi.yaml` (`"200"`/`OK` ⇒ `"201"`/`Created`). Endpoint mới trả mã khác 200 phải khai báo `@ResponseStatus` tương tự. Response lỗi (4xx/5xx) chưa mô tả trong OpenAPI; nguồn chuẩn là `ERROR_CONTRACT.md`/`AUTH_CONTRACT.md`.

## Regression test
`OpenApiSnapshotTest.createAccountIsDocumentedWithActualCreatedStatus` (đối chiếu với `AccountControllerTest` kiểm runtime 201).

## Ảnh hưởng
FE/APP dùng generated client từ OpenAPI có thể xử lý thiếu response 201. Snapshot còn có server URL `http://localhost`; khi tích hợp cần cấu hình base URL môi trường thực tế.

## Lesson
Trước bàn giao phải đối chiếu OpenAPI với HTTP status do controller trả, ngoài kiểm snapshot equality.
