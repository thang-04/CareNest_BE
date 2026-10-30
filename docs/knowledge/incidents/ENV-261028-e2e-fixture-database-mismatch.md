---
id: ENV-261028-e2e-fixture-database-mismatch
type: env
modules: [education-plan]
rules: [EDU-24]
status: fixed
date: 2026-10-28
keywords: [E2E, fixture, school year not found, carenest_merge, E2E_PG_DB]
similar_to: []
---
# E2E tạo fixture sai database nên API không tìm thấy năm học

## Symptom

Đăng nhập Keycloak thành công nhưng E2E beforeAll tạo kế hoạch nhận 404: Không tìm thấy năm học. ID/năm của cấu hình giống dữ liệu seed nên dễ tưởng SQL fixture trỏ đúng DB.

## Điều kiện tái hiện

Backend DB_URL dùng database carenest_merge qua proxy localhost:5433, nhưng helper CareNest_FE/tests/e2e/support/realBe.mjs mặc định E2E_PG_DB=carenest, container carenest_be-postgres-1. Fixture năm mới được SQL insert vào DB mặc định, còn API đọc DB khác.

## Attempts — đã thử

| # | Cách thử | Kết quả | Bài học |
| --- | --- | --- | --- |
| 1 | Chạy suite sau khi user cập nhật credential | Auth thành công; create plan 404 năm học | Không còn là lỗi mật khẩu |
| 2 | Đối chiếu config API, SQL fixture, DB_URL và container ports | DB name API và helper khác nhau; seed ID cũ trùng nên che mismatch | Phải khớp DB thật trước khi tạo fixture |
| 3 | Đặt E2E_PG_DB=carenest_merge trong process chạy suite | Fixture được tạo đúng DB; 12 ca đầu và ca Save riêng đã chạy qua | Chỉ cấu hình môi trường, không sửa endpoint hoặc reset DB |

## Root cause

realBe.mjs sql dùng database mặc định carenest trong khi Backend runtime dùng carenest_merge. Tạo dữ liệu qua SQL và đọc qua API thuộc hai database khác nhau.

## Fix

Dùng E2E_PG_DB khớp database của Backend khi chạy real-BE suite; giữ cleanup đúng ID fixture đã tạo, không xóa dữ liệu chung. Không ghi password/token vào log hoặc tài liệu.

## Regression test

Suite year-plan-import.spec.mjs với cùng BE/Keycloak thật và E2E_PG_DB đúng. Cấu hình đúng đã cho suite chạy qua fixture; kết quả toàn bộ cuối nằm trong quickstart feature003. Không coi auth hoặc setup thành công là toàn bộ tính năng đã đạt.

## Kiểm chứng cuối và hardening

13/13 E2E đạt sau cấu hình DB đúng và sửa hai assertion test chưa đúng UI. Post-run audit phát hiện/dọn đúng orphan fixture của lần sai DB; thêm T022 yêu cầu E2E_PG_DB và cleanup theo ID+marker/ID+name. Rerun sau hardening tiếp tục 13/13 đạt; số fixture import còn lại 0. Không sửa app/Java policy hoặc ghi secret vào repo.
