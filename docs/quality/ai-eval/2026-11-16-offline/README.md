# Eval thực đơn tuần — chạy không key (16/11/2026)

**Không phải kết quả AI.** Chạy `MenuAiLiveEvalTest` với `AI_LIVE_EVAL=true`, `AI_LIVE_EVAL_RUNS=1`, không có `AI_GEMINI_API_KEY`/`AI_GROQ_API_KEY` ⇒ mọi case dùng phương án theo quy tắc (`fallbackReason=AI_DISABLED`). Dữ liệu giả trong file `menu-cases.json` (đã gỡ cùng luồng AI thực đơn 27/11/2026), không phải định lượng/định mức thật của trường.

Mục đích: kiểm đường ống bằng chứng (AC-18) và lấy số nền của phương án không AI.

| Chỉ số | Kết quả |
| --- | --- |
| Case / lần chạy | 10 / 10 |
| Phương án nhãn AI còn lỗi chặn | 0 (không có lần nào nhãn AI) |
| Ô dùng món chứa thực phẩm thiếu (`thieu-thuc-pham`) | 0 |
| Ngày tổng kcal hệ thống lệch tổng cộng tay từ `lines.csv` | 0 / 50 |
| Ngày ngoài khoảng kcal 300–600 | 0 / 50 |
| Món mặn lặp trong tuần | 1–4 mỗi case (danh mục giả chỉ 6 món mặn) |
| Ô lặp so lịch sử 4 tuần (`lich-su-4-tuan`, 5 món đã dùng) | 8 / 25 ô |
| `thieu-du-lieu` | Phương án quy tắc còn lỗi chặn ⇒ chưa đạt, không công bố được |

File: `records.json` (chỉ số từng lần), `lines.csv` (món – thực phẩm – gam – giá trị /100 g – phần đóng góp), `days.csv` (tổng ngày hệ thống so tổng cộng lại, định mức). Cách đọc: `docs/quality/ai-menu-quality-criteria.md` mục 4a.

Còn thiếu để thành bằng chứng AI: key Gemini/Groq, định lượng và định mức thật của trường, chạy 3 lần mỗi case.
