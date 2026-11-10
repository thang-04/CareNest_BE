# Thiết kế AI bán trú — lõi AI dùng chung và cổng giá trị

Trạng thái: **đã duyệt thiết kế (09/11/2026)**, chưa triển khai. Thay bản nháp `2026-11-07-boarding-ai-design.md` (đã bị xóa khỏi worktree); phạm vi và luồng người dùng của bản đó được chép vào mục 1a, 1b.

## 1. Mục tiêu

- Phase 1: AI gợi ý thực đơn cho luồng bán trú (AI-07 (4)), theo thứ tự (a) kế hoạch tuần (bắt buộc), (b) thực đơn mẫu một ngày, (c) thay một món trong tuần còn DRAFT.
- Dùng model qua API **miễn phí**. Mọi provider đi qua `AiClient`/gateway (AI-06, AI-09).
- **Không phải phản hồi nào của model cũng được dùng.** Nháp chỉ hiện với nhãn AI khi qua cổng cứng tất định **và** điểm không thấp hơn baseline không dùng AI. Ngược lại, hệ thống trả baseline và ghi rõ lý do.
- Lõi AI viết sao cho sức khỏe (HLT-04/05) và giáo án (AI-07 (2)) chỉ cần cắm phần riêng của luồng, không viết lại.

## 1a. Không thuộc phạm vi

- AI không nhập, suy đoán hay sửa giá trị dinh dưỡng nguồn, định mức, giá, số suất, tồn kho, kiểm nhận hàng, lưu mẫu, hồ sơ sức khỏe (NUT-05, NUT-06).
- AI không tự công bố thực đơn, không sửa tuần PUBLISHED, không quyết định số suất thay thế dị ứng, không nhận tên/ID/ngày sinh/ảnh trẻ (AI-02, AI-04, NUT-08).
- Gợi ý món thay thế dị ứng (NUT-23) chưa có BE quản lý món thay thế ⇒ ngoài phase 1; khi làm thì lọc cứng theo chất dị ứng trước, AI chỉ xếp hạng/giải thích món đã qua lọc.
- Demo UI hiện có trên FE (`source: demo`, `RULE_BASED_FALLBACK`) và kết quả eval nhận xét trẻ không là bằng chứng AI thực đơn đã chạy thật.

## 1b. Luồng người dùng

```text
PHT chọn tuần + khối (hoặc mẫu ngày, hoặc một ô của tuần DRAFT) → nhập mùa, mong muốn tùy chọn → "AI gợi ý"
    → BE chạy pipeline mục 4 → lưu menu_ai_drafts → Web hiện lưới, nhãn nguồn, điểm, cảnh báo
    → PHT bỏ | dùng vào DRAFT rồi sửa → PHT công bố tuần qua cổng hiện có
```

- **Tuần:** server xét mọi nhóm tuổi thuộc khối; apply chỉ điền khối được chọn, giữ khối còn lại.
- **Mẫu một ngày:** kết quả là bữa × món cho khối; apply điền form mẫu DRAFT, lưu vẫn qua kiểm tra mẫu của BE.
- **Thay một món:** một `toDishId` + lý do cho đúng ô; chỉ áp vào tuần còn DRAFT.

## 2. Quyết định đã chốt

