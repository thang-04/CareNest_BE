# Domain spec — Feature014 AI gợi ý thực đơn bán trú

Nguồn: AI-01..10, NUT-05, NUT-06, NUT-08, NUT-09, NUT-13, NUT-17..20, NUT-23..25; flow `docs/business/flows/meal-management.md` mục "AI gợi ý thực đơn"; thiết kế đã duyệt `docs/superpowers/specs/2026-11-09-boarding-ai-quality-gate-design.md` (user 09–10/11/2026).

## 1. Domain Overview
PHT phụ trách dinh dưỡng soạn kế hoạch thực đơn tuần, thực đơn mẫu ngày. AI đề xuất phương án chọn từ danh mục món có sẵn; hệ thống kiểm bằng logic xác định và chỉ giữ phương án có giá trị; PHT xem, dùng hoặc bỏ, rồi tự công bố.

## 2. Scope
Trong: gợi ý kế hoạch tuần theo khối (bắt buộc), gợi ý thực đơn mẫu một ngày, gợi ý thay một món trong tuần DRAFT (tùy chọn theo AI-07). Ngoài: món thay thế dị ứng (NUT-23), tự công bố, sửa tuần PUBLISHED, sửa dữ liệu dinh dưỡng/giá/định mức, sức khỏe, giáo án, nhận xét trẻ.

## 3. Actors & Roles
PHT hoặc HT có quyền `MENU_MANAGE` (AUTH-20, NUT-08). Bếp, GV, phụ huynh không dùng tính năng.

## 4. Domain Concepts
- **Nháp gợi ý thực đơn** (`menu_ai_drafts`): phương án đề xuất, chưa phải thực đơn.
- **Món ứng viên**: món đang dùng, đúng loại bữa, đủ dữ liệu dinh dưỡng cho mọi nhóm tuổi của khối.
- **Baseline**: phương án hệ thống tự điền khi không có AI.
- **Điểm phương án** (0–100): sát định mức, giá, đa dạng, cảnh báo.
- **Cổng cứng**: không lỗi cấu trúc, không issue `BLOCKING` của evaluator.

## 5. Domain Relationships
Nháp → tuần (`week_start` + khối) hoặc mẫu ngày (khối) hoặc một ô tuần. Nháp tham chiếu món (`dishId`) của danh mục; không tham chiếu trẻ.

## 6. User Requirements
- UR-1: PHT bấm "AI gợi ý" cho tuần/khối, nhận lưới năm ngày kèm nguồn (AI/Theo quy tắc), điểm, cảnh báo, lý do theo ngày.
- UR-2: PHT dùng nháp vào tuần DRAFT (chỉ khối đã chọn) hoặc bỏ.
- UR-3: AI tắt/lỗi vẫn nhận phương án theo quy tắc hoặc thông báo không có phương án kèm nguyên nhân.
- UR-5 (user, 10/11/2026: "gợi ý món ăn mà"): danh mục chưa ghép được tuần/ngày đạt định mức vẫn hiện lưới món theo quy tắc kèm lỗi theo ngày, nhãn chưa đạt; PHT dùng làm điểm bắt đầu rồi sửa tay. Công bố vẫn theo NUT-09.
- UR-4: Gợi ý mẫu ngày và thay một món dùng cùng cách.

## 7. Business Rules
AI-01 (nháp, `source`, model, thời điểm), AI-02 (người duyệt công bố), AI-03 (chạy khi AI tắt), AI-04/AI-08 (không dữ liệu trẻ), AI-05 (log metadata), AI-06 (qua `integration/`), AI-07 (4), AI-09 (Gemini, Groq), AI-10 (giọng văn), NUT-05/06 (AI không tạo số liệu dinh dưỡng), NUT-08 (công bố cả tuần, đã công bố không sửa), NUT-09 (kiểm xác định), NUT-19, NUT-20, NUT-24, NUT-25.

## 8. Business Processes
`[S]` PHT chọn tuần + khối, nhập mùa, mong muốn → `[S]` dựng ứng viên, baseline, gọi AI, kiểm, sửa, chấm, quyết định → `[S]` lưu nháp, hiển thị → `[S]` PHT dùng (ghi vào tuần DRAFT) | bỏ → `[S]` PHT sửa tiếp, công bố qua luồng hiện có.

## 9. State / Lifecycle
`PENDING_REVIEW` → `APPLIED` | `DISMISSED`. `NO_FEASIBLE` là trạng thái cuối (không dựng được lưới món, không dùng được). Nháp chưa đạt định mức (`feasible=false`) vẫn là `PENDING_REVIEW`. Không quay lại.

## 10. Business Constraints
- Dị ứng: thực đơn chung không loại món có chất dị ứng (NUT-23 xử lý bằng món thay thế); hiển thị chất dị ứng như evaluator (`ALLERGENS`, mức INFO), không trừ điểm.
- Tuần PUBLISHED không nhận nháp. Dữ liệu đổi giữa lúc tạo và dùng ⇒ kiểm lại.
- `source=AI` chỉ khi AI qua cổng cứng và điểm ≥ baseline.

## 11. Non-Functional Requirements
Tạo nháp đồng bộ, trả trong deadline task (mặc định 30s tuần). Provider free; task dữ liệu công khai được dùng provider có thể train trên dữ liệu; không log prompt/response.

## 12. Acceptance Criteria
Xem `spec.md` và plan quản trị `docs/plans/active/2026-11-10-boarding-menu-ai.md` (AC-1..AC-10).

## 13. Domain Terminology
Khối (`MenuBlock`: nhà trẻ/mẫu giáo), bữa (`MealSession`), kế hoạch tuần (`MealPlanWeek`), mẫu ngày (`MenuTemplate`).

## 14. Assumptions & Open Questions
- A1 (agent chọn, chờ soát): trọng số điểm 45/20/25/10; baseline = điền theo mẫu ngày ACTIVE của khối, không có thì quay vòng món ứng viên.
- A2 (agent chọn): nháp mẫu ngày không ghi vào `menu_templates`; "dùng" chỉ đánh dấu, Web điền form tạo mẫu và lưu qua API mẫu hiện có (mẫu không có trạng thái DRAFT).
- A3 (sửa thiết kế 09/11): bỏ lọc cứng chất dị ứng khỏi ứng viên vì mâu thuẫn NUT-23 và evaluator hiện hành.
- Không có câu hỏi nghiệp vụ chặn. C01, C04 không ảnh hưởng phần này.
