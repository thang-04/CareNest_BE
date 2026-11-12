# Contract đề xuất — education plans feature012

**Thiết kế delta Q4=A/Q5=A cập nhật 07/11/2026:** mẫu nhánh PHT tùy chọn lấy/không lấy và sửa tên bản lớp; đổi thứ tự chuyển cả khối chủ đề kèm nhánh, giữ số tuần học, dời ngày. Thiết kế đã cập nhật theo quyết định mới, còn draft chờ duyệt triển khai; không giữ ràng buộc khóa tên hoặc bắt lấy đủ mẫu cũ.

Chờ user duyệt plan và thay đổi API theo AGENTS.md; chưa sửa controller/DTO/ApiCode/OpenAPI. Prefix triển khai vẫn lấy cấu hình, đường dẫn dưới đây không chứa prefix. Giữ ResponseJson và auth/exception/logging convention hiện có. Consumer cụ thể ở [clients.md](clients.md).

## 1. Level và nguồn

Giữ `/education-plans/goals` cùng detail/actions/version/changes/edit lease. Bổ sung:

| Interface | Delta |
| --- | --- |
| GET /goals | optional query `planLevel=SCHOOL_YEAR|TEAM`; absent → TEAM; scope/filter/pagination cũ giữ nguyên |
| POST /goals | YearPlanRequest thêm `planLevel` (absent → TEAM), `curriculumSourcePlanId` nullable, `branchTemplates` array |
| GET detail/list/version summaries | thêm `planLevel`, `curriculumSourcePlanId`, `sourceMode=INHERITED|LEGACY|ROOT`, `durationPolicySnapshot`; detail thêm `branchTemplates` và capability fields |
| PUT /goals/{id} | cùng payload cây/tick; level/source/snapshot không đổi được; TEAM INHERITED chỉ tick nội dung và phần được phép; annual không nhận ticks |
| POST /goals/{id}/versions | level kế thừa predecessor, `sourcePlanId` vẫn predecessor; optional body `{curriculumSourcePlanId}` cho TEAM để chọn bản annual mới; absent giữ clone/source cũ; snapshot limits mới theo thời điểm tạo |
| GET /goals/{id}/versions, /changes | chỉ so cùng level/năm/nhóm tuổi, không lẫn quan hệ curriculum source |

TEAM tạo mới bắt buộc curriculumSourcePlanId là SCHOOL_YEAR APPROVED hiện hành đúng năm/tuổi/scope; server copy tree, theme, breaks, templates, trả IDs target. Request tạo TEAM không được tự cấp nội dung cây khác nguồn; nếu gửi cây cùng request tạo thì kiểm tương đương nguồn trước ghi, không bỏ qua dữ liệu khác âm thầm. SCHOOL_YEAR không nhận curriculumSourcePlanId. API không nhận legacy flag/sourceMode/snapshot do client đặt. Bản cũ sourceMode LEGACY đọc và version theo cơ chế cũ, không ép tạo annual để mở lại; POST TEAM nguồn-less mới bị từ chối, không fallback tạo legacy.

Đổi nguồn năm chỉ qua tạo version TEAM từ bản tổ đã ban hành: body source mới là lựa chọn tường minh, phải là annual hiện hành cùng năm/tuổi và có quyền xem. Server clone curriculum nguồn mới, map local themes/content/templates và ticks bằng lineage cùng chuỗi nguồn; template source pointer trỏ annual đã chọn, theme/template local IDs thuộc TEAM mới. Trả `sourceChanges`/`remapWarnings` trong detail version mới để nêu mục tiêu/nội dung/chủ đề bị thêm/bỏ và tick không còn đích; không giữ orphan hoặc ghép bằng tên/số MT. Bản mới duyệt từ đầu theo EDU-54, mọi reference chưa hợp lệ chặn gửi theo validator/version-fixes hiện có; không thêm trạng thái/chuỗi duyệt. TEAM legacy có thể nhận nguồn lần đầu bằng cùng lựa chọn version này và trở thành INHERITED; body absent giữ logic legacy cũ. TEAM đang nháp không bị tự đổi nguồn khi annual mới ban hành; muốn chọn nguồn khác thì xử lý bản nháp theo thao tác xóa nháp/tạo lại hiện có, không rebind bằng PUT. Bản tổ/lớp đã ban hành không đổi, không có live sync.

