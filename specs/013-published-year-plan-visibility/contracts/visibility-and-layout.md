# Contract — quyền xem và bố cục mục tiêu

**Status**: Implemented08/11/2026 theo user yêu cầu vòng implement/converge; EDU-60/Q1=A. JSON/endpoint giữ nguyên, scope đọc/UI và metadata schema names đã cập nhật, native evidence tại quickstart.

## API hiện có

Base controller giữ `/education-plans/goals` (base URL/prefix triển khai giữ hiện hành). Không thêm endpoint/query/request/response field hoặc ApiCode.

| Bề mặt | Hợp đồng đích |
| --- | --- |
| GET collection, planLevel=SCHOOL_YEAR, schoolYearId/ageGroupId/q/page/size hiện có | Chỉ có quyền lập tổ: APPROVED ∩ scope tuổi ∩ quyền tạo TEAM. Scope tuổi của HT/PHT hiệu lực là mọi nhóm tuổi, tổ trưởng/GV là đúng tuổi được phân công; không cấp phạm vi theo role danh nghĩa khi không có phân công. Filter client không mở rộng scope. Không có dữ liệu hợp lệ: response PageResponse thành công, total/count chỉ tính record hợp lệ, content rỗng |
| GET /{planId} | Kiểm đúng level/status/age/user assignment. Tồn tại ngoài quyền: 403 YEAR_PLAN_ACCESS_DENIED (7004). Không tồn tại: 404 YEAR_PLAN_NOT_FOUND như hiện tại |
| GET /{planId}/changes và fromPlanId | Kiểm quyền cả hai plan; cấm lộ bản nháp/tuổi ngoài quyền qua diff hoặc version chain |
| Nguồn tạo/version TEAM | Chọn từ annual list/detail đã kiểm quyền; thao tác ghi vẫn bắt source SCHOOL_YEAR + APPROVED + cùng năm/tuổi + current source; source sai giữ error code/source validation hiện hành |
| Version chain / current-version metadata trong detail/summary | Không trả id/tóm tắt version chưa được xem; không dùng bản nháp ẩn làm metadata để lộ nội dung hoặc bật action ngoài quyền |
| planLevel=TEAM hoặc không truyền level | Giữ behavior/default TEAM; không áp siết SCHOOL_YEAR lên import nguồn TEAM liên nhóm |

**Phạm vi nhóm tuổi, user chốt sau analyze08/11/2026**: HT/PHT hiệu lực xem mọi nhóm tuổi trong quyền đọc hợp lệ; tổ trưởng/GV chỉ đúng tuổi phân công. Không đổi trạng thái, quyền soạn/duyệt hay điều kiện source phải cùng năm/tuổi với TEAM đích.

**Q1=A**: quyền soạn/duyệt năm độc lập hợp lệ được giữ theo cấu hình/phân công và trạng thái. HT/PHT toàn trường phải có creator/reviewer eligibility trong cấu hình schoolYear và phân công hiệu lực; policy loại role đó khỏi năm thì không được đọc DRAFT/REVIEW nhờ raw base TEAM. Reviewer không được thêm quyền DRAFT chỉ vì đọc REVIEW; chỉ giữ quyền DRAFT năm vốn có độc lập và eligibility năm còn hợp lệ. Quyền xem nháp cho nhiệm vụ năm không cho dùng nháp làm nguồn tổ. Không cần field canView mới hoặc truyền userId; CurrentUser lấy từ xác thực như hiện hành. Auth/ResponseJson/permissions object và cơ chế active-account giữ nguyên.

Hành vi payload/schema giữ nguyên nhưng tập record/metadata được phép đọc thay đổi; review consumer bên dưới là bắt buộc. Không tự sửa snapshot OpenAPI nếu không có khác biệt schema thật; chạy test snapshot để phát hiện thay đổi ngoài dự kiến.

## Web consumer matrix (đường dẫn repo FE)