| # | Quyết định | Lý do |
|---|---|---|
| D1 | Code trên nhánh AI `feature/G94-200-BE-FEAT-28-ai-assessment-drafting` (worktree `CareNest_BE-ai`), merge `dev` vào để cập nhật; viết lại `integration/ai` theo task + chuỗi provider ngay trên nhánh đó, làm thực đơn trước | User chốt 10/11/2026. Nhánh đang sau `dev` 245 commit, toàn bộ việc AI chưa commit; migration V7–V9 trùng số với `dev` (đã tới V81) ⇒ phải đánh lại số; slice nhận xét cũ (`Observation`, `ChildSummary`) lệch module observation mới của `dev` (V43) |
| D2 | Chuỗi provider theo task và `dataClass`: thực đơn `PUBLIC_CATALOG` ⇒ Gemini Flash → Groq → baseline; dữ liệu trẻ `CHILD_DERIVED` ⇒ chỉ provider `may-train-on-data=false` (Groq) | Dữ liệu thực đơn không chứa thông tin trẻ nên dùng được Gemini free; prompt thực đơn lớn dễ gặp giới hạn token/phút của Groq free |
| D3 | Gọi đồng bộ, deadline mặc định 30s; baseline chạy song song nên luôn có kết quả | PHT dùng vài lần/tuần; tránh thêm executor, trạng thái và polling |
| D4 | Cổng giá trị = cổng cứng + điểm AI ≥ điểm baseline | Người dùng chọn phương án B |
| D5 | Baseline = cách hệ thống làm khi không có AI (điền theo thực đơn mẫu/vòng quay món) + cùng bước code sửa, **không** dùng bộ tối ưu mạnh | So công bằng; phần thắng đến từ khung món model chọn. Bộ tối ưu mạnh gần như luôn thắng model ở các số đo được |
| D6 | Nháp lưu ở bảng `menu_ai_drafts` thuộc nutrition, không dùng bảng `ai_drafts` chung | AI-01 (nháp ở module nghiệp vụ). Bản 07/11 dẫn ADR-0018 cho `ai_drafts`, nhưng ADR-0018 là media S3 ⇒ cần ADR mới cho quyết định này |
| D7 | Phase 1 không có RAG | Phép tính dinh dưỡng lấy từ DB và `MealPlanEvaluator`; RAG để dành cho giáo án |

## 3. Kiến trúc

```text
integration/ai/                       không import domain, không dùng repository (AI-06)
  AiTask            MENU_WEEKLY, MENU_DAILY, MENU_DAY_FIX
                    mỗi task: dataClass, prompt@version, JSON schema, maxTokens, temperature
  AiGateway         complete(task, messages, deadline) → AiRawResult{json, provider, model,
                    promptVersion, inputTokens, outputTokens, latencyMs}
  ProviderChain     đọc carenest.ai.providers[] và carenest.ai.tasks.<task>
                    dataClass CHILD_DERIVED ⇒ bỏ provider may-train-on-data=true
                    chuỗi rỗng sau khi lọc ⇒ lỗi lúc khởi động (không âm thầm gửi đi)
                    429 / timeout / 5xx ⇒ chuyển provider kế tiếp nếu còn deadline
  OpenAiCompatibleAdapter   Groq; Gemini qua endpoint OpenAI-compatible; response_format JSON
  PromptTemplateLoader      file có version trong resources/prompts/menu/ (giữ cách làm của BE-ai)
  AiClientException         RATE_LIMITED | TIMEOUT | PROVIDER_UNAVAILABLE | DISABLED

service/meals/ (nutrition)
  MenuCandidateBuilder    lọc cứng món ứng viên cho từng ô
  RuleBasedMenuPlanner    baseline tất định
  MenuPlanRepairer        đổi món ở ô có lỗi trong tập ứng viên (dùng chung cho AI và baseline)
  MenuQualityGate         guard cấu trúc + MealPlanEvaluator + MenuScore
  MenuAiDraftService      điều phối, lưu nháp, apply/dismiss
```

Cấu hình (`.env.example` ghi đủ biến; hạn mức free thay đổi theo thời gian ⇒ không hard-code, kiểm lại trước demo):

```yaml
carenest:
  ai:
    enabled: ${AI_ENABLED:false}
    providers:
      - id: gemini
        base-url: ${AI_GEMINI_BASE_URL:https://generativelanguage.googleapis.com/v1beta/openai}
        model: ${AI_GEMINI_MODEL:}
        api-key: ${AI_GEMINI_API_KEY:}
        may-train-on-data: true
      - id: groq
        base-url: ${AI_GROQ_BASE_URL:https://api.groq.com/openai/v1}
        model: ${AI_GROQ_MODEL:}
        api-key: ${AI_GROQ_API_KEY:}
        may-train-on-data: false
    tasks:
      MENU_WEEKLY:  { chain: [gemini, groq], data-class: PUBLIC_CATALOG, deadline: 30s, max-repair-rounds: 2 }
      MENU_DAILY:   { chain: [gemini, groq], data-class: PUBLIC_CATALOG, deadline: 20s, max-repair-rounds: 2 }
      MENU_DAY_FIX: { chain: [gemini, groq], data-class: PUBLIC_CATALOG, deadline: 15s, max-repair-rounds: 1 }
```

