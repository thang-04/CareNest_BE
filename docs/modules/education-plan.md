# Module — education-plan

> **Quyết định mới user 10/11/2026 — EDU-72:** Bỏ thao tác lập kế hoạch tổ lần đầu. Khi kế hoạch năm được ban hành, hệ thống tự tạo một TEAM DRAFT cùng năm học/nhóm tuổi, gắn phiên bản năm đã ban hành mới nhất và giao tổ trưởng duy nhất đang phụ trách tuổi đó soạn. User làm rõ “chỉ có một tổ trưởng của một nhóm tuổi, gửi cho người đó soạn”. Quyết định này thay phần “không tự lập tổ/giữ nút lập tổ/chọn nguồn thủ công” ở EDU-62 và tài liệu09/11; các quy tắc giữ nội dung đang soạn, rà soát và giữ tổ ban hành cũ làm nguồn lớp vẫn áp dụng. Đây là chỉ đạo user, không suy là xác nhận mới từ giáo viên trường.


> **Quyết định user10/11/2026 — duyệt hồ sơ lớp:** Theo chuỗi HT cấu hình, không bắt buộc có bước TT; chỉ PHT duyệt cũng hợp lệ. Giữ cấp trên duyệt cấp dưới, chống tự duyệt, phạm vi phân công và REVIEW cấm sửa. Quyết định này thay yêu cầu TT bắt buộc trước PHT/HT trong tài liệu09/11; chưa phải bằng chứng triển khai/nghiệm thu.

## Mốc nghiệp vụ hiện hành — ghi nhận09/11/2026

[12 câu trả lời đã chốt](../business/education-plan-confirmation-2026-11-09.md): user xác nhận đã được giáo viên phía trường xác nhận; chưa cung cấp tên/ngày xác nhận trực tiếp. Tài liệu mô tả nghiệp vụ đích, không dùng mức CONFIRMED để suy delivery. Ưu tiên phần này và [flow hiện hành](../business/flows/education-plan.md) khi đọc các delta/lịch sử cũ bên dưới.

- Năm → tổ → nhánh lớp → kế hoạch chủ đề → tuần/ngày → đánh giá. Tổ theo nhóm tuổi, dùng chung hai điểm trường; mỗi loại/năm/tuổi một chuỗi, tối đa một bản nháp/chờ.
- Năm biên soạn cây; tổ chọn một annual APPROVED hiện hành cùng năm/tuổi và tick từng nội dung. Theo EDU-65 PROPOSED (user trực tiếp 09/11), tổ trưởng được thêm/sửa nội dung riêng của tổ, không sửa nguồn năm; GV thấy nội dung tổ theo phạm vi và trạng thái hiện hành. Lập tổ vào form trực tiếp, nhập tên riêng; không tự lập tổ lần đầu.
- EDU-66 CONFIRMED + EDU-52 (Tiền 10/11/2026): khi gửi năm mọi chủ đề và mẫu nhánh đã khai báo phải đủ ngày bắt đầu/kết thúc; nháp vẫn có thể thiếu ngày. Mọi điều kiện bắt buộc khi ban hành chặn ngay từ bước gửi: `EduYearPlanServiceImpl.requireReadyToIssue` (= `validateForPublication` + `requireReadyForReview` + `requireThemeDates`) chạy ở cả `submitYearPlan` và `issueYearPlan`.
- Theo EDU-67/68 PROPOSED (user trực tiếp 09/11), xung đột sửa nội dung năm/tổ giữ bản tổ và báo rà soát; phiên bản năm điều chỉnh chụp cấu hình thời lượng hiện tại, snapshot mất chỉ khôi phục khi có bằng chứng và giữ trạng thái.
- Theo EDU-69/70 PROPOSED (user trực tiếp 09/11), kiểm 9 chủ đề/nội dung/tick khi gửi; duyệt theo thứ bậc cố định, một người được chỉ định soạn, bỏ hẳn trạng thái/nhãn Đang sửa, lease 90 giây và heartbeat 20 giây khỏi luồng nghiệp vụ và UI; vẫn giữ revision/chống ghi đè. Các mô tả lease cũ bên dưới phản ánh lịch sử hoặc source hiện tại, không phải nghiệp vụ đích; chưa chứng minh đã gỡ khỏi code.
- Theo EDU-71 PROPOSED (user trực tiếp 09/11), hồ sơ mất giới hạn hoặc chưa phân biệt được legacy thật phải bị chặn gửi và báo kiểm tra; chỉ khôi phục khi có bằng chứng, giữ trạng thái. Legacy thật được xác nhận giữ ngoại lệ thời lượng cũ và vẫn kiểm đủ ngày.
- Theo EDU-70, PHT soạn năm → chỉ HT duyệt; TT soạn năm hoặc tổ → PHT hoặc HT duyệt; GV soạn hồ sơ chủ đề lớp → chuỗi theo cấu hình HT, không bắt buộc bước TT; chỉ PHT duyệt cũng hợp lệ (user10/11). HT phân công một người soạn theo phạm vi; không tự duyệt hoặc cấp dưới duyệt cấp trên. REVIEW cấm sửa, cuối chuỗi ban hành; không dùng lease. Phần HT cấu hình chuỗi tùy ý và TT duyệt năm/tổ trong quyết định lịch sử đã được EDU-70 thay thế.
- Năm mới ban hành tự chuyển nguồn tổ nháp/chờ (giữ phần nhập, chờ về nháp); tổ đã ban hành có bản cập nhật nháp, giữ bản ban hành cũ cho lớp tới khi cập nhật duyệt. Đỏ phần đổi/xóa/liên kết ảnh hưởng; mục mới riêng để phân bổ, chủ đề kết thúc không đỏ; baseline là nguồn tổ dùng trước chuyển.
- Không ai xóa tổ kể cả nháp; chỉ điều chỉnh và giữ lịch sử. Chỉ năm hiện phiên bản, tổ giữ lịch sử nội bộ.
- Mẫu nhánh tùy chọn nhưng gửi phải phủ mọi tuần học bằng nhánh có tên; nhánh nhiều tuần có đủ giáo án từng tuần. Năm nhập file hai chế độ thay toàn bộ/thay phần có, preview/xác nhận nháp/lưu tay; tổ không nhập file sửa cây.

## Trạng thái triển khai tách khỏi mức xác nhận

