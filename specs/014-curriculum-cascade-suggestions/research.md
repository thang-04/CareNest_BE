# Research — feature014

Ngày 08/11/2026. Khảo sát source địa phương readonly bằng hai research agents; chưa chạy runtime hoặc tái hiện thao tác người dùng. Nguồn là checkout hiện tại có các thay đổi từ trước, không giả là main/dev.

## R1. Phạm vi và nguyên nhân ở source

**Decision:** sửa cả danh sách đích, lựa chọn nội dung và guard ngay trước callback; không chỉ thay nhãn/thứ tự select.

**Rationale:** Web [CurriculumGoalsModal.jsx](../../../CareNest_FE/src/components/education-plan/CurriculumGoalsModal.jsx):26 lấy mọi goalTargets; :29–33 chỉ lọc nguồn mục C; :86–95 confirm lấy checked từ toàn data.contents; :120 chỉ kiểm target/suggested nonempty. Helper [curriculum.mjs](../../../CareNest_FE/src/models/education-plan/curriculum.mjs):88 chỉ có id/domainName/text. Đây là bằng chứng lỗ hổng tĩnh phù hợp phản ánh, chưa là regression RED/runtime fix.

**Alternatives considered:** reset target đơn lẻ không ngăn target ngoài nhánh chọn lại hoặc nguồn tick ẩn; lọc theo câu chữ không phân biệt nhánh trùng tên.

## R2. Dùng lại dữ liệu và contract BE

**Decision:** FE dùng goals theo ageGroupId cho nhóm/mục tiêu/nội dung mục D; giữ contents cho mục C độc lập. Không endpoint/DTO/ApiCode/schema/migration/dependency mới; không sửa raw seed, nhưng chuẩn hóa source identity tại mock adapter theo R7.

**Rationale:** [CurriculumGoalResponse.java](../../src/main/java/com/carenest/dto/eduplan/CurriculumGoalResponse.java):5–19 có sourceId/domainId/subDomainId/expectedResultId và contents. [EduCurriculumServiceImpl.java](../../src/main/java/com/carenest/service/eduplan/impl/EduCurriculumServiceImpl.java):82–119 join nội dung bằng goal ID; :131 trả mục C riêng. [CurriculumContentResponse.java](../../src/main/java/com/carenest/dto/eduplan/CurriculumContentResponse.java):3 không có expectedResultId. Web [educationPlanApi.js](../../../CareNest_FE/src/services/education-plan/api/educationPlanApi.js):83–87/:111–113 không cắt quan hệ.

**Alternatives considered:** thêm API filter/cache/library/quan hệ mục C→nhóm làm tăng phạm vi và không cần cho yêu cầu user đã chốt.

## R3. Phân biệt cấu trúc đích và liên kết nguồn

**Decision:** quét flat ordered rows để dựng actual local sub/group context; local row IDs dùng lọc đích. Source IDs dùng tra nguồn, không thay local parent. Giữ kiểu khóa/null nhất quán, không ghép sub/group theo tên.

**Rationale:** [YearPlanDetailResponse.java](../../src/main/java/com/carenest/dto/eduplan/YearPlanDetailResponse.java):137–150 có kind và provenance riêng từng dòng. Web [GoalTables.jsx](../../../CareNest_FE/src/components/education-plan/GoalTables.jsx):102–132 đã chia cây theo thứ tự; [yearPlanMapper.js](../../../CareNest_FE/src/services/education-plan/api/yearPlanMapper.js):84–90/:197–200 giữ source IDs. Source ID vẫn là provenance khi người soạn đổi vị trí; quan hệ đích phải theo cây hiện tại.

**Alternatives considered:** thêm actual parent ID vào DTO không cần; suy cha chỉ từ curriculumGoalId bỏ sót mục tiêu nhập tay và mục tiêu đã chuyển nhóm.

## R4. Gợi ý tại ô nhập và trường nhập tay

**Decision:** thay nguồn hardcode của GoalTable bằng goals từ service facade hiện có. Tái sử dụng Suggest/FloatingPanel, nâng option thành object có key/text/source metadata. Nhóm theo source sub đã xác định; goal/content theo source group; nội dung là hợp các contents của mọi goal trong nhóm đã chọn theo Q2.

**Rationale:** [expectedResults.js](../../../CareNest_FE/src/models/education-plan/expectedResults.js):58 chỉ ưu tiên sub phù hợp rồi nối sub khác, không strict. [GoalTables.jsx](../../../CareNest_FE/src/components/education-plan/GoalTables.jsx):135–164 đã mở focus nhưng nhận string, giới hạn12 và chỉ onMouseDown; :738/:750 chỉ gợi ý nhóm. Chọn nhóm/goal cần lưu provenance qua field sẵn có, không tự chèn con hoặc sửa hàng khác.

Với sub/group chưa gắn nguồn: hiển thị lời hướng dẫn và một cách chọn ngữ cảnh nguồn rõ ràng cạnh vùng gợi ý; giữ lựa chọn nguồn này trong state phiên theo row, không âm thầm gắn ID vì tên giống. Chỉ chọn gợi ý để điền dòng mới gắn provenance tương ứng. Không có nguồn hợp lệ thì vẫn cho nhập tay theo quyền. Không tạo placeholder sub/group.

