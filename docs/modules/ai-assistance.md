# Module — ai-assistance

Vị trí: `integration/ai/` (`AiClient`) · Status: port + adapter OpenAI-compatible đã có, dùng cho giải thích xu hướng sức khỏe (2026-11-10); task khác (thực đơn, tóm tắt, tổng kết năm): thiết kế · ADR-0007, ADR-0008

## Mục đích
Cách ly AI provider khỏi domain; áp guardrail, data minimization, logging metadata.

## Owns
Port `AiClient` (task-typed: `suggestMenu`, `interpretHealthTrend`, `draftSummary`, gợi ý nội dung hoạt động, nháp tổng kết năm — AI-07), adapters (`ExternalApiAdapter`, `LocalModelAdapter`, `DisabledAdapter`), prompt templates, output schema validation, usage log.

## KHÔNG owns
Draft nghiệp vụ (lưu ở module domain), quyết định duyệt, dữ liệu domain (không query domain).

## Rules
AI-01..10, HLT-04, NUT-05. Provider: Gemini API, Groq (AI-09, ADR-0007).

## Hiện có (2026-11-10)
Plan `docs/plans/completed/2026-11-10-health-record-complete.md`.
- `AiClient.complete(AiRequest(task, systemPrompt, userPrompt, maxTokens[, jsonOutput]))` ⇒ `AiResult(text, model)`; `isEnabled()`. `jsonOutput` ⇒ gửi `response_format: json_object` (domain vẫn tự kiểm output). Lỗi: `AiException` lý do `UNAVAILABLE` (tắt, lỗi gọi, phản hồi rỗng, bị cắt vì hết `max_tokens`) / `TIMEOUT` — domain đổi thành 8001 / 8002.
- `OpenAiCompatibleAiClient`: một adapter `POST {base-url}/chat/completions` (Bearer key, temperature 0.2) dùng cho Gemini (`https://generativelanguage.googleapis.com/v1beta/openai`, mặc định) và Groq (`https://api.groq.com/openai/v1`) — AI-09, không thêm SDK. Log chỉ task, model, độ trễ, `completionTokens`, kết quả (`ok`, `truncated`, `empty`) (AI-05).
- Cấu hình `carenest.ai.*` (`AiProperties`): env `CARENEST_AI_BASE_URL`, `CARENEST_AI_API_KEY`, `CARENEST_AI_MODEL` (mặc định `gemini-2.0-flash`), `CARENEST_AI_READ_TIMEOUT` (20s); connect 3s. Không có khóa ⇒ AI tắt, không gọi mạng; nghiệp vụ không AI vẫn chạy (AI-03). Khóa chỉ ở env/secret, không commit; `toString()` che khóa.
- Prompt nằm ở domain gọi (vd. `HealthTrendServiceImpl`): domain tự tối giản dữ liệu (AI-04), kiểm output (HLT-04) và lưu nháp `source=AI`, model (AI-01, AI-02).

## Phụ thuộc
- Dùng: common/utils. **Không import module domain nào.**
- Được dùng bởi: nutrition, health, learning-observation, education-plan — domain tự build input tối thiểu rồi gọi port.

```text
domain → build input (bí danh, tổng hợp) → AiClient → adapter → provider
       ← DRAFT (source=AI, model, createdAt) lưu ở domain → người duyệt
```

## PENDING
P-34: nháp tổng kết năm học (AI-07 mục 5) — làm tới đâu, module nào lưu nháp.

## Known pitfalls
- Gọi SDK provider trực tiếp từ domain ⇒ vi phạm AI-06.
- Timeout/lỗi AI không được làm fail nghiệp vụ; trả "unavailable", UI dùng baseline.
- Log prompt/response chứa dữ liệu trẻ ⇒ vi phạm AI-05.
- Không tin output AI: validate schema + rule xác định trước khi lưu DRAFT.
- Nhận xét tự do của GV đưa vào prompt cũng phải lọc tên, ngày sinh (AI-08, AI-04).
- Gợi ý thực đơn có công tắc bật/tắt (AI-07); tắt ⇒ không gọi provider.

## Related issues
Chưa có.