| Phần | Source hiện thấy | Phần chưa kiểm chứng/còn thiếu |
|---|---|---|
| Hai cấp SCHOOL_YEAR/TEAM, kế thừa readonly/tick, policy ba trạng thái, lease/revision | Có BE/FE | Không suy toàn feature012 đã nghiệm thu; còn gap nguồn lớp/quyền preview/cổng duyệt |
| Mẫu nhánh, duration snapshot, lịch lớp v2 | Có source | Chi tiết nghiệm thu xem quickstart012, không dùng mô tả “chưa code” lịch sử để phủ nhận source |
| Giáo án duyệt → đánh giá trẻ/lineage | Có EduLessonQueryService và consumer observation | Chưa smoke runtime trong lượt tài liệu |
| Nhãn chờ duyệt đúng loại/tên và ẩn phiên bản tổ | Còn gap FE | Theo EDU-62/spec015 |
| Tự chuyển nguồn/tạo bản cập nhật/rà soát tổ | Chưa đủ luồng | Theo EDU-62/spec015, giữ bản cũ cho lớp |
| Hai bảng vật lý năm/tổ | Entity còn chung edu_year_plans, V89 local chuẩn bị | Chưa cutover/V90; không chạy migration lượt này |
| TT duyệt năm/tổ; nguồn hồ sơ lớp TEAM; preview annual; bất biến JSON nội dung lớp | Có gap tĩnh ở controller/service | Cần sửa/test BE theo biên bản, không đổi rule theo code |
| AI backend | Chưa hoàn tất | Mock/proposed không phải tích hợp thật |

## Delta và chi tiết kỹ thuật theo thời điểm

Lịch sử đặc tả trước bộ xác nhận09/11 — EDU-62, [feature015](../../specs/015-separate-year-team-plans/spec.md): chờ duyệt phải đúng tên/loại năm và tổ; chỉ năm hiện phiên bản, tổ giữ lịch sử nội bộ. Năm mới ban hành mới rà soát tổ, giữ bản tổ cũ cho lớp tới khi cập nhật được duyệt. Chỉ đạo cuối giữ nút lập tổ, vào trang soạn trực tiếp không modal, chọn một nguồn năm đã ban hành hiện hành cùng năm/tuổi và nhập tên riêng; list có tên tổ/nhóm tuổi/ngày gửi/người soạn/trạng thái. User yêu cầu tách bảng nghiệp vụ năm/tổ; source hiện vẫn chung `edu_year_plans`, xem [DB hiện tại/đích](../../specs/015-separate-year-team-plans/database-context.md). Đây là delta chưa hoàn tất source/cutover; V89 local chuẩn bị không là bằng chứng đã migrate; user09/11 đã chốt hai ca: tổ đang làm chuyển nguồn mới nhất giữ phần nhập, bản chờ về nháp/gửi lại; chủ đề đã kết thúc không đỏ. Spec sẵn sàng plan làn L, chưa có plan được duyệt. Không dùng phương án modal hoặc tự lập tổ đã bị user thay thế. Lỗi nhãn user báo được ghi [incident](../knowledge/incidents/BUG-261109-year-team-approval-identity.md), chưa xác minh nguyên nhân.

User 08/11/2026 yêu cầu cảnh báo thời lượng chủ đề và nhánh ngay khi nhập lịch, trước khi gửi duyệt; dùng giới hạn snapshot và giữ lưu nháp. [Incident](../knowledge/incidents/BUG-261108-duration-feedback-late.md).

Hiển thị trạng thái EDU-54 theo yêu cầu user 08/11/2026: hồ sơ đã gửi duyệt hiển thị “Chờ duyệt”; `REVIEW` và các mã chờ cũ được tính vào số chờ. Mã API và bước xử lý không đổi. [Incident](../knowledge/incidents/BUG-261108-education-review-label.md).

Audit nguồn Bộ 08/11/2026: [BUG-261108-ministry-source-split](../knowledge/incidents/BUG-261108-ministry-source-split.md) open — catalog cũ tách sai một câu có khoảng “5 - 7” và bỏ33 dấu hai chấm; [delta migration draft](../../specs/014-curriculum-cascade-suggestions/source-correction-plan.md) chờ duyệt. Chưa sửa dữ liệu runtime.

Thiết kế014 ngày08/11: [plan](../../specs/014-curriculum-cascade-suggestions/plan.md) draft dự kiến FE-only, BE catalog/provenance đủ dữ liệu, không migration/API/dependency;3increment modal→inline→E2E. Bug filter đã có bằng chứng lỗ hổng source Web (xem incident), chưa runtime/fixed. User đã cấp quyền sửa giáo dục FE014 ngày08/11/2026; user đã duyệt vòng implement→converge plan014; delta sửa catalog bằng migration có plan riêng chờ duyệt, không xin lại quyền FE.

Delta đặc tả 08/11/2026 — EDU-61: lọc cả nguồn/đích theo lĩnh vực → lĩnh vực con → kết quả mong đợi → mục tiêu; bỏ lựa chọn sai, giữ dữ liệu đã thêm; bấm ô mở gợi ý rồi gõ lọc; nội dung theo nhóm lấy từ mục tiêu mục D. Xem [feature014](../../specs/014-curriculum-cascade-suggestions/spec.md). Known pitfall user báo: [mục tiêu đích còn sau đổi bộ lọc](../knowledge/incidents/BUG-261108-curriculum-target-filter.md), chưa tái hiện/fix. Chỉ đặc tả, chưa sửa source.

Delta đặc tả08/11/2026 — EDU-60: năm chỉ xem từ quyền lập tổ khi đã ban hành; HT/PHT có phân công hiệu lực mọi nhóm tuổi, tổ trưởng/GV chỉ tuổi được giao (user làm rõ sau analyze). Giữ Q1=A về quyền soạn/duyệt năm độc lập, scope tuổi không mở trạng thái; source TEAM vẫn cùng năm/tuổi đích. Mục tiêu nguồn trong tổ hiển thị đủ, không cuộn riêng card; giữ nguồn readonly/tick. Xem [feature013](../../specs/013-published-year-plan-visibility/spec.md) và [plan013](../../specs/013-published-year-plan-visibility/plan.md). Đã triển khai source và nativeverify theo [quickstart013](../../specs/013-published-year-plan-visibility/quickstart.md); chưa restart/deploy API user hoặc live Keycloak E2E.

