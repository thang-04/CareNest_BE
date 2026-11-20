# Tasks — Feature014

Thứ tự: lõi AI → engine + gợi ý tuần → mẫu ngày, thay món, eval, tri thức → FE (plan riêng) → converge.

## Phase1 — Specification/design
- [x] T001 Domain spec 14 mục, spec, research, data model, contract, plan, plan quản trị.

## Phase2 — Lõi AI (US3, AC-7, AC-8)
- [x] T002 (`AiTask`, `AiDataClass`, `AiRequest`, `AiRawResult`, `AiGateway`, `AiProperties`) Kiểu và cấu hình mới; `application.yml`, `.env.example`, `compose.yaml` đổi biến `AI_*` theo provider. AC-8.
- [x] T003 (`OpenAiCompatibleProvider`, `ProviderChainAiGateway`) Gọi `/chat/completions`, `response_format` JSON, phân loại lỗi 429/timeout/5xx, chuyển provider, dừng theo deadline, log metadata. Xóa `AiClient`, `ExternalAiClient`, `DisabledAiClient`, `AiOutputGuard`, `dto/Summary*`, embedding, `prompts/summary` cùng test của chúng. AC-7.
- [x] T004 (`ProviderChainAiGatewayTest`, `AiPropertiesTest`) MockRestServiceServer: thành công, 429 ⇒ provider 2, timeout, hết chuỗi, tắt, thiếu key; `CHILD_DERIVED` + `may-train-on-data` ⇒ lỗi khởi động; key không lộ trong `toString`. AC-7, AC-8.
- [x] T005 Verify, Progress log, dừng review.

## Phase3 — Gợi ý tuần (US1, US2, AC-1..AC-6)
- [x] T006 (`MealPlanEvaluationSupport`, `MealPlanServiceImpl`) Tách dựng input evaluator, hành vi cũ không đổi (test meals hiện có pass).
- [x] T007 (`V82__create_menu_ai_drafts.sql`, `MenuAiDraft`, repository, enum kind/status/source/fallback) Bảng nháp.
- [x] T008 (`MenuCandidateBuilder`, `RuleBasedMenuPlanner`, `MenuPlanRepairer`, `MenuScore`) + unit test: ứng viên đúng bữa/đủ dữ liệu, baseline tất định, sửa hết `BLOCKING` khi có món thay được, điểm theo trọng số. AC-1, AC-3.
- [x] T009 (`MenuAiPromptBuilder`, `MenuAiOutputParser`, `prompts/menu/weekly_v1.txt`, `repair_v1.txt`) Alias, prompt, guard cấu trúc, làm sạch lý do + unit test (alias lạ, sai ô, thiếu ô, JSON hỏng, chữ số, từ y khoa). AC-2.
- [x] T010 (`MenuSuggestionEngine`) Bước 1–8 + unit test quyết định với `AiGateway` giả: AI thắng, hòa, thua, lỗi, không hợp lệ, cả hai không đạt. AC-3, AC-4, AC-7.
- [x] T011 (`MenuAiDraftService(Impl)`, `MenuAiDraftController`, DTO) Tạo/xem/bỏ/apply tuần. AC-5, AC-6.
- [x] T012 (`MenuAiDraftIntegrationTest`, `MenuAiDraftControllerTest`) Testcontainers + WebMvc: quyền, 400, tuần PUBLISHED 409, apply giữ khối khác, apply hai lần, dữ liệu đổi phát sinh `BLOCKING` 409, dismiss, không lưu prompt. AC-1..AC-7.
- [x] T013 Verify, Progress log, dừng review.

## Phase4 — Mẫu ngày, thay món, eval, tri thức (US4, US5, AC-9, AC-10)
- [x] T014 `MENU_DAILY`, `MENU_DAY_FIX` (prompt `daily_v1`, `dayfix_v1`) + test. AC-9.
- [x] T015 (`MenuAiLiveEvalTest`, `src/test/resources/ai-eval/menu-cases.json`) 8 case × 3 lần, chỉ chạy khi `AI_LIVE_EVAL=true`; báo cáo `docs/quality/ai-eval/<ngày>/`. AC-10.
- [x] T016 ADR-0024, `docs/modules/ai-assistance.md`, `docs/modules/nutrition.md`, flow `meal-management.md` (sửa tham chiếu ADR-0018), `BUSINESS_RULES` nếu đổi, `CROSS_REPO_MAP`, `docs/api/openapi.yaml` + snapshot.
- [x] T017 Verify, check-ai-layer, `quickstart.md`, Progress log.

## Phase5 — Web (plan FE riêng, cần user cho phép sửa repo FE)
- [ ] T018 FE đổi ba màn sang contract mới.
- [ ] T019 Converge spec/contract với code BE/FE.

## Test trace
AC-1..AC-4 → `MenuSuggestionEngineTest`, `MenuScoreTest`, `MenuAiOutputParserTest`, `MenuCandidateBuilderTest` · AC-5..AC-7 → `MenuAiDraftIntegrationTest`, `MenuAiDraftControllerTest`, `ProviderChainAiGatewayTest` · AC-8 → `AiPropertiesTest` · AC-9 → `MenuAiDraftIntegrationTest` · AC-10 → `MenuAiLiveEvalTest` (tay). Dữ liệu test tổng hợp, tên "(giả)".
