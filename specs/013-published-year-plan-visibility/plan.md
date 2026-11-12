# Implementation Plan: Quyền xem năm đã ban hành và mục tiêu tổ đầy đủ

**Branch**: `feature/G94-326-BE-FEAT-09-school-year-goals-backup` | **Date**: 2026-11-08 | **Spec**: [spec.md](spec.md)

**Input**: [domain-spec.md](domain-spec.md), [spec.md](spec.md), Q1=A và EDU-60.

**Status**: Implemented —34/34 task hoàn tất; sau review user cho phép sửa full-repo format và kiểm liveE2E: fullSpotless/native tests và2Keycloak/server-save E2E đạt. Ngày chủ đề/nhánh wrap theo cột năm/tổ đã kiểm browser; chưa deploy, xem quickstart/VERIFICATION. Plan quản trị dùng lại [plan năm/tổ hiện có](../../docs/plans/active/2026-11-07-school-year-team-plans.md); approval feature012 không tự áp dụng feature013.

## Summary

Chỉ tư cách lập tổ thì chỉ xem SCHOOL_YEAR đã ban hành; HT/PHT hiệu lực được mọi nhóm tuổi, tổ trưởng/GV giới hạn nhóm tuổi được giao theo user làm rõ sau analyze08/11/2026. Giữ quyền năm độc lập theo Q1=A và quyền TEAM cũ. Dùng scope có cấp kế hoạch ở server cho list/detail/source/version; lọc ngay truy vấn trước phân trang. Web dùng dữ liệu server đã kiểm quyền, đồng bộ mock và bỏ vùng cuộn riêng của bảng mục tiêu tổ bằng CSS có phạm vi.

## Technical Context

**Language/Version**: Java 21; Web JavaScript/JSX/CSS hiện có.
**Primary Dependencies**: Spring Boot 4.1.1, Spring Data JPA, Spring Security/Keycloak, React/Vite hiện có. Không thay dependency/version.
**Storage**: PostgreSQL 17, tái sử dụng edu_year_plans và staff_assignments; không migration/backfill.
**Testing**: JUnit/Mockito, integration PostgreSQL Testcontainers; Node test runner/SSR, ESLint, Vite và kiểm layout với CSS thật trong Chromium.
**Target Platform**: API hiện tại và trình duyệt Web; không triển khai APP (sibling không hiện diện).
**Project Type**: web-service + Web client, BE là nguồn quy tắc và quyền.
**Performance Goals**: lọc quyền trước pageable/count; không fetch toàn bộ plan để lọc bằng Java, không mở thêm truy vấn phân công cho từng hàng. Bộ UI nghiệm thu 50 mục tiêu × ít nhất 2 nội dung, không phải giới hạn nghiệp vụ/SLA.
**Constraints**: tài khoản từ CurrentUser, phân công hiệu lực theo Clock hiện hành, không tin userId/ageGroupId của client; không cache quyền qua request; không sửa workflow/nhãn/lịch/ticks.
**Scale/Scope**: 1 trường, 2 campus; SCHOOL_YEAR theo nhóm tuổi; thay đổi gói eduplan và policy giáo dục, FE giáo dục/CSS mục tiêu. Chia 3 phase độc lập kiểm chứng; danh sách source/test dự kiến vượt 8 file nên chia delivery BE rồi Web, không tách rule giữa hai plan.

## Constitution Check

| Nguyên tắc | Trước nghiên cứu | Sau thiết kế |
| --- | --- | --- |
| I — nghiệp vụ | EDU-54/55/60 và Q1=A có nguồn; không hỏi lại quyết định | Đạt ở mức thiết kế; EDU-60 giữ PROPOSED theo mức nguồn, không tự nâng thành xác nhận trường |
| II — kiến trúc | Giữ tầng controller/service/policy/repository | Đạt; không module/hạ tầng mới, không thay auth controller toàn repo |
| III — dữ liệu/quyền | Scope server bắt buộc | Đạt; phân biệt SCHOOL_YEAR/TEAM, chặn direct id và version metadata; dữ liệu nghiệm thu giả |
| IV — kiểm chứng/quyền user | Làn L, chưa có approval delta | Thiết kế test/rollback có ở quickstart; chỉ tài liệu được tạo, chưa claim test runtime |
| V — truy vết | Domain/spec đã clarify | FR/AC→phase→test ở quickstart; tasks/analyze ở bước sau, không chạy harness/setup/hooks |