Provider thiếu `api-key` thì bị bỏ khỏi chuỗi. `enabled=false` hoặc chuỗi rỗng ⇒ chỉ chạy baseline, `fallback_reason=AI_DISABLED`.

## 4. Pipeline cổng giá trị (gợi ý tuần; mẫu ngày và thay món dùng cùng các bước, ít ô hơn)

1. **Dựng ngữ cảnh (code):** các nhóm tuổi thuộc khối, định mức hiệu lực (NUT-17, NUT-24, NUT-25), bữa phục vụ, giá suất (NUT-19), món của 2 tuần trước, mùa, mong muốn của PHT (≤ 300 ký tự, chỉ là ưu tiên).
2. **Lọc ứng viên (code) cho từng ô bữa × khối:** món đang dùng, đúng loại bữa, đủ dữ liệu dinh dưỡng và nguồn gốc ĐV/TV, không chứa chất dị ứng mức nặng có trong tập chất cần tránh (dạng tổng hợp, không có danh tính trẻ — AI-04, NUT-09). Rút còn tối đa `candidate-limit` (mặc định 15) món/ô theo độ đa dạng. Món gửi cho model dưới dạng alias `D1..Dn` kèm loại món, 6 chỉ số, giá/suất, nhãn chính ⇒ ít token, model không bịa được ID thật.
3. **Baseline (code, chạy song song từ đầu):** `RuleBasedMenuPlanner` điền theo thực đơn mẫu của khối nếu có, không có thì quay vòng món ứng viên, tránh lặp; sau đó chạy `MenuPlanRepairer`.
4. **Gọi model** theo chuỗi provider: trả JSON `{days:[{date, items:[{session, dish:"D7"}], reason}]}`, `reason` ≤ 150 ký tự/ngày.
5. **Guard cấu trúc:** đúng schema; alias thuộc tập ứng viên của **đúng ô**; đủ ô, không thừa ô; ngày nghỉ để trống. Sai ⇒ coi như output lỗi của vòng đó.
6. **Đánh giá:** `MealPlanEvaluator` ⇒ issue BLOCKING/WARNING; `MenuScore` ⇒ điểm 0–100.
7. **Sửa** (còn deadline, tối đa `max-repair-rounds`):
   a. `MenuPlanRepairer` đổi món ở ô gây lỗi trong tập ứng viên (tất định, rẻ);
   b. còn lỗi BLOCKING ⇒ gửi model danh sách lỗi gọn theo ngày (vd. "Thứ 3: thiếu đạm ĐV; D7 lặp với Thứ 2"), yêu cầu chỉ sửa các ngày đó; quay lại bước 5.
8. **Quyết định:**
   - AI qua cổng cứng **và** `scoreAi ≥ scoreBaseline` ⇒ `source=AI`;
   - ngược lại và baseline qua cổng cứng ⇒ `source=RULE_BASED`, `fallback_reason` ∈ `AI_INVALID | AI_NOT_BETTER | AI_UNAVAILABLE | AI_DISABLED | AI_TIMEOUT`;
   - cả hai không qua ⇒ `status=NO_FEASIBLE`, kèm nguyên nhân theo ô (vd. "bữa chiều khối nhà trẻ: chỉ 3 món đủ dữ liệu dinh dưỡng") để PHT biết danh mục cần bổ sung gì.
9. **Lý do của model:** xóa mọi chữ số, bỏ câu có thuật ngữ y khoa/chẩn đoán, cắt độ dài. Số liệu hiển thị lấy từ evaluator, không lấy từ model.

**Cổng cứng:** không lỗi guard cấu trúc, không issue BLOCKING của evaluator, không xung đột dị ứng. WARNING (vượt giá suất, lặp món, lệch % kcal bữa) không chặn mà trừ điểm.

**MenuScore (tất định, có unit test):**

