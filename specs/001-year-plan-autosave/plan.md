# Implementation Plan: Tự lưu mục tiêu năm học

**Branch**: `feature/G94-226-BE-FEAT-09-school-year-goals` | **Date**: 2026-10-27 | **Spec**: [spec.md](spec.md)

**Input**: Đặc tả 001-year-plan-autosave; Tiền đã duyệt sửa cả BE/FE và các mốc thời gian ngày 27/10/2026. Kế hoạch CareNest chính: [phần bổ sung tự lưu](../../docs/plans/active/2026-10-22-muc-tieu-nam-hoc.md).

## Summary

Lưu nội dung đang soạn, kể cả chưa đầy đủ, sau 2 giây ngừng nhập hoặc tối đa 15 giây nhập liên tục. Một phiên sửa giữ quyền qua tín hiệu 20 giây và hết hạn sau 90 giây mất liên lạc; nội dung có revision để chặn ghi đè. Bản phục hồi theo tài khoản/kế hoạch/phiên trên máy; không tự trộn khi xung đột. Không sửa bản đã ban hành, không thêm dịch vụ hoặc dependency.

## Technical Context

**Language/Version**: Java 21; JavaScript/JSX trên Web hiện có.
**Primary Dependencies**: Spring Boot, Spring Data JPA/Flyway; React, axios và router hiện có, không thêm thư viện.
**Storage**: PostgreSQL 17; bản phục hồi thiết bị qua service lưu trữ Web.
**Testing**: JUnit/MockMvc/Testcontainers; node:test, lint/build FE và kiểm thử trình duyệt.
**Target Platform**: Backend hiện có và Web CareNest_FE; không sửa Mobile.
**Project Type**: Web + API trong hai repo sibling.
**Performance Goals**: Spec SC-001..006; các mốc được kiểm bằng clock giả, trạng thái lưu đúng với xác nhận thật.
**Constraints**: Một trường/hai campus; quyền server; dữ liệu nhập dở không được tự ban hành; không gửi dữ liệu thật vào log/test.
**Scale/Scope**: Trang mục tiêu năm học, bao gồm Phó HT tự lập và sửa bản chờ duyệt.

## Constitution Check

- Constitution Spec Kit đang là mẫu chưa ratify, không coi placeholder là chính sách. Áp dụng AGENTS.md và các quy tắc CareNest đã có, không sáng tạo nguyên tắc quản trị mới.
- Quyền server và khóa giao dịch: giữ nguyên, bổ sung kiểm tra phiên/revision tại mọi thao tác ghi.
- Migration mới; không sửa migration đã commit; không thay dependency, không commit/push/tạo branch.
- BE/FE và đổi contract đã được user duyệt; giữ thay đổi có sẵn ngoài task. Review làn L dùng subagent theo AGENTS.md.
- Gate sau thiết kế: không có thay đổi kiến trúc ngoài stack hiện có; hai phần BE/FE review riêng. Không báo hoàn thành khi gate còn fail/skip.

## Project Structure

### Documentation (this feature)

- `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/year-plan-autosave.md`, `quickstart.md`, `tasks.md`.

### Source Code (repository roots)

- BE: entity/repository/DTO/service/controller `eduplan`, cấu hình quyền sửa, Flyway V24, unit/controller/schema/service integration tests và snapshot OpenAPI.
- FE: `GoalFormPage.jsx`, `useYearPlanAutosave.js`, `yearPlanAutosave.mjs`, `yearPlanRecovery.js`, facade/provider/API mapper và tests/education-plan.
- `useManualSave` và các màn khác giữ nghiệp vụ lưu hiện hành; giai đoạn tiếp theo có đặc tả riêng.

## Delivery and Traceability

1. US1: queue lưu tuần tự; giữ phần nhập tiếp; request ID chống lặp; nới validation nháp và giữ validation gửi/ban hành.
2. US2: lease theo phiên, trạng thái chờ duyệt độc lập, revision fencing, heartbeat/nhả; đồng bộ headers FE và snapshot BE.
3. US3: phục hồi thiết bị qua service, mỗi phiên một bản; reconnect kiểm tra quyền/revision, tải bản để đối chiếu khi xung đột; đóng trang không được báo đã lưu giả.
4. Kiểm thử và review; Analyze chỉ đọc, Converge chỉ bổ sung task còn thiếu vào cuối tasks.md; sửa theo task rồi chạy lại. Không xóa task cũ.

## Execution History and Limits

- Đặc tả, làm rõ và kế hoạch CareNest được duyệt trước code. User bổ sung chuỗi Spec Kit đầy đủ khi code tự lưu đang tiến hành; các tài liệu Plan/Tasks này ghi đúng trạng thái hiện tại, không giả rằng Analyze đã chạy trước phần code trước đó.
- Docker hiện đã hoạt động, kiểm thử tích hợp mục tiêu năm/schema/tài liệu lớp đã chạy thành công một lượt; cần chạy lại sau sửa cuối cùng.
- Lần verify FE đầu tiên dừng ở test thực đơn `ai-week.test.mjs` với dữ liệu tuần 2026-11-02 bị trùng; module thực đơn chưa bị sửa trong task. Phải ghi giới hạn và xác minh nguyên nhân, không lách gate.