Feature: `eduplan` (`docs/architecture/PACKAGE_STRUCTURE.md`) · Status: đang code (mục tiêu năm học, cấu hình, tài liệu lớp xong BE, G94-226; AI chưa làm)

## Mục đích
Soạn/duyệt kế hoạch giáo dục theo chuỗi năm→tổ→lớp và nối nguồn giáo án đã duyệt với đánh giá trẻ. Năm biên soạn mục tiêu/nội dung và dự kiến chủ đề; tổ phân bổ từng nội dung bằng tick trên cây kế thừa; lớp triển khai lịch, hoạt động, tuần/ngày. Policy và trạng thái theo mốc hiện hành ở đầu card, không dùng luồng TT/PHT đồng soạn cũ.

## Owns
- Kế hoạch năm: `EduYearPlan` (kèm khóa Đang sửa), `EduPlanSubDomain`, `EduPlanExpectedResult`, `EduPlanGoal`, `EduGoalContent`, `EduTheme`, `EduBreak`, `EduContentThemeTick`, lịch sử `EduPlanEvent`.
- Danh mục và cấu hình: `EduDomain`, `EduSettings` (chuỗi người ký, dòng hoạt động tuần).
- Tài liệu của lớp: `EduClassDoc`, một bảng cho chủ đề nhánh, kế hoạch chủ đề, giáo án tuần + 5 kế hoạch ngày. Khóa, trạng thái là cột thường có ràng buộc; nội dung, người duyệt, lịch sử là JSONB (bảng tuần đổi theo cấu hình dòng, luôn đọc cả tài liệu).

## KHÔNG owns
Năm học, nhóm tuổi, lớp, phân công (school-structure); tài khoản/role (identity-access); kết quả đánh giá trẻ theo MT (learning-observation — chỉ đọc giáo án đã duyệt của module này).

## Rules (ID)
EDU-01..28, EDU-39..43, EDU-46..64 (các ID có trong BUSINESS_RULES; lịch chủ đề, lấy chủ đề, nhập file mục tiêu/nhánh; `docs/business/BUSINESS_RULES.md`); EDU-29..38 (khảo sát team 27/10 từ `dev`, đổi số khi merge; EDU-31/32/35/38 SUPERSEDED theo code — xem flow); OBS-07, PAR-01 (EDU-37), AI-07, AUTH-03, AUTH-23. ADR-0020 (số cũ ADR-0017), ADR-0021, ADR-0018, ADR-0006, ADR-0003; phân quyền động: ADR-0017. ADR-0019 của `dev` (làm lại trên `dev`) lệch với việc đã merge nhánh này — chờ user chốt.

## Flows
`docs/business/flows/education-plan.md`. Sequence mục tiêu năm học SD-01..SD-10: `huong_dan_nghiep_vu/sequence/muc_tieu_nam_hoc_sequence.drawio` (ngoài repo).

## Phụ thuộc
- Dùng: school-structure (`SchoolStructureService`, `StaffAssignmentService`), identity-access (`AccountService.findFullNames`), `security/authorization`; AI qua `integration/` (khi làm gợi ý chủ đề nhánh, tiến trình giờ học). Không đọc repository của module khác.
- Được dùng bởi: learning-observation (đọc MT, hoạt động của giáo án đã duyệt — EDU-19).

## API & bảng
- Có: `GET/POST /education-plans/goals`, `GET/PUT/DELETE /education-plans/goals/{planId}`, `POST /education-plans/goals/{planId}/submit|issue|return|start-editing|finish-editing|editing-heartbeat`, `GET/PUT /education-plans/config` (cấu hình: lĩnh vực, người ký, dòng tuần — PUT cho HT, PHT, quyền `plans.config`) (`docs/api/openapi.yaml`).
- Có: `GET /education-plans/class-docs?schoolYearId=` (tài liệu + lớp + giáo viên/tổ trưởng trong phạm vi người xem), `PUT /education-plans/class-docs/{kind}/{docKey}` (giáo viên của lớp lưu; tạo mới nếu chưa có), `POST /education-plans/class-docs/{kind}/{docKey}/actions` (`submit|approve|reject` + `note`). `kind` = `branch|theme|week`; `docKey` do Web đặt theo lớp: `bp_<lớp>_<kế hoạch năm>`, `tp_<lớp>_<chủ đề>`, `wp_<lớp>_<thứ Hai>`, BE kiểm khớp.
- Có: `POST /education-plans/goals/file-preview` (multipart `file`, EDU-46): đọc file bảng mục tiêu thành bản xem trước, không lưu — `EduGoalFileService(Impl)`, `GoalFileGrid` (.doc nhị phân qua POI HWPF; .docx/.xlsx/.doc HTML/CSV giữ bộ đọc JDK), `GoalFileTable` (diễn giải). .doc mã hóa/hỏng bị từ chối; fixture tổng hợp kiểm tương đương .docx, ô gộp, giới hạn file/dòng.
- PROPOSED (AI, chưa làm): `POST /education-plans/ai/branch-names`, `/ai/branch-names/rewrite`, `/ai/lesson-steps`.
- Có: `POST /education-plans/class-docs/branch/file-preview` (EDU-47): giáo viên lớp preview file, kiểm kế hoạch đã ban hành/hiện hành và tài liệu sửa được; ghép tên vào teachingWeeks ở server, trả matches/warnings, không lưu. `BranchFileTable` dùng cùng `GoalFileGrid` (POI cho .doc); không nhận lịch/người ký file. Web chỉ áp key khớp sau xác nhận, giữ ghi chú/key khác và bỏ pending AI ở key thay; hai nút luôn hiện, khóa kèm lý do theo quyền/trạng thái, giữ chuỗi EDU-18.
- Code: `EduYearPlanController`, `EduYearPlanService(Impl)`, `EduConfigService(Impl)`, `EduClassDocController`, `EduClassDocService(Impl)`, quyền ở `security/authorization/EduPlanAccessPolicy` (EDU-06, EDU-08), job nhả khóa `EduPlanLockCleanupConfig`.
- PUT kế hoạch năm gửi cả cây: dòng/nội dung/chủ đề có id thì cập nhật, không id thì thêm, vắng thì xóa; nội dung tích chủ đề qua `themeKeys` trỏ `themes[].key` (chủ đề mới chưa có id).
- Bảng `edu_*`: `docs/database/DATA_DICTIONARY.md`.

