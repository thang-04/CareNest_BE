# Module — ai-assistance

Vị trí: `integration/` (`AiClient`) · Status: thiết kế · ADR-0007, ADR-0008

## Mục đích
Cách ly AI provider khỏi domain; áp guardrail, data minimization, logging metadata.

## Owns
Port `AiClient` (task-typed: `suggestMenu`, `interpretHealthTrend`, `draftSummary`, gợi ý nội dung hoạt động, nháp tổng kết năm — AI-07), adapters (`ExternalApiAdapter`, `LocalModelAdapter`, `DisabledAdapter`), prompt templates, output schema validation, usage log.

## KHÔNG owns
Draft nghiệp vụ (lưu ở module domain), quyết định duyệt, dữ liệu domain (không query domain).

## Rules
AI-01..10, HLT-04, NUT-05. Provider: Gemini API, Groq (AI-09, ADR-0007).

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