Không vi phạm cần xin ngoại lệ. User đã cho phép sửa giáo dục FE trong tin nhắn “cho phép Fe nhé” sau lượt plan; giữ quyền đó trong phiên, không hỏi lại. Plan delta vẫn draft, cần đối chiếu approval triển khai làn L trước source; chưa có thay đổi source.

## Project Structure

### Documentation (this feature)

```text
specs/013-published-year-plan-visibility/
  domain-spec.md
  spec.md
  plan.md
  research.md
  data-model.md
  contracts/visibility-and-layout.md
  quickstart.md
  checklists/requirements.md
```

[tasks.md](tasks.md) đã được tạo theo lệnh speckit-tasks ngày08/11/2026; việc tạo task không phải phê duyệt triển khai. Tất cả source tasks chờ analyze và approval làn L.

### Source Code (repository root)

```text
src/main/java/com/carenest/security/authorization/EduPlanAccessPolicy.java
src/main/java/com/carenest/service/eduplan/impl/EduYearPlanServiceImpl.java
src/main/java/com/carenest/repository/eduplan/EduYearPlanRepository.java
src/test/java/com/carenest/service/eduplan/impl/EduWorkflowAccessPolicyTest.java
src/test/java/com/carenest/service/eduplan/impl/SchoolYearTeamPlanIntegrationTest.java
src/test/java/com/carenest/service/eduplan/impl/EduYearPlanServiceIntegrationTest.java
```

Web tại sibling đã được user cấp quyền sửa phần giáo dục sau lượt plan: `src/styles/modules/education-plan.css`, `src/services/education-plan/mock/educationPlanMockRepository.js`, `src/pages/education-plan/GoalFormPage.jsx` (thông báo nguồn trống nếu cần), tests giáo dục. Các consumer khác đọc/kiểm regression theo [contract](contracts/visibility-and-layout.md), không mặc định sửa tất cả.

**Structure Decision**: giữ policy hiện có và query hiện có; chỉ thêm scope trạng thái nội bộ nếu cần biểu diễn reviewer theo nhóm tuổi mà không cấp DRAFT. Không tạo authorization framework hoặc DTO mới.

## Phase 0 — Research

Đã đối chiếu source BE và nghiên cứu client chỉ đọc qua research agent. Quyết định, file:line và phương án loại bỏ tại [research.md](research.md). Không có bất định nghiệp vụ chặn; cách biểu diễn scope nội bộ được chọn dưới đây.

## Phase 1 — Design

### D1. Scope cấp năm