- Đã xong: EDU-54 (06/11/2026): Cấu hình người tạo/duyệt theo loại hồ sơ cho giáo dục (`GET/PUT /education-plans/workflow-policy`), chỉ Hiệu trưởng cấu hình; tách soạn/duyệt theo loại; 3 trạng thái duy nhất `DRAFT` → `REVIEW` → `APPROVED`; `REVIEW` cấm sửa/cấm lease; duyệt bước cuối tự Ban hành và đóng băng `print_snapshot` (ô ký, vai trò, tên người); Flyway V80 expand và V81 contract siết trạng thái. Đã kiểm chứng toàn diện 1,104 test BE PASS, 199 test FE PASS, Spotless GREEN, 0 lỗi AI layer.

## PENDING — triển khai, không phải câu hỏi nghiệp vụ đã trả lời

- EDU-62/spec015: nhãn loại/tên, ẩn phiên bản tổ; tự cập nhật nguồn/rà soát theo quyết định09/11, tách bảng vật lý và bảo toàn liên kết lớp chưa hoàn tất.
- Các gap controller/service trong bảng trạng thái đầu card cần regression trực tiếp. EDU-19 đã có source đọc giáo án duyệt, không còn ghi pending “chỉ mock”. AI BE EDU-20 vẫn chưa hoàn tất.
- Các ghi nhận feature012 draft07/11 là lịch sử thiết kế; source đã có hai cấp, guard, duration, templates và lịch v2. Chỉ phần chưa đáp ứng/kiểm chứng mới ghi pending; xem quickstart012 và biên bản09/11. Không suy CONFIRMED là đã deploy.
- Nhập nhánh EDU-47 đã có source/evidence feature008; không hỏi lại các lựa chọn đã chốt.

## Nhập đầy đủ kế hoạch năm — mở rộng EDU-46 (02/11/2026)
User đã duyệt [feature009](../../specs/009-full-year-plan-file-import/spec.md). Quyết định mới nhất02/11 thay luồng chỉ bổ sung bằng hai chế độ Web: thay toàn bộ (phần thiếu trống) hoặc chỉ thay nguyên phần file có (phần thiếu giữ). Giữ năm/khoảng ngày hệ thống, field cố định, tuổi, quyền/lease và manual save; remap tick khi chủ đề thay. Backend preview/parser/API/DB không đổi trong delta này; hai chế độ FE đã implement/verify phase2 sau user duyệt tài liệu, đang chờ review trước convergence; E2E BE thật chưa chạy do thiếu credential kiểm thử. Parser YearPlanFileTable phối hợp metadata ngoài bảng với GoalFileTable.

## Known pitfalls

- Khoảng ngày chủ đề/nhánh phải wrap theo cột ở cả năm/tổ; không ép nowrap cho range hoặc trigger. Xem [lỗi tràn ngày](../knowledge/incidents/BUG-261108-theme-date-column-overflow.md).
- Lịch sử EDU-48 trước cập nhật05/11 (không dùng làm mặc định hiện hành): thao tác xóa lĩnh vực con ở Web phải bỏ toàn bộ nhánh sau xác nhận số lượng; không chỉ bỏ heading rồi normalize vì con có thể đổi cha. Contract PUT vẫn cho phép giữ/chuyển các dòng client cố ý gửi; xem delta feature006, chưa coi là đã implement khi plan mới draft.

- Tên lĩnh vực trong .doc có thể dùng kĩ/kỹ, mĩ/mỹ; chuẩn hóa khóa so tên, không đổi nội dung người dùng. [Regression feature009](../knowledge/incidents/BUG-261102-year-plan-sample-incomplete-preview.md).

- ID hàng mục tiêu và nội dung không chung miền; test/ghép dữ liệu phải so cùng loại thực thể, không cấm mọi trùng số giữa hai bảng identity. [Regression](../knowledge/incidents/BUG-261101-e2e-goal-id-domains.md).

- File nhỏ có thể khai tọa độ/span rất lớn; kiểm dòng/cột trước cấp phát trong decoder, không chờ interpreter. [Regression](../knowledge/incidents/BUG-261101-file-grid-allocation-limits.md).

- Mock CSV nhập nhánh phải bỏ nhãn Nhánh n/dòng nghỉ trước khi chiếm tuần như parser BE; không dùng fixture chỉ có tên sạch. [Regression](../knowledge/incidents/BUG-261101-branch-csv-demo-slots.md).

- V35 backfill với FK deferred phải SET CONSTRAINTS ALL IMMEDIATE trước DDL; [incident](../knowledge/incidents/BUG-261029-hierarchy-deferred-ddl.md). Lưu chuyển cha phải flush trước khi xóa tiêu đề cũ để CASCADE không xóa MT còn giữ.

