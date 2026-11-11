# Client impact — feature011

Đánh giá nguồn client ban đầu ngày 06/11/2026; các cột “Hiện tại/Thay đổi cần thiết” ghi trạng thái trước remediation. User sau đó đã cho phép cập nhật riêng phần kế hoạch giáo dục của Web để hoàn tất T062/T071. Paths dưới đây tính từ repo client tương ứng.

## Web source đã xác minh

| Consumer CareNest_FE | Hiện tại | Thay đổi cần thiết sau approval |
| --- | --- | --- |
| `src/pages/education-plan/EduConfigPage.jsx:16`, `:27`, `:65` | Chỉ chuỗi ký, ép PHT trong year, một form save chung | Khối Người tạo/duyệt riêng; chỉ HT có nút sửa theo canConfigure; creators/reviewers disjoint; policy save riêng/revision; phần khác giữ quyền hiện hành |
| `src/routes/AppRoutes.jsx:209`, `:250`, `:270` | Year author route cố định PHT/TL; settings HT/PHT | Route vào chức năng và capabilities server; không đổi settings toàn bộ thành HT-only |
| `src/services/education-plan/api/yearPlanMapper.js:37` | Mapper whitelist bỏ field mới | Giữ workflow/capabilities/coverage; không bỏ metadata server |
| `src/services/education-plan/api/educationPlanApi.js:220`, `:32` | PUT full signers, cache config session | Gọi workflow-policy riêng; invalidate/refetch khi conflict/focus/đổi policy; mutation luôn tin server, không cache authorize |
| `src/models/education-plan/eduConfig.js:8`, `:49`, `:60` | Default role/status/withDefaults cố định | Seed đúng đã chốt, preserve policy fields; schema mới không biến week thành policy độc lập |
| `src/services/education-plan/api/yearPlanMapper.js:180`, `:188`; `educationPlanApi.js:58` | Desired PENDING_VP suy ra submit | Gửi action intent submit/approve/return rõ; status gửi duyệt là REVIEW cho mọi first step, action intent tách khỏi status |
| `src/pages/education-plan/GoalDetailPage.jsx:82`, `:88`, `:131`; `GoalFormPage.jsx:143` | Có permissions nhưng còn nhánh VP pending edit | Reviewer không được soạn, review không lấy author lease; refresh revision/capabilities sau policy change |
| `src/pages/education-plan/GoalListPage.jsx:53`, `:57` | Create theo PHT/TL, đếm chỉ PENDING_VP | Dùng creationScopes/capabilities; chỉ status REVIEW, lượt theo waitingRole |
| `src/components/education-plan/planUi.jsx:74`, `:98` | Review turn theo user.role, send theo teacher | Dùng per-document capabilities/effective waitingRole; xử lý nhiều role, không kiểm chỉ frontend |
| `src/pages/education-plan/BranchPlanPage.jsx:72`, `ClassThemePlanPage.jsx:67`, `WeekPlanPage.jsx:78` | Edit theo GV lớp | Dùng creationCapabilities theo class/kind, metadata version/dossierVersion và expected headers cho save/action/adjustment; adjustment theo canEditAdjustments |
| `src/pages/education-plan/PlanReviewQueuePage.jsx:18` | Year queue chỉ PHT/PENDING_VP | REVIEW/capability lọc server, waitingRole chỉ người tới lượt; không tạo PENDING theo role |
| `src/services/education-plan/mock/eduConfigMockRepository.js:7`; `mock/classPlanMockRepository.js:230` | Mock HT/PHT config, class snapshot chain cũ | Parity policy/principal-only/round/reconcile, không làm demo khác backend |
| `src/utils/education-plan/yearPlanDoc.js:57`, `classPlanDoc.js:31`, `:49`, `:196` | Global signers và nhãn cố định PHT/TT | RoleKey/actual approvals và completion snapshot theo hồ sơ; completed không đổi khi global config đổi |

Focused tests hiện có để mở rộng: `tests/education-plan/year-plan-api.test.mjs:88`, `:197`; `branch-review-ui.test.mjs:16`; `doc-signers.test.mjs:27`. Chưa chạy các test này trong lượt plan. Kiểm responsive/keyboard cho hai nhóm role và reorder, giữ manual save, không lộ ID/technical state vào UI không cần thiết.

## APP

Không tìm thấy repo CareNest_APP tại sibling hoặc dưới `D:/UNIVERSITY_FPT/DOAN`, nên không thể xác minh consumer cụ thể. BE CROSS_REPO_MAP ghi HT/PHT dùng Web, GV Web+App; EDU-37 parent activity còn planned. Đây không đủ để kết luận APP không chịu tác động enum/DTO. Trước rollout phải đọc đúng source APP nếu đang tích hợp giáo dục hoặc ghi release gate chưa xác minh; không tự sửa APP.

## Backend downstream consumer

`src/main/java/com/carenest/repository/observation/ThemeEvaluationRepository.java:54` và `service/observation/impl/ThemeEvaluationServiceImpl.java:536` đang coi year issuer là PHT. Giữ DTO ThemeSigners.vicePrincipal và quyền observation hiện hành; với workflow mới lấy actual VP approval nếu có, dùng fallback chữ ký hiện tại khi không có, không đặt tên HT vào ô PHT. Legacy completed giữ path cũ. Sau chỉ đạo chỉ giáo dục, không sửa source/test module observation. Producer edu giữ issued_by là actual VP approval hoặc null, publisher thật ở published_by/completed_at; DTO giáo dục dùng publisher thật, trạng thái cuối APPROVED giữ nguyên. Chỉ đối chiếu consumer cũ bằng đọc source, không triển khai phần khác.

## Rollout contract

Phase backend chưa được bật phục vụ write với Web cũ: old mapper và role gates không hiểu semantic mới. Chuẩn bị migration+BE+Web cùng release, backup trước activation, smoke API/capabilities/print/multi-tab; không tự deploy. Client version cũ ghi signers trái policy phải bị server từ chối, không có bypass tạm để giữ tương thích.

Remediation06/11: backend P1 có snapshot enum tương thích nội bộ nhưng không release; contract client phát hành chỉ DRAFT/REVIEW/APPROVED sau T047/T051, waitingRole riêng. Bản in lấy review.printSnapshot; helper AccountService ACTIVE-ID là ngoại lệ chỉ đọc không có endpoint client mới hoặc quyền tài khoản mới.

## Xác nhận trong vòng implement/converge 06/11/2026

User đã cho phép sửa CareNest_FE phần kế hoạch giáo dục để hoàn tất T062/T071 và xác nhận App mobile chưa tích hợp kế hoạch giáo dục. FE `npm run verify` sau remediation đạt 207/207 test kế hoạch giáo dục và lint/format/design/API/build. Browser WF-01/02/03 được chạy lại trên BE build từ source cuối, Keycloak và PostgreSQL fixture riêng: 3/3 đạt. Vì APP chưa tích hợp nên hiện không có consumer giáo dục APP cần cập nhật; không mở phạm vi viết APP. Trước một đợt tích hợp APP sau này vẫn cần đối chiếu contract đương thời.
