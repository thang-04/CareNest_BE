---
id: BUG-261116-education-seed-missing-plan-owner
type: bug
modules: [education-plan]
rules: [EDU-25, EDU-54]
status: fixed
date: 2026-11-16
keywords: [R__dev_04_education.sql, ck_edu_plan_events_owner, plan_id, Flyway]
---

# Seed giáo dục tạo lịch sử không có kế hoạch

## Symptom

Backend không khởi động vì Flyway chạy `R__dev_04_education.sql` gặp
`violates check constraint "ck_edu_plan_events_owner"`.

## Điều kiện tái hiện

Nhóm Nhà trẻ đã có bản REVIEW khác số phiên bản mẫu (ví dụ bản 3), trong khi bản mẫu 2 chưa tồn tại.
Chạy lại seed giáo dục. Dùng dữ liệu giả và PostgreSQL Testcontainers, không sửa DB người dùng.

## Attempts

| # | Cách thử | Kết quả |
| --- | --- | --- |
| 1 | Đọc seed và constraint V102 | Guard một bản đang mở bỏ qua INSERT bản 2 nhưng `_e_yp.plan_id` vẫn NULL; bước tạo lịch sử dùng dòng đó |
| 2 | Chạy `DevSeedIntegrationTest.rerunWithDifferentOpenYearPlanKeepsExistingPlan` trước sửa | Exit 1, vi phạm `ck_edu_plan_sub_domains_owner` tại bước chép cây trước lịch sử: cùng nguyên nhân owner NULL |
| 3 | Sửa tập mẫu rồi chạy cả `DevSeedIntegrationTest` | Exit 0, 5 test, 0 lỗi/skip trên PostgreSQL 17 Testcontainers |

## Root cause

Seed giữ lại dòng mô tả bản REVIEW đã chủ động không tạo vì có bản chưa ban hành khác.
Các bước sau vẫn coi dòng đó là kế hoạch tồn tại, tạo sự kiện không có chủ sở hữu.

## Fix

Bỏ riêng dòng REVIEW không được tạo do bản đang mở khác trước khi dựng dữ liệu phụ thuộc;
giữ constraint, trạng thái và nội dung kế hoạch sẵn có.

## Regression test

`src/test/java/com/carenest/repository/organization/DevSeedIntegrationTest.java`:
test xung đột bản đang mở, nạp mới, invariants và chạy lại không nhân đôi.

Lệnh: `mvnw.cmd -q -Dtest=DevSeedIntegrationTest test`.
Kết quả 16/11/2026: 5 test, 0 failure/error/skip. Không chạy e2e, không sửa DB môi trường người dùng.

## Ảnh hưởng

Chỉ seed dev; không đổi schema/API hoặc nghiệp vụ duyệt, không sửa FE/APP.
