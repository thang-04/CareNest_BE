# Consumers và rollout — feature012

**Thiết kế delta Q4=A/Q5=A cập nhật 07/11/2026:** mẫu nhánh PHT tùy chọn lấy/không lấy và sửa tên bản lớp; đổi thứ tự chuyển cả khối chủ đề kèm nhánh, giữ số tuần học, dời ngày. Thiết kế đã cập nhật theo quyết định mới, còn draft chờ duyệt triển khai; không giữ ràng buộc khóa tên hoặc bắt lấy đủ mẫu cũ.

Chỉ khảo sát read-only client. Không có quyền sửa FE/APP trong lệnh plan. [API đề xuất](education-plans.md), [data model](../data-model.md).

## Web

Giữ `/education/goals` là Kế Hoạch Tổ cho bookmark/client cũ; thêm `/education/school-year-plans` cho cấp năm, cùng list/form/detail component nhận level từ route. Route năm đứng trên tổ. Các link create/detail/version/review phải giữ level; không suy cấp từ title.

| File trong CareNest_FE | Thay đổi tối thiểu dự kiến |
| --- | --- |
| src/layouts/menuConfig.js:216; src/routes/AppRoutes.jsx:249 | route/menu năm mới phía trên tổ, URL cũ vẫn tổ |
| src/pages/education-plan/GoalListPage.jsx; GoalFormPage.jsx; GoalDetailPage.jsx | level-specific list/source/create/permissions/header; annual nhập cây, inherited TEAM chỉ tick; detail hiển thị nguồn; tạo version TEAM cho chọn annual mới tường minh, absent giữ clone cũ, hiển thị sourceChanges/remapWarnings |
| src/components/education-plan/GoalTables.jsx:182,759,1020 | tách curriculumReadOnly và canAssignContents; annual không cột mục tiêu phần chủ đề, không cột tick phần 3; bốn cột dữ liệu Nhóm/STT/Mục tiêu/Nội dung |
| src/services/education-plan/api/yearPlanMapper.js:149; educationPlanApi.js:113,151,250 | roundtrip level/source/templates/snapshot/capabilities; filter nguồn theo level, old year workflow là TEAM |
| src/services/education-plan/educationPlanService.js; src/hooks/education-plan/useEducationPlan.jsx | tải riêng hai cấp, không trộn state/cache/selector, propagate effective calendar |
| src/models/education-plan/planVersions.mjs:14; src/components/education-plan/planUi.jsx:29 | chain key thêm level, teacher source chỉ TEAM APPROVED; previous/current/pinned không lẫn annual |
| src/pages/education-plan/EduConfigPage.jsx:397; src/models/education-plan/eduConfig.js:21 | hiển thị bốn loại workflow year/team alias đúng, thêm duration UI HT-only; lấy limits hồ sơ cho form, không global config live |
| src/pages/education-plan/BranchPlanPage.jsx:118; src/models/education-plan/classPlan.js:95,142 | chọn none/some/all mẫu, rename local giữ UUID/source, thêm custom; đổi thứ tự khối qua calendar-preview, chỉ apply form+dirty; dates/tuần dựa lịch lớp |
| src/pages/education-plan/ClassThemePlanPage.jsx:138; WeekPlanPage.jsx:55; PlanReviewQueuePage.jsx | cùng effective calendar, reviewer không lẫn hai cấp, hiện đủ hai tuần/nhánh |
| src/models/education-plan/importBranchFile.mjs:9; src/components/education-plan/ImportBranchFileModal.jsx | RENAME branchKey hoặc CREATE_CUSTOM từ tuần trống khi không lấy mẫu; draftCalendar chưa lưu; không lấy dates/duration từ file; identity+formRevision ngăn response muộn sau mọi rename/reorder/source switch |
| src/utils/education-plan/classPlanDoc.js:164 | bỏ hardcode một tuần, in đúng range/số tuần học; giữ output legacy; xuất hai giáo án tuần |
| src/components/education-plan/ImportGoalFileModal.jsx; src/models/education-plan/importGoalFile.mjs | annual không apply tick, inherited TEAM không thay cây; không bật backdoor lấy Bộ/nguồn cũ |

Tên/path ở bảng là vị trí source khảo sát, không phải lệnh tạo component nhân đôi. Các file mock và test sát module phải cùng xử lý metadata để demo không mở quyền rộng hơn BE.

## Các đường không được hồi quy

1. Tick một content vẫn chỉ đổi liên kết đó, goal tương ứng đi theo; cây/text không đổi. Read-only source không vô hiệu hóa tick hợp lệ ở TEAM.
2. Hai cấp cùng năm/tuổi có thể đều version 1; helper không chọn bản annual làm nguồn GV hay tính superseded cho TEAM.
3. Mẫu nguồn readonly, bản nhánh lấy về lớp đổi tên được; GV được không lấy/bỏ chọn từng mẫu (Q4=A), custom thuộc lớp; không dùng name làm identity; hai tuần cùng nhánh không thành hai nhánh.
4. UI lỗi/empty/loading và save draft hiện có được giữ; stale revision yêu cầu tải/đối chiếu và giữ dữ liệu form, không ghi đè bản khác. Ngôn ngữ tiếng Việt; keyboard/tab/select/ellipsis/responsive theo pattern component có sẵn, không thêm thiết kế hệ thống mới.
5. Approved/review docs không sửa, version/print snapshot bất biến, kế hoạch điều chỉnh sau duyệt vẫn riêng EDU-27; học liệu gắn đúng theme/day, không gán lại do thay số tuần.

