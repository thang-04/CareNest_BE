# Quickstart — Feature014 AI gợi ý thực đơn

## Chạy không cần AI (mặc định)
`AI_ENABLED=false`: mọi gợi ý dùng phương án theo quy tắc, `fallbackReason=AI_DISABLED`. Cần tài khoản có `MENU_MANAGE` (HT, hoặc PHT được cấp).

```http
POST /api/nutrition/menu-ai-drafts
{ "kind": "MENU_WEEKLY", "block": "KINDERGARTEN", "weekStart": "2026-11-16", "season": "Mùa đông", "wishes": "nhiều rau" }
```
Xem `status`, `source`, `score`, `checks`, `days`. Dùng: `POST /api/nutrition/menu-ai-drafts/{id}/apply` (tuần vẫn nháp, công bố qua `/nutrition/meal-plans/{weekStart}/publish`). Bỏ: `POST /{id}/dismiss`.

Mẫu ngày: `kind=MENU_DAILY` (không cần `weekStart`) ⇒ Web điền form mẫu ⇒ `POST /{id}/mark-used`. Thay một món: `kind=MENU_DAY_FIX` + `weekStart`, `targetDate`, `targetSession`, `fromDishId` ⇒ `apply` chỉ đổi đúng ô.

## Bật AI
`.env`: `AI_ENABLED=true`, `AI_GEMINI_MODEL`, `AI_GEMINI_API_KEY`, `AI_GROQ_MODEL`, `AI_GROQ_API_KEY` (thiếu một provider thì chuỗi bỏ provider đó). Proxy trước BE: read timeout ≥ 60s cho route này. Chỉ bật cho demo sau khi eval đạt mốc ở dưới.

## Eval tay (AC-10)
```bash
AI_LIVE_EVAL=true AI_LIVE_EVAL_RUNS=3 ./mvnw test -Dtest=MenuAiLiveEvalTest
```
Cần các biến `AI_GEMINI_*`, `AI_GROQ_*` như trên trong môi trường. 8 case × 3 lần (`src/test/resources/ai-eval/menu-cases.json`), giãn 4s/lần (`AI_LIVE_EVAL_PAUSE_MS`). Kết quả `target/ai-eval/menu-eval-*.json` + dòng log `MENU EVAL | valid | aiWins | blockingLeaks`. Test fail nếu có phương án nhãn AI còn lỗi chặn. Mốc bật demo: 0 lỗi lọt, AI thắng ≥ 60% case hợp lệ (bỏ `provider-loi` và `NO_FEASIBLE`). Chép báo cáo vào `docs/quality/ai-eval/<ngày>/` và ghi kết quả trung thực vào plan.

## Kiểm chứng tự động
`node scripts/verify.mjs` (test engine, parser, điểm, ứng viên, integration Postgres, controller). `MenuAiLiveEvalTest` bị skip khi không bật — phần chất lượng model thật chưa kiểm chứng tới khi chạy eval tay.

## Tài liệu
Plan `docs/plans/active/2026-11-10-boarding-menu-ai.md` · ADR-0024 · tiêu chí `docs/quality/ai-menu-quality-criteria.md` · contract `contracts/menu-ai-drafts.md`.
