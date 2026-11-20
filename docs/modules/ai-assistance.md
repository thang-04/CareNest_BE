# Module — ai-assistance

Vị trí: `integration/ai/` (port `AiGateway`, cổng AI duy nhất) · Status: lõi gọi provider theo task xong (10/11/2026); dùng cho AI thực đơn bán trú (`docs/plans/active/2026-11-10-boarding-menu-ai.md`) và giải thích xu hướng sức khỏe (task `HEALTH_TREND`, chuyển từ `AiClient` sang `AiGateway` ngày 13/11/2026, `AiClient` đã gỡ). Embedding cho xếp lại món ứng viên thực đơn (`EmbeddingClient`, 19/11/2026). Lát cắt nhận xét trẻ cũ và feature `knowledge` (RAG tài liệu) đã gỡ, code còn ở commit `0d8938a` · ADR-0007, ADR-0008

## Mục đích
Cách ly AI provider khỏi domain; chọn provider theo loại dữ liệu, giới hạn thời gian, log metadata. Kiểm output là việc của domain (mỗi tác vụ có guard riêng).

## Owns
- `integration/ai/`:
  - Port `AiGateway`: `isAvailable(task)`, `taskConfig(task)`, `complete(AiRequest)` ⇒ `AiRawResult` (nội dung thô, provider, model, prompt version, token, latency).
  - Dùng enum `utils/enums/AiTask` (`MENU_WEEKLY`, `MENU_DAILY`, `MENU_DAY_FIX`), `utils/enums/AiDataClass` (`PUBLIC_CATALOG`, `CHILD_DERIVED`) — enum dùng chung phải ở `utils/enums` (ArchUnit `sharedEnumsInUtils`).
  - `ProviderChainAiGateway`: thử lần lượt provider trong chuỗi của task tới khi có nội dung hoặc hết deadline.
  - `OpenAiCompatibleProvider`: `POST {baseUrl}/chat/completions`, `response_format` JSON, không retry.
  - `AiProperties`, `PromptTemplateLoader` (prompt có version trong `resources/prompts/`), `AiClientException` + `AiErrorReason`.
  - Port `EmbeddingClient` (`isAvailable()`, `modelName()`, `budget()`, `embed(texts, timeout)`) + `OpenAiCompatibleEmbeddingClient` (`POST {baseUrl}/embeddings`, chia lô, kiểm số chiều, không retry, không log nội dung) + `EmbeddingProperties` (`carenest.embedding.*`). Chỉ nhận văn bản danh mục công khai và câu mong muốn của PHT (AI-04). Vector lưu ở `dish_embeddings` (nutrition, V162).

## KHÔNG owns
Nháp nghiệp vụ (lưu ở module domain, vd. `menu_ai_drafts`), guard output theo tác vụ, quyết định duyệt, dữ liệu domain (không query domain).

## Rules
AI-01..10, HLT-04, NUT-05. Provider: Gemini API, Groq (AI-09, ADR-0007).

## Lịch sử: `AiClient` (đã gỡ 13/11/2026, thay bằng `AiGateway`)
Plan `docs/plans/completed/2026-11-10-health-record-complete.md`. Phần dưới chỉ mô tả bản cũ; cấu hình hiện hành ở mục "Cấu hình".
- `AiClient.complete(AiRequest(task, systemPrompt, userPrompt, maxTokens))` ⇒ `AiResult(text, model)`; `isEnabled()`. Lỗi: `AiException` lý do `UNAVAILABLE` (tắt, lỗi gọi, phản hồi rỗng) / `TIMEOUT` — domain đổi thành 8001 / 8002.
- `OpenAiCompatibleAiClient`: một adapter `POST {base-url}/chat/completions` (Bearer key, temperature 0.2) dùng cho Gemini (`https://generativelanguage.googleapis.com/v1beta/openai`, mặc định) và Groq (`https://api.groq.com/openai/v1`) — AI-09, không thêm SDK. Log chỉ task, model, độ trễ, kết quả (AI-05).
- Cấu hình `carenest.ai.*` (`AiProperties`): env `CARENEST_AI_BASE_URL`, `CARENEST_AI_API_KEY`, `CARENEST_AI_MODEL` (mặc định `gemini-2.0-flash`), `CARENEST_AI_READ_TIMEOUT` (20s); connect 3s. Không có khóa ⇒ AI tắt, không gọi mạng; nghiệp vụ không AI vẫn chạy (AI-03). Khóa chỉ ở env/secret, không commit; `toString()` che khóa.
- Prompt nằm ở domain gọi (vd. `HealthTrendServiceImpl`): domain tự tối giản dữ liệu (AI-04), kiểm output (HLT-04) và lưu nháp `source=AI`, model (AI-01, AI-02).

