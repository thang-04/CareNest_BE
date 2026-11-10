---
title: AI gợi ý thực đơn bán trú có cổng giá trị
status: in-progress
owner: Thangnd04
jira: G94-200 / BE-FEAT-28   # nhánh hiện tại; mã Jira riêng cho AI thực đơn chờ user cung cấp trước commit đầu tiên
branch: feature/G94-200-BE-FEAT-28-ai-assessment-drafting
modules: [ai-assistance, nutrition]
rules: [AI-01, AI-02, AI-03, AI-04, AI-05, AI-06, AI-07, AI-08, AI-09, AI-10, NUT-05, NUT-06, NUT-08, NUT-09, NUT-19, NUT-20, NUT-23, NUT-24, NUT-25]
risk: high
created: 2026-11-10
updated: 2026-11-10
---
# AI gợi ý thực đơn bán trú có cổng giá trị

Artifact: `specs/014-boarding-menu-ai/` (domain spec, spec, research, data model, contract, plan, tasks). Thiết kế đã duyệt: `docs/superpowers/specs/2026-11-09-boarding-ai-quality-gate-design.md`.

## Context
AI-07 (4) bắt buộc gợi ý kế hoạch tuần. Bán trú đã có `MealPlanEvaluator` (`service/meals/impl/MealPlanEvaluator.java:245`) và cổng công bố (`MealPlanServiceImpl.java:167-170`), chưa có API/bảng nháp AI. Nhánh AI vừa merge `dev` (`cf9008e`) và gỡ slice nhận xét cũ + RAG (`d6a4d87`); `integration/ai` còn adapter một provider chỉ phục vụ nhận xét (`integration/ai/AiClient.java`). Web có `AiWeekComposer`/`AiTemplateComposer` chạy dữ liệu demo.

## Quyết định đã chốt
1. Cổng giá trị: cổng cứng + điểm AI ≥ baseline — user, 09/11/2026.
2. Chuỗi provider theo task + loại dữ liệu; thực đơn Gemini Flash → Groq; dữ liệu trẻ chỉ provider không train — user, 09/11/2026.
3. Gọi đồng bộ, deadline ~30s, baseline luôn có — user, 09/11/2026.
4. Baseline = cách làm không AI + cùng bước code sửa — user, 09/11/2026.
5. Nháp ở bảng `menu_ai_drafts` thuộc nutrition — user, 09/11/2026.
6. Code trên nhánh AI G94-200, merge `dev` — user, 10/11/2026 (đã làm).

## Làm rõ nghiệp vụ
- **Hiểu nghiệp vụ:** PHT/HT có `MENU_MANAGE` · nhận phương án tuần dùng được nhanh hơn soạn tay · AI-01..10, NUT-08/09 (CONFIRMED/ACCEPTED) · `[S]` gợi ý → xem → dùng/bỏ → công bố như cũ.
- **Đối chiếu thiết kế:** flow `meal-management.md` mục "AI gợi ý thực đơn" ghi nháp ở `ai_drafts` theo ADR-0018 — ADR-0018 là media S3 ⇒ sửa tham chiếu, ADR-0024 mới. Thiết kế 09/11 bước 2 lọc dị ứng nặng — mâu thuẫn NUT-23 và evaluator (`MealPlanEvaluator.java:772-796`, INFO) ⇒ bỏ lọc.
- **Ảnh hưởng:** nutrition (đọc món/định mức/tuần, ghi tuần qua service sẵn có), ai-assistance (viết lại port). Quyền `MENU_MANAGE` toàn trường. Không dữ liệu dẫn xuất mới. FE: `FE: /menu-planning` (3 màn). APP: không. Dữ liệu cũ: không.

### Câu hỏi mở
Không có câu hỏi nghiệp vụ chặn. Giả định agent chọn (chờ soát): A1 trọng số điểm + baseline theo mẫu ngày; A2 nháp mẫu ngày không ghi bảng mẫu; A3 không lọc dị ứng (`domain-spec.md` mục 14).

