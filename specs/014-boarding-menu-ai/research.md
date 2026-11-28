# Research — Feature014

- **Evaluator dùng lại được:** `MealPlanEvaluator.evaluate(days, groups, dishes, normOf)` tĩnh, package-private (`service/meals/impl/MealPlanEvaluator.java:245`); `Result.publishable()` = không `BLOCKING` (`:167-170`). Dựng input đang nằm private trong `MealPlanServiceImpl` (`:558-600`) ⇒ tách ra helper cùng package để service nháp dùng chung.
- **Dị ứng:** evaluator chỉ ghi `ALLERGENS` mức INFO (`MealPlanEvaluator.java:772-796`); NUT-23 xử lý dị ứng nặng bằng món thay thế, không đổi thực đơn chung ⇒ không lọc ứng viên theo dị ứng (sửa thiết kế 09/11).
- **Quyền:** `MealPlanController` dùng `@PreAuthorize("@permissionGuard.has('MENU_MANAGE')")` (`controller/meals/MealPlanController.java:67,80`); service kiểm lại `Permission.MENU_MANAGE` (`MealPlanServiceImpl.java:199-201`).
- **Ghi tuần:** `MealPlanService.saveMealPlan(currentUser, weekStart, MealPlanRequest)` nhận cả tuần (`MealPlanRequest.DayInput/ItemInput`) ⇒ apply dựng request = tuần hiện có, thay ô của khối chọn.
- **Mẫu ngày:** `MenuTemplateService` chỉ có `CatalogStatus`, không có DRAFT (`MenuTemplateService.java:13-21`) ⇒ nháp mẫu ngày không ghi bảng mẫu.
- **Lõi AI hiện có:** `AiClient` chỉ có `draftChildSummary` (`integration/ai/AiClient.java`), `AiProperties` một LLM + embedding + RAG; adapter OpenAI-compatible `ExternalAiClient` (RestClient, retry 429, ẩn key trong `toString`). Slice dùng nó đã gỡ (commit `d6a4d87`) ⇒ thay bằng `AiGateway` tổng quát, bỏ phần riêng nhận xét và embedding (lấy lại từ `0d8938a` khi cần).
- **Provider:** Gemini có endpoint OpenAI-compatible (`/v1beta/openai`) và `response_format` JSON; Groq OpenAI-compatible. Hạn mức free đổi theo thời gian ⇒ cấu hình qua env, eval đo lại trước demo. Eval cũ: Groq free 429 khi gọi dồn (`docs/modules/ai-assistance.md`).
- **Migration:** `dev` tới `V81__selected_parent_account.sql`; hook chặn sửa migration đã commit và trùng version.