| Thành phần | Trọng số | Cách tính |
|---|---|---|
| Sát định mức | 45% | trung bình theo ngày và nhóm tuổi: kcal, cơ cấu P:L:G, 5 chỉ số gam, % kcal từng bữa; trong khoảng = điểm tối đa, lệch giảm tuyến tính; mục chưa đặt định mức thì bỏ khỏi mẫu số |
| Giá | 20% | trong giá suất = đủ điểm; vượt giảm theo % vượt |
| Đa dạng | 25% | số món khác nhau / số ô; trừ điểm món lặp trong tuần và trùng 2 tuần trước |
| Cảnh báo | 10% | trừ theo số WARNING |

Trọng số đặt trong cấu hình nhưng chỉ chỉnh khi có kết quả eval.

## 5. Dữ liệu

Bảng `menu_ai_drafts` (số version migration chọn theo `dev` lúc làm):

| Cột | Ghi chú |
|---|---|
| `id`, `version` | khóa lạc quan |
| `kind` | `MENU_WEEKLY`, `MENU_DAILY`, `MENU_DAY_FIX` |
| `block`, `week_start`, `menu_template_id`, `target_date`, `target_session`, `from_dish_id` | theo `kind` |
| `status` | `PENDING_REVIEW`, `APPLIED`, `DISMISSED`, `NO_FEASIBLE` |
| `source`, `fallback_reason` | `AI` / `RULE_BASED` |
| `provider`, `model`, `prompt_version`, `repair_rounds`, `latency_ms` | metadata |
| `output_json` | lưới món đã map về `dishId` thật, lý do đã làm sạch; không đổi sau khi tạo |
| `score_json` | `{ai, baseline, breakdown}` |
| `issues_json` | issue của phương án được chọn |
| `input_fingerprint` | hash phiên bản món/định mức/cấu hình dùng lúc tạo |
| `request_json` | mùa, mong muốn (đã cắt độ dài) |
| `created_by`, `created_at`, `decided_by`, `decided_at` | |

Không lưu prompt hay response thô (AI-05).

## 6. API

Dưới `/nutrition/menu-ai-drafts`, quyền `MENU_MANAGE`, phạm vi theo quy tắc quyền hiện hành:

- `POST` — tạo nháp (đồng bộ). Trả lưới món, issue, điểm AI và baseline, `source`, `fallbackReason`, lý do theo ngày.
- `GET /{id}` — xem.
- `POST /{id}/dismiss` — đánh dấu bỏ.
- `POST /{id}/apply`:
  - tuần/mẫu đích phải còn DRAFT; tuần PUBLISHED ⇒ 409;
  - `input_fingerprint` đổi ⇒ chạy lại evaluator trên dữ liệu mới; có BLOCKING mới ⇒ 409 kèm danh sách issue;
  - tuần: chỉ điền khối được chọn, giữ khối còn lại; thay món: chỉ đổi đúng ô;
  - gọi lại khi đã `APPLIED` ⇒ trả kết quả lần trước (idempotent, theo cơ chế idempotency header hiện có);
  - **không** công bố; công bố vẫn qua endpoint publish hiện có (NUT-08, AI-02).

## 7. Web

- Bỏ contract demo `/menu/ai-suggestions`; `AiWeekComposer`, `AiTemplateComposer`, gợi ý thay món trong `WeeklyMenuPage` gọi API mới.
- Hiện badge `AI` hoặc `Theo quy tắc` kèm lý do fallback dễ hiểu; điểm AI và baseline; issue lấy từ BE.
- Tuần đã có món ⇒ cho xem phần sẽ bị thay trước khi xác nhận apply.
- Đang tạo ⇒ spinner, chặn bấm lặp; `NO_FEASIBLE` ⇒ hiện nguyên nhân theo ô.

## 8. Lỗi và vận hành

- Lỗi provider không làm hỏng nghiệp vụ (AI-03): luôn có baseline hoặc `NO_FEASIBLE` rõ ràng; soạn tay vẫn hoạt động.
- Hết deadline giữa vòng sửa ⇒ dùng phương án tốt nhất đã qua cổng tính đến lúc đó.
- Proxy/gateway trên VPS phải để read timeout ≥ 60s cho route này (kiểm cấu hình nginx khi triển khai).
- Log chỉ metadata: task, provider, model, token, latency, số vòng sửa, quyết định, `fallback_reason`.

## 9. Đo giá trị

