# Implementation Plan: Lọc phân cấp và gợi ý từ Bộ

**Branch**: feature/G94-326-BE-FEAT-09-school-year-goals-backup | **Date**: 2026-11-08 | **Spec**: [spec.md](spec.md)

**Input**: specs/014-curriculum-cascade-suggestions/spec.md

**Status**: In progress — user08/11/2026 duyệt triển khai qua yêu cầu implement→converge đến sạch. User đã cho phép sửa giáo dục FE014 ngày08/11/2026; không xin lại quyền này. Approval012/013 không thay approval014.

## Summary

Sửa Web để nguồn và mục tiêu đích theo đúng lĩnh vực → lĩnh vực con → nhóm → mục tiêu; reconcile lựa chọn sai và guard trước thêm; đưa gợi ý nhóm/goal/content mục D vào ô nhập. Tái sử dụng catalog/DTO, helper và FloatingPanel; không đổi BE/API/DB/dependency. [Research](research.md) ghi bằng chứng source; chưa chạy runtime.

## Technical Context

**Language/Version**: Web JavaScript/JSX, React18; backend giữ Java21/Spring Boot4.1.1.

**Primary Dependencies**: Vite/React/axios, useAsync, FloatingPanel, CSS/token hiện có; không thêm/nâng package.

**Storage**: PostgreSQL17 hiện có; state filter/source context dẫn xuất trong bộ nhớ Web, không lưu DB.

**Testing**: node:test, ESLint/Vite native, Playwright fixture/E2E hiện có; Maven targeted khi cần kiểm contract/provenance.

**Target Platform**: Web desktop/mobile viewport; APP native không checkout.

**Project Type**: Web client + Spring REST backend, thay đổi runtime dự kiến ở FE.

**Performance Goals**: Một catalog load cho scope editable dùng chung ở cấp bảng, không request theo từng ô; lọc từ index theo snapshot; không SLA mới ngoài SC của spec.

**Constraints**: Không mất dữ liệu/ticks; không đoán source từ tên; giữ manual save, immutable TEAM, scope/session/revision; user đã cấp quyền sửa FE014 và duyệt vòng implement→converge ngày08/11; delta sửa catalog nguồn có plan riêng chờ duyệt.

**Scale/Scope**: Một trường/hai campus;4 nhóm tuổi nguồn hiện có; không giới hạn kết quả tìm khớp bằng trần12 ẩn. Dự kiến >8 file FE kể cả test, chia3 increment bên dưới, không mở refactor toàn module.

## Constitution Check

| Nguyên tắc | Trước research | Sau thiết kế |
| --- | --- | --- |
| Business truth / V1 | Q1–Q3 đã chốt; EDU-61 PROPOSED đúng nguồn | Giữ EDU-09/22/28/53/54/55/60; không rule mới |
| Stack / boundaries | Dùng source/service hiện có | Không BE code/API/DB/dependency mới |
| Security / privacy | Không dữ liệu thật/secret | Giữ server permissions/lease/revision và TEAM guard |
| Verification / human control | Chỉ artifact trực tiếp, không harness | Native regression/browser/save-reload cần chạy khi implement; chưa claim pass runtime |
| Traceability / approval | Domain14/spec/checklist/clarify có sẵn | AC01–08→quickstart/phases và tasks.md đã sinh; plan014 đã được duyệt, delta sửa catalog chờ duyệt riêng |

Constitution2.0.0 phù hợp và được tái sử dụng. Gate thiết kế không có vi phạm chưa giải thích; approval plan014 và quyền FE đã có; kiểm chứng implementation và delta catalog nguồn còn chưa hoàn tất. Template kế hoạch quản trị còn nhắc verify.mjs/check-ai-layer: delta014 thay bằng lệnh native theo chỉ đạo07/11, không sửa constitution hoặc lịch sử012/013.

## Project Structure

### Documentation (this feature)

- domain-spec.md, spec.md, checklists/requirements.md: đầu vào giữ nguyên.
- plan.md, research.md, data-model.md, contracts/curriculum-cascade.md, quickstart.md: output lượt này.
- [tasks.md](tasks.md): đã sinh30task theo user stories, chưa thực hiện; bước tiếp speckit-analyze.
- Plan quản trị: [delta014 trong plan hiện có](../../docs/plans/active/2026-11-07-school-year-team-plans.md), không tạo plan trùng chủ đề.

### Source Code (repository root)