6. Chọn/bỏ mẫu không autosave hoặc sửa template; bỏ rồi reload/restore không bị auto-add. Nháp mới có thể không lấy mẫu nào; UI vẫn tạo custom/import từ tuần học trống và giữ điều kiện calendar coverage trước gửi.
7. Đổi thứ tự là full permutation theme IDs, preview server giữ counts/offsets từng nhánh; A2/B4→B4/A2, nhánh renamed/custom đi theo đúng cha, không đổi text/ticks/tree. Slots/gap không biểu diễn được trả lỗi, không tự nén/lấp. Dùng controls hiện có, không dựng bảng kéo-thả thư viện mới; trước khi có dates đủ thì khóa nút đổi thứ tự kèm lý do, vẫn lưu nháp.
8. Preview ràng identity+form revision toàn payload, không chỉ server calendar revision. Save response chỉ đánh sạch đúng payload đã gửi; user sửa tiếp giữ dirty/backup như useManualSave. Source/yearPlan đổi trong cùng class cũng đổi request identity, không dựa riêng React remount classId.
9. Copy version remap theme/template IDs bằng lineage, giữ tên local/custom/việc bỏ mẫu; không dùng name/Monday làm identity. weekDocId giữ suffix TEAM planId cho version>1. Reorder không di chuyển week docs/media đã có; draft phụ thuộc sai ngày hiện unresolved trước gửi.

API client cần thêm `previewClassCalendar` gọi POST branch/calendar-preview, mapper serialize optional orderedThemeIds như command và bỏ projections names/notes/calendarRevision khi v2 save. UI sửa ghi chú vào branch.weekNotes theo offset tuần học, export đọc projection tương ứng Monday mới; hai note của nhánh2tuần không gộp hoặc nằm lại ngày cũ khi reorder. File preview giữ operation/matchKey phân biệt UUID hiện hữu với CREATE_CUSTOM chưa có UUID; React key không chỉ weekStart vì range hai tuần và nhiều operation có thể cùng row. Không reset local name theo source trong normalizer, getter hoặc render.

## Downstream BE

`repository/observation/ThemeEvaluationRepository.java` thêm filter TEAM cho findApprovedThemes/findApprovedPlanIssuer/findYearGoals. `service/observation/impl/ThemeEvaluationServiceImpl.java` dùng eduplan projection theo class cho lịch list/range/confirm và từng lớp trong childThemeResults; `entity/observation/ThemeEvaluation.java` thêm presentation snapshot, đồng bộ metadata chưa chốt trước confirm. Union/lookup confirmed trước live themes để theme bị bỏ trong version mới vẫn list/get/link cũ được; frozen cells/name/date/goal columns/membership không tính lại. Legacy snapshot ghi rõ LEGACY_BASELINE, không tuyên bố tái dựng lịch sử metadata chưa từng lưu. `YearEndSheetServiceImpl` giữ shape/formula, chỉ thêm regression chứng minh source TEAM hiện hành và frozen results không mất. `EduLessonQueryServiceImpl` contract giữ, test tuần thứ hai và lineage.

Không đổi endpoint/DTO observation hoặc quy tắc kết quả, không mở rộng module đánh giá ngoài tương thích source/lịch. Đây là lý do phase 2 cần regression observation dù phần lớn source thay ở education-plan.

## Mobile và deployment

APP chưa có bằng chứng consumer kế hoạch mới trong đợt này, không đọc/sửa APP hàng loạt. Nếu contract audit ở bước implement tìm thấy caller thật thì báo tác động trước sửa; không tự triển khai màn APP. Không mặc định quyền user cũ từ feature011 là quyền sửa FE trong feature012.

Rollout phối hợp BE/FE, không thêm cờ hạ tầng mới:

1. Deploy schema additive và BE hiểu legacy/v2, default GET TEAM, giữ year alias. Chưa tạo annual/v2 từ UI cũ.
2. Sau permission sửa FE và kiểm chứng cùng BE, deploy FE route/mapper/calendar mới rồi mới sử dụng annual/templates/v2. Thời điểm cắt POST TEAM nguồn-less phải cùng FE tạo TEAM từ annual; trong khoảng chuyển đổi từ chối tạo thiếu nguồn với hướng dẫn rõ, không tạo sai loại.
3. Existing bookmarks và read legacy hoạt động; client cũ không hỗ trợ range v2 bị conflict khi ghi để không flatten. Không tuyên bố rollout BE-only hoàn thành feature hoặc mọi client cũ ghi được.
4. Sau có dữ liệu annual/v2, rollback app phải giữ khả năng đọc mới; không quay lại binary cũ ghi full cây hoặc tính tuần sai. Không tự deploy/backup-restore trong lệnh plan.