- **Eval chạy tay** (giống `AiLiveEvalTest`, không chạy trong CI): khoảng 8 case dữ liệu giả lập — đủ món; thiếu dữ liệu dinh dưỡng; dị ứng mức nặng; ngân sách chặt; ít món; mong muốn có câu chèn lệnh; danh mục lớn (thử giới hạn token); provider lỗi. Mỗi case chạy 3 lần.
- Báo cáo: tỉ lệ qua cổng cứng, **tỉ lệ thắng baseline**, số vòng sửa, latency, token, provider thực dùng. Lưu vào `docs/quality/ai-eval/<ngày>/`.
- **Mốc bật cho demo:** thắng baseline ≥ 60% số case hợp lệ và 0 lần lọt lỗi BLOCKING. Không đạt ⇒ thêm prompt version mới rồi chạy lại, hoặc ghi trung thực kết quả vào báo cáo.
- **Khi dùng thật:** đo từ `menu_ai_drafts` — tỉ lệ `source=AI`, tỉ lệ APPLIED/DISMISSED, mức PHT sửa sau khi apply (so món giữa nháp và tuần lúc công bố).

## 10. Mở rộng sau phase 1 (chưa viết code)

Mỗi luồng chỉ cắm 4 phần: ngữ cảnh, guard, evaluator/điểm, baseline.

| Luồng | dataClass ⇒ chuỗi | Cổng giá trị | Baseline |
|---|---|---|---|
| Sức khỏe — giải thích xu hướng (HLT-04, HLT-05) | `CHILD_DERIVED` ⇒ Groq | z-score WHO tính bằng code; mọi con số phải có trong facts; cấm từ chẩn đoán (port `AiOutputGuard`); mỗi ý trỏ về một mốc đo | câu template theo phân loại WHO |
| Giáo án — gợi ý hoạt động, tên tuần (EDU-20, AI-07 (2)) | `PUBLIC_CATALOG` ⇒ Gemini → Groq | mỗi hoạt động gắn `goalId` thuộc input; phủ đủ mục tiêu của tuần; độ dài; không lặp; RAG tài liệu chương trình (port từ `BE-ai`) | nội dung gợi ý sẵn có của chương trình |
| Nhận xét trẻ (đã có ở `BE-ai`) | `CHILD_DERIVED` ⇒ Groq | guard hiện có | template hiện có |

## 11. Kiểm chứng chấp nhận

1. AI tắt, thiếu key hoặc mọi provider lỗi ⇒ nhận baseline (hoặc `NO_FEASIBLE`) trong deadline; soạn tay và công bố vẫn chạy.
2. Model trả alias ngoài tập ứng viên, sai ô, thiếu ô, sai schema ⇒ không bao giờ được lưu với `source=AI`.
3. Phương án có BLOCKING hoặc xung đột dị ứng không được apply; issue trong preview khớp với evaluator sau khi lưu.
4. `source=AI` chỉ xuất hiện khi `scoreAi ≥ scoreBaseline` (unit test cho quyết định ở bước 8).
5. Task `CHILD_DERIVED` cấu hình chuỗi có provider `may-train-on-data=true` ⇒ ứng dụng không khởi động được (test cấu hình).
6. Apply: tuần PUBLISHED, dữ liệu đổi giữa lúc tạo và apply, gọi hai lần, thiếu quyền ⇒ kết quả xác định như mục 6.
7. Log và bảng nháp không chứa prompt/response thô.
8. Eval đạt mốc ở mục 9 trước khi bật `AI_ENABLED` trên môi trường demo.

## 12. Việc mở

- Viết ADR mới cho `menu_ai_drafts` và cơ chế cổng giá trị; sửa tham chiếu ADR-0018 trong `docs/business/flows/meal-management.md`.
- C01 (cách tách suất dị ứng) và C04 (công bố khi thiếu dữ liệu dinh dưỡng) vẫn chờ khách hàng; pipeline chỉ dùng tập chất cần tránh dạng tổng hợp và báo "thiếu dữ liệu", không tự chọn quy tắc.
- Chuẩn bị nhánh AI trước khi code: commit WIP hiện có (mã G94-200, cần user cho phép), merge `origin/dev`, đánh lại số migration AI sau version lớn nhất của `dev`, quyết định giữ hay tạm gỡ slice nhận xét cũ và RAG (pgvector) khỏi phase 1.
