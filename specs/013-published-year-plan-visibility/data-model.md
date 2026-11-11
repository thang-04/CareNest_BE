# Data Model — feature013

## Dữ liệu lưu dùng lại

| Concept | Dữ liệu hiện có | Quan hệ/ràng buộc |
| --- | --- | --- |
| Kế hoạch | EduYearPlan: id, planLevel, schoolYearId, ageGroupId, status, versionNo, sourcePlanId, curriculumSourcePlanId | SCHOOL_YEAR là nguồn năm; TEAM giữ liên kết nguồn và phiên bản, không hồi tố tổ cũ |
| Phân công | StaffAssignment: userId, staffRole, ageGroupId/classroomId/campusId, validFrom, validTo | Phải thuộc CurrentUser, hiệu lực tại ngày từ Clock; scope theo role/policy đã có |
| Cấu hình | EduSettings/workflow: schoolYear và year role policies | schoolYear cho năm; year cho tổ; reviewer-precedence giữ nguyên |
| Cây nguồn | domain/subdomain/expected-result/goal/content, displayLabel, nội dung–theme ticks | Không thêm/xóa/sửa nguồn; chỉ đổi hiển thị; TEAM vẫn tick từng nội dung |

Không thêm entity/cột/index hoặc migration/backfill. Không đổi FK hoặc bản snapshot/print, không chuyển sourceId. Bản tổ legacy vẫn đọc theo policy TEAM, không bắt tạo liên kết năm.

## Dữ liệu dẫn xuất trong request

- **TeamCreationAges**: các nhóm tuổi user có canAuthorYearPlan với policy year; dùng cùng cơ sở yearCreationScopes, phân công hiệu lực và reviewer-precedence. User làm rõ sau analyze: HT/PHT hợp lệ ở vai trò tạo TEAM có phạm vi mọi nhóm tuổi; TEAM_LEADER/TEACHER chỉ các tuổi phân công. HT/PHT kiêm nhiệm dùng scope quản lý khi còn phân công hiệu lực, hết phân công đó thì không giữ scope toàn trường.
- **AnnualIndependentScope**: quyền xem SCHOOL_YEAR từ policy schoolYear, phân công và status gates hiện hành. Q1=A giữ author/reviewer độc lập; mọi đóng góp HT/PHT toàn trường phải thuộc creator/reviewer policy schoolYear và có phân công hiệu lực. Không copy role-based schoolWide của TEAM. Reviewer tuổi-scoped không có DRAFT do thấy REVIEW; chỉ giữ quyền DRAFT năm nếu đã có theo status gates độc lập và vẫn đủ eligibility năm.
- **AnnualViewScope**: union AnnualIndependentScope với APPROVED ∩ TeamCreationAges. Không union base full/team-leader schoolWide APPROVED; không dùng ownerId làm giới hạn người phụ trách.
- **ViewScope nội bộ**: schoolWideStatuses, fullAgeGroupIds, approvedAgeGroupIds dùng lại; thêm reviewAgeGroupIds để biểu diễn REVIEW theo nhóm tuổi. Constructor/query overload cũ dùng review set rỗng. Không xuất thêm field này ra JSON.
- **VisibleVersionChain/current-version projection**: chỉ plan/version id người dùng được xem; source validation vẫn kiểm phiên bản nguồn hiện hành theo cơ chế feature012, không thay bằng current visible id để lách nguồn.

Scope được tính lại mỗi request; không lưu DB/cache/browser làm nguồn quyền. Danh sách SQL phải áp cùng điều kiện trạng thái/nhóm tuổi như predicate detail, trước count/page. Luồng source check dùng row lock hiện có.

## Trạng thái và validation

| Cấp/quyền | DRAFT | REVIEW | APPROVED |
| --- | --- | --- | --- |
| Chỉ quyền tạo TEAM, là tổ trưởng/GV tại tuổi X, không HT/PHT hiệu lực | Không xem năm | Không xem năm | Chỉ năm tuổi X |
| Chỉ quyền tạo TEAM, là HT/PHT hiệu lực | Không xem năm | Không xem năm | Mọi nhóm tuổi |
| Có quyền năm độc lập | Theo quyền soạn năm/status gates hiện hành | Theo quyền năm độc lập/phân công | Theo quyền năm độc lập/phân công |
| Tạo TEAM nhưng phân công hết/thu hồi/không có | Không phát sinh quyền năm | Không phát sinh quyền năm | Không phát sinh quyền năm từ quyền TEAM |
| Quyền TEAM hiện có | Giữ nguyên | Giữ nguyên | Giữ nguyên, gồm import nguồn TEAM liên nhóm đã có |

Yêu cầu lọc ageGroupId do client gửi luôn giao với scope server; không cho dùng userId khác. Phân công thay đổi không sửa nội dung kế hoạch và không tạo event ban hành giả; lần đọc tiếp theo kiểm scope mới. Mọi quyền năm vẫn không cho dùng source chưa APPROVED/cùng năm/tuổi/current source để tạo tổ.

## Truy vết

FR-001–004 và AC-01–05/08–10 → scope/đọc/source/version. FR-005–007 và AC-06/07 → cây nguồn và ticks giữ dữ liệu, chỉ thay cách cuộn trang. Không có thay đổi dữ liệu persist hoặc state transition mới.
