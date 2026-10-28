---
title: Lấy mục tiêu từ kế hoạch năm có sẵn
status: done
owner: CareNest team
jira: null
branch: feature/G94-226-BE-FEAT-09-school-year-goals
modules: [education-plan]
rules: [EDU-03, EDU-06, EDU-09, EDU-10, EDU-22, EDU-23, EDU-24]
risk: medium
created: 2026-10-28
updated: 2026-10-28
---
# Lấy mục tiêu từ kế hoạch năm có sẵn

Làn L vì tác động Web từ repo BE và >8 file dự kiến; thiết kế chi tiết tại [Spec Kit plan](../../../specs/003-import-year-plan-goals/plan.md), [spec](../../../specs/003-import-year-plan-goals/spec.md). Đã triển khai source Web theo chỉ đạo vòng lặp; E2E thật còn chờ credential, chưa done. Jira riêng chưa được user cung cấp, không suy từ task trên branch; cần chốt nếu có yêu cầu commit/PR.

## Context

- FE ImportGoalsModal.jsx:13–18 chỉ lọc nguồn đã có trong provider, :21 dùng vị trí làm khóa, :36–40 mất hierarchy khi trả items.
- FE educationPlanApi.js:59–64 chỉ đọc page0 size200 và mọi detail; GoalFormPage.jsx:491–519 chép cả lịch/ticks và append goal trần.
- Các file trên ở src/components/education-plan/, src/services/education-plan/api/, src/pages/education-plan/ của CareNest_FE; đường dẫn và bằng chứng đầy đủ trong [research](../../../specs/003-import-year-plan-goals/research.md).
- BE EduYearPlanController.java:49/70 đã có filter + list/detail; giữ PUT full document và edit headers tại :100. Không sửa source BE/schema/OpenAPI.

## Quyết định đã chốt

1. User chọn A/A, 28/10: chỉ Phó HT lấy khác nhóm tuổi; Tổ trưởng nguồn cùng nhóm; chỉ chép mục tiêu/nội dung, không chủ đề/đợt nghỉ/dấu tích (EDU-24).
2. Giữ luồng sửa bước 1–2–3, manual save/Ctrl+S, lease/revision, nguồn độc lập sau copy.
3. Không đổi permission server, API/DTO, dependencies, migration, auth hoặc layout chung; không tạo branch/commit/push trong task.

## Làm rõ nghiệp vụ

- Actor: Phó HT/Tổ trưởng đang có quyền sửa đích; Outcome: chọn nguồn khác năm/nhóm trong quyền, chọn một/tập con/tất cả và thêm đúng vào bản soạn.
- Rule: EDU-03/06/09/10/22/23/24 đều CONFIRMED; flow [S] bước2 → chọn nguồn/MT → thêm vào form → Lưu → gửi/ban hành theo luồng cũ.
- Đối chiếu: code hiện chép themes/breaks/ticks, yêu cầu EDU-24 mới đã xác nhận bỏ hành vi này. Không gọi đây là contract BE mới.
- Ảnh hưởng: Web GoalFormPage/ImportGoalsModal và facade/API/mock đọc nguồn; APP không thuộc scope; BE chỉ tài liệu. Dữ liệu cũ giữ nguyên.

### Câu hỏi mở

Không còn câu hỏi nghiệp vụ hoặc kỹ thuật chặn thiết kế; hai câu trả lời đã nằm trong spec/EDU-24. User đã chỉ đạo triển khai theo vòng tasks→analyze→implement→converge ngày 28/10/2026; tiếp tục qua checkpoint sau review nội bộ. Constitution Spec Kit chưa ban hành, dùng AGENTS và rule hiện hành làm cổng bắt buộc.

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc |
| --- | --- | --- | --- | --- |
| Tổ trưởng dùng nguồn khác nhóm? | Không; giữ quyền hiện tại | User | 2026-10-28 | EDU-24, FR-007 |
| Chép kèm gì? | MT và nội dung, không lịch/chủ đề/ticks | User | 2026-10-28 | EDU-24, FR-008 |

## Acceptance criteria

- **AC-1** (EDU-24): Given nguồn hợp lệ khác năm/tuổi, When lọc độc lập header, Then đọc đủ nguồn/trang và không đổi đích.
- **AC-2** (EDU-06/24): Given Phó HT/Tổ trưởng, When lấy nguồn khác nhóm, Then import đúng A/A; server giữ phạm vi xem vốn có.
- **AC-3** (EDU-24): Given có goal, When chọn từng goal/theo lĩnh vực/tất cả/reset, Then số lượng đúng và zero không xác nhận.
- **AC-4** (EDU-09/24): Given nguồn nhiều sub/group, When lấy tập con, Then đúng context/order/ID mới và ticks mới rỗng.
- **AC-5** (EDU-24): Given đích có hoặc chưa có chủ đề, When lấy và lưu, Then nguồn, themes/breaks/ticks cũ và metadata đích không bị đổi.
- **AC-6** (EDU-24): Given trùng/domain thiếu/context không biểu diễn được, When ghép, Then báo added/duplicates/unavailable, không bịa cha hoặc bỏ âm thầm.
- **AC-7** (EDU-06/24): Given nguồn đổi/xóa/mất quyền hoặc lỗi tải, When xác nhận, Then không ghép snapshot cũ, giữ form, cho xem/chọn lại.
- **AC-8** (EDU-10/24): Given đổi lọc/đóng modal/confirm đôi/mất lease, When response về, Then không callback cũ hoặc ghép hai lần.
- **AC-9** (EDU-22/24): Given đã lấy nhưng chưa lưu, When rời/Hủy/lưu lỗi, Then guard/khôi phục đúng, không tự mutation HTTP; save–reopen đúng, wizard cũ.
- **AC-10** (EDU-24, SC-006): Given 200 goals đã tải, When thao tác chọn, Then phản hồi ≤1s, nhãn/focus/bàn phím và responsive dùng được.