- Giữ nguyên nhánh TEAM của `viewScope`/workflow và import liên nhóm của TEAM (EDU-06/24).
- SCHOOL_YEAR dùng scope từ hai nguồn quyền: (a) quyền năm độc lập theo cấu hình/phân công và điều kiện trạng thái hiện hành; (b) quyền tạo TEAM hiệu lực ở nhóm tuổi đó, chỉ APPROVED. Không lấy `fullAgeGroupIds` hoặc APPROVED toàn trường do tư cách tổ trưởng của base TEAM làm quyền năm.
- Nhóm tuổi từ quyền tạo TEAM tính bằng `canAuthorYearPlan(user, loadPolicy("year"), ageId)` như `yearCreationScopes`, có reviewer-precedence và phân công theo ngày hiện tại. Kết quả HT/PHT có quyền tạo TEAM và phân công hiệu lực là mọi nhóm tuổi (nhánh PRINCIPAL/VICE_PRINCIPAL của helper trả true); user đã chốt đây là phạm vi đúng sau analyze. TEAM_LEADER/TEACHER chỉ nhận tuổi phân công, không được lấy toàn bộ ageGroups hoặc signerNames làm scope cho hai vai trò đó.
- Nhánh năm độc lập tái sử dụng author/reviewer eligibility và status gates của service. Mọi đóng góp HT/PHT toàn trường MUST có eligibility creator/reviewer trong policy schoolYear hiện hành VÀ phân công hiệu lực; không copy raw base theo tên role. Khi policy năm loại PHT khỏi cả creator/reviewer, PHT không có quyền năm độc lập dù còn phân công và quyền tạo TEAM; chỉ nhánh APPROVED từ quyền tạo TEAM có thể cấp quyền; nếu HT/PHT còn đủ quyền tạo TEAM/phân công hiệu lực thì nhánh đó bao phủ mọi nhóm tuổi theo quyết định mới, không mở DRAFT/REVIEW. Không thêm quyền duyệt hoặc coi reviewer là author. Reviewer không được cấp DRAFT chỉ vì được xem REVIEW; chỉ giữ DRAFT nếu có quyền xem năm độc lập theo status gates đã có (ví dụ PHT hợp lệ trong cấu hình năm và còn phân công có quyền xem cũ), không từ full tuổi của tổ trưởng.
- Để biểu diễn rõ tập theo trạng thái: mở rộng record ViewScope nội bộ bằng `reviewAgeGroupIds` (constructor 3 tham số cũ ủy quyền review set rỗng). `allows` thêm nhánh REVIEW trong tập này; query thêm cùng điều kiện bằng overload nội bộ, không đổi API. SCHOOL_YEAR authors→full, reviewers→review+approved theo scope; nguồn TEAM creator→approved. TEAM dùng constructor/query overload cũ, kết quả giữ nguyên. Giữ các schoolWideStatuses độc lập theo status gates cũ SAU KHI giao với eligibility schoolYear và phân công; không sao chép tập schoolWide từ viewScope TEAM. Tổ trưởng là reviewer năm chỉ có REVIEW/APPROVED đúng tuổi nếu không có quyền DRAFT độc lập. Đây là scope đọc, không cấp hành động soạn/duyệt.
- List lọc ở repository trước count/page. Detail và load-for-update dùng cùng hàm predicate theo level/status và các guard nghiệp vụ hiện có; không còn đường fallback base TEAM cho SCHOOL_YEAR. Chuỗi version và changes lọc/kiểm từng id bằng predicate tương ứng; không trả id/tóm tắt của bản nháp bị cấm. Không dùng currentVersion metadata để lộ id bản mới chưa được phép xem.
- `requireCurriculumSource` vẫn bắt APPROVED, cùng năm/tuổi, nguồn hiện hành như cũ kể cả người có quyền năm độc lập; quyền xem nháp không đồng nghĩa quyền dùng nháp lập tổ. Giữ thứ tự khóa hiện tại.

### D2. Contract và client

Không thêm endpoint/DTO/ApiCode: đây là siết phạm vi dữ liệu đọc, tác động consumer được ghi tại [contracts/visibility-and-layout.md](contracts/visibility-and-layout.md). List ngoài scope trả trang rỗng, detail id tồn tại ngoài quyền giữ 403/7004; id không tồn tại giữ 404 hiện hành. Client xử lý 403 bằng trạng thái không có quyền, không fallback mock hoặc giữ lại nội dung response trước.

Web thật dựa server; source selector tiếp tục APPROVED/cùng năm/tuổi, hiển thị lý do chưa có nguồn hợp lệ. Mock annual phải dùng cùng predicate ở getAll/list/detail/source/version, không gán permissions cho mọi bản rồi để trang tin. Q1=A giữ scope năm độc lập. Không sửa logic sourceVisible của TEAM để vô tình bỏ EDU-24.

### D3. Layout

Chỉ selector bảng mục tiêu TEAM `.ga-team-plan .table-wrap:has(> .ga-goal-table)` (cả không có chủ đề): `max-height: none; overflow: visible` ở cả hai trục, bỏ sticky thead dành cho vùng cuộn cũ. Không chỉ đặt overflow-y vì overflow-x:auto của shared table-wrap làm Y tính lại thành auto. Giữ min-width và đầy đủ chữ/ticks; trang sở hữu cuộn, kể cả chiều ngang khi bảng nhiều chủ đề trên màn hẹp. Không đổi shared .table-wrap hoặc bảng annual. Không virtualization/phân trang/xem thêm; GoalTable đã render đủ cây. Kiểm cả không chủ đề và nhiều chủ đề, màn soạn/chi tiết.