Capabilities detail thêm `canEditCurriculum`, `canAssignContents`, `canEditBranchTemplates`. Resolve bằng level/sourceMode và author rights, không để capability thay kiểm server. INHERITED TEAM: curriculum=false, assign=true khi soạn được; SCHOOL_YEAR: curriculum=true, assign=false; legacy TEAM theo quyền soạn cũ. REVIEW/APPROVED đều false cho ghi nội dung. canCreateVersion/canApprove/lease vẫn lấy EDU-54.

`branchTemplates[]` request `{key,id,themeKey,name,startDate,endDate}`; key ổn định cho mẫu chưa lưu, themeKey thuộc themes[].key cùng request. Response `{id,themeId,lineageId,sourceTemplateId,name,startDate,endDate}`; sourceTemplateId do server copy, không cho client thay. Annual không bắt buộc có mẫu; mẫu có thì ban hành phải có tên và đúng chủ đề cha, không bắt ngày ở mẫu chỉ gợi ý tên theo cập nhật user FE review. Nếu truyền lịch mẫu thì lịch phải đầy đủ, nằm trong cha và hợp limits. Bản mẫu trên TEAM không bị GV sửa trực tiếp; GV đổi tên bản lớp qua branch document, không mở UI tạo nhánh lớp trong trang tổ.

Annual vẫn khai báo theme name/abbr/lịch như phần chủ đề cũ; không hiển thị viết tắt thành cột tick ở phần mục tiêu, không yêu cầu gán content-theme tick để ban hành. TEAM giữ tick theo từng content (`themeKeys`), mục tiêu đi theo; không tự tick cả goal. Chỉ annual được dùng nhập toàn bộ cây/Bộ/nguồn vào phần curriculum; TEAM INHERITED tắt các đường thay cây, server từ chối payload qua đường vòng. Giữ các giới hạn file hiện hành.

## 2. Workflow

GET/PUT `/education-plans/workflow-policy`: map keys `year` (TEAM), `schoolYear` (SCHOOL_YEAR), `branch`, `theme`; body `{revision,policies}` như hiện có. Mỗi policy creators/reviewers không rỗng, không trùng và tách soạn/duyệt. Chỉ HT hiệu lực cấu hình; roles khác vẫn xem theo quyền hiện tại.

Default schoolYear PHT→HT; year TL→PHT, branch/theme giữ defaults và mọi cấu hình đã có. Old PUT ba key được xử lý như cập nhật ba key cũ, giữ schoolYear đang lưu, tuyệt đối không xóa/reset key mới. New PUT bốn key kiểm expected revision và reconcile đúng các key đổi. Unknown key reject. Projection `signers.year` cũ vẫn TEAM; không đổi publication JSON đã ban hành.

## 3. Duration policy

| Interface | Request / response |
| --- | --- |
| GET /education-plans/duration-policy | `{revision,theme:{minWeeks,maxWeeks},branch:{minWeeks,maxWeeks},canConfigure,updatedAt}` trong data |
| PUT cùng path | body `{revision,theme:{minWeeks,maxWeeks},branch:{minWeeks,maxWeeks}}`; response như GET |

Chỉ HT assignment hiệu lực được PUT; GET trong scope quyền xem cấu hình giáo dục, canConfigure=false cho PHT. Từng giá trị integer>0, min≤max. Stale revision không ghi; không dùng workflow revision. Default theme2..4/branch1..2. UI hiển thị trong cấu hình giáo dục nhưng không gộp quyền ghi với PUT config chung. Snapshot limits trong hồ sơ readonly, không nhận global-config hiện tại làm validation cho hồ sơ cũ.

## 4. Class docs lịch v2

Giữ GET `/education-plans/class-docs?schoolYearId=`, PUT `/{kind}/{docKey}`, POST actions; giữ headers `X-Class-Doc-Version`, `X-Dossier-Version` hiện có và `Idempotency-Key` tại các endpoint đã áp dụng, không đổi thành X-Request-Id. Keep docKey/ID/approvals/history/review metadata.