**Alternatives considered:** giữ hardcode tiếp tục sai scope/nhóm tuổi; datalist/string-only mất identity và khó xử lý trạng thái; thư viện autocomplete mới không cần. Không giữ giới hạn12 như trần ẩn: danh sách cuộn phải cho truy cập mọi kết quả đang khớp bằng bàn phím/chạm.

## R5. Request scope và quyền

**Decision:** dùng useCurriculum/useAsync hiện có, chỉ load cho editable tree và age hợp lệ; reconcile khi scope/form đổi, kiểm membership lần cuối ở confirm và boundary áp bản soạn hiện tại. Không sửa useAsync nếu không xuất hiện gap mới.

**Rationale:** [useCurriculum.js](../../../CareNest_FE/src/hooks/education-plan/useCurriculum.js):5–22 dùng Promise.all; [useAsync.js](../../../CareNest_FE/src/hooks/useAsync.js):22–73 đã có latest-request/scope guard. Cần thêm tùy chọn enabled tương thích default hiện có để GoalTable readonly/TEAM không phát request mới chỉ do render. [GoalFormPage.jsx](../../../CareNest_FE/src/pages/education-plan/GoalFormPage.jsx):665 áp callback lên importState.current.form nên guard phải nhìn form mới nhất.

BE [EduYearPlanServiceImpl.java](../../src/main/java/com/carenest/service/eduplan/impl/EduYearPlanServiceImpl.java):313–325 kiểm editable/session/revision, :1264 bảo vệ inherited tree, :1782 cấp canEditCurriculum. Danh mục chung không thay kiểm quyền kế hoạch.

**Alternatives considered:** viết request coordinator mới hoặc dựa disabled button làm guard duy nhất đều không cần/không đủ.

## R6. Kiểm chứng và consumer

**Decision:** ưu tiên helper regression, browser component thật cho keyboard/touch/filter và live API save/reload sau triển khai. Không gọi kiểm tài liệu là bằng chứng runtime.

**Rationale:** Web curriculum.test.mjs đã có helper/import/provenance tests; branch-template-enter.test.mjs có fixture của GoalTable cần cập nhật khi đổi import. team-plan-browser.mjs là mẫu browser fixture, không phải bằng chứng live save. Hai E2E hiện có xem quickstart. APP sibling không tồn tại trong workspace; chưa thể xác minh consumer Mobile.

**Alternatives considered:** chỉ SSR/source assertion không chứng minh focus/portal/Enter; chạy toàn hệ thống thay đổi tài liệu không mang thêm bằng chứng cần thiết.

## R7. Mock identity và live E2E đúng cấp kế hoạch

**Decision:** mở rộng curriculumMockRepository ở boundary nguồn để enrich ID mock ổn định từ hierarchy nguồn; không yêu cầu raw seed đã có API IDs. Không đoán source cho dòng nhập tay. Namespace và tuple nguồn (age/source/domain/sub/groupNo/groupText) phải serialize không va chạm; source sub/group cùng nhánh dùng cùng ID, khác tuổi/nhánh phải khác ID; không dùng array index toàn dataset làm identity.

**Rationale:** Web [curriculumMockRepository.js](../../../CareNest_FE/src/services/education-plan/mock/curriculumMockRepository.js):19–22 trả raw seed; [curriculumSeed.js](../../../CareNest_FE/src/mocks/curriculumSeed.js) thiếu domainId/subDomainId/expectedResultId trên327/327 goal theo native inspection của research agent. API thật đã có numeric IDs; mock IDs được tạo trong mock-only namespace, ổn định qua reload, chỉ được lưu/đọc bằng mock repository. Không gửi mock IDs sang API thật, không đổi serverId/DTO để chấp nhận chúng. Kiểm parity quan hệ/hành vi trên cả hai mode và4 nhóm tuổi; test mock roundtrip giữ provenance.

**Alternatives considered:** sửa raw seed hàng loạt hoặc bỏ mock parity làm tăng phạm vi/để một mode hỏng; fallback ghép tên plan tùy ý sai danh tính. Chuẩn hóa danh tính từ chính cây nguồn mock khác với suy provenance của cây kế hoạch nhập tay.

**Decision:** tạo live suite014 riêng: tests/e2e/system/curriculum-cascade.spec.mjs; dùng SCHOOL_YEAR, route annual và creator/reviewer policy hiệu lực theo fixture published-year-plan-visibility. Không dựa vào hai legacy suite như bằng chứng014.

**Rationale:** edu-plan-version-ministry.spec.mjs:43 tạo legacy TEAM, còn kỳ vọng EDITING/REVIEW lease; year-plan-subdomain-delete.spec.mjs:80 thiếu planLevel. TEAM hiện fixedCells ở GoalFormPage.jsx:1019–1025 nên thay selectors không đủ để nghiệm thu inline nhập cây. Suite014 phải tạo fixture đúng cấp/quyền/workflow hiện hành, cleanup theo marker và không sửa policy toàn cục của user.

**Alternatives considered:** refactor hai suite legacy ngoài scope hoặc chạy pass cũ rồi gán coverage014 đều không thích hợp.

## Kết quả research

Không còn unknown kỹ thuật chặn thiết kế. Không quyết định lại nghiệp vụ Q1–Q3. User đã cho phép sửa giáo dục FE014 ngày08/11/2026 sau analyze; user đã duyệt vòng implement→converge plan014 ngày08/11/2026; chưa có quyền APP và delta catalog nguồn có plan riêng chờ duyệt. Quyền/approval012–013 không tự mở rộng sang014.
