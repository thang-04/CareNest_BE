---
id: BUG-261127-ai-prose-forced-json
type: bug
modules: [ai-assistance, observation]
rules: [AI-03, OBS-34]
status: fixed
date: 2026-11-27
keywords: [AI viết nháp, AI chưa phản hồi, aiFallback, response_format, json_object, groq, 400, PROVIDER_UNAVAILABLE, WEEKLY_COMMENT, PERIOD_COMMENT]
similar_to: []
---

# BUG-261127-ai-prose-forced-json — AI viết nháp nhận xét luôn rơi về mẫu câu

## Symptom
Web báo "AI chưa phản hồi, đã viết nháp theo số liệu tuần." dù AI đã cấu hình. Log:
```text
|complete|task=WEEKLY_COMMENT|provider=groq|...|status=FAILED|reason=PROVIDER_UNAVAILABLE
```
Gọi thẳng Groq với cùng kiểu request: HTTP 400
```text
'messages' must contain the word 'json' in some form, to use 'response_format' of type 'json_object'.
```

## Điều kiện tái hiện
Provider Groq, tác vụ có prompt văn xuôi (không nhắc "json"), số liệu giả.

## Attempts — đã thử (cập nhật mỗi phiên điều tra, để phiên sau không lặp lại)
| # | Cách thử | Kết quả | Vì sao không đúng / bài học |
| --- | --- | --- | --- |
| 1 | Kiểm env AI trên môi trường dev | Đủ model và key | Không phải lỗi cấu hình; log BE không ghi mã HTTP nên phải gọi thẳng provider |

## Root cause
`OpenAiCompatibleProvider` gửi `response_format: json_object` cho mọi tác vụ. `WEEKLY_COMMENT` và `PERIOD_COMMENT` cần văn xuôi, prompt không nhắc "json" nên Groq trả 400; gateway gộp mã này thành `PROVIDER_UNAVAILABLE`.

## Fix
`AiRequest.expectsJson()` chọn theo `AiTask` (switch đủ nhánh); provider chỉ gửi `response_format` cho `HEALTH_TREND`, `EDU_PLAN`, `EDU_MATCH`. Không thêm chữ "json" vào prompt văn xuôi vì output sẽ thành JSON.

## Regression test
`ProviderChainAiGatewayTest#doesNotForceJsonOnProseTasks` (fail trước fix) và `#asksForJsonObjectOnJsonTasks`.

## Ảnh hưởng
Nhận xét tuần và nhận xét kỳ dùng lại được AI sau khi deploy. API không đổi; nháp mẫu câu đã lưu giữ nguyên, giáo viên bấm viết lại nếu cần.

## Lesson
Test gateway bằng server giả không bắt được ràng buộc phía provider: thêm tác vụ AI mới phải gọi thử provider thật ít nhất một lần.