## Key decisions

| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Đọc nguồn | Facade riêng dùng GET sẵn, summary đủ trang + detail theo chọn | Đổi getAll toàn cục; API import mới | Scope nhỏ, không N+1/giới hạn trang0, không đổi contract |
| Ghép | Helper thuần giữ rows/cha, ID mới, ticks rỗng | items trần; copy lịch/ticks | Đúng EDU-09/24, không gán sai cha |
| Concurrency | useAsync + query key/generation confirm + fresh detail | Tin cache; khóa nguồn | Không callback cũ, không chặn người soạn nguồn |

Không thêm ADR vì dùng kiến trúc hiện hành. Chi tiết giới hạn vai trò kép/provenance, legacy context ở research; không hứa quyền import riêng hoặc giao dịch nguyên tử trên BE.

## Phases

### Phase 1 — Đọc nguồn đúng phạm vi, đủ trang (5 file)
- M: facade educationPlanService.js, API educationPlanApi.js, mock educationPlanMockRepository.js, test year-plan-api.test.mjs; C: test import-goal-sources.test.mjs (tất cả trong CareNest_FE).
- Steps: test RED → list/detail methods, mapping refs, all pages, scope mock/read-only/cache isolation → GREEN.
- Tests: AC-1/2/7; case size/pages/filter, 403/404, actor giả nhóm khác và detail không sửa nguồn/cache.
- Exit: tại FE, node scripts/verify.mjs → VERIFY PASS full; node scripts/check-ai-layer.mjs exit0 nếu sửa docs. **Review nội bộ và tiếp tục vòng lặp theo chỉ đạo user 28/10/2026.**

### Phase 2 — Ghép mục tiêu và nội dung độc lập (2 file)
- C: models/education-plan/importGoals.mjs và tests/education-plan/import-goals.test.mjs ở FE.
- Steps: RED fixtures nhiều cha/trùng/domain thiếu/orphan → helper chọn theo ID, clone tổ tiên đúng, new IDs, clear ticks → GREEN.
- Tests: AC-4/5/6, source/target deep-compare và merge→normalize/reopen so context cả đích cũ/nguồn mới (ca first-sub), dedupe cả batch, không sinh cha rỗng hoặc ID số nguồn.
- Exit: FE verify đầy đủ đạt; snapshot mapper test chứng minh old IDs giữ và new IDs gửi null. **Review nội bộ và tiếp tục vòng lặp theo chỉ đạo user 28/10/2026.**

### Phase 3 — Modal, tích hợp form, E2E (4 source/test + docs Web)
- M: ImportGoalsModal.jsx, GoalFormPage.jsx, tests/e2e/system/edu-plan-flows.spec.mjs; C: hooks/education-plan/useGoalImportSources.js; cập nhật feature doc Web dẫn EDU-24.
- Steps: RED interaction → hook useAsync/key guards → modal rows/count/errors/fresh-confirm → handler merge current form → E2E thật và docs.
- Tests: AC-3/7/8/9/10; parent kiểm quyền/lease lại dù modal portal còn mở; không đổi provider/getAll/save hook/normalizer chung.
- Exit: FE verify + E2E toàn luồng đạt, BE verify full skip0, AI layer hai repo exit0; ghi output thật. **Review nội bộ và tiếp tục vòng lặp theo chỉ đạo user 28/10/2026.**

## Test matrix

| AC | Rule | Test (dự kiến) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| 1,2,7 | EDU-06/24 | year-plan-api + import-goal-sources (node), generic GET scope E2E | 1 | Chưa triển khai |
| 4,5,6 | EDU-09/24 | import-goals (node), mapper request assertions | 2 | Chưa triển khai |
| 3,7,8,9,10 | EDU-10/22/24 | edu-plan-flows (Playwright), timing/keyboard/error scenarios | 3 | Chưa triển khai |

## Rủi ro & rollback

| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Vai trò kép teacher/Tổ trưởng | Nhầm filter UX với quyền BE mới | Giữ generic scope, test import nhóm đích; ghi giới hạn provenance | Hoàn tác riêng methods/UI import, không sửa policy |
| Flat hierarchy/ID cũ | Gán sai cha hoặc xóa dữ liệu khi full PUT | Clone context/IDs mới, giữ toàn bộ đích, báo unavailable khi không biểu diễn | Khôi phục đúng diff feature, không restore toàn repo |
| Late response/lease mất | Ghép sai nguồn hoặc form cũ | Snapshot/generation/current-parent guard; BE Save vẫn kiểm revision | Bỏ đường confirm mới, giữ form hiện có |
| >8 file | Khó review và regression rộng | Chia 3 phase ≤8 file/phase như trên, không chia thành kiến trúc mới | Hoàn tác từng phase đã xác nhận phạm vi |

## Validation log

- [x] Source claims có file:line trong research và Context; API/caller cụ thể đã đối chiếu.
- [x] Không đổi HTTP contract; contract doc chỉ mô tả dùng lại list/detail/save và interface nội bộ FE.
- [x] Mỗi AC có test dự kiến; mọi phase có exit và dừng review; chưa ghi test dự kiến thành pass.
- [x] >8 file đã chia 3 phase; không thêm scope, dependency, hạ tầng hoặc security policy mới.
- [x] Chỉ rule CONFIRMED; không placeholder trong artifacts hoặc câu hỏi nghiệp vụ còn mở.
- [x] Review finding first-sub reparent đã tái hiện qua helper hiện có; bổ sung guard unavailable và regression round-trip trong thiết kế.
- [x] Pre/post design gate dùng AGENTS/rules; constitution template không được coi đã ban hành.

## Progress log

### 2026-10-28 — Thiết kế Phase 0–1 Spec Kit (chờ duyệt triển khai)
- Chưa commit; chỉ tạo tài liệu plan/research/data-model/contract/quickstart và plan này.
- Verify: chờ lệnh kiểm tài liệu/backend sau lần ghi cuối, kết quả báo trong chat; không phải bằng chứng tính năng mới.
- Khác plan: không có. Tiếp: user đã chỉ đạo tiếp tục tasks/analyze/implement/converge; ghi nhận duyệt triển khai từ chỉ đạo này, không tự thay đổi business decisions.

### 2026-10-28 — User chỉ đạo triển khai vòng lặp
- Nguồn duyệt: tin nhắn user yêu cầu sau plan tiếp tục tasks → analyze (tự sửa lỗi) → implement → converge, lặp tới sạch và giữ lịch sử tasks.
- Chạy tiếp qua các phase sau review nội bộ theo chỉ đạo mới; không tạo nhánh/commit/push.
- Verify nền trước triển khai: VERIFY PASS full | tests 410 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok.

### 2026-10-28 — Implement bước đọc nguồn, ghép và UI
- 59/59 test:edu; browser mock pass chọn nguồn khác năm/tuổi, chọn một/all/reset, giữ source/target và manual Save–reopen; review tích hợp không P1/P2.
- Khác plan: suite E2E riêng year-plan-import.spec.mjs để không phụ thuộc chuỗi serial cũ; scope/AC giữ nguyên. Task IDs và lịch sử giữ nguyên.
- Tiếp: E2E thật, verify tổng thể, converge append-only; chưa báo hoàn thành tính năng.

### 2026-10-28 — Converge pass 1, chờ real-BE E2E
- Code gaps=0 sau đối chiếu 15FR/6SC/21AC/7edge/10design; evidence gap V001 MEDIUM: E2E chưa chạy vì thiếu credential. Append-only thêm T021, giữ nguyên bytes task cũ.
- FE verify full đạt; BE VERIFY PASS full | tests 410 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 123s. Mock browser happy + 7 edge cases đạt; review nguồn không có finding P1/P2/P3 đã chứng minh.
- 19/21 tasks hoàn tất; T019/T021 mở, không báo converged hoặc done. Tiếp tục implement/converge khi E2E_DEV_PASSWORD có trong môi trường; không gửi secret qua chat.

- E2E retry sau user đặt file: config đọc được, setup fail token Phó HT invalid_grant; actual account enabled, không required actions. T019/T021 chờ mật khẩu khớp tài khoản, chi tiết quickstart.

- E2E cuối 28/10: 13 passed (1.1m), không fail/skip, đúng DB runtime. T019/T021 hoàn tất; 21/21 tasks. Chờ cổng bàn giao cuối và converge read-only, không còn vướng credential.

### 2026-10-28 — Done / converged
- 22/22 tasks; no-write converge: 15FR/6SC/21AC/7edge/10design, actionable gaps=0; ledger hash không đổi trong converge, không thêm phase rỗng.
- E2E sau hardening: 13 passed (1.0m), zero fail/skip; fixture import còn lại 0. FE: VERIFY PASS full | npm run verify | log verify.log.
- BE: VERIFY PASS full | tests 410 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 153s. Không sửa source Java/OpenAPI; không commit/push.
- Plan chuyển completed; quickstart và quality verification lưu bằng chứng, mọi lịch sử task/failed attempts vẫn giữ.