- Thiết kế ba bảng cha–con (user, 29/10/2026): yêu cầu tách lĩnh vực con, nhóm kết quả mong đợi, mục tiêu thành ba bảng vật lý có FK; user chọn giữ cấp cha tùy chọn để bảo toàn dữ liệu đang có. Source dùng ba entity/repository và V35 chuyển dữ liệu từ `edu_goal_rows`; `EduGoalRow` chỉ là mapped superclass. Migration giữ ID/lineage, API giữ rows/kind. Xem phần bổ sung DB trong [plan mục tiêu năm](../plans/active/2026-10-22-muc-tieu-nam-hoc.md).
- Status do BE quyết định. Không nhận status từ client.
- Approval JSON legacy có thể chứa `at` dạng giờ trường `yyyy-MM-dd HH:mm`; giữ parser tương thích với `Instant` ISO mới và mốc giờ `Asia/Ho_Chi_Minh` cũ ([BUG-261106-edu-legacy-approval-time](../knowledge/incidents/BUG-261106-edu-legacy-approval-time.md)).
- Khóa Đang sửa (EDU-10): mọi thao tác ghi đọc kế hoạch bằng `findForUpdateById` (khóa dòng) để hai người bấm sửa cùng lúc không cùng lấy được khóa; khóa quá hạn coi như trống ngay cả khi job (1 phút/lần) chưa chạy.
- Tài liệu lớp: lịch tuần (`teachingWeeks`) phải tính giống Web (`yearWeeks` trong `CareNest_FE/src/models/education-plan/classPlan.js`): bỏ tuần trùng đợt nghỉ, chủ đề bắt đầu T7/CN thì từ Thứ Hai kế tiếp; lệch thì giáo án tuần bị từ chối hoặc gửi bị chặn.
- Gửi / duyệt hồ sơ chủ đề đổi trạng thái cả kế hoạch chủ đề và giáo án các tuần nhánh trong cùng giao dịch (khóa dòng tài liệu chính).
- MT đánh số lại khi xóa hoặc chèn dòng; module khác tham chiếu MT qua id dòng, không qua số.
- Kế hoạch đã ban hành bị khóa sửa, vì lớp dùng chủ đề, đợt nghỉ và MT của nó; sửa = tạo phiên bản mới (EDU-25), không sửa dòng của bản đã ban hành.
- So sánh phiên bản dùng lineage hiệu dụng `COALESCE(lineage_id, id)`; MT đánh lại số không phải là đổi. Tra "hiện hành" bằng truy vấn chuỗi, không lưu cờ.
- Thứ tự chủ đề, đợt nghỉ = thứ tự mảng trong PUT (`sort_order`); dấu tích lưu theo chênh lệch, không xóa JPQL rồi ghi lại ([BUG-261023-edu-theme-tick-lost-on-resave](../knowledge/incidents/BUG-261023-edu-theme-tick-lost-on-resave.md)).
- Kế hoạch chủ đề lưu luôn MT, nội dung lấy từ kế hoạch năm để bản đã duyệt không đổi theo (EDU-15).
- Lịch sử EDU-18 trước EDU-54 (không áp dụng như policy hiện hành): chuỗi duyệt hồ sơ lớp chụp lúc gửi vào `edu_class_docs.sign_chain` (V41); duyệt/trả lại đi theo chuỗi đã chụp, đổi cấu hình giữa chừng không làm hồ sơ tự APPROVED. Hồ sơ cũ `sign_chain` NULL: dùng cấu hình; cấu hình đã bỏ bước đang chờ thì giữ bước đó + các bước cấu hình chưa duyệt. Đang chờ Tổ trưởng mà nhóm tuổi hết Tổ trưởng (hoặc Tổ trưởng dạy chính lớp) ⇒ `getClassDocs` hiện và `actOnClassDoc` duyệt ở bước kế (DB chỉ đổi khi có người thao tác); chuỗi chỉ còn bước Tổ trưởng thì vẫn chờ tới khi HT phân công Tổ trưởng mới.
- Hai chỗ comment FE lệch code, làm BE theo code: mock ghi chủ đề nhánh "Phó HT duyệt" nhưng chuỗi mặc định là Tổ trưởng → Phó HT; gợi ý người ký `week` ghi "mỗi tuần duyệt riêng" nhưng code duyệt cả hồ sơ chủ đề một lần (EDU-17).
- Mã YCCĐ (TC1.1…) trong `educationPlanConstants.js` của FE chỉ còn để đọc dữ liệu cũ; không dùng cho dữ liệu mới (EDU-09).

- Lấy mục tiêu giữ context cả sau normalize/mở lại: thêm sub đầu tiên vào lĩnh vực đích vốn không-sub có thể đổi cha goal cũ; xem [case](../knowledge/incidents/CASE-261028-year-plan-import-first-sub-reparent.md). Helper import mới đã có guard và regression round-trip.
- Khóa chống gửi trùng nhận ở header `Idempotency-Key` (UUID), không dùng `X-Request-Id`: proxy hosting VPS luôn đặt `X-Request-Id` thành ULID ⇒ 9002 cho mọi thao tác ghi; local/test không có proxy nên không lộ ([BUG-261102-proxy-overrides-x-request-id](../knowledge/incidents/BUG-261102-proxy-overrides-x-request-id.md)).

## Related issues
- [CASE-261027-activity-media-positional-identity](../knowledge/incidents/CASE-261027-activity-media-positional-identity.md): không ghép học liệu theo ID vị trí đơn lẻ.
- [BUG-261023-edu-theme-tick-lost-on-resave](../knowledge/incidents/BUG-261023-edu-theme-tick-lost-on-resave.md)
- [BUG-261102-proxy-overrides-x-request-id](../knowledge/incidents/BUG-261102-proxy-overrides-x-request-id.md): 9002 trên VPS do proxy ghi đè X-Request-Id.

## Đọc thêm khi
Đổi quyền hoặc phân công Tổ trưởng ⇒ `docs/business/USER_ROLES.md`, ADR-0003. Đụng đánh giá theo MT ⇒ card learning-observation, ADR-0006.

## Lưu mục tiêu năm (Tiền duyệt 27/10/2026)

EDU-10/22/23: phiên sửa riêng từng tab, TTL 90s và heartbeat 20s; cùng tài khoản gọi start-editing bằng phiên mới thì nhận quyền ngay (`EduYearPlan.acquireSession`), phiên cũ nhận 409 ở heartbeat/lưu, người khác vẫn 409 tới khi hết hạn; FE chỉ ghi khi bấm Lưu/Ctrl+S hoặc gửi/ban hành (bỏ tự lưu 2s/15s, Tiền chốt 27/10), kiểm revision và request ID chống lưu trùng. DRAFT giữ trạng thái khi giữ lease; REVIEW cấm lease/sửa theo EDU-54; draft giữ nội dung chưa hoàn chỉnh, gửi/ban hành mới kiểm tra đầy đủ. V30 bổ sung phiên/revision; contract hiện hành cần X-Edit-Session, X-Plan-Revision, Idempotency-Key. Bản phục hồi Web riêng theo user/kế hoạch/slot; không trộn hay ghi đè khi xung đột. Chi tiết: [đặc tả](../../specs/001-year-plan-autosave/spec.md).

## Học liệu hoạt động (Tiền yêu cầu, 27/10/2026)
Mọi binary ảnh/video/slide/tệp lưu S3 private, metadata riêng trong edu_learning_resources; backend scope check, không public bucket/URL. Không đổi MT/chuỗi duyệt EDU-12..18. Sửa hoạt động làm fingerprint không khớp thì cần gắn lại. Chi tiết [feature002](../../specs/002-class-plan-teaching-media/spec.md), [S3](../system/EDUCATION_MEDIA_S3.md), ADR-0018. Tích hợp S3 thật cần cấu hình và smoke riêng.

