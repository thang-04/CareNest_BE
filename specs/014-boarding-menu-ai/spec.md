# Feature014 — AI gợi ý thực đơn bán trú có cổng giá trị

Nguồn: `domain-spec.md`; thiết kế `docs/superpowers/specs/2026-11-09-boarding-ai-quality-gate-design.md`. Plan quản trị: `docs/plans/active/2026-11-10-boarding-menu-ai.md`.

## User stories
- US1 (P1): PHT nhận nháp kế hoạch tuần cho một khối, có nhãn nguồn, điểm AI và baseline, cảnh báo. AC-1, AC-2, AC-3, AC-4.
- US2 (P1): PHT dùng nháp vào tuần DRAFT hoặc bỏ; tuần đã công bố không nhận. AC-5, AC-6.
- US3 (P1): AI tắt, thiếu key, provider lỗi hoặc quá hạn vẫn có kết quả xác định. AC-7.
- US4 (P2): Gợi ý mẫu ngày cho khối và thay một món. AC-8, AC-9.
- US5 (P2): Nhóm đo được giá trị AI (eval tay, số liệu từ bảng nháp). AC-10.

## Functional requirements
- FR-01: `integration/ai` có `AiGateway` theo `AiTask`; mỗi task khai `dataClass`, chuỗi provider, deadline, số vòng sửa, prompt version.
- FR-02: Task `CHILD_DERIVED` không được cấu hình provider `may-train-on-data=true` (ứng dụng không khởi động). Provider thiếu key bị bỏ khỏi chuỗi.
- FR-03: 429/timeout/5xx/mất kết nối ⇒ thử provider kế tiếp nếu còn deadline; hết chuỗi ⇒ `AiClientException` có lý do.
- FR-04: Ứng viên mỗi ô (ngày × bữa × khối): món ACTIVE, đúng loại bữa theo cấu hình, có định lượng và dữ liệu dinh dưỡng cho mọi nhóm tuổi của khối; tối đa `candidate-limit` món; gửi model dưới alias `D1..Dn`.
- FR-05: Baseline tất định chạy trước khi gọi model; cả AI và baseline qua cùng bước code sửa.
- FR-06: Output model qua guard cấu trúc (schema, alias đúng ô, đủ ô, ngày nghỉ trống) rồi `MealPlanEvaluator`; còn `BLOCKING` ⇒ code sửa, rồi gửi lỗi gọn cho model sửa, tối đa `max-repair-rounds`.
- FR-07: Quyết định nguồn theo thiết kế mục 4 bước 8; `fallbackReason` ∈ `AI_DISABLED`, `AI_UNAVAILABLE`, `AI_TIMEOUT`, `AI_INVALID`, `AI_NOT_BETTER`.
- FR-08: Lý do của model bị xóa chữ số, bỏ câu có thuật ngữ y khoa, cắt 150 ký tự; số liệu hiển thị lấy từ evaluator.
- FR-09: API `/nutrition/menu-ai-drafts` (`MENU_MANAGE`): `POST` tạo, `GET /{id}`, `POST /{id}/dismiss`, `POST /{id}/apply`. Contract: `contracts/menu-ai-drafts.md`.
- FR-10: Apply tuần: tuần phải chưa PUBLISHED; ghi đè ô của khối đã chọn, giữ khối khác, qua `MealPlanService.saveMealPlan`; dữ liệu đổi ⇒ chạy lại evaluator, có `BLOCKING` ⇒ 409; gọi lại khi đã `APPLIED` ⇒ trả kết quả cũ. Không công bố.
- FR-11: Không lưu, không log prompt/response thô; log metadata một dòng mỗi lần gọi.

## Clarifications
### Session 2026-11-09/10
- Q: Output có giá trị là gì? → A: user: cổng cứng + phải không thua baseline.
- Q: Provider? → A: user: chuỗi theo task + loại dữ liệu; thực đơn Gemini Flash → Groq.
- Q: Đồng bộ hay bất đồng bộ? → A: user: đồng bộ ~30s.
- Q: Baseline mạnh hay không AI? → A: user: baseline không AI + cùng bước code sửa.
- Q: Code ở đâu? → A: user: nhánh AI G94-200, merge `dev` (đã làm 10/11).
- Q: Dị ứng trong thực đơn chung? → A: agent theo NUT-23 + evaluator: không lọc, hiển thị INFO (A3).

## Success criteria
- Không nháp `source=AI` nào có lỗi cấu trúc hoặc `BLOCKING` (test + eval).
- Eval tay: AI thắng baseline ≥ 60% case hợp lệ trước khi bật `AI_ENABLED` cho demo; báo cáo lưu `docs/quality/ai-eval/`.
- Verify full pass; snapshot OpenAPI đổi có chủ đích.
