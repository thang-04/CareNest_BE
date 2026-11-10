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

## Delta plan — 02/11/2026 (approved, user duyệt triển khai tới sạch)
Nguồn: user chấp nhận xóa cả nhánh và yêu cầu ô Lĩnh vực một dòng/ellipsis; [domain14](domain-spec.md), FR-009..015, SC-005..008. User đã trả lời “Duyệt kế hoạch và triển khai tới khi sạch” trong chat02/11/2026; đây là nguồn approval kế hoạch D2, không phải tự suy ra từ approval nghiệp vụ. Plan quản trị hiện có: docs/plans/active/2026-10-22-muc-tieu-nam-hoc.md, phần delta02/11 cuối file.

### Constitution check hiện tại
Tái sử dụng constitution1.0.0 đã điền01/11, thay thông tin template chỉ có ý nghĩa lịch sử trong phần DB29/10. Giữ React18/JSX/CSS token, node:test và API hiện có; không migration/source BE/dependency/auth/APP. Do bộ spec+source+test vượt8file và ảnh hưởng FE, delta làn L; user đã duyệt plan trước implementation.

### Phương án và lựa chọn kỹ thuật đề xuất
| Việc | Đề xuất | Phương án khác | Lý do |
| --- | --- | --- | --- |
| Xác định nhánh | Helper thuần lấy hàng từ sub ID đến trước sub kế tiếp trong cùng domain | Xóa riêng heading; hoặc đổi format tree/API | Đúng flat rows hiện có, kiểm thử được, không đổi contract |
| Xác nhận | Tính số nhóm/goal/content/tick trên nhánh, dùng ConfirmationModal danger hiện có | Xóa ngay; chỉ báo tên | User chốt xác nhận tác động trước xóa |
| Select dài | Tái sử dụng cơ chế native select/ellipsis đang có ở ô mục tiêu; title đủ nhãn | Select mới/dependency; cắt dữ liệu value | Giữ hành vi lọc và convention, không đổi dữ liệu |

### Phase D1 — Hồ sơ và analyze
Cập nhật domain/spec/clarify/research/model/contracts/tasks; ghi EDU-48/module; check prereq/template hiện có; analyze FR/task/constitution; review read-only. Kết thúc bằng check-ai-layer và trình user duyệt delta kế hoạch, chưa có implementation/converge.

### Phase D2 — Implement và review
Files dự kiến: FE src/models/education-plan/goalTable.js; src/components/education-plan/GoalTables.jsx; src/components/education-plan/CurriculumGoalsModal.jsx; src/styles/modules/education-plan.css; tests/education-plan/goal-subdomain-delete.test.mjs và test gần nhất phù hợp. Không sửa importGoals/curriculum nguồn nếu không có regression chứng minh cần thiết.
RED cho nhánh đầu/giữa/cuối/duy nhất/rỗng/không tìm thấy/cùng tên khác ID, goal không nhóm và bất biến input. GREEN helper rồi callback/hộp xác nhận đúng tác động. Ô lĩnh vực/title cùng cơ chế cắt chữ của target; không sửa lựa chọn đã tick hoặc nguồn đang dùng. Browser xác nhận/Hủy/read-only/đánh số/dấu tích,4viewport, keyboard; kiểm payload/save/reopen khi có runtime credential. Full FE verify, BE quick cho delta tài liệu, check-ai-layer hai repo; review độc lập. Ghi Progress log rồi dừng review phase theo workflow.

### Vòng convergence trong Phase D2
Sau implement, so từng FR-009..015/SC-005..008/US4..5 với code/test; append task cho gap vào cuối tasks.md, không đổi task cũ. Implement gap trong scope được duyệt, verify/review rồi converge lại. Vòng sạch phải giữ tasks byte-for-byte. Không gọi toàn feature DB là mới kiểm lại bằng test client.

### Risks và kiểm soát
Xóa thiếu nhánh hoặc quá sang nhánh khác: so boundary/ID, fixture cùng tên và ticks; helper không mutate. Xác nhận/lưu: mở/Hủy không ghi, giữ manual save/lease. Ellipsis: base-select nội bộ có thể wrap, dùng chính cơ chế appearance của target; thử computed style và ảnh. Thiếu credential save/reopen: báo chưa kiểm, task tương ứng còn mở. Rollback delta: hoàn tác đúng source delta bằng patch được duyệt; không reset/rebase/migration hay sửa dữ liệu thật.