Branch PUT v2 thêm `schemaVersion=2`, `themeSchedules:[{themeId,startDate,endDate}]`, `branches:[{key,themeId,sourceTemplateId,name,startDate,endDate,weekNotes}]`. weekNotes là map offset tuần học0..n-1 trong nhánh, giữ riêng notes của từng tuần khi nhánh2tuần/reorder. Keys UUID, sourceTemplateId chỉ được tham chiếu mẫu TEAM của đúng class/yearPlan. GV được giữ hoặc sửa name bản nhánh lớp mà không sửa mẫu nguồn; không bắt buộc lấy đủ mẫu, GV được không lấy/bỏ từng mẫu theo Q4=A. Source ID/cha phải đúng ownership; GV thêm nhánh sourceTemplateId=null. `names`/`notes` response là projections theo Monday, v2 serializer bỏ hai fields này; nếu client gửi projection sai so với branches thì từ chối, không chọn dữ liệu cuối theo thứ tự field. V1 adapter giữ notes[Monday] tại offset0 của nhánh tuần legacy, không gộp.

Branch legacy v1 vẫn đọc được; trước rollout client v2, write v1 chỉ giữ logic lịch TEAM cũ và bảo vệ mẫu nguồn nếu có. Khi một hồ sơ có v2 range/lịch riêng, write v1 bị conflict yêu cầu tải client mới, không flatten làm mất range. GET không ghi lại DB. Adapter cũ không gom các tuần trùng tên; mỗi tuần vẫn một nhánh legacy. Submit v2 kiểm đầy đủ mọi tuần theo lịch lớp, duration snapshot, nguồn và ngày; dở dang nháp vẫn lưu theo EDU-23, structural/source/ownership invalid không bao giờ lưu.

Theme/week GET thêm read-only `effectiveCalendar`/`calendarRevision` và mỗi tuần có branchKey/name/range; source `yearPlanId` tiếp tục là TEAM ID. New reads luôn trả calendarRevision từ branch version và source revision để client hiển thị nhất quán. Week vẫn `weekStart` Monday, không dùng ngày bắt đầu nhánh làm khóa chung cho hai tuần.

Mọi save/action/version fix/preview/dossier grouping dùng cùng calendar resolver theo class+TEAM, cùng lock/version. Lock order cho mutation liên quan: settings khi cần → TEAM/source plan → branch doc → theme dossier root → week docs (ID tăng); không khóa ngược ở action/reconcile/media. Không thêm global lock cho mọi lớp. Quyền reviewer/teacher tiếp tục EDU-54; PHT tạo template annual không có nghĩa PHT được viết branch lớp.

### Lấy mẫu tùy chọn và preview đổi thứ tự

`branches=[]` là nháp hợp lệ. Không có endpoint bắt lấy mẫu hoặc field mandatory; chọn mẫu tạo entry local key mới với sourceTemplateId hợp lệ, tên được sửa; bỏ chọn xóa entry local, không sửa source. Không lấy template nào vẫn được tạo đủ lịch bằng custom/import. Submit kiểm phủ tuần của lịch lớp, không kiểm phủ danh sách mẫu PHT. Với existing key, không được tráo sourceTemplateId/themeId qua rename; thay lựa chọn tạo entry mới.

`POST /education-plans/class-docs/branch/calendar-preview` (đề xuất mới, không ghi DB) dùng `ClassCalendarPreviewRequest {classId,yearPlanId,expectedBranchVersion,draftCalendar,orderedThemeIds}`. draftCalendar gồm themeSchedules/branches (kèm weekNotes) của form; orderedThemeIds là full permutation. expectedBranchVersion=-1 cho chưa lưu, version hiện tại cho record đã có; server kiểm scope/creator/TEAM issued, trạng thái nháp, version và source identity như save rồi tính bằng EduTeachingCalendar. Không ghi history/ledger/revision khi preview, không tạo branch doc nếu chưa có. Response `ClassCalendarPreviewResponse {calendar,countsBefore,countsAfter,calendarRevision}`; countsBefore/After có maps themes theo themeId và branches theo UUID, values số tuần học; calendar có dates mới, key/source/theme/name/weekNotes giữ nguyên, names/notes projections theo Monday được sinh lại.

`PUT .../branch/{docKey}` thêm optional non-persisted `orderedThemeIds`. Có field này thì server chạy helper trên draftCalendar nhận trong payload và lưu output, kiểm counts trước/sau, không tin dates do FE tự tính. Không có field thì full-save lịch theo FR-010/015 như cũ, không cấm user resize hợp lệ. Cùng helper phải idempotent với calendar đã preview; mọi lỗi giữ form/DB cũ, không partial persist. UI chỉ apply preview vào draft+dirty và gửi PUT khi Lưu/Ctrl+S. Serializer v2 bỏ names/notes/calendarRevision/operation metadata readonly, không echo projections cũ gây xung đột; orderedThemeIds được đọc rồi loại khỏi persisted content.