## Mã lỗi (chuẩn ERROR_CONTRACT, merge daily-attendance 28/10/2026)
Mọi lỗi của module ném `GlobalException.of(ApiCode.X)`; response có `errorCode` 4 số, Web/App phân biệt bằng mã, không so `desc`. Dải: 70xx kế hoạch năm (7006 `Đang được {0} sửa`, 7020 mất phiên sửa, 7021 revision cũ, 7022 chờ duyệt chỉ Phó HT sửa…), 71xx tài liệu lớp + học liệu (7115–7120), 72xx cấu hình; tệp/S3 dùng 80xx (8003 kho lỗi, 8004 loại tệp, 8005 quá cỡ, 8006–8013). Thiếu part multipart/header ⇒ 9003. Danh sách đầy đủ: `ApiCode.java`, `docs/contracts/ERROR_CONTRACT.md`.

## Phiên bản kế hoạch năm, kế hoạch điều chỉnh (Tiền chốt 28/10/2026)

EDU-25/26/27: mỗi phiên bản là một dòng `edu_year_plans` (`version_no`, `source_plan_id`); dòng con chép giữ `lineage_id` (NULL = chính id). `POST /goals/{id}/versions` tạo phiên bản n+1, `GET /goals/{id}/changes?fromPlanId=` so sánh theo lineage (`YearPlanVersionDiff`). Tài liệu lớp tách theo `year_plan_id`; khóa tuần phiên bản ≥2 `wp_<lớp>_<thứ Hai>_<id kế hoạch>`; bản chép nhớ `source_doc_id`, chặn gửi 7121 khi còn ô đỏ, ghi trên phiên bản cũ 7122. Ô "Kế hoạch điều chỉnh và bổ sung" ở cột `adjustments`, `PUT /class-docs/week/{key}/adjustments`. V32. Chi tiết: [feature004](../../specs/004-year-plan-versioning/spec.md).

## Lấy mục tiêu từ kế hoạch có sẵn (user chốt 28/10/2026)

EDU-24: quyền lấy mục tiêu theo EDU-06/24 hiện hành; TT được lấy bản đã ban hành khác tuổi theo delta30/10, không suy quyền này cho nguồn tạo TEAM (bắt buộc cùng năm/tuổi). Tích từng mục tiêu hoặc tất cả trong một nguồn; chỉ chép mục tiêu/nội dung, không chép chủ đề, đợt nghỉ, lịch hoặc dấu tích. Giữ dữ liệu đích, bản nguồn và luồng sửa ba bước. Source Web đã triển khai, 13/13 E2E thật đạt và T019/T021 hoàn tất: [feature003](../../specs/003-import-year-plan-goals/spec.md).

## Lấy mục tiêu từ Chương trình của Bộ (Tiền chốt 28/10/2026)

EDU-28: danh mục chỉ đọc nạp bằng V33/V34 (`edu_curriculum_*`) từ VBHN 01/VBHN-BGDĐT: 327 mục tiêu (nhà trẻ 41, 3–4 tuổi 84, 4–5 tuổi 99, 5–6 tuổi 103) kèm nội dung, 423 gợi ý nội dung (mục C). API `GET /education-plans/curriculum/{sources,goals,contents}`; kế hoạch năm gửi/trả `curriculumGoalId` (+ `curriculumGoalText`, `curriculumSectionRef`), sai nhóm tuổi ⇒ 7030. V37: danh mục 3 tầng (`edu_curriculum_sub_domains` > `edu_curriculum_expected_results` > `edu_curriculum_goals`); `/curriculum/goals` thêm `subDomainId`, `expectedResultId`, `/curriculum/contents` thêm `subDomainId`; dòng sub/group gửi `curriculumSubDomainId`/`curriculumExpectedResultId`, trả thêm `curriculumText` (câu gốc), cùng kiểm 7030. Lấy vào kế hoạch làm ở Web như EDU-24 rồi lưu tay. Công cụ tách và kiểm: specs/005-ministry-goal-catalog/research.md.

## Nhập file bảng mục tiêu (Tiền duyệt 01/11/2026)

EDU-46, spec `specs/007-year-plan-goal-file-import/`. Nhận Word .docx bảng mẫu Thái Dương (STT | Mục tiêu | Nội dung | cột chủ đề viết tắt), Word .doc do CareNest xuất (HTML), Excel .xlsx, CSV mẫu cũ; ≤ 2 MB, ≤ 2.000 dòng; mã lỗi 7033–7035. BE chỉ đọc (không lưu, không đổi DB, không thêm thư viện; XML chặn DOCTYPE, giới hạn giải nén). Đó là luồng lịch sử spec007/EDU-46 trước quyết định mới02/11; Web đã có hai chế độ thay nội dung spec009 ở source; trạng thái review/nghiệm thu xem bằng chứng bên trên, giữ lưu tay EDU-22. Nút lấy từ kế hoạch khác vẫn theo EDU-24, không bị đổi theo nhập file.
Lịch sử parser cũ: .doc nhị phân chưa đọc được; source hiện đã dùng POI HWPF cho .doc theo phần API bên trên, giữ kiểm giới hạn/tệp hỏng; Word mở file mẫu .doc rồi lưu lại có thể giữ dạng HTML — vẫn đọc được.

## Delta nhãn mục tiêu — 07/11/2026

EDU-58 theo yêu cầu user đã duyệt: domains[].rows[].displayLabel cho goal, max100; null/omitted giữ nhãn, blank clear về MTn. Giữ goalNo/ID; V86 additive; clone/version/annual→TEAM giữ nhãn và server chặn đổi nhãn TEAM. Web annual nhập/kéo tăng số, tổ chỉ xem và tick nội dung; in/export giữ nhãn. Native BE64/64/FE248/248, runtime V86/healthUP; chi tiết VERIFICATION. Schema Domain/Row/Content OpenAPI dùng tên riêng, xem incident BUG-261107-year-plan-openapi-row-collision.

