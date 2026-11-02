---
id: BUG-261102-proxy-overrides-x-request-id
type: bug
modules: [education-plan]
rules: [EDU-10]
status: fixed
date: 2026-11-02
keywords: [X-Request-Id, Idempotency-Key, 9002, MALFORMED_REQUEST, MethodArgumentTypeMismatchException, ULID, x-osh-dp, proxy, VPS, EduYearPlanController, LearningResourceController, handleMalformedRequest]
similar_to: []
---

# BUG-261102-proxy-overrides-x-request-id — VPS trả 9002 cho mọi thao tác ghi kế hoạch năm vì proxy ghi đè `X-Request-Id`

## Symptom
web.carenest.edu.vn, `/education/goals/new?buoc=3` bấm Lưu ⇒ `{"code":400,"desc":"Dữ liệu gửi lên không đúng định dạng","errorCode":9002}`. Dính mọi endpoint ghi của `EduYearPlanController` (create, update, submit, issue, return, start/finish-editing, heartbeat, versions, delete) và upload học liệu (`LearningResourceController`). Local, test, e2e đều pass.

## Điều kiện tái hiện
Gọi API qua domain VPS. Response có `x-osh-dp: hn01-dnsgr01-dataplan01` và `x-request-id: 01M6C4Y401Q2TBAJP49WXFHSVT` (ULID): proxy hosting đứng trước Caddy luôn đặt header này cho request đi vào, kể cả khi client gửi UUID hoặc không gửi.

## Attempts — đã thử
| # | Cách thử | Kết quả | Vì sao không đúng / bài học |
| --- | --- | --- | --- |
| 1 | Đọc tĩnh mapper FE `toYearPlanRequest`, DatePickerCell, ghi chú gửi duyệt | Không thấy sai | Hình payload đúng; lỗi không nằm ở body |
| 2 | So bundle web đang chạy với FE `main` | Khớp | Loại khả năng web chạy bản FE cũ |
| 3 | Deserialize payload thật bằng Jackson 3.1.5 vào `YearPlanRequest` | Parse OK | Loại giả thuyết record nhiều constructor / ngày sai |
| 4 | Gửi lại body với `title:null` | 9001 | Body parse được; 9002 nổ sau khi validate body |
| 5 | Body tối thiểu hợp lệ + `schoolYearId` không tồn tại | Vẫn 9002, không tới service | Lỗi ở header/tham số |
| 6 | Bỏ từng header | Bỏ `X-Edit-Session`/`X-Plan-Revision` ⇒ 9003; bỏ `X-Request-Id` vẫn 9002 | Server luôn nhận `X-Request-Id` không phải UUID ⇒ do proxy đặt |

## Root cause
BE nhận khóa chống gửi trùng bằng `@RequestHeader("X-Request-Id") UUID`. `X-Request-Id` là header truy vết mà proxy hosting coi là của mình và ghi đè thành ULID ⇒ `MethodArgumentTypeMismatchException` ⇒ `handleMalformedRequest` trả 9002. Log cũ chỉ có tên class exception nên không biết header nào hỏng.

## Fix
Đổi header sang `Idempotency-Key` (UUID) ở 11 endpoint, CORS `SecurityConfig`, `docs/api/openapi.yaml`. `handleMalformedRequest` log thêm tên header/param hoặc đường dẫn field JSON (không log giá trị). FE (`educationPlanApi.js` `editHeaders`, `learningResourceService.js`) đổi cùng đợt.

## Regression test
`EduYearPlanControllerTest.createIgnoresProxyRequestIdHeader`, `EduYearPlanControllerTest.createWithoutIdempotencyKeyIs400NamingHeader`, `LearningResourceControllerTest.multipartAcceptsActivityBindingAsParts` (gửi kèm `X-Request-Id` ULID), `SecurityConfigTest.shouldAllowCorsPreflightFromConfiguredOrigin`.

## Ảnh hưởng
Đổi API contract (tên header), phải deploy BE và FE cùng đợt: BE mới + FE cũ ⇒ 9003 "Thiếu thông tin bắt buộc: Idempotency-Key". Không có dữ liệu sai (request bị chặn trước service). APP không dùng.

## Lesson
Không dùng header truy vết chuẩn hạ tầng (`X-Request-Id`, `X-Forwarded-*`, `traceparent`) để mang dữ liệu nghiệp vụ; proxy có quyền ghi đè. Dò 9002/9001/9003 bằng cách bỏ dần từng phần request.
