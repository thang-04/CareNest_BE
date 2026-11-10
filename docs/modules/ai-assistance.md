# Module — ai-assistance

Vị trí: `integration/ai/` (`AiClient`, `EmbeddingClient`) + feature `knowledge` (RAG) · Status: đã có lát cắt nhận xét trẻ · ADR-0007, ADR-0008

## Mục đích
Cách ly AI provider khỏi domain; áp guardrail, data minimization, logging metadata. RAG dùng chung cho mọi module AI.

## Owns
- `integration/ai/`:
  - Port `AiClient` (task-typed; hiện có `draftChildSummary`).
  - Port `EmbeddingClient`.
  - Adapter `ExternalAiClient` / `ExternalEmbeddingClient`: API tương thích OpenAI `/chat/completions`, `/embeddings`.
  - Adapter `DisabledAiClient` / `DisabledEmbeddingClient`.
  - `AiOutputGuard`, `PromptTemplateLoader` (prompt có version trong `resources/prompts/`), `AiProperties`.
- Feature `knowledge`: tài liệu tham khảo (`ai_documents`, `ai_document_chunks` + pgvector), nạp/cắt chunk/embedding, truy xuất có lọc metadata. API `/ai-documents` (ADMIN, PRINCIPAL; VP xem).

## KHÔNG owns
Draft nghiệp vụ (lưu ở module domain, vd. `child_summaries`), quyết định duyệt, dữ liệu domain (không query domain).

## Rules
AI-01..10, HLT-04, NUT-05. Provider: Gemini API, Groq (AI-09, ADR-0007).

## Phụ thuộc
- Dùng: common/utils. **Không import module domain nào.**
- Được dùng bởi: nutrition, health, learning-observation, education-plan — domain tự build input tối thiểu rồi gọi port.

```text
domain: facts tính bằng code → ẩn danh → KnowledgeService.retrieve (lọc SQL rồi mới tìm vector)
      → AiClient → adapter → provider → AiOutputGuard
      ← DRAFT (source=AI, model, prompt version, generatedAt) lưu ở domain → GV sửa → GV duyệt
AI tắt/lỗi ⇒ domain dùng template baseline, nghiệp vụ không dừng
```

## Cấu hình (`carenest.ai.*`, env `AI_*` — xem `.env.example`)
- `AI_ENABLED=false` mặc định.
- LLM và embedding là 2 provider độc lập; đổi provider chỉ đổi env (base URL, model, key).
- Đề xuất free tier (đổi được):

  | Vai trò | Provider | Lưu ý |
  |---|---|---|
  | LLM | Groq | Theo docs Groq: mặc định không giữ dữ liệu inference |
  | Embedding | Gemini `gemini-embedding-001` | Chỉ nhận tài liệu công khai + query dựng bằng code |

- `AI_EMBEDDING_DIMENSIONS=0`: dùng số chiều mặc định của model.
- Cột `embedding` là `vector` không cố định số chiều. Truy vấn lọc theo `embedding_model`. Đổi model ⇒ gọi `POST /ai-documents/{id}/reindex`.
- Không tạo index ANN: kho tài liệu nhỏ. Thêm HNSW khi có số chiều cố định ≤ 2000 và kho lớn.
- `AI_RAG_REGION` (mặc định `HAI_PHONG`): tài liệu có region này hoặc `ALL` mới được dùng.

## Guardrail đã enforce bằng code
- **Output:**
  - Đúng JSON schema.
  - Mỗi ý có `evidenceIds` thuộc input.
  - `sources` thuộc các chunk đã gửi.
  - Không có từ chẩn đoán (tự kỷ, tăng động, rối loạn…).
  - Mọi con số phải có trong facts.
  - Sai ⇒ retry 1 lần ⇒ `OUTPUT_INVALID`.
- **Input:**
  - Không gửi họ tên/ngày sinh/id thật; tuổi tính theo tháng.
  - Ghi chú được ẩn danh: tên trẻ ⇒ "Trẻ", tên bạn cùng lớp ⇒ "bạn", SĐT/email ⇒ "[đã ẩn]".
  - Facts có `dataNotAvailable` để AI không nhắc nguồn chưa có.
