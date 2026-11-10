---
id: BUG-261029-hierarchy-deferred-ddl
type: bug
modules: [education-plan]
rules: [EDU-09]
status: fixed
date: 2026-10-29
keywords: [Flyway, deferred, pending trigger events, CREATE INDEX, hierarchy]
similar_to: []
---
# BUG-261029-hierarchy-deferred-ddl — Backfill có FK deferred chặn DDL

## Symptom
`cannot CREATE INDEX "edu_plan_expected_results" because it has pending trigger events` (SQLSTATE 55006).

## Điều kiện tái hiện
Nâng PostgreSQL 17 từ V34 có dữ liệu mục tiêu lên V35, trong transaction Flyway.

## Attempts
| # | Cách thử | Kết quả | Bài học |
| --- | --- | --- | --- |
| 1 | Backfill FK deferred rồi tạo index cùng transaction | Test nâng cấp fail ở CREATE INDEX | FK còn trigger chưa xử lý dù dữ liệu hợp lệ |
| 2 | SET CONSTRAINTS ALL IMMEDIATE sau backfill, trước DDL | GREEN 4/4 migration tests | Buộc kiểm FK trước khi sửa cấu trúc bảng |

## Root cause
V35 chèn các hàng nhóm/mục tiêu có FK INITIALLY DEFERRED, sau đó CREATE INDEX trên bảng còn pending trigger. Test `EduGoalHierarchyMigrationTest` và `target/hierarchy-green.log` tái hiện trên PostgreSQL thật.

## Fix
Thêm SET CONSTRAINTS ALL IMMEDIATE sau backfill, trước ALTER/CREATE INDEX. Không sửa migration đã commit; V35 là migration mới của task này.

## Regression test
`EduGoalHierarchyMigrationTest#migratesHierarchyWithoutInventingParents`; RED trước fix (SQLSTATE 55006 trong target/hierarchy-green.log); GREEN 4/4 migration tests, 46/46 regression liên quan, skip 0 trong target/hierarchy-green2.log.

## Ảnh hưởng
Chỉ kiểm trên container dữ liệu giả. Không chạy V35 lên DB đang dùng.

## Lesson
Migration kết hợp backfill deferred FK và DDL phải xử lý các constraint đang chờ trước DDL.