## Acceptance criteria
- **AC-1** (`NUT-09`, `NUT-24`): Given danh mục đủ món cho khối, When PHT tạo nháp tuần, Then mọi ô dùng món ACTIVE đúng loại bữa, đủ dữ liệu, và issue khớp evaluator chạy trên chính lưới đó.
- **AC-2** (`AI-01`, `NUT-05`): Given model trả alias lạ/sai ô/thiếu ô/JSON hỏng, When kiểm, Then không lưu nháp `source=AI` từ output đó.
- **AC-3** (`AI-07`, `NUT-08`): Given AI còn `BLOCKING` sau mọi vòng sửa hoặc điểm < baseline, Then nháp `source=RULE_BASED` với `fallbackReason` `AI_INVALID`/`AI_NOT_BETTER`; `source=AI` chỉ khi qua cổng cứng và điểm ≥ baseline.
- **AC-4** (`NUT-09`): Given cả AI và baseline không qua cổng cứng, Then `status=NO_FEASIBLE` kèm nguyên nhân theo ô.
- **AC-5** (`AI-02`, `NUT-08`): Given nháp tuần `PENDING_REVIEW` và tuần chưa công bố, When apply, Then chỉ ô của khối chọn đổi, khối khác giữ, tuần vẫn chưa công bố; apply lần hai trả cùng kết quả, không ghi lại.
- **AC-6** (`NUT-08`): Given tuần PUBLISHED, hoặc dữ liệu đổi làm phát sinh `BLOCKING`, When tạo/apply, Then 409; người thiếu `MENU_MANAGE` ⇒ 403.
- **AC-7** (`AI-03`, `AI-05`): Given AI tắt, thiếu key, 429/timeout ở mọi provider, Then vẫn trả nháp baseline hoặc `NO_FEASIBLE` trong deadline; log chỉ metadata, bảng không có prompt/response thô.
- **AC-8** (`AI-04`, `AI-09`): Given task `CHILD_DERIVED` cấu hình provider `may-train-on-data=true`, Then ứng dụng không khởi động; 429 provider 1 ⇒ provider 2.
- **AC-9** (`AI-07`): Nháp mẫu ngày và thay một món theo contract; thay món chỉ đổi đúng ô của tuần chưa công bố.
- **AC-10** (`AI-07`): Eval tay 8 case × 3 lần: không lọt `BLOCKING`; AI thắng baseline ≥ 60% case hợp lệ trước khi bật cho demo (báo cáo trung thực nếu không đạt).

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Port AI | `AiGateway` tổng quát + task config | Giữ `AiClient` theo method mỗi task | Thêm sức khỏe/giáo án không sửa adapter |
| Prefix API | `/nutrition/menu-ai-drafts` | `/nutrition/meal-plans/ai-drafts` | Trùng `/meal-plans/{weekStart}` (`MealPlanController.java:55`) |
| Apply tuần | Qua `MealPlanService.saveMealPlan` | Ghi thẳng repository | Giữ khóa, kiểm tra, log của luồng lưu hiện có |
| Dị ứng | Không lọc, hiển thị INFO | Lọc chất dị ứng nặng | NUT-23: món thay thế, không đổi thực đơn chung |
Kiến trúc ⇒ ADR-0024 (Phase 3).

