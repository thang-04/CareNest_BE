# Implementation Plan: Người tạo/duyệt kế hoạch giáo dục

**Branch**: `feature/G94-226-BE-FEAT-09-school-year-goals` | **Date**: 2026-11-06 | **Spec**: [spec.md](spec.md)

**Implementation status (06/11/2026)**: User subsequently authorized `$speckit-implement` and requested repeated implement/converge. T001–T078 are complete; BE full verify 1129/1129, FE verify 207/207, browser WF-01/02/03 3/3. Current evidence and limits are in [quickstart](quickstart.md) and [verification](../../docs/quality/VERIFICATION.md). The design-time gates below describe the plan when it was first generated and are superseded by this user authorization.

**Input**: FR-001..018, EDU-54 PROPOSED (các lựa chọn có nguồn user). [Plan làn L](../../docs/plans/active/2026-11-06-education-workflow-policy.md) status draft. Helper trả `BRANCH=011-plan-creator-reviewer` theo feature pointer, không phải nhánh Git mới; nhánh thực tế được xác minh bằng `git branch --show-current` như trên.

## Summary

Tách tập vai trò soạn và chuỗi duyệt cho year/branch/theme, chỉ Hiệu trưởng cấu hình. Giữ entity hiện có, thêm policy/revision, approvals nhiều bước cho year và sổ người soạn. Dùng bộ đánh giá chung để tính lượt, bỏ bước thiếu người, giữ approvals cùng vòng nội dung và hoàn tất nếu mọi bước chuỗi mới đã duyệt. Đổi cấu hình và reconciliation bản đang chờ trong cùng transaction; không sửa bản đã hoàn tất. Không mở ma trận phân quyền global, không thêm dependency.

## Technical Context

**Language/Version**: Java 21; Node cho cổng verify. `pom.xml:10/20` là nguồn version.
**Primary Dependencies**: Spring Boot 4.1.1, JPA, Flyway, Jackson hiện có; không thêm/nâng thư viện.
**Storage**: PostgreSQL 17, migration mới; không sửa migration đã commit. Cấp số mới khi implement; không tái sử dụng số đã có.
**Testing**: JUnit/AssertJ/MockMvc, PostgreSQL Testcontainers; unit engine, transaction/security/API, snapshot, Web thật/mock/E2E.
**Target Platform**: Backend web service, Web React hiện có; APP source chưa có cục bộ.
**Project Type**: Service trong repo hiện có; FE education được user cho phép sửa và đã kiểm chứng. APP không có tích hợp giáo dục theo xác nhận user.
**Performance Goals**: Batch dữ liệu đọc qua service hiện có và một policy snapshot mỗi transaction; helper ACTIVE-account read tối thiểu đã được user cho phép riêng trong AccountService; tránh N+1 theo ô/hồ sơ, không gọi Keycloak per-business-request. Không tự đặt SLA; đo transaction với fixture tổng hợp trong quickstart.
**Constraints**: Một trường/hai campus; scope server theo phân công; reviewer không soạn cùng loại dù có nhiều role; không dùng kết quả vòng cũ cho nội dung mới. Giữ phiên sửa/revision/idempotency.
**Scale/Scope**: Ba policy year/branch/theme; week/day theo theme. Đích chỉ DRAFT/REVIEW/APPROVED. Reconcile REVIEW khi lưu policy và thu hồi lease của DRAFT bị mất quyền; không reconcile APPROVED hoặc đổi snapshot cũ. Tác động nguồn được liệt kê tại research/contracts.

## Constitution Check

| Cổng | Trước research | Sau thiết kế |
| --- | --- | --- |
| Nghiệp vụ có nguồn | Bốn điểm hoãn đã hỏi user | Có đáp án trong spec, không còn unknown nghiệp vụ chặn thiết kế |
| Stack/module | Giữ stack, ADR-0020 | Ledger/engine local; đọc account/assignment qua service |
| Scope/privacy | Kiểm quyền tại server | Role hiệu lực và capability theo context; fixture tổng hợp |
| Human control | Chỉ lập plan tại thời điểm tạo plan | User đã gọi implement sau đó; code/migration/contract được triển khai trong phạm vi đã chốt |
| Verification | Chưa chạy chức năng mới | RED/GREEN/full/snapshot/E2E được thiết kế; cổng tài liệu riêng |
| Spec loop | Domain + constitution 1.0.0 | Sau approval: tasks → analyze → implement → converge |

Không có ngoại lệ constitution. **Implementation gate tại thời điểm tạo plan** yêu cầu duyệt plan/contract/migration và chốt delta rule trước code; user đã cấp quyền implement sau đó bằng lệnh cụ thể. EDU-54 vẫn PROPOSED; không tự nâng thành trường/team đã chốt. APP source chưa có là giới hạn kiểm chứng rollout, không phải unknown để tự đoán.

## Project Structure

### Documentation (this feature)

```text
specs/011-plan-creator-reviewer/
  domain-spec.md, spec.md, plan.md, research.md, data-model.md, quickstart.md
  contracts/workflow-api.md, contracts/client-impact.md
  checklists/requirements.md
```

[tasks.md](tasks.md) ban đầu có 58 task; implementation/convergence đã nối đến T078 và toàn bộ task đã hoàn tất. Việc gọi `$speckit-tasks` ban đầu không tự duyệt implementation; user đã ủy quyền riêng sau đó bằng `$speckit-implement` và các chỉ đạo tiếp theo. [ADR-0024](../../docs/decisions/ADR-0024-education-workflow-policy.md) ghi trạng thái và giới hạn rollout.