| Consumer | Kiểm tra/thay đổi dự kiến |
| --- | --- |
| src/services/education-plan/api/educationPlanApi.js getAll/listGoalSources/getGoalSource | Tin scope server; không mở rộng bằng role legacy cho SCHOOL_YEAR, không fallback mock khi403. getAll hiện lấy page0/size200, không tự refactor tổng thể trong delta |
| src/pages/education-plan/GoalListPage.jsx | Chỉ render dữ liệu annual server/mock đã được phép xem; đổi filter không cấp quyền. permissions không chứng minh mock getAll đã lọc |
| src/pages/education-plan/GoalFormPage.jsx | Source annual chỉ APPROVED/cùng năm/tuổi và source server cho phép; scopes rỗng không suy diễn toàn trường; empty notice rõ lý do; render đủ cây TEAM và giữ readonly/ticks |
| src/pages/education-plan/GoalDetailPage.jsx | Direct id bị từ chối không giữ dữ liệu cũ; source version selector giữ APPROVED/year/age; version chain không hiện bản bị cấm; render đủ cây TEAM |
| src/services/education-plan/api/yearPlanMapper.js | Giữ mapping yearCreationScopes/schoolYearCreationScopes và nhãn; không cần response field mới |
| src/services/education-plan/mock/educationPlanMockRepository.js | SCHOOL_YEAR cùng predicate annual list/detail/source/version; phân biệt independent author/reviewer; TEAM sourceVisible/EDU-24 không đổi |
| src/styles/modules/education-plan.css | Chỉ TEAM goal wrapper: bỏ max-height/overflow container hai trục và sticky header riêng. Giữ shared table CSS, annual và readonly/ticks |

Không mặc định sửa các consumer nếu đã xử lý đúng dữ liệu server; tasks phải dựa source và regression, tránh thay renderer/API adapter không cần thiết. Sửa repo FE cần user cho phép. APP không có sibling để đối chiếu; chưa có consumer được xác minh và không sửa APP.

## Hợp đồng UI

- Form và detail TEAM hiển thị đủ domains/subdomains/nhóm/mục tiêu/nội dung/nhãn, cả hàng chưa tick, không pagination/xem thêm/virtualization.
- Card/table wrapper không sở hữu thanh cuộn riêng ở cả hai trục; chiều cao tự theo nội dung. Trang cuộn để đọc hết, chiều ngang toàn trang được phép khi bảng nhiều cột; giữ min-width để tick không bị ép/cắt.
- Giữ riêng nguồn readonly và thao tác tick từng nội dung, lưu/mở lại không đổi liên kết khác; bảng annual không thay layout.
- Empty không có source hợp lệ nêu cần bản năm đã ban hành đúng nhóm tuổi được giao. Direct read bị403 không giả làm tải được hoặc hiển thị cache nguồn bị cấm. Không yêu cầu xóa dữ liệu đã đọc hợp lệ khỏi màn đang mở ngay khi phân công đổi; lần đọc tiếp theo phải kiểm quyền mới.

## Kiểm chứng contract

Ma trận Q01–Q12 và native commands ở [quickstart.md](../quickstart.md). Bộ nghiệm thu không dùng dữ liệu/tài khoản thật trong artifact; test authenticated user khác nhau, 3 status, 2 tuổi và phân công biên ngày. Browser kiểm CSS computed và kích thước thực tế, không chỉ SSR/regex.

## Delivery notes — 08/11/2026

- Scope implementation và mock đã dùng policy theo cấp/trạng thái, thêm tập REVIEW nội bộ; HTTP JSON không thêm field. CurrentUser truyền tới mock diff qua usePlanChanges/facade. Source import legacy cross-age UX chỉ áp TEAM, annual tin quyền server.
- UI delta FR008–010 dùng bảng hiện có, cột nhánh readonly và native select có label Xem mục tiêu; chọn xem toàn văn mục tiêu/nội dung tick, không sửa/tự tick dữ liệu. Phần2 header bốn cột nguồn xám; phần3 toàn bảng xám, select tương tác riêng. Annual editing giữ nguyên.
- Metadata OpenAPI được sửa theo T030 do collision có sẵn: nested schema tên riêng cho preview/config/changes và theme/break/branch request-response; sửa snapshot từ runtime sau shape regression. Không đổi URL/HTTP/JSON field, client FE/APP dùng JSON không cần migration; tooling sinh client từ schema cần lấy snapshot mới. APP không sửa.