## Phases & Validation

1. BE scope + query + regression: AC-01–05/08–10 và metadata version; Maven targeted, OpenAPI snapshot, architecture/format. Dừng review.
2. Web mock/selector + layout + regression: AC-06/07 và annual visibility; native Node/ESLint/Vite. Quyền sửa giáo dục FE đã được user cấp, không xin lại; giữ gate plan/review. Dừng review.
3. Nghiệm thu API thật + Chromium/CSS thật và converge delta: chạy ma trận [quickstart.md](quickstart.md); không dùng SSR stub làm bằng chứng hết scroll. Dừng review/bàn giao, giữ lịch sử feature012 và không claim full-feature sạch.

## Complexity Tracking

Không cần ngoại lệ constitution. Record/query thêm nhánh REVIEW là dữ liệu nội bộ tối thiểu để tránh mở DRAFT cho reviewer; giữ overload tương thích TEAM, không thêm entity/bảng/cache/framework.

## Design Review — 08/11/2026

Review độc lập bằng subagent theo workflow làn L: phát hiện1P2 về raw HT/PHT schoolWide bypass cấu hình năm. Đã bổ sung bắt buộc giao eligibility policy schoolYear + phân công trước contribution toàn trường, giữ status gates độc lập và regression Q05 khi policy loại PHT. Đồng bộ research/data-model/contract/quickstart; review lại xác nhận P2 đã xử lý nhất quán, không còn phát hiện chặn ở mức thiết kế. Đây là review thiết kế, không phải approval triển khai hoặc test runtime.

## Analyze remediation — 08/11/2026

A1 đã được user làm rõ: HT/PHT hiệu lực mọi nhóm tuổi, tổ trưởng/GV đúng tuổi phân công; helper canAuthorYearPlan hiện có phù hợp phạm vi này khi dùng đúng policy/assignment, không đổi Q1=A/status. I1 đã bổ sung school-year-team.test.mjs và goal-labels.test.mjs vào quickstart/T020. Chỉ sửa tài liệu, chưa chạy lại phân tích đầy đủ hoặc kiểm thử runtime; giữ trạng thái draft.

## Execution delta — UI-first, user08/11/2026

User đã yêu cầu triển khai tới sạch và ưu tiên UI, thay thứ tự BE-first/review dừng từng phase cũ cho vòng này; tự review/verify mỗi increment, không dừng xin lại quyền. Làm T025–T029 UI trước, sau đó T003–T024 còn lại. Phần2 header nguồn xám; phần3 giữ cột nhánh và thêm select xem mục tiêu/nội dung theo tick, toàn bảng readonly xám; giữ bảng năm, label/ticks và data contract. Native select có label/chi tiết đa dòng, nền xám + chữ đậm theo token. Thêm khoảng40 dòng component thay renderer MT cũ, không thêm dependency hoặc framework.

## Bổ sung từ convergence — metadata OpenAPI

Native snapshot lộ collision nested records giáo dục có từ trước. T030 chỉ tách tên schema tài liệu và đồng bộ snapshot sau regression cấu trúc; không đổi JSON field/endpoint, không migration/dependency. Mục tiêu là chứng minh contract tài liệu đúng source trước bàn giao, không chạy snapshot update để che sai schema. User đã yêu cầu vòng implement/converge tới sạch; phạm vi mở rộng giới hạn metadata eduplan, không sửa module khác.

## Review cuối

Review độc lập nguồn UI/auth/metadata đã kiểm các findings và không còn blocker trong delta. TaskT031 giữ TEAM version base; T032 khóa reviewer mixed-role. Nativebrowser dùng fixture và local draft, không gọi E2E save qua server. Không dùng scoped Spotless thay kết luận full-repo gate; ghi riêng lỗi format có trước ngoài delta.