### Source Code (repository root)

```text
src/main/java/com/carenest/
  controller/eduplan/                 # config/workflow-policy/year/class
  dto/eduplan/, entity/eduplan/, repository/eduplan/
  service/eduplan/impl/               # engine/coordinator, year/class/config
  security/authorization/EduPlanAccessPolicy.java
src/main/resources/db/migration/      # chỉ migration mới
src/test/java/com/carenest/service/eduplan/impl/
```

**Structure Decision**: Reuse entity/layers hiện có. Một evaluator thuần và một workflow service điều phối, không platform workflow tổng quát. Ngoài helper account chỉ đọc được phép dưới đây, không sửa source module khác. Education producer giữ mã APPROVED và issued_by compatibility, lưu publisher thật/print snapshot riêng trong edu; helper AccountService.findActiveUserIds chỉ đọc là ngoại lệ user đã cho phép; không endpoint/quyền/business tài khoản mới.

## Phase 0 — Research

[research.md](research.md): evidence file:line, lựa chọn và phương án loại bỏ; technical unknown đã resolve từ source. Bốn quyết định hoãn được user trả lời trong lượt plan. Không cần đổi stack/dependency hoặc tra web cho lựa chọn này.

## Phase 1 — Design & Contracts

- [data-model.md](data-model.md): fields/FK/CHECK/index, vòng duyệt, backfill/provenance, thuật toán/lock.
- [contracts/workflow-api.md](contracts/workflow-api.md): endpoint riêng, guard legacy PUT, metadata/capabilities, actions/lỗi.
- [contracts/client-impact.md](contracts/client-impact.md): mapper/route/mock/queue/print, giới hạn APP và rollout.
- [quickstart.md](quickstart.md): prerequisites, commands, test matrix và expected outcomes, không chứa code implementation.

## Delivery slices after approval

1. **P1 — Schema/policy/provenance**: migration expand, model/evaluator tương thích legacy, helper ACTIVE-ID chỉ đọc đã được phép, unit/integration tests; chưa expose API mới, không rollout riêng. Dừng review.
2. **P2 — Backend integration**: workflow API/legacy guard, year/class và mọi đường ghi, source/test cutover + migration contract T047, atomic reconcile/capabilities, print snapshot và compatibility producer chỉ trong giáo dục; full/snapshot/API E2E. Dừng review.
3. **P3 — Client/release validation**: Web sau khi user cho phép sửa FE trong tin nhắn hiện tại; adapter/mock/print/routes/E2E, APP khi có source. Không rollout backend với Web cũ; deploy chỉ khi có chỉ thị. Dừng review.

Phạm vi >8 file được tách ba slice có gate. Backend xong không đồng nghĩa cả feature xong. Lượt này chỉ artifacts; không sửa FE/APP, không commit/push/tạo branch/PR.

## Complexity Tracking

Không xin ngoại lệ. Thêm một bảng người soạn + fields nhỏ thay workflow-instance/step framework. Shared settings lock cho ghi hồ sơ, exclusive cho đổi policy; vẫn cho ghi các hồ sơ khác nhau song song. Nếu integration test không chứng minh isolation/thứ tự lock thì sửa thiết kế, không bỏ khóa hoặc chuyển sang eventual consistency.


## Delta theo user sau analyze06/11/2026

Chỉ ba trạng thái Nháp/Duyệt/Ban hành (DRAFT/REVIEW/APPROVED), review role là metadata, REVIEW không sửa/không lease; final approve tự Ban hành. Return để sửa ánh xạ về DRAFT và giữ lý do/history. Bản DRAFT mất quyền phải thu hồi lease/increment revision cùng policy transaction (U1). print_snapshot/review.printSnapshot đã chốt shape chứa ô ký, role/label/names (U2), tách khỏi completion_policy. Chỉ giáo dục; không sửa observation/organization, account chỉ ngoại lệ helper đọc được user cho phép sau lệnh fix. Publisher thật dùng published_by/completed_at, legacy issued_by giữ actual VP approval hoặc null để consumer cũ không nhầm HT là PHT. Thiết kế delta cần review/analyze lại, review vòng trước không tự phê duyệt phần mới. U3 đã được giải quyết ở thiết kế bằng đúng ngoại lệ helper đọc AccountService.findActiveUserIds user cho phép; không mở rộng thêm scope.


## Sửa I2 — giữ cổng full thực thi được
P1 dùng expand và enum giữ legacy+REVIEW; cập nhật switch exhaustive REVIEW cấm sửa, helper/predicate mới chưa thay behavior caller cũ, T014 full phải pass. Sau year/class/lease/history/test đã chuyển, T047 thêm contract migration mới, remap DB và xóa legacy enum constants trong cùng cutover; T051 full cuối. Release chỉ ba trạng thái, không rollout P1, không skip/hạ cổng full. T002 bind hai số migration lúc implement, không sửa SQL đã commit.

### Ngoại lệ phạm vi được user bổ sung trong triển khai
User cho phép helper chỉ đọc `StaffAssignmentService.findActiveEducationReviewerAssignments(LocalDate)` và test tương ứng; đồng thời cho phép thay riêng fixture `DailyAssessmentIntegrationTest` từ `PENDING_VP` sang `REVIEW`. Các ngoại lệ này bổ sung cho helper đọc account đã nêu ở trên; không đổi nghiệp vụ hay API tổ chức, quan sát hoặc tài khoản.