## Phụ thuộc
- Dùng: common/utils. **Không import module domain nào.**
- Được dùng bởi: nutrition (đang làm); health, learning-observation, education-plan (sau) — domain tự dựng input tối thiểu rồi gọi port.

```text
domain: dữ liệu tính bằng code → prompt tối thiểu (không danh tính trẻ) → AiGateway.complete
      → chuỗi provider của task (lọc theo AiDataClass, bỏ provider thiếu cấu hình) → nội dung thô
      ← domain guard + kiểm xác định → DRAFT (source, model, prompt version) lưu ở domain → người duyệt
AI tắt/lỗi/hết deadline ⇒ domain dùng phương án không AI, nghiệp vụ không dừng (AI-03)
```

## Cấu hình (`carenest.ai.*`, env `AI_*` — xem `.env.example`)
- `AI_ENABLED=false` mặc định.
- `providers[]`: `id`, `base-url`, `model`, `api-key`, `may-train-on-data`, timeout, `reasoning-effort`. Thiếu base URL/model/key ⇒ provider bị bỏ khỏi chuỗi (không lỗi khởi động).
- `tasks.<TASK>`: `chain` (thứ tự provider), `data-class`, `deadline` (cho cả chuỗi), `max-repair-rounds` (domain dùng).
- Lỗi khởi động khi: id provider trống/trùng, chuỗi trỏ provider không tồn tại, task `CHILD_DERIVED` (hoặc không khai `data-class`) có provider `may-train-on-data=true`.
- Embedding (`carenest.embedding.*`, env `AI_EMBEDDING_*`): `base-url` (mặc định Gemini OpenAI-compat), `model` (vd. `gemini-embedding-001`), `api-key`, `dimensions` (0 = mặc định của model), `batch-size` 50, `budget` 8s. Cần `AI_ENABLED=true` và đủ base URL/model/key, thiếu ⇒ embedding tắt (không lỗi khởi động). Groq không có embedding.
- Mặc định (đổi được):

  | Provider | `may-train-on-data` | Dùng cho |
  |---|---|---|
  | `gemini` (OpenAI-compat `/v1beta/openai`) | true (free tier có thể dùng dữ liệu để train) | task `PUBLIC_CATALOG` (thực đơn), đứng đầu chuỗi |
  | `groq` | false (theo docs Groq: không giữ dữ liệu inference) | dự phòng thực đơn; task dữ liệu trẻ |

## Guardrail đã enforce bằng code
- **Chọn provider:** task dữ liệu trẻ không đi qua provider có thể train (kiểm lúc khởi động và lúc gọi).
- **Thời gian:** read timeout mỗi lần gọi = min(timeout provider, thời gian còn lại tới deadline); hết deadline ⇒ `TIMEOUT`.
- **Lỗi:** 429 ⇒ `RATE_LIMITED`; timeout ⇒ `TIMEOUT`; 4xx/5xx khác, mất kết nối ⇒ `PROVIDER_UNAVAILABLE`; response rỗng ⇒ `OUTPUT_INVALID`. Mọi lỗi ⇒ thử provider kế tiếp; hết chuỗi ⇒ ném lỗi cuối. Không có provider dùng được ⇒ `DISABLED`.
- **Log:** một dòng mỗi lần gọi: task, provider, model, prompt version, trạng thái/lý do, latency, token. Không log prompt, output, API key.