EDU-59/FR021 (user07/11/2026): mẫu nhánh có khoảng ngày đầy đủ phải Thứ Hai–Thứ Sáu và không overlap inclusive sibling cùng themeId. Server kiểm chung submit/publication bằng EDUCATION_CALENDAR_INVALID; draft/name-only và bounds/duration giữ nguyên. Web luôn hai DatePickerCell compact dd/mm/yy - dd/mm/yy, taken/warnings, bỏ nhãn và thu gọn. BE71/71, FE254/254; xem VERIFICATION.


## Đối chiếu luồng năm học → tổ — user 08/11/2026

User tái nêu: kế hoạch năm học tạo mục tiêu/nội dung, kế hoạch tổ lấy nguồn đó để tick. Đây là tái xác nhận luồng EDU-55 đã chốt, không tự coi là quyết định mới đổi từ tick nội dung sang tick cả mục tiêu; quyết định làm rõ07/11 vẫn là tick từng nội dung, mục tiêu tương ứng đi theo.

Đối chiếu source08/11: Web GoalFormPage dùng GoalTable chung, annual truyền themes=[] và cho biên soạn; TEAM chọn annual APPROVED cùng năm/tuổi, inheritAnnual giữ cây nguồn, bỏ tick khi lấy lần đầu; readOnly/fixedCells khóa cây nhưng canAssignContents bật tick từng nội dung. BE InheritedCurriculumGuard kiểm ID/kind/text/nhãn/liên kết nguồn và cây content, không cấm themeKeys thay đổi. Thiết kế dùng chung UI với hai chế độ là đúng luồng; đánh giá UI không đề xuất mở biên soạn tại TEAM hay tách hai giao diện chỉ vì các thao tác khóa. Nguồn user trực tiếp, giữ mức nguồn của EDU-55; không sửa code/contract trong lượt đối chiếu.

Feature013 delivery08/11/2026 (EDU-60): đã có scope annual theo policy/status/assignment, riêng TEAM giữ hành vi cũ. UI readonly gồm header xám, phần3 nhánh+select Xem mục tiêu/nội dung từ tick, đầy đủ cây không inner scroll. Xem evidence/giới hạn ở [quickstart013](../../specs/013-published-year-plan-visibility/quickstart.md). Đổi schema name tài liệu để chống collision không đổi JSON; chưa restart/deploy API user hoặc chạy live Keycloak E2E.

## Quyền xem, hàng chờ, bản đã ban hành (spec 002 — 10/11/2026, EDU-73)

- Quyền ở `EduYearPlanServiceImpl`: `isAuthor` (đủ điều kiện soạn, không xét trạng thái) dùng cho xem; `isEditor` (= isAuthor, kế hoạch năm thêm điều kiện người soạn) + DRAFT dùng cho sửa; `isCurrentApprover` (vai trò đang chờ, loại người đã soạn). `canView` = isAuthor ∨ (APPROVED ∧ trong phạm vi) ∨ (REVIEW ∧ isCurrentApprover). Danh sách dùng `listScope` (người phụ trách mọi trạng thái nhóm tuổi mình; HT/PHT toàn trường APPROVED; GV/Tổ trưởng APPROVED nhóm tuổi được phân công).
- `GET /education-plans/goals/review-queue` → `List<YearPlanSummaryResponse>` kế hoạch năm/tổ REVIEW chờ đúng người gọi. Hồ sơ lớp: danh sách `class-docs` chỉ trả DRAFT/REVIEW cho GV chủ nhiệm và người duyệt của lớp.
- Chi tiết: `history`/`lastReturnNote` rỗng với người chỉ xem; `versions` lọc theo `canView` (cả TEAM). Hồ sơ lớp có `releases[] {id, version, releasedAt, approvedBy}`; `GET /education-plans/class-docs/releases/{id}` trả nội dung bản cũ.
- Mã lỗi 7384 `EDU_PLAN_NOT_RELEASED`.
- `EduClassDocFutureEdit.validateBranch/validateWeek` nhận tập chủ đề đã ban hành (`releasedThemes`: theme APPROVED hoặc có sự kiện `revise_future`); chủ đề chưa ban hành không bị khóa phần đã qua — sửa lỗi lưu chủ đề nhánh lần đầu bị 409 7117.
- Kiểm chứng: Playwright `CareNest_FE/tests/e2e/integration/edu-approval-visibility-api.spec.mjs` (4) và `system/edu-approval-visibility-ui.spec.mjs` (5) trên BE thật; JUnit `EduClassDocBranchReleaseLockTest`.

## Mốc duyệt theo thời gian dạy (spec 003 — 10/11/2026, EDU-74)

- Gốc gửi/duyệt mới trong `edu_class_docs` (V96): `semester` (gói học kỳ, `sp_<lớp>_<kế hoạch tổ>_<1|2>`, chuỗi duyệt policy `branch`) và `dossier` (hồ sơ chủ đề, `ds_<lớp>_<chủ đề>`, policy `theme`); `EduClassDoc.policyType/isRoot`. Thành viên (`theme`, `week`, `day`) mang cùng trạng thái gốc. Lịch nhánh (`branch`) không mang trạng thái duyệt; bản ban hành gói học kỳ chụp tên nhánh các tuần của kỳ.
- Giáo án ngày tách khỏi giáo án tuần: kind `day` (`dd_` + hậu tố của `wp_`) giữ `days`, `dayGoals`, `adjustments` (EDU-27); `week` từ chối `days`/`dayGoals`. `PUT /class-docs/day/{key}/adjustments` (giữ `/week/{key}/adjustments`, khóa `wp_` đổi sang `dd_`).
- ~~Cách gửi của chủ đề `theme.content.submitMode`~~ — bỏ ở spec 004 (EDU-75), BE bỏ qua nếu nhận được.
- Hạn tính khi đọc (`EduDeadline`, `EduSemesters`): gói = `semesterN_start`, hồ sơ = ngày bắt đầu chủ đề (lịch lớp, không có thì kế hoạch tổ); mở hạn = sự kiện `extend` trong `history`. `deadlineState`: NORMAL/NEAR (≤7 ngày)/OVERDUE/ON_TIME/LATE; hạn trước ngày mở lớp (`ClassroomService.findOpenedDates`) không quá hạn.
- `actOnClassDoc`: chỉ nhận `semester`/`dossier`; `submit` quá hạn ⇒ 7385 `CLASS_DOC_DEADLINE_PASSED`; hồ sơ khi gói học kỳ chưa từng ban hành ⇒ 7386 `SEMESTER_PACKAGE_NOT_RELEASED`; `extend` (HT/PHT mọi điểm trường, `note` + `deadline` > hôm nay, chỉ khi OVERDUE và DRAFT). `ClassDocActionRequest.deadline`.
- `GET /class-docs` thêm `semesterPackages`, `themeDossiers`, `dayPlans`, `semesterConfigMissing`; gốc có `deadline, extendedTo, daysLeft, deadlineState, everReleased, themeIds, semesterNo, branchNames, blockedReason, canExtend`; thành viên có `containerKey`, `containerStatus`. Người không soạn: `branch.names` ghép từ bản ban hành gói từng kỳ + `branchUnreleasedSemesters`.
- Thông báo (V97, `notifications.link`): `EDU_SUBMITTED`, `EDU_STEP_APPROVED` (người duyệt bước đang chờ), `EDU_RELEASED`, `EDU_RETURNED`, `EDU_DEADLINE_EXTENDED` (GVCN / người gửi kế hoạch năm, tổ), `EDU_DEADLINE_NEAR` (job `remindNearDeadlines` 06:00, một lần mỗi người + đường dẫn). Email: hoãn (chưa duyệt dependency).
- Known pitfalls: gói học kỳ đang Chờ duyệt khóa sửa/nhập lịch nhánh; thêm gói/hồ sơ mới được tạo khi lưu tài liệu của lớp (`ensureRoots`), không tạo khi chỉ đọc.

