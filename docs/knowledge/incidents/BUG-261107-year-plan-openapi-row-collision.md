---
id: BUG-261107-year-plan-openapi-row-collision
type: bug
modules: [education-plan]
rules: [EDU-58]
status: fixed
date: 2026-11-07
keywords: [OpenAPI, Row, Domain, schema collision, displayLabel]
similar_to: []
---

# Schema Row của kế hoạch bị DTO khác ghi đè trong OpenAPI

## Symptom
Snapshot OpenAPI: `YearPlanRequest.domains -> Domain.rows -> Row`, nhưng schema Row chỉ có assignment/user của DTO khác, không có mục tiêu/nội dung. Không thể mô tả đúng field displayLabel mới qua schema này.

## Attempts
| # | Cách kiểm tra | Kết quả |
| --- | --- | --- |
| 1 | Đối chiếu source request/response với schemas Domain/Row trong snapshot | Nhiều nested records cùng simple name; schema dùng chung bị ghi đè |
| 2 | Đặt Schema.name riêng cho Domain/Row/Content của request và response kế hoạch | Đã sửa annotation; chờ đối chiếu OpenAPI runtime |
| 3 | Đọc /v3/api-docs từ backend thật ở cổng thử8089 | 6 schema tách riêng; request displayLabel max100, response goalNo integer; themeKeys/themeIds đúng hướng |
| 4 | Cập nhật 8 schema kế hoạch từ runtime và so snapshot | Paths và mọi schema ngoài 8 block giữ nguyên, parser YAML đạt |

## Root cause
Springdoc đặt tên nested record theo simple name khi không chỉ định tên schema duy nhất. DTO kế hoạch và nhân sự có record Row/Domain/Content trùng tên.

## Fix
Đặt 6 tên riêng YearPlanDomainRequest/Response, YearPlanGoalRowRequest/Response, YearPlanGoalContentRequest/Response. Không đổi tên JSON field hoặc endpoint; cập nhật riêng schema kế hoạch từ runtime, giữ schema module khác.

## Regression test
Python asserts trên JSON runtime: request label max100, response goalNo integer, content request themeKeys / response themeIds. Snapshot YAML kiểm domain response trỏ YearPlanGoalRowResponse, paths và mọi schema ngoài scope không đổi. Backend khởi động, health UP; phiên thử đã dừng, không dừng process8080 của user.

## Ảnh hưởng
API tài liệu và client sinh từ schema; thực thi JSON trước đó không bị ảnh hưởng. Không sửa APP (repo sibling không có trong workspace).

## Tái phát khi kiểm feature013 — 08/11/2026

- Native OpenApiSnapshotTest thất bại: generic Domain thực tế là EduConfigResponse.Domain nhưng preview Domain cần name/rows; generic Content thực tế có text/themes trong khi diff cần lineage/change fields. Không có thay đổi field JSON trong delta013.
- Attempts: (1) so AssertJ expected/actual từ XML Surefire, phát hiện540 dòng diff; (2) review source độc lập xác định collision còn ở preview/config/changes, không chỉ thứ tự YAML; (3) thêm schema-shape regression, tách tên nested records eduplan, rồi sinh snapshot mới. Chưa đánh fixed trước output native cuối.
- Kế hoạch T030 giới hạn annotation/schema metadata + docs, giữ API runtime, không bypass hoặc đổi assertion snapshot.

### Kết quả tái phát08/11
T030 tách13Schema.name ở nested DTO giáo dục, không đổi JSON/endpoint. educationNestedSchemasKeepDistinctPayloadShapes RED→GREEN, snapshot sinh sau sửa ref đúngshape; lượt cuối OpenApiSnapshotTest bình thường4/4, suite94/94 fail/error/skip0. Đã kiểm preview rows/kind/themes, config standard, diff lineage/change, request key/response id.
