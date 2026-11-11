# Research — feature013

Ngày 08/11/2026; chỉ đọc source BE/FE, không gọi công nghệ mới hoặc tìm best practice trên web vì tái sử dụng convention của repo. Constitution 2.0.0 phù hợp; không chạy setup/harness/hooks.

## R1. Scope đang bị kế thừa từ cấp tổ

**Evidence**: `src/main/java/com/carenest/security/authorization/EduPlanAccessPolicy.java:87` dựng scope chung; `:107` cho tổ trưởng xem APPROVED toàn trường. `src/main/java/com/carenest/service/eduplan/impl/EduYearPlanServiceImpl.java:935` bắt đầu từ base rồi thêm author/reviewer, không thu hẹp base theo level. Chi tiết `:887` và load khóa `:910` dùng base trực tiếp. Đây là hành vi hiện tại khác yêu cầu EDU-60; không sửa trước khi duyệt plan.

**Decision**: nhánh SCHOOL_YEAR tính quyền theo level, cộng quyền năm độc lập và quyền đọc APPROVED từ tạo TEAM đúng nhóm tuổi; TEAM giữ nhánh cũ. ViewScope nội bộ thêm tập REVIEW để reviewer không nhận mọi trạng thái khi thêm fullAgeGroupIds. Giữ overload 3 tham số và overload search cũ cho caller TEAM.

**Rationale**: cùng representation dùng ở SQL và allows; list/count phân trang đúng; không cần bảng/permission mới. Q1=A được áp dụng theo quyền độc lập, không dựa role danh nghĩa.

**Alternatives considered**: lọc FE hoặc sau phân trang Java làm lộ dữ liệu/count và hụt trang; thay base scope toàn cục làm đổi TEAM import EDU-24; gán full ages cho mọi reviewer có thể mở DRAFT ngoài trạng thái được phép. Các phương án này bị loại.

## R2. Phân công và reviewer/author đã có primitive

**Evidence**: `src/main/java/com/carenest/security/authorization/EduPlanAccessPolicy.java:227` canAuthorYearPlan dùng policy/effectiveRoles và phân công; `:291` canReviewYearPlan kiểm reviewer theo nhóm tuổi hoặc phân công HT/PHT. `src/main/java/com/carenest/service/organization/impl/StaffAssignmentServiceImpl.java:158` và `:172` đọc nhóm tuổi hiệu lực theo user/date. `src/main/java/com/carenest/service/eduplan/impl/EduConfigServiceImpl.java:143` yearCreationScopes dùng policy year; `:171` scopes năm dùng schoolYear. Clock hiện hành quyết định ngày, không dùng ngày đầu năm của signerNames làm quyền truy cập.

**Decision**: tái sử dụng các service read-only/role policy hiện có, scope snapshot trong request. Mọi schoolWide contribution HT/PHT phải giao với creator/reviewer eligibility của schoolYear và phân công, không copy raw base theo role. PHT bị loại khỏi policy năm không có quyền DRAFT/REVIEW độc lập dù còn phân công/quyền TEAM; reviewer tuổi-scoped không nhận DRAFT từ full tuổi tổ trưởng. Chỉ giữ quyền DRAFT năm đã có độc lập với TEAM khi eligibility năm vẫn hợp lệ; quyền xem thay đổi ở lần đọc tiếp theo khi phân công đổi. Không đổi module organization hoặc ghi phân công vào JWT.

**Rationale**: đúng tài khoản xác thực và phân công, tránh tự tạo một nguồn quyền song song ở client.

**Alternatives considered**: lọc theo ownerId bản năm là sai vì người lập tổ không phải tác giả năm; tin ageGroupId/userId client là vượt quyền; cache dài theo role giữ quyền sau thu hồi. Loại cả ba.

## R3. Source và version là đường đọc cần bảo vệ

**Evidence**: `src/main/java/com/carenest/service/eduplan/impl/EduYearPlanServiceImpl.java:791` requireCurriculumSource đã bắt cấp/năm/tuổi/APPROVED/current source. `:1680` visibleChain chỉ lọc base scope hiện tại; current version được tính trước đó. `:757` getChanges kiểm hai plan id qua loadViewablePlan. `src/main/java/com/carenest/repository/eduplan/EduYearPlanRepository.java:81` query search lọc level và quyền trước pageable.

**Decision**: giữ source validation, đổi quyền của loadViewablePlan/loadPlanForUpdate/visibleChain/current-version projection sang scope cấp năm. Changes phải kiểm cả hai đầu; compareVersions nội bộ không được biến thành public read bypass. Current-version fields chỉ chứa id được phép thấy; predicate chọn nguồn hiện hành không đổi.

**Rationale**: ẩn bản nháp trong list chưa đủ nếu detail response còn đưa id/version của nháp hoặc source chọn từ đường đọc khác.

**Alternatives considered**: chỉ sửa getYearPlans bỏ sót direct id/version/source; dùng quyền xem nháp để cho source nháp vi phạm EDU-55. Loại.