- BE: src/main/java/com/carenest/{controller,dto,service}/eduplan — chỉ đọc và regression, không sửa dự kiến.
- FE: ../CareNest_FE/src/models/education-plan/curriculum.mjs — index/context/reconcile helper.
- FE: ../CareNest_FE/src/components/education-plan/{CurriculumGoalsModal,GoalTables}.jsx; src/hooks/education-plan/useCurriculum.js; src/pages/education-plan/GoalFormPage.jsx.
- FE: ../CareNest_FE/src/styles/modules/education-plan.css — chỉ scoped styles nếu cần cho gợi ý.
- FE tests: ../CareNest_FE/tests/education-plan/curriculum.test.mjs, branch-template-enter.test.mjs, curriculum-cascade-browser.mjs (mới); ../CareNest_FE/tests/e2e/system/curriculum-cascade.spec.mjs (mới, fixture SCHOOL_YEAR).
- API/mapper/source page giữ transport contract; mock/curriculumMockRepository.js chuẩn hóa source IDs còn thiếu bằng namespace mock ổn định (research R7), có test parity/roundtrip, không đổi raw seed. Điều chỉnh fixture theo imports/interface, không weakening test.

**Structure Decision**: giữ layout module hiện có, helper thuần đặt trong curriculum.mjs thay vì thêm framework/state layer. Không có nhánh code Mobile vì chưa có checkout.

## Design Decisions

1. Local parent context từ flat rows, provenance riêng; không dùng source ID làm local parent hoặc join sub/group theo tên.
2. Modal kiểm eligible target/suggestions lúc render và confirm; guard callback dùng form mới nhất.
3. Inline dùng goals mục D; nhóm/goal có source metadata, content pool theo nhóm đúng Q2; mục C độc lập.
4. Source context nhập tay chọn tường minh, chỉ giữ state phiên theo row; không tự liên kết tên giống.
5. Dùng lại useAsync scope guard; thêm enabled tương thích vào useCurriculum để readonly không phát request mới. Tái dùng Suggest/FloatingPanel và bổ sung keyboard/touch/empty/error.
6. Mock adapter enrich source parent IDs ổn định qua reload, mock-only namespace và parity4 nhóm tuổi; không gửi mock IDs qua API thật. Live fixture014 phải SCHOOL_YEAR/quyền hiện hành, không dùng legacy TEAM suite làm bằng chứng inline.
7. Không change transport/schema, không migration/backfill. Chi tiết [data model](data-model.md) và [contract](contracts/curriculum-cascade.md).

## Phases & Validation

1. **Context và modal**: helper actual hierarchy/filter/reconcile, mock source identity normalization/parity/roundtrip, cập nhật modal/confirm và boundary latest form; mở rộng curriculum.test. AC01–02/06/08. Chạy Node targeted + browser scope cases; dừng review.
2. **Inline suggestions**: catalog editable/age scope, group/goal/content inputs, explicit context cho dòng tay, provenance, accessibility; fixture Enter và scoped CSS. AC03–07/08; Q10a nghiệm thu tại T020, Q10b tại T025 sau test/guard US3. Chạy Node education suite, ESLint changed source, Vite build, browser keyboard/touch1440/390; dừng review.
3. **Nghiệm thu/save-reload**: tạo live suite014 với SCHOOL_YEAR, creator/reviewer hợp lệ, route annual và assertions save/reload/version; không chỉ đổi selectors hai suite legacy TEAM; real API và readonly TEAM, regression native phù hợp; speckit-converge sau implement. AC01–08/SC001–004. Chạy quickstart, ghi skip/blocker thật; dừng review/bàn giao.

Mỗi increment có test riêng; tổng >8 file do UI+fixtures/E2E, không tách thành feature nghiệp vụ mới hoặc đụng source ngoài phạm vi. Không chạy whole Maven suite cho thay đổi FE-only.

## Complexity Tracking

Không vi phạm constitution cần miễn trừ. Explicit source context là cần để hỗ trợ hàng tay chưa linked mà không đoán relation; không thêm lớp persistence. Mã Jira riêng014 chưa xác nhận; chưa cần mã để lập thiết kế, không tự suy dùng mã nhánh cho commit/PR.

## Approval & Next Step

Đọc [quickstart](quickstart.md), tasks đã sinh; analyze sau I1 không còn finding; user đã duyệt implement→converge và quyền FE ngày08/11/2026. Tiếp tục tự kiểm từng increment; riêng [delta sửa catalog](source-correction-plan.md) chờ duyệt vì có migration và thay dữ liệu response.

## Execution approval — 2026-11-08

User yêu cầu thực hiện vòng implement→converge đến sạch: tự kiểm/review các increment rồi tiếp tục, thay yêu cầu dừng xin duyệt từng phase. Quyền FE014 đã có, không mở thêm API/DB/dependency/Git/deploy.