## Phases
### Phase 1 — Lõi AI
- Files: C `integration/ai/{AiRequest,AiRawResult,AiGateway,ProviderChainAiGateway,OpenAiCompatibleProvider}.java`, `utils/enums/{AiTask,AiDataClass}.java` · M `AiProperties.java`, `application.yml`, `.env.example`, `compose.yaml` · D `AiClient`, `ExternalAiClient`, `DisabledAiClient`, `AiOutputGuard`, embedding, `dto/Summary*`, `prompts/summary/*` + test cũ
- Tests: AC-7, AC-8 → `ProviderChainAiGatewayTest`, `AiPropertiesTest`
- Exit: `node scripts/verify.mjs` → `VERIFY PASS full`. **Dừng chờ user review.**
### Phase 2 — Gợi ý tuần end-to-end
- Files: C `V82__create_menu_ai_drafts.sql`, entity/repository/enum nháp, `service/meals/impl/{MealPlanEvaluationSupport,MenuCandidateBuilder,RuleBasedMenuPlanner,MenuPlanRepairer,MenuScore,MenuAiPromptBuilder,MenuAiOutputParser,MenuSuggestionEngine,MenuAiDraftServiceImpl}`, `MenuAiDraftService`, `MenuAiDraftController`, DTO, `prompts/menu/{weekly_v1,repair_v1}.txt` · M `MealPlanServiceImpl.java`
- Tests: AC-1..AC-7 → unit test engine/score/parser/candidate + `MenuAiDraftIntegrationTest`, `MenuAiDraftControllerTest`
- Exit: verify full pass, snapshot cập nhật. **Dừng chờ user review.**
### Phase 3 — Mẫu ngày, thay món, eval, tri thức
- Files: prompt `daily_v1`, `dayfix_v1`; `MenuAiLiveEvalTest`, `menu-cases.json`; ADR-0024; docs module/flow/CROSS_REPO_MAP/openapi
- Tests: AC-9 → `MenuAiDraftIntegrationTest`; AC-10 → eval tay
- Exit: verify + check-ai-layer pass, `quickstart.md`. **Dừng chờ user review.**

FE (3 màn) là plan FE riêng sau Phase 2, cần user cho phép sửa repo FE.

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1 | NUT-09, NUT-24 | `MenuCandidateBuilderTest`, `MenuSuggestionEngineTest` (unit), `MenuAiDraftIntegrationTest` | 2 | pass |
| AC-2 | AI-01, NUT-05 | `MenuAiOutputParserTest` (unit), `MenuAiDraftIntegrationTest` | 2 | pass |
| AC-3, AC-4 | AI-07, NUT-09 | `MenuSuggestionEngineTest`, `MenuScoreTest` (unit) | 2 | pass |
| AC-5, AC-6 | AI-02, NUT-08 | `MenuAiDraftIntegrationTest`, `MenuAiDraftControllerTest` | 2 | pass |
| AC-7 | AI-03, AI-05 | `ProviderChainAiGatewayTest`, `MenuAiDraftIntegrationTest` | 1, 2 | pass |
| AC-8 | AI-04, AI-09 | `AiPropertiesTest`, `ProviderChainAiGatewayTest` | 1 | |
| AC-9 | AI-07 | `MenuAiDraftIntegrationTest`, `MenuSuggestionEngineTest` | 3 | pass |
| AC-10 | AI-07 | `MenuAiLiveEvalTest` (tay) | 3 | chưa chạy với provider thật (cần key) |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| AI hiếm khi thắng baseline | AI trông vô dụng | Eval đo tỉ lệ thắng, chỉnh prompt bằng version mới, báo trung thực | `AI_ENABLED=false`, baseline vẫn chạy |
| Hạn mức free (429) | Hay rơi về baseline | Chuỗi 2 provider, ứng viên ≤15 món/ô, alias ngắn | Như trên |
| Proxy VPS timeout < 30s | 504 trên Web | Kiểm nginx read timeout ≥ 60s khi deploy | Giảm deadline task |
| Tách `MealPlanEvaluationSupport` đổi hành vi | Lỗi lưu/công bố tuần | Test meals hiện có phải pass trước khi viết engine | Revert commit tách |

## Validation log
- 2026-11-10: claim code kèm `file:line` trong Context, Key decisions, `research.md`. Contract mới, consumer FE ghi ở `contracts/menu-ai-drafts.md`. >8 file nhưng ≤3 phase, mỗi phase dừng review. Không placeholder; mã Jira riêng cho AI thực đơn chưa có (không phải câu hỏi nghiệp vụ, hỏi trước commit).

