# ADR-0024 — Nháp gợi ý thực đơn ở `menu_ai_drafts` và cổng giá trị AI

- Status: ACCEPTED
- Date: 2026-11-10
- Liên quan: AI-01..AI-10, NUT-05, NUT-06, NUT-08, NUT-09, NUT-13, NUT-19, NUT-20, NUT-23..NUT-26; module nutrition, ai-assistance; plan `docs/plans/active/2026-11-10-boarding-menu-ai.md`; thiết kế `docs/superpowers/specs/2026-11-09-boarding-ai-quality-gate-design.md`

## Bối cảnh
AI-07 (4) yêu cầu gợi ý thực đơn tuần (tùy chọn: mẫu ngày, thay một món). Flow `meal-management.md` từng ghi nháp ở bảng `ai_drafts` chung và dẫn ADR-0018, nhưng ADR-0018 là lưu trữ media S3. Model miễn phí trả kết quả không ổn định; user yêu cầu (09/11/2026) "không phải phản hồi nào cũng dùng", output phải có giá trị.

## Quyết định
1. Nháp lưu ở bảng `menu_ai_drafts` thuộc nutrition (V82), không dùng bảng `ai_drafts` chung (AI-01: nháp ở module nghiệp vụ). Lưu lưới món đã map về `dishId`, điểm, issue, bảng kiểm tiêu chí, metadata gọi model; không lưu prompt/response thô (AI-05); không xóa nháp (đo giá trị AI).
2. Cổng giá trị: phương án chỉ mang nhãn `source=AI` khi (a) qua guard cấu trúc (bí danh thuộc ứng viên của đúng ô, đủ ô, đủ ngày), (b) không còn issue `BLOCKING` của `MealPlanEvaluator` sau bước sửa, (c) điểm ≥ điểm phương án theo quy tắc chạy trên cùng dữ liệu. Không đạt ⇒ phương án theo quy tắc kèm `fallbackReason`; cả hai không qua cổng cứng ⇒ tuần/mẫu ngày vẫn trả phương án theo quy tắc (`source=RULE_BASED`, response `feasible=false`, lỗi chặn theo ngày) để PHT sửa tay — sửa 11/11/2026 theo phản hồi user khi thử màn (không hiện món nào thì không dùng được); công bố vẫn bị evaluator chặn (NUT-09). `NO_FEASIBLE` chỉ còn khi không dựng được lưới (ô không có món ứng viên) hoặc thay một món không có món đạt.
3. Model chỉ chọn trong món ứng viên do code lọc (gửi dưới bí danh `D1..Dn`); số liệu dinh dưỡng, giá chỉ do code tính (NUT-05, NUT-06).
4. Gọi đồng bộ trong deadline task (mặc định tuần 30s); đọc ngữ cảnh và lưu nháp ở transaction ngắn, không giữ kết nối DB khi chờ model.
5. Dùng nháp ghi qua `MealPlanService.saveMealPlan` (giữ khóa, kiểm tra hiện có); không công bố (NUT-08, AI-02). Mẫu ngày không ghi bảng mẫu (bộ mẫu không có trạng thái nháp) — chỉ đánh dấu đã dùng.

## Phương án đã cân nhắc
| Phương án | Ưu | Nhược |
| --- | --- | --- |
| Bảng `ai_drafts` chung mọi module | Một chỗ đo | Trái AI-01; JSON lỏng, khó kiểm theo nghiệp vụ |
| Dùng output model nếu qua schema | Đơn giản | Model có thể thua cách không AI; không chứng minh được giá trị |
| So với bộ tối ưu mạnh thay vì phương án quy tắc | Phương án tốt nhất | Gần như luôn thắng model ⇒ AI vô nghĩa; so không công bằng (thiết kế D5) |
| Gọi bất đồng bộ + polling | Không lo timeout proxy | Thêm executor, trạng thái, polling cho tác vụ dùng vài lần/tuần |

## Hệ quả
- Tác vụ AI mới (sức khỏe, giáo án) theo cùng khuôn: ngữ cảnh do code dựng, guard, evaluator/điểm, baseline; nháp ở bảng của module đó.
- Proxy/gateway VPS phải để read timeout ≥ 60s cho `POST /nutrition/menu-ai-drafts`.
- Đổi prompt ⇒ thêm file version mới (`prompts/menu/*_vN.txt`), không sửa file cũ đã dùng.
- Đổi trọng số điểm chỉ khi có kết quả eval (`MenuAiLiveEvalTest`).

## Còn chờ
- Kết quả eval với provider thật (AC-10): mốc bật demo 0 lần lọt lỗi chặn và AI thắng phương án quy tắc ≥ 60% case hợp lệ.
- P-02: điều khoản lưu dữ liệu của provider trước khi dùng cho tác vụ dữ liệu trẻ.
