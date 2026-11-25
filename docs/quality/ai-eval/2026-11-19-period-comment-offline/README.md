# Eval nhận xét tháng / học kỳ / cuối năm — chạy không key (19/11/2026)

**Không phải kết quả AI.** Chạy `PeriodCommentLiveEvalTest` với `AI_LIVE_EVAL=true`, `AI_LIVE_EVAL_RUNS=1`, không có `AI_GROQ_API_KEY` / `AI_GROQ_MODEL` ⇒ 9/10 lần ghi `ai-disabled`, case 04 ghi `no-content` (không ngày học nào, luôn dùng mẫu câu). Chưa gọi model thật; chưa có số về chất lượng văn bản AI.

Mục đích: kiểm đường ống bằng chứng và cho người kiểm tính lại số đầu vào bằng bảng tính.

## Cách kiểm lại (người, không cần lập trình)
1. Mở `days.csv`: từng ngày thô của 10 case giả (459 dòng): đi học, có đánh giá, cảm xúc, ngủ, ăn trưa, tham gia, hành vi, cờ, mục tiêu đạt / chưa đạt.
2. Dùng COUNTIF theo `case` để tính: số ngày đi học, số ngày được đánh giá, số cờ, số ngày ăn hết suất trưa, số lần đạt / chưa đạt từng mục tiêu.
3. So với `facts.csv` (cột `in_prompt` là số hệ thống đưa vào prompt, cột `expected_independent` do script tính riêng): mọi dòng `KHỚP`, 0 dòng `LỆCH`.
4. Khi có key: `output.csv` ghi từng lần chạy (AI hay mẫu câu, lý do rơi về mẫu, độ dài, số xuất hiện trong văn bản, số ngoài đầu vào = 0 với bản được nhận, tên bạn lọt vào). `records.json` có văn bản để chấm tay (giọng văn, bám nhận xét của cô, không bịa).

## Bộ case (`src/test/resources/ai-eval/period-comment-cases.json`, dữ liệu giả)
01 tháng đủ dữ liệu · 02 ít dữ liệu · 03 nghỉ nhiều · 04 không có ngày học · 05 nhận xét chứa tên, ngày sinh, số điện thoại của trẻ · 06 nhận xét nhắc tên bạn khác · 07 câu chèn lệnh · 08 nhận xét có từ chẩn đoán · 09 học kỳ + 3 nhận xét tháng đã xác nhận · 10 cuối năm, nhiều nhận xét (kiểm trần).

## Chạy lại
```bash
AI_LIVE_EVAL=true AI_LIVE_EVAL_RUNS=2 AI_GROQ_MODEL=<model> AI_GROQ_API_KEY=<key> ./mvnw test -Dtest=PeriodCommentLiveEvalTest
```
Kết quả ở `target/ai-eval/period-comment-<thời điểm>/`; chép vào `docs/quality/ai-eval/<ngày>/`. Kiểm tự động không cần key: `PeriodCommentEvalCasesTest` (số đếm khớp, prompt không có danh tính trẻ, trần nhận xét, chèn lệnh và từ chẩn đoán bị chặn).

## Giới hạn đã biết
- Chỉ lọc tên của chính trẻ; tên bạn khác trong nhận xét của cô lọt vào prompt (case 06), giáo viên rà khi sửa nháp (OBS-34).
- Tên gọi trùng từ thường (vd. "Đạt") bị thay luôn trong nhận xét của cô: ưu tiên không lộ tên.
- Số nền không AI: nháp mẫu câu `EvaluationDraftWriter` (đã có từ trước).
