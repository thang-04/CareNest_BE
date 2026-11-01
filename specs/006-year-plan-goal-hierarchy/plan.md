# Implementation Plan: Ba bảng mục tiêu cha–con
**Branch**: feature/G94-226-BE-FEAT-09-school-year-goals | **Date**: 2026-10-29 | **Spec**: [spec.md](spec.md)
**Approval**: Phần bổ sung DB trong docs/plans/active/2026-10-22-muc-tieu-nam-hoc.md được user duyệt 29/10.
## Summary
Chuyển lưu trữ một bảng sang ba bảng vật lý, FK tường minh, cấp cha tùy chọn, giữ rows/kind tại API.
## Technical Context
Java 21, Spring Boot/JPA và PostgreSQL 17/Flyway hiện có; không thêm dependency. Kiểm bằng JUnit, PostgreSQL Testcontainers, snapshot OpenAPI và verify full.
## Constitution Check
Constitution hiện là template chưa điền: bỏ qua template, áp AGENTS.md và rule EDU được CONFIRMED. Không tạo nhánh/commit, không sửa FE/APP, không chạy migration lên DB đang dùng.
## Project Structure
- src/main/resources/db/migration/V35__split_edu_goal_hierarchy.sql
- src/main/java/com/carenest/entity/eduplan/: EduGoalRow là mapped superclass, ba concrete entity; EduGoalContent trỏ goal_id.
- src/main/java/com/carenest/repository/eduplan/: ba repository; bỏ repository/converter của bảng cũ.
- src/main/java/com/carenest/service/eduplan/impl/: lưu/đọc/copy/diff và membership học liệu.
- src/test/java/com/carenest/: schema/upgrade/service/class-doc/resource regression.
- docs/database/, docs/modules/, docs/decisions/ và hai tài liệu CSDL ngoài repo đã được user yêu cầu.
## Architecture Decisions
Chi tiết [research.md](research.md), [data-model.md](data-model.md), [contracts/compatibility.md](contracts/compatibility.md).
Giữ EduGoalRow dưới dạng mapped superclass để mapper/logic dùng chung định danh và thứ tự, nhưng mỗi loại có entity/repository riêng và không còn bảng edu_goal_rows.
Shared sequence tránh ID lặp xuyên ba loại tại rows API. FK ghép gồm plan/domain; khóa sub chuẩn hóa 0 cho phép kiểm cả khi sub NULL. FK cha deferred để đổi cha hợp lệ trong một transaction.
## Phases
Một phase triển khai DB-1 đã duyệt; tasks chia theo US để review, không deploy rời schema/source.
## Validation
Theo [quickstart.md](quickstart.md). Mọi FR/SC được ánh xạ trong tasks; analyze trước code, implement → converge → implement phần thiếu cho tới không còn gap.
## Risks & rollback
Sao lưu trước triển khai DB dùng chung; migration fail thì rollback transaction; sau nâng cấp không quay về source cũ riêng lẻ. Không tự triển khai DB thật trong task này.