## Dùng ở đâu
- Thực đơn bán trú (nutrition, ADR-0024): `MenuSuggestionEngine` gọi task `MENU_WEEKLY`, `MENU_DAILY`, `MENU_DAY_FIX`; prompt `prompts/menu/{weekly_v2,repair_v1,daily_v1,dayfix_v2}.txt` (bản v1 giữ cho nháp cũ). Sơ đồ kiến trúc (dữ liệu nạp → SQL lọc cứng + pgvector xếp lại → LLM chỉ chọn bí danh → code tính dinh dưỡng → người duyệt): `docs/quality/ai-menu-quality-criteria.md` mục 0, ảnh `docs/quality/images/ai-menu-architecture.png`. `DishSimilarity` (nutrition) gọi `EmbeddingClient`, lưu/đọc `dish_embeddings`, trả điểm giống cho `MenuCandidateBuilder`. Eval truy xuất vector: `DishSimilarityLiveEvalTest` (`AI_LIVE_EVAL_EMBEDDING=true`, `AI_EMBEDDING_MODEL`, `AI_EMBEDDING_API_KEY`; 40 món giả, 10 câu mong muốn, top-5) ghi `target/ai-eval/embedding-<thời điểm>/{retrieval.md,retrieval.csv}` ⇒ chấm tay cột "Chấm tay", chép vào `docs/quality/ai-eval/<ngày>/`. Eval tay: `MenuAiLiveEvalTest` (`AI_LIVE_EVAL=true`, `AI_LIVE_EVAL_RUNS`, `AI_LIVE_EVAL_PAUSE_MS`), 10 case `src/test/resources/ai-eval/menu-cases.json`, kết quả `target/ai-eval/menu-eval-<thời điểm>/{records.json,lines.csv,days.csv}` ⇒ chép vào `docs/quality/ai-eval/<ngày>/`; cách đọc ở mục 4a cùng file.

## Lịch sử eval nhận xét trẻ (slice đã gỡ)
- 2026-10-15, Groq `openai/gpt-oss-120b`, `reasoning_effort=low`, prompt `summary/draft_v2`: 5/5 case qua guard, ~1.0–1.5 giây, ~1.5k token/bản; câu chèn lệnh bị bỏ qua. Báo cáo: `docs/quality/ai-summary-evaluation-report.md`.
- Groq free giới hạn token/phút ⇒ gọi dồn dập bị 429.
- Gemini (OpenAI-compat) trả `index: null` trong response embeddings ⇒ khi lấy lại embedding phải giữ thứ tự response.

## PENDING
- P-02 / ADR-0007: chốt điều khoản lưu dữ liệu trước khi gửi dữ liệu dẫn xuất từ trẻ thật.
- P-34: nháp tổng kết năm học (AI-07 mục 5) — làm tới đâu, module nào lưu nháp.
- RAG tài liệu (giáo án): lấy lại `knowledge` từ `0d8938a` (bảng `ai_documents`/`ai_document_chunks`, tìm cosine `<=>`); image pgvector đã có từ V155; nạp PDF cần PDFBox (hỏi trước khi thêm dependency).

## Known pitfalls
- Gọi SDK/HTTP provider trực tiếp từ domain ⇒ vi phạm AI-06 (ArchUnit chặn `org.springframework.web.client` ngoài `integration/`).
- Timeout/lỗi AI không được làm fail nghiệp vụ; domain dùng phương án không AI.
- Log prompt/response chứa dữ liệu trẻ ⇒ vi phạm AI-05.
- Không tin output AI: domain guard + kiểm xác định trước khi lưu DRAFT.
- Sửa prompt ⇒ tạo file version mới (`weekly_v2.txt`), không sửa file cũ; version được lưu kèm nháp.
- Prompt `.txt` phải commit: `.gitignore` có ngoại lệ `!src/main/resources/prompts/**/*.txt` (ENV-261017-gitignore-txt-drops-ai-prompts).
- Nhận xét tự do của GV đưa vào prompt phải lọc tên, ngày sinh (AI-08, AI-04).

## Related issues
G94-200 / BE-FEAT-28 — `docs/plans/active/2026-11-10-boarding-menu-ai.md`; lịch sử `docs/plans/active/2026-10-14-ai-assessment-drafting.md`.