## Cách duyệt cố định, phiên bản và khóa theo thời gian (spec 004 — Tiền 11/11/2026, EDU-75/76/77)

- Thành phần cố định: `semester` (`sp_…`) = tên nhánh kỳ + **mọi** `theme`, `week` của chủ đề thuộc kỳ; `dossier` (`ds_…`) = chỉ `day`.
  `classThemePlans[]`/`weekPlans[]` có `containerKey` = khóa gói học kỳ; `dayPlans[]` = khóa hồ sơ chủ đề. Bỏ `submitMode`.
- `GET /class-docs`: `semesterPackages[]` thêm `missing: [{kind: theme|branch|week, themeId, weekStart}]` (rỗng ⇒ đủ gửi),
  `timeLocked`, `canRevise`; `themeDossiers[]` thêm `timeLocked`, `canRevise`; `canExtend` đúng cả khi gốc APPROVED đã `timeLocked`.
- `POST /class-docs/{kind}/{docKey}/actions`:
  - `submit` gói thiếu phần ⇒ 400 **7387** `SEMESTER_PACKAGE_INCOMPLETE` + `details` (danh sách mục thiếu).
  - `revise` (**mới**, GVCN, `semester`/`dossier` APPROVED, trước hạn hiệu lực, chưa có phiên bản dở): ghi sự kiện `revise`
    (kèm `previousApprovals`, `previousPrintSnapshot`), thành viên về DRAFT, bản ban hành cũ vẫn hiệu lực tới khi bản mới ban hành.
    Qua hạn ⇒ 409 **7388** `CLASS_DOC_TIME_LOCKED`; không APPROVED / đã có phiên bản dở ⇒ 409 `CLASS_DOC_LOCKED`.
  - `extend` (HT/PHT, nhãn UI "Mở khóa"): nhận gốc DRAFT quá hạn **hoặc** APPROVED `timeLocked`; `note` + `deadline` > hôm nay.
- `PUT /class-docs/{kind}/{docKey}`: thành viên của gốc APPROVED chưa `revise` ⇒ 409 `CLASS_DOC_LOCKED` (bỏ tự mở lại khi lưu);
  qua hạn hiệu lực ⇒ 409 7388. Hạn hiệu lực = `max(mốc, extendedTo)`; mốc gói = `semesterN_start`, hồ sơ = ngày bắt đầu chủ đề.
  `day` lưu được khi gói chưa ban hành (Q1), chỉ `submit` hồ sơ bị chặn (7386).
- Q7: gói học kỳ phiên bản mới ban hành ⇒ so `week` với `print_snapshot` bản trước; tuần đổi ⇒ `day` cùng `weekStart` (nếu APPROVED)
  và `dossier` chứa nó về DRAFT, sự kiện `week_changed {weeks:[…]}`, thông báo GVCN.
- Kế hoạch năm/tổ (`EduPlanTimeLock`): mốc = `school_years.start_date`; hạn hiệu lực = `max(start_date, deadline` của sự kiện `unlock` mới nhất).
  Từ hạn: `submit`/`issue` bản chưa từng ban hành, `POST /goals/{id}/versions`, tạo/nhận bản cập nhật tổ (EDU-62/72) ⇒ 409 **7389**
  `PLAN_TIME_LOCKED` (bản cập nhật tổ tự động thì bỏ qua); REVIEW vẫn duyệt/trả lại. `GET /goals/{id}` có
  `timeLock {deadline, extendedTo, deadlineState, timeLocked, canUnlock}`.
- **Mới** `POST /api/v1/education-plans/goals/{planId}/unlock` body `{note, deadline}` — chỉ Hiệu trưởng; thiếu lý do ⇒ 400
  `RETURN_REASON_REQUIRED`; hạn ≤ hôm nay ⇒ 400 `INVALID_VALUE`; khác HT ⇒ 403. Ghi `edu_plan_events(action='unlock', deadline)` (V98),
  thông báo người soạn `EDU_DEADLINE_EXTENDED`.
- Web: `/education/theme-plans/:classId?hk=1|2` (gói học kỳ), `/education/day-plans/:classId?week=|theme=` (hồ sơ chủ đề); đường dẫn cũ
  `/education/class/:id`, `/class/:id/semesters/:no`, `/class/:id/weeks/:ws/days` chuyển hướng.
- Dữ liệu thử tài liệu lớp trên DB dev `carenest_merge` đã xóa 11/11/2026 (Q8, không có migration V99); sao lưu
  `local-backups/carenest_merge_class_docs_20261111.sql`.
- Known pitfalls: sửa kế hoạch chủ đề/tuần đã ban hành phải `revise` gói học kỳ trước (lưu thẳng ⇒ 409); khóa theo thời gian chặn cả
  lưu lẫn gửi, chỉ mở khóa mới làm tiếp; `missing` chỉ tính tuần học (bỏ tuần nghỉ cả tuần, EDU-41).