## R4. Web đã render đầy đủ, CSS tạo vùng cuộn

**Evidence** (đường dẫn tương đối repo FE): `src/styles/modules/education-plan.css:2350` đặt max-height/overflow:auto cho bảng tổ; `:2355` sticky header. `src/styles/components.css:607` shared table-wrap vẫn overflow-x:auto nếu chỉ xóa rule đầu. `src/components/education-plan/GoalTables.jsx:877` wrapper, `:922` duyệt hết domains. Form `src/pages/education-plan/GoalFormPage.jsx:688` và detail `src/pages/education-plan/GoalDetailPage.jsx:159` có ga-team-plan; call GoalTable `:1019` và `:269`. Readonly/tick props form `:1022`–`:1025` phải giữ.

**Decision**: override hẹp TEAM goal wrapper overflow:visible cả hai trục, max-height:none; gỡ sticky header của container cũ. Giữ cỡ bảng, nhãn/ticks và toàn văn. Trang cuộn khi quá màn hình.

**Rationale**: không cần sửa cây dữ liệu/renderer. Ancestors card/body/page không clip: FE components.css:520/556, components.css:7; layout.css:14/21 và base.css:9 không đặt overflow hidden. Browser phải xác nhận document sở hữu scroll; không coi đọc CSS là nghiệm thu.

**Alternatives considered**: chỉ bỏ max-height còn vùng cuộn ngang riêng; chỉ overflow-y:visible bị computed style chuyển auto khi X auto; sửa global table-wrap tác động mọi bảng. Loại. Không ép co cột quá nhỏ: FE education-plan.css:639 có min-width trên mobile, cuộn trang ngang được phép theo yêu cầu cuộn trang.

## R5. Consumer thật và mock khác nhau

**Evidence** (FE): annual routes `src/routes/AppRoutes.jsx:249`; `src/services/education-plan/api/educationPlanApi.js:150` getAll fetch TEAM+SCHOOL_YEAR rồi detail, listSource `:114` và getGoalSource `:140`. Form `src/pages/education-plan/GoalFormPage.jsx:149` và detail `src/pages/education-plan/GoalDetailPage.jsx:318` lọc source annual APPROVED cùng năm/tuổi. `src/pages/education-plan/GoalListPage.jsx:55` tin permissions từ server; `src/services/education-plan/mock/educationPlanMockRepository.js:472` trả mọi goal có permissions. Mock sourceVisible `:25` vẫn giữ cross-age APPROVED TEAM theo EDU-24. Scope adapter `src/services/education-plan/api/yearPlanMapper.js:47` map yearCreationScopes/schoolYearCreationScopes; identity ở `src/hooks/education-plan/useEducationPlan.jsx:89`.

**Decision**: server là thẩm quyền; mock annual đồng bộ predicate riêng ở mọi bề mặt đọc, giữ TEAM. Form không tự cấp quyền bằng role/age fallback nếu scope server rỗng. Empty source phải nói chưa có bản ban hành đúng nhóm tuổi/phân công; không tự chuyển mock khi API từ chối.

**Rationale**: lọc tại GoalListPage không sửa lộ dữ liệu trong mock repository; permissions object không phải bằng chứng record thực sự được phép xem.

**Alternatives considered**: chỉ lọc danh sách UI, fallback mock khi 403, thay mọi sourceVisible không phân cấp. Loại. getAll page0 size200 là giới hạn có sẵn; không mở rộng thành refactor phân trang provider trong delta này, dùng fixture phù hợp và ghi giới hạn khi nghiệm thu.

## R6. Làm rõ phạm vi nhóm tuổi sau analyze

**Decision**: user08/11/2026 chốt HT/PHT hiệu lực xem mọi nhóm tuổi; tổ trưởng/GV đúng tuổi được giao. Chỉ xác định chiều phạm vi tuổi, không bỏ điều kiện trạng thái hoặc eligibility.

**Rationale**: nhánh PRINCIPAL/VICE_PRINCIPAL trong canAuthorYearPlan trả true theo mọi tuổi là phù hợp quyết định mới khi có quyền tạo TEAM/phân công hiệu lực; nhánh tổ trưởng/GV vẫn dựa phân công. Không cần thiết kế thêm phân công tuổi riêng cho HT/PHT. Annual độc lập giữ Q1=A; chỉ TEAM creator vẫn chỉ APPROVED.

**Alternatives considered**: bắt HT/PHT có phân công từng tuổi hoặc cho tổ trưởng/GV mọi tuổi đều không theo quyết định user.

## Kết luận nghiên cứu

Không cần schema, dependency, endpoint hoặc DTO mới. Hành vi đọc API siết theo EDU-60 có consumer cụ thể nên phải được duyệt cùng plan làn L. Những dòng tài liệu cũ còn nhắc verify harness/triển khai feature012 không dùng làm gate hoặc quyền triển khai feature013. Cần tasks/analyze sau thiết kế, rồi approval mới code; không có quyết định nghiệp vụ chưa trả lời.