Algorithm/giới hạn ở data-model mục4.1: phân lại **các slot Monday học đang sử dụng**, giữ số tuần/offset nhánh, bỏ nghỉ trọn tuần, không tự lấp/nén gaps. Phép đổi không biểu diễn được bằng range mà giữ counts thì trả EDUCATION_CALENDAR_INVALID kèm lý do, không âm thầm thay thời lượng. Thiếu ngày để tính cũng báo lỗi rõ; vẫn lưu nháp qua luồng thường được khi chưa hoàn thiện. Không thêm rule phủ kín mọi tuần năm học.

Mọi preview/import/save response FE phải ràng buộc document identity (class+TEAM+source version) và **local form revision toàn payload**, không chỉ DB calendarRevision: rename/chọn-bỏ mẫu/restore/source switch/reorder/đóng modal trong khi chờ làm response không còn áp dụng được. Save đang chạy thì chỉ đánh sạch payload đã gửi; phần user sửa tiếp giữ dirty/backup. Không tự lấy lại mẫu đã bỏ hoặc đè tên vừa sửa.

## 5. Import/export và bản in

`POST /education-plans/goals/file-preview`: thêm optional planLevel (default TEAM) để resolve đúng author policy; preview không persist. Annual preview có thể giữ shape cũ nhưng không apply ticks. TEAM INHERITED không apply thay cây, file không được mở loophole. Legacy TEAM còn dùng nhập cũ theo quyền.

`POST /education-plans/class-docs/branch/file-preview`: giữ file/classId/yearPlanId và matches cũ; thêm optional multipart `draftCalendar` JSON v2 (themeSchedules/branches đang trên form, kiểm scope/shape/source trước preview, không ghi). Nếu có draftCalendar thì match theo lịch form, nếu không theo lịch hiệu lực đã lưu. Response thêm calendarRevision/schemaVersion và mỗi match `operation,branchKey,sourceTemplateId,weekStarts,startDate,endDate` ngoài row/themeId/themeName/name/weekStart cũ. `RENAME` có branchKey hiện hữu, giữ key/source/dates, đổi tên local theo xác nhận; nhánh hai tuần là một match có hai Monday. `CREATE_CUSTOM` dùng tuần học trống của lịch form, branchKey=null/sourceTemplateId=null, UUID tạo đúng một lần khi FE apply. Mặc định một dòng tên vào một tuần trống như logic cũ; nếu snapshot limits không cho một tuần thì cảnh báo để GV ghép/chỉnh trước gửi, không lấy thời lượng từ file hoặc tự ghép hai tên giống nhau. Vì vậy form branches rỗng/không chọn mẫu vẫn import được. Match không duy nhất hoặc không đủ slot thì warning, không đoán nguồn. FE dùng identity+local form revision để không apply response muộn sau rename/reorder/chọn mẫu. Preview có thể đổi tên bản lớp lấy từ mẫu hoặc custom, không sửa mẫu nguồn hoặc tự chọn lại templates. Giữ EDU-47 bỏ ngày/số tuần trong file và xác nhận trước apply; file không thay lịch hiện có. Export/in hiện range/số tuần học và đủ các week docs.

## 6. Projection nội bộ cho observation

Thêm `EduPlanCalendarQueryService.findApprovedThemes(Long classroomId)` → `List<EffectiveTheme>`; fields `themeId, lineageId, teamPlanId, name, startDate, endDate`. Chỉ đọc TEAM hiện hành và lịch lớp đã được duyệt; mọi lớp chưa có branch APPROVED có lịch riêng (gồm legacy và branch còn DRAFT/REVIEW) fallback lịch TEAM, không để nháp lịch lớp đổi kết quả đánh giá live. Caller kiểm scope trước, không endpoint mới, không truyền repository/entity xuyên module. Giữ `EduLessonQueryService.findApprovedDayLessons` và `findGoalLineages` contract cũ để đánh giá ngày/phiếu cuối năm không thay shape.