## Progress log
- 2026-11-10 — Viết plan + artifact Feature014, `status: draft`, chờ user duyệt.
- 2026-11-10 — User duyệt plan ("Duyệt, làm Phase 1"); `status: in-progress`, bắt đầu Phase 1.
- 2026-11-10 — Phase 1 code xong: `AiGateway`, `ProviderChainAiGateway`, `OpenAiCompatibleProvider`, `AiProperties` (providers + tasks, kiểm AI-04 lúc bind), `AiTask`, `AiDataClass`, `AiRequest`, `AiRawResult`; xóa `AiClient`, adapter/guard nhận xét, embedding, prompt summary, `AiLiveEvalTest` (lấy lại từ `0d8938a`). Env đổi `AI_LLM_*`/`AI_EMBEDDING_*`/`AI_RAG_*` ⇒ `AI_GEMINI_*`, `AI_GROQ_*` (`.env.example`, `compose.yaml`, `application.yml`); card `ai-assistance` viết lại. Test `AiPropertiesTest` 7, `ProviderChainAiGatewayTest` 9: pass. Quyết định agent: provider không tự retry (chuỗi lo); lỗi nào cũng thử provider kế tiếp; read timeout = min(timeout provider, thời gian còn lại).
- 2026-11-10 — Verify lần 1: `VERIFY FAIL` 1 test — `ArchitectureRulesTest.sharedEnumsInUtils` (enum `AiDataClass` ngoài `utils`). Khác plan: chuyển `AiTask`, `AiDataClass` sang `utils/enums`.
- 2026-11-10 — Verify lần 2 sau lần sửa cuối: `VERIFY PASS full | tests 1233 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok`. check-ai-layer 0 lỗi. Phase 1 xong, dừng chờ user review. Chưa commit.
- 2026-11-10 — User: "tiếp phase 2 đi" (session mới tiếp phiên trước). Phase 2 code xong: `MealPlanEvaluationSupport` tách từ `MealPlanServiceImpl` (26 test meals cũ pass sau khi tách); `V82__create_menu_ai_drafts.sql` (V81 vẫn là lớn nhất trên `origin/dev` local), `MenuAiDraft`, `MenuAiDraftRepository`, enum `MenuAiDraftKind/Status/Source`, `MenuAiFallbackReason`; engine `MenuSlot`, `MenuGrid`, `MenuContext`, `MenuCandidateBuilder`, `RuleBasedMenuPlanner`, `MenuPlanRepairer`, `MenuScore`, `MenuAiPromptBuilder`, `MenuAiOutputParser`, `MenuSuggestionEngine`; `MenuAiDraftService(Impl)`, `MenuAiDraftController`, DTO, `MenuAiProperties` (`carenest.menu-ai`), prompt `menu/weekly_v1`, `menu/repair_v1`; ApiCode 6080–6082; `DATA_DICTIONARY`, contract, snapshot OpenAPI (+211 dòng, chỉ thêm).
- 2026-11-10 — Quyết định agent Phase 2 (chờ soát): (a) khung ô mỗi bữa = chuỗi loại món hay gặp nhất của bữa trong bộ mẫu đang dùng của khối (ưu tiên bộ trùng nhãn mùa), không có thì theo `carenest.menu-ai.default-slots` (sáng SNACK/DESSERT; trưa CARB, MAIN, SOUP, VEGETABLE; chiều CARB/SNACK/DESSERT); (b) tuần của bộ mẫu xoay vòng theo số tuần kể từ thứ Hai 05/01/1970 mod 4; (c) điểm: lệch định mức tính theo bề rộng khoảng, vượt giá suất 50% mất hết điểm giá, mỗi WARNING trừ 20% điểm cảnh báo, món trùng 2 tuần trước trừ nửa phần đa dạng; (d) baseline không qua cổng mà AI qua ⇒ chọn AI dù điểm thấp hơn; (e) sửa tất định: tối đa 4 lần đổi một ô/ngày, chọn phép đổi giảm lệch nhiều nhất; (f) output sai cấu trúc ở vòng đầu ⇒ gửi lại đề kèm lỗi gọn (tính là một vòng sửa); (g) tạo nháp `NOT_SUPPORTED` transaction: đọc ngữ cảnh và lưu mỗi bước một transaction ngắn, không giữ kết nối DB khi chờ model; (h) apply luôn tính lại fingerprint (món + thực phẩm + định lượng + định mức + bữa phục vụ), khác thì kiểm lại evaluator, lỗi ⇒ 409 `6082` kèm `data` theo ngày; (i) tuần không phải thứ Hai dùng `6034` như kế hoạch tuần thay vì `9002` trong contract cũ (đã sửa contract); (j) `promptVersion` lưu prompt lần đầu (`menu/weekly_v1`), số vòng sửa ở `repair_rounds`.
- 2026-11-10 — Test mới: `MenuCandidateBuilderTest` 3, `MenuScoreTest` 3, `MenuAiOutputParserTest` 5, `MenuSuggestionEngineTest` 9, `MenuAiDraftIntegrationTest` 7, `MenuAiDraftControllerTest` 4: pass. Lần chạy đầu fail 2 (đã sửa): `ApiCodeTest` trùng mã 6073–6075 (mã khai nhiều dòng nên grep số lớn nhất sót) ⇒ đổi 6080–6082; `createdAt` trả về độ chính xác nano khác giá trị đọc lại (Postgres micro) ⇒ cắt `MICROS`.
- 2026-11-10 — Verify full sau lần sửa cuối: `VERIFY PASS full | tests 1264 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok`. check-ai-layer 0 lỗi (14 cảnh báo ở plan khác, không từ file Phase 2). Phase 2 xong, dừng chờ user review. Chưa commit.
- 2026-11-10 — User: "đảm bảo các tiêu chí output (nghị định, lịch sử thực đơn, mùa, địa phương, thực phẩm có hàng) và có lý lẽ trả lời hội đồng". Đối chiếu nguồn: định mức theo NUT-18/NUT-24 (Chương trình GDMN, PHT nhập số) đã chặn; NUT-20 chưa có số hiệu văn bản; dữ liệu không có thuộc tính mùa, vùng miền, tồn hàng tươi. Đã làm theo dữ liệu có thật: (a) loại món chứa thực phẩm ngừng dùng khỏi ứng viên; (b) tín hiệu `MenuContext.Signals`: hợp mùa = món của bộ mẫu trùng nhãn mùa (NUT-13), món quen = có trong tuần đã công bố 12 tuần gần đây, kho khô hết = mọi điểm trường người lập xem được hết lô còn hạn tại thứ Hai (`DryStockOutlook.usableBalances`); xếp ứng viên theo tín hiệu, gắn nhãn trong prompt; phương án quy tắc và bước sửa tránh món hết kho; món hết kho trong phương án ⇒ WARNING `DRY_STOCK_OUT` (trừ điểm cảnh báo cho cả AI và quy tắc, không chặn vì PHT nhập thêm được — NUT-11); (c) `MenuCriteriaReport`: mỗi nháp có `checks` (13 tiêu chí, rule căn cứ), lưu trong `score_json`; (d) tài liệu lập luận `docs/quality/ai-menu-quality-criteria.md` (ma trận tiêu chí, giới hạn trung thực, câu hỏi hội đồng). Không đổi trọng số điểm, không thêm rule nghiệp vụ. Quyết định agent (chờ soát): ngưỡng 12 tuần cho "món quen"; hàng tươi không xét tồn (NUT-12); kho khô chỉ cảnh báo.
- 2026-11-10 — Verify full sau lần sửa cuối: `VERIFY PASS full | tests 1268 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok`. check-ai-layer 0 lỗi. Chưa commit.
- 2026-11-10 — User: "Làm Phase 3". Code: `MENU_DAILY` (`MenuSuggestionEngine.suggestDaily`, prompt `menu/daily_v1`; ngày neo = hôm nay, phương án quy tắc lấy thứ tương ứng của bộ mẫu tuần xoay vòng hiện tại), `MENU_DAY_FIX` (`suggestDayFix`, prompt `menu/dayfix_v1`, output `{dish, reason}` qua `MenuAiOutputParser.parseChoice`); tách dựng ngữ cảnh sang `MenuAiContextLoader` (service còn điều phối, lưu, dùng/bỏ); `MenuAiDraft.create` + `Target`; response thêm `targetDate`, `targetSession`, `fromDishId`; endpoint mới `POST /{id}/mark-used` cho mẫu ngày; ApiCode 6083 `MENU_AI_DRAFT_KIND_INVALID`; `MenuAiLiveEvalTest` + `ai-eval/menu-cases.json` (8 case); ADR-0024; docs `nutrition.md`, `ai-assistance.md`, flow `meal-management.md` (sửa tham chiếu ADR-0018 ⇒ ADR-0024), `CROSS_REPO_MAP.md`, contract, `quickstart.md`, snapshot OpenAPI (+261 dòng, chỉ thêm).
- 2026-11-10 — Khác plan / quyết định agent (chờ soát): (a) "dùng" mẫu ngày là endpoint riêng `mark-used` vì `apply` trả `MealPlanResponse` (contract cũ ghi `apply` trả nháp cho mẫu ngày — đã sửa contract); (b) thay món: ứng viên = món cùng loại với món cũ, chưa có trong bữa đó; phương án quy tắc = ứng viên điểm cao nhất mà ngày không còn lỗi chặn; (c) cổng cứng của thay món chỉ xét lỗi chặn của đúng ngày đó (+ chưa đặt định mức) vì tuần nháp thường còn ngày chưa lập — phát hiện khi viết, có test `dayFixOnPartialWeekChecksOnlyTargetDay`; bảng kiểm nói về ngày đó; (d) apply thay món luôn kiểm lại ngày đó trên dữ liệu mới, món cũ đã bị sửa tay khỏi ô ⇒ 409 `6082`; (e) thay món không có bước sửa bằng code (đổi ô khác sẽ trái ý "chỉ thay một món"), chỉ vòng gửi lại lỗi cho model.
- 2026-11-10 — Test mới Phase 3: `MenuAiDraftIntegrationTest` +4 (mẫu ngày đánh dấu đã dùng, thay món chỉ đổi đúng ô + idempotent, tuần mới lập một ngày, ô đã bị sửa tay ⇒ 409), `MenuSuggestionEngineTest` +3. Lần chạy đầu fail 1: so sánh `days` khi gọi `apply` lần hai lệch thứ tự món trong ngày (thứ tự đọc lại từ DB khác thứ tự vừa lưu — hành vi có sẵn của `MealPlanService`) ⇒ test so theo tập món. Chạy thử `MenuAiLiveEvalTest` không key (không gọi mạng): 8 case chạy hết, mọi case dùng phương án quy tắc, 0 lỗi chặn lọt; `thieu-du-lieu` ra `NO_FEASIBLE` (thiếu món đủ dữ liệu để cân béo). **Đây không phải kết quả eval AI**: AC-10 chưa chạy vì cần key thật.
- 2026-11-10 — Verify full lần 1 sau Phase 3 không dùng được: Docker treo ~1 giờ (máy ngủ), `MealPlanPublishConcurrencyIntegrationTest` lỗi `ContainerLaunchException` (môi trường, không phải code) ⇒ dừng, chạy lại. Lần chạy lại sau lần sửa cuối: `VERIFY PASS full | tests 1276 fail 0 err 0 skip 1 | archunit ok | snapshot ok | spotless ok`; skip duy nhất `MenuAiLiveEvalTest` (cần key thật ⇒ AC-10 chưa kiểm chứng). check-ai-layer 0 lỗi. Phase 3 xong trừ eval tay; còn Phase 5 FE (plan riêng). Chưa commit.
