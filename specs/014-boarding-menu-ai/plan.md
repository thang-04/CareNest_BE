# Implementation plan — Feature014

Status: chờ user duyệt plan quản trị trước khi code (làn L: `integration/`, migration, contract mới). Quản trị phase, AC, key decisions: [plan quản trị](../../docs/plans/active/2026-11-10-boarding-menu-ai.md). Branch `feature/G94-200-BE-FEAT-28-ai-assessment-drafting`.

## Technical context
Java 21, Spring Boot, PostgreSQL 17, `RestClient` sẵn có. Không dependency mới. Module: ai-assistance (`integration/ai`), nutrition (`service/meals`). Constitution: `.specify/memory/constitution.md` + AGENTS.md.

## Design
- **`integration/ai`:** `AiTask` (enum, khóa cấu hình), `AiDataClass` (`PUBLIC_CATALOG`, `CHILD_DERIVED`), `AiRequest(task, systemPrompt, userPrompt, jsonSchema, Instant deadline)`, `AiRawResult(json, providerId, model, promptVersion, inputTokens, outputTokens, latencyMs)`, `AiGateway` (port), `ProviderChainAiGateway` (impl: lọc chuỗi theo dataClass + key, thử lần lượt, dừng khi hết deadline), `OpenAiCompatibleProvider` (một provider; viết lại từ `ExternalAiClient`). `AiProperties` mới: `enabled`, `providers[]`, `tasks{}`; kiểm FR-02 lúc khởi động. Giữ `PromptTemplateLoader`, `AiClientException`, `AiErrorReason`.
- **`service/meals/impl`:** `MealPlanEvaluationSupport` (tách từ `MealPlanServiceImpl`: nhóm tuổi, `DishInfo`, `normOf`), `MenuCandidateBuilder`, `RuleBasedMenuPlanner`, `MenuPlanRepairer`, `MenuScore`, `MenuAiPromptBuilder` (alias, prompt, sửa), `MenuAiOutputParser` (guard cấu trúc + làm sạch lý do), `MenuSuggestionEngine` (điều phối bước 1–8, không I/O DB), `MenuAiDraftServiceImpl` (quyền, đọc dữ liệu, gọi engine, lưu, apply/dismiss).
- **API:** `MenuAiDraftController` theo `contracts/menu-ai-drafts.md`.
- **Prompt:** `resources/prompts/menu/weekly_v1.txt`, `daily_v1.txt`, `dayfix_v1.txt`, `repair_v1.txt`.

## Phases
1. Lõi AI (`integration/ai`) + test adapter/chuỗi/cấu hình.
2. Gợi ý tuần end-to-end: bảng, engine, API, apply/dismiss, test.
3. Mẫu ngày, thay món, eval tay, ADR-0024, tri thức, OpenAPI.

## Validation
Test map AC trong `tasks.md`. Mỗi phase: `node scripts/verify.mjs`; cuối: `node scripts/check-ai-layer.mjs`, eval tay `MenuAiLiveEvalTest`; kết quả ghi `quickstart.md`.