Ba native queries trong ThemeEvaluationRepository cần TEAM filter; issuer vẫn nguồn TEAM, không lấy HT ban hành annual vào ô ký đánh giá. Đánh giá chưa chốt dùng projection live. List themes union các CONFIRMED đã lưu của đúng lớp theo lineage với themes hiện hành; khi cùng lineage, bản frozen ưu tiên. GET/link cũ tìm saved CONFIRMED trước, rồi mới resolve live; ID theme lịch sử được resolve lineage từ TEAM lịch sử cùng năm/tuổi sau kiểm scope. Theme bị bỏ trong TEAM mới vẫn đọc được theo saved lineage/ID cũ. childThemeResults hợp nhất frozen của từng lớp trẻ từng học, không chỉ duyệt themes hiện hành; period/enrollment/goal columns của bản frozen lấy snapshot đã lưu, không resolve lại từ lịch/goal mới. Không đổi công thức hoặc endpoint đánh giá.

Khóa tuần không đổi convention hiện tại: version1 `wp_<class>_<Monday>`, version>1 `wp_<class>_<Monday>_<TEAM-planId>`. Một nhánh hai tuần dùng hai Monday/docKeys, không thay suffix bằng branch UUID, không chuyển khóa/học liệu đã có khi preview/reorder.

## 7. Lỗi đề xuất và atomicity

Mã sau chưa tồn tại trong ApiCode tại thời điểm nghiên cứu; phải kiểm tra trùng khi implement và cập nhật snapshot/consumer cùng thay đổi, không đổi số mã đã phát hành.

| Code / enum dự kiến | HTTP | Khi nào |
| --- | --- | --- |
| 7040 PLAN_LEVEL_INVALID | 400 | level/key không hợp lệ hoặc nguồn sai cấp |
| 7041 CURRICULUM_SOURCE_INVALID | 409 | nguồn chưa ban hành/khác scope/không hợp tạo TEAM |
| 7042 INHERITED_CURRICULUM_READ_ONLY | 409 | thay cây nguồn bằng PUT/import/omission |
| 7130 BRANCH_SOURCE_INVALID | 409 | source ID/ownership không hợp lệ; đổi tên bản nhánh lớp hợp lệ không phải lỗi |
| 7131 EDUCATION_CALENDAR_INVALID | 409 | duration/ngày/coverage/overlap sai, desc nêu loại và giới hạn áp dụng |
| 7132 EDUCATION_CALENDAR_STALE | 409 | payload lịch cũ/v1 không giữ được v2 |
| 7210 DURATION_POLICY_INVALID | 400 | limits sai |
| 7211 DURATION_POLICY_STALE | 409 | revision cấu hình cũ |

403 quyền/scope, 409 state/lease/revision và lỗi file giữ mã hiện có khi đúng nghĩa. Mọi reject không thay DB/revision/history/author ledger; foreign IDs không được tiết lộ nội dung nguồn ngoài scope. JSON mới giới hạn keys/range theo năm học và dung lượng request hiện có, không có vòng lặp tạo tuần ngoài năm. Không đổi ResponseJson hay auth contract.

### Delta nguồn user — FE review 07/11/2026
- Mẫu nhánh/dự án được nhập tên ngay trong cột phần 2 annual, Enter thêm dòng. start/end mẫu nullable; không bắt lịch để ban hành annual khi chỉ gợi ý tên. Khi GV lấy mẫu về lớp thì chọn lịch theo snapshot limits và parent bounds. TEAM giữ cột map mục tiêu.
- FR018/EDU57: canDelete=false và DELETE TEAM bị từ chối nếu actor có vai trò PHT; không đổi quyền xóa các cấp/chức năng khác.

## Delta additive displayLabel — user approved07/11/2026

POST/PUT goals: domains[].rows[].displayLabel optional string max100, chỉ goal. Update null/omitted giữ nhãn, empty/whitespace clear về null; create null/omitted là mặc định. GET detail/POST/PUT response domains[].rows[].displayLabel nullable string; goalNo giữ Short, ID giữ nguyên. Legacy request không field không xóa nhãn. TEAM có nhãn nguồn và từ chối chỉnh nhãn khác; status/lease/scope không đổi. Không endpoint mới, không ApiCode mới. FE annual/TEAM và export đọc field; APP tương thích additive, không sửa APP.

## FR021 — validation lịch nhánh07/11/2026
Không thêm field hoặc endpoint. Draft cho sửa khoảng ngày; submit/publication kiểm khoảng đầy đủ của các nhánh cùng themeId không trùng inclusive và Thứ Hai–Thứ Sáu; sai trả EDUCATION_CALENDAR_INVALID hiện có. Mẫu name-only giữ hợp lệ, partial vẫn theo validation publish/duration hiện có.