- **RAG:**
  - Nạp tài liệu từ chối câu mang tính ra lệnh (prompt injection).
  - Chunk được bọc `<reference>` và trung hoà ký tự `<`, `>`.
  - Prompt ghi rõ tài liệu chỉ là bằng chứng.
- **Lỗi:**
  - 429 ⇒ `RATE_LIMITED`; timeout ⇒ `TIMEOUT` (không retry); 5xx/mất kết nối ⇒ `PROVIDER_UNAVAILABLE`.
  - Domain trả `aiErrorCode` (vd. `AI_TIMEOUT`) kèm bản template.
- **Log:** chỉ metadata (model, prompt version, latency, token, số chunk). Không log prompt, facts, output, nội dung chunk.

## Eval chạy tay với provider thật (không chạy trong CI)
- **Lệnh** (Git Bash, ở thư mục repo):
  ```bash
  set -a && . ./.env && set +a
  AI_LIVE_EVAL=true ./mvnw test -Dtest=AiLiveEvalTest
  ```
- **Dữ liệu:** `src/test/resources/ai-eval/summary-cases.json`, gồm 5 case giả lập: ổn định, giấc ngủ cần theo dõi, kỳ có xu hướng, dữ liệu ít, ghi chú dụ chẩn đoán + chèn lệnh.
- **Tiêu chí chấm:** case nào cũng phải qua `AiOutputGuard`.
- **Kết quả:** nội dung nháp ghi UTF-8 vào `target/ai-eval/summary-<model>-<prompt>.json` để nhóm đọc và so sánh.
- **Kết quả 2026-10-15** với `openai/gpt-oss-120b` (Groq), `reasoning_effort=low`, prompt `summary/draft_v2`:
  - 5/5 case qua guard.
  - Khoảng 1.0–1.5 giây và ~1.5k token mỗi bản nháp.
  - Câu chèn lệnh trong ghi chú bị bỏ qua.
- **Prompt v1 → v2:** v1 hay thêm thắt (vd. "khả năng lãnh đạo" từ một ghi chú). v2 cấm suy diễn tính cách/năng lực và cấm khái quát một lần thành thói quen.
- **Embedding `gemini-embedding-001`:** 3072 chiều; câu liên quan ~0.80, không liên quan ~0.52 ⇒ `AI_RAG_MIN_SCORE=0.65`.
- **Lưu ý provider:**
  - Gemini (OpenAI-compat) trả `index: null` trong response embeddings ⇒ adapter giữ thứ tự response.
  - Groq free giới hạn token/phút ⇒ gọi dồn dập bị 429, luồng tự chuyển template.

## PENDING
- P-02 / ADR-0007: chốt provider + điều khoản lưu dữ liệu trước khi gửi dữ liệu trẻ thật. Free tier Gemini có thể dùng dữ liệu để train ⇒ chỉ dùng cho embedding tài liệu công khai.
- Nạp PDF cần thư viện (PDFBox) ⇒ hỏi trước khi thêm dependency; v1 chỉ nhận text/markdown.
P-34: nháp tổng kết năm học (AI-07 mục 5) — làm tới đâu, module nào lưu nháp.

## Known pitfalls
- Gọi SDK provider trực tiếp từ domain ⇒ vi phạm AI-06 (ArchUnit chặn `org.springframework.web.client` ngoài `integration/`).
- Timeout/lỗi AI không được làm fail nghiệp vụ; domain dùng baseline.
- Log prompt/response chứa dữ liệu trẻ ⇒ vi phạm AI-05.
- Không tin output AI: guard trước khi lưu DRAFT.
- Query RAG gửi ra embedding API ⇒ chỉ dựng từ nhóm tuổi + tên tiêu chí, không đưa ghi chú/tên trẻ.
- Sửa prompt ⇒ tạo file version mới (`draft_v2.txt`), không sửa file cũ; version được lưu kèm nháp.
- Không tin output AI: validate schema + rule xác định trước khi lưu DRAFT.
- Nhận xét tự do của GV đưa vào prompt cũng phải lọc tên, ngày sinh (AI-08, AI-04).
- Gợi ý thực đơn có công tắc bật/tắt (AI-07); tắt ⇒ không gọi provider.

## Related issues
G94-200 / BE-FEAT-28 — `docs/plans/active/2026-10-14-ai-assessment-drafting.md`.
