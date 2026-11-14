---
title: Giải thích xu hướng sức khỏe có cấu trúc (AI)
status: in-progress        # draft → approved (chỉ user duyệt) → in-progress → done | blocked | cancelled
owner: carenestg94@gmail.com
jira: G94-180 / BE-FEAT-43
branch: feature/G94-180-BE-FEAT-43-child-health-pickup
modules: [health, ai-assistance]
rules: [HLT-02, HLT-04, HLT-05, HLT-13, HLT-21, AI-01, AI-02, AI-03, AI-04, AI-05, AI-06, AI-07, PAR-03]
risk: high           # migration + integration/ + đổi contract API + vùng dùng chung utils/enums
created: 2026-11-14
updated: 2026-11-14
---
# Giải thích xu hướng sức khỏe có cấu trúc (AI)

## Context
- Hiện AI chỉ trả một đoạn `summary` 3–5 câu (`HealthTrendServiceImpl.java:59-64`, `maxTokens` 400 ở `:98`). Kiểm từ cấm trên cả đoạn; trúng ⇒ 503 (`:106-109`).
- Cảnh báo tính xác định ở `HealthGrowth.java:61-113`. `TrendWarning(level, text)` không có mã (`PublishedHealthResponse.java:61`), nên output AI không tham chiếu được cảnh báo.
- `AiClient` chỉ trả text. Body không có `response_format` (`OpenAiCompatibleAiClient.java:53-59`). Không đọc `finish_reason`, không log số token.
- Mục tiêu: AI trả 5 mục cố định; mỗi điểm cần chú ý gắn một cảnh báo; kiểm từng mục; người duyệt sửa từng mục.
- Consumer:
  - FE chỉ gọi POST analyze (`docs/system/FE_API_CALLS.md:246`).
  - `AiTrendPanel.jsx` đọc `summary`, `warnings`; có nút tải lại (`:25`, `:37`).
  - Chưa client nào gọi approve. Phụ huynh chưa đọc `interpretation`. APP chưa dùng.

## Quyết định đã chốt
1. AI viết 5 mục: `overview`, `attentionPoints`, `positives`, `followUps`, `limitations` — user 14/11/2026. Giữ tên `followUps`.
2. Cảnh báo, mức cảnh báo và loại gợi ý được phép do code tính; AI chỉ viết lời — HLT-02, HLT-04.
3. Phụ huynh thấy `overview`, `attentionPoints`, `positives`, `limitations`; ẩn `followUps` — user 14/11/2026 (Q2).
4. Không có loại gợi ý "đi khám / chuyển y tế". Cảnh báo chỉ để cảnh báo, nhân sự tự quyết — user 14/11/2026 (Q3).
5. Thêm rule HLT-21 (PROPOSED) — user 14/11/2026 (Q4).
6. Body approve chỉ nhận `{ content }` — user 14/11/2026 (Q5).
7. JSON hỏng hoặc bị cắt cụt ⇒ 503, không retry; enum ở `utils/enums`; giới hạn độ dài như AC-5 — user 14/11/2026.
8. Gợi ý "đo lại" không lặp lại: mỗi lần đo chỉ gợi ý đo lại một lần — user 14/11/2026 (Q2, "xử lý câu thừa").
9. Có số đo mới sau lần phân tích ⇒ bản cũ đánh dấu `stale`; phụ huynh vẫn thấy bản cũ kèm ghi chú — user 14/11/2026 (Q6 = A).

## Làm rõ nghiệp vụ
- **Hiểu nghiệp vụ:**
  - Actor: HT/PHT/GV tạo và duyệt; phụ huynh xem bản đã duyệt.
  - Flow: [S] tính cảnh báo → [S] AI viết nháp → [B] nhân sự duyệt và sửa → [S] phụ huynh thấy.
- **Đối chiếu:**
  - `docs/modules/health.md:32` ghi `summary` do AI viết và body approve `{ summary }`. Plan đổi body thành `{ content }`; `summary` là văn bản render từ `content`.
  - `docs/modules/ai-assistance.md:9` đã dự kiến "output schema validation".
- **Ảnh hưởng:**
  - Code: `health`, `integration/ai`.
  - Dữ liệu cũ: bản ghi V88 có `content = null`.
  - FE: `AiTrendPanel` không vỡ. Phần hiển thị khối, màn duyệt và dòng "đã có số đo mới" làm ở plan FE riêng.
- **Rule HLT-21 (PROPOSED, ghi vào `BUSINESS_RULES.md` ở Phase 1):** Giải thích xu hướng sức khỏe (AI-07 mục 3) gồm 5 mục:
  - Tổng quan.
  - Điểm cần chú ý: mỗi điểm gắn một cảnh báo xác định (HLT-02); AI không thêm cảnh báo.
  - Điểm tích cực.
  - Gợi ý theo dõi, chỉ các loại hệ thống cho phép:
    - Theo dõi ở kỳ cân đo sau: luôn được phép.
    - Trao đổi với phụ huynh: khi có cảnh báo.
    - Đo lại để xác nhận: khi có cảnh báo, kỳ cân đo của lần đo mới nhất đang diễn ra (HLT-13), và lần đo đó chưa từng được gợi ý đo lại.
    - Không gợi ý đưa trẻ đi khám hay chuyển y tế.
  - Giới hạn dữ liệu.

  Phụ huynh thấy bản đã duyệt, trừ mục gợi ý theo dõi. Có số đo mới được công bố sau lần phân tích ⇒ bản cũ được đánh dấu cần phân tích lại; phụ huynh vẫn thấy bản cũ, kèm ghi chú rằng giải thích theo các lần đo trước.

### Câu hỏi mở
- [x] Q1: Mã Jira và nhánh.
- [x] Q2: Phụ huynh thấy mục nào.
- [x] Q3: Gợi ý đi khám.
- [x] Q4: Rule mới.
- [x] Q5: Body approve.
- [x] Q6: Bản đã duyệt cũ khi có số đo mới.

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| Q1 | Nhánh G94-180 (`feature/G94-180-BE-FEAT-43-child-health-pickup`) | user | 2026-11-14 | plan frontmatter |
| Q2 | Theo đề xuất: ẩn `followUps` với phụ huynh. Câu "đo lại" thừa ⇒ mỗi lần đo chỉ gợi ý đo lại một lần | user | 2026-11-14 | HLT-21 (Phase 1) |
| Q3 | B: bỏ hẳn loại `REFER_HEALTH_STAFF`; chỉ cảnh báo | user | 2026-11-14 | HLT-21 (Phase 1) |
| Q6 | A: đánh dấu `stale`; phụ huynh vẫn thấy bản cũ kèm ghi chú | user | 2026-11-14 | HLT-21 (Phase 1) |
| Q4 | Thêm HLT-21 | user | 2026-11-14 | `BUSINESS_RULES.md` (Phase 1) |
| Q5 | A: chỉ `{ content }` | user | 2026-11-14 | `health.md`, `openapi.yaml` (Phase 3) |

## Acceptance criteria
- **AC-1** (HLT-02): Given lần đo có sụt ≥ 0,2 kg / chiều cao không tăng ≥ 90 ngày / z < −2 / BMI z ngoài ±2 / BMI z đổi ≥ 1 / không bất thường. When tính cảnh báo. Then `code` lần lượt là `WEIGHT_DROP`, `HEIGHT_STALL`, `WFA_LOW`/`HFA_LOW`, `BMI_OUT_OF_RANGE`, `BMI_Z_JUMP`, `NORMAL`.
- **AC-2** (HLT-21): `allowedFollowUps(warnings, remeasurePossible)`:
  - Chỉ có `NORMAL` ⇒ `{RECHECK_NEXT_ROUND}`.
  - Có cảnh báo khác `NORMAL` ⇒ thêm `TALK_TO_PARENT`; thêm `REMEASURE` chỉ khi `remeasurePossible`.
  - `remeasurePossible` = kỳ cân đo của lần đo mới nhất đang diễn ra **và** chưa có bản giải thích nào của trẻ vừa chứa `REMEASURE` vừa có lần đo mới nhất đó trong `measurement_ids`. Given lần phân tích 1 đã gợi ý đo lại, GV cân lại, số không đổi. When phân tích lần 2. Then không còn `REMEASURE`.
- **AC-3** (AI-05, AI-06):
  - `jsonOutput = true` ⇒ body có `"response_format": {"type": "json_object"}`; `false` ⇒ không có trường này.
  - `finish_reason = "length"` ⇒ `AiException(UNAVAILABLE)`, log `result=truncated`.
  - Log `completionTokens`.
- **AC-4** (HLT-21): JSON hợp lệ ⇒ lưu `content`, trong đó:
  - `attentionPoints` có mã không nằm trong warnings bị bỏ; mỗi mã chỉ giữ một mục.
  - `followUps` có loại không được phép bị bỏ; trùng loại giữ một.
  - Cảnh báo bị AI bỏ sót ⇒ thêm điểm cần chú ý bằng nguyên `text` của cảnh báo.
  - Chỉ có `NORMAL` ⇒ `attentionPoints` rỗng.
- **AC-5** (HLT-04, AI-03):
  - Mục chứa từ cấm hoặc vượt 300 ký tự ⇒ bỏ mục.
  - `limitations` chứa từ cấm ⇒ null.
  - `overview` vượt 500 ký tự ⇒ cắt tại dấu kết câu cuối cùng trong 500 ký tự; không cắt được ⇒ 503.
  - `overview` chứa từ cấm, JSON hỏng hoặc thiếu `overview` ⇒ 503 `AI_SERVICE_UNAVAILABLE`, không lưu.
  - JSON bọc trong ```` ``` ```` vẫn đọc được.
- **AC-6** (AI-04, HLT-21): Prompt có mã cảnh báo, các loại gợi ý được phép, số lần đo, số tháng giữa lần đầu và lần cuối, và câu "không gợi ý đưa trẻ đi khám hay chuyển y tế". Prompt vẫn không có tên, ngày sinh, mã trẻ, ngày đo.
- **AC-7** (HLT-05, AI-01): Bản nháp lưu `content` (jsonb) và `summary = render(content)`. Response trả cả hai.
- **AC-8** (AI-02):
  - Duyệt với `content` khác bản lưu ⇒ `source=HUMAN`, `summary` render lại.
  - `content` sai (overview trống, mã cảnh báo lạ, vượt giới hạn) ⇒ 400 `INVALID_REQUEST_DATA` kèm field errors.
  - Không gửi body ⇒ giữ bản AI.
- **AC-9** (PAR-03, HLT-21): `getPublishedHealth` trả `content` của bản đã duyệt, không có `followUps`. `summary` gửi cho phụ huynh render từ `content.forParent()`, nên không lộ khối "Gợi ý theo dõi". Bản cũ (`content = null`) trả `summary` đã lưu.
- **AC-10** (HLT-21): Có thêm số đo đã công bố sau lần phân tích ⇒ GET latest và published có `stale = true`. Phụ huynh vẫn nhận bản cũ (Q6 = A); FE hiện ghi chú.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Chỗ đặt enum | `utils/enums/HealthTrendWarningCode`, `utils/enums/HealthTrendFollowUpType` | Lồng trong DTO | ADR-0014, guide 6.3 (`backend-coding-guide.md:131`). Vùng dùng chung ⇒ in bản tác động trước khi tạo (2 file mới, chưa có consumer) |
| Ép JSON | `response_format: json_object` + kiểm ở BE | `json_schema` strict | Gemini và Groq (OpenAI-compat) đều nhận; strict schema mỗi provider một khác |
| JSON hỏng | 503, không retry | Retry 1 lần | `read-timeout` 20s (`application.yml:153`) ⇒ retry có thể chờ 40s; FE có nút tải lại |
| `maxTokens` | 1200; prompt yêu cầu mỗi mục tối đa 1 câu | 800 | Giới hạn ký tự cho tới ~4.400 ký tự (500 + 6×300 + 3×300 + 3×300 + 300); tiếng Việt có dấu tốn token. Đo `completionTokens` ở smoke test rồi chỉnh |
| `REMEASURE` | Kỳ đang diễn ra và chưa từng gợi ý đo lại cho lần đo mới nhất | Luôn cho khi có cảnh báo; thêm nút "đã đo lại" | Kỳ đã đóng thì không sửa được số đo (HLT-13). Đã gợi ý một lần thì lần sau là câu thừa (Q2). Không cần endpoint hay migration mới |
| `stale` | Tính khi đọc: so tập `measurement_ids` | Lưu thêm dấu vân tay số đo | Không cần migration. Không phát hiện được trường hợp sửa số đo giữ nguyên id (xem Rủi ro) |
| Lưu trữ | Cột mới `content jsonb` nullable, giữ `summary` dạng render | Thay hẳn `summary` | Bản cũ và client đang đọc `summary` không vỡ |
| Luật kiểm | `HealthTrendContentRules` (final, static, package-private) | Viết trong ServiceImpl | ServiceImpl đã 265 dòng; tách để test riêng |

## Phases
### Phase 1 — Rule, mã cảnh báo, loại gợi ý, JSON mode
- Files:
  - M `docs/business/BUSINESS_RULES.md`: thêm HLT-21 (nội dung ở mục "Làm rõ nghiệp vụ").
  - C `utils/enums/HealthTrendWarningCode.java`: 7 giá trị. C `utils/enums/HealthTrendFollowUpType.java`: `RECHECK_NEXT_ROUND`, `TALK_TO_PARENT`, `REMEASURE`. In bản tác động trước khi tạo.
  - M `dto/health/PublishedHealthResponse.java`: `TrendWarning(HealthTrendWarningCode code, String level, String text)`.
  - C `dto/health/HealthTrendContent.java`: `record HealthTrendContent(String overview, List<AttentionPoint> attentionPoints, List<String> positives, List<FollowUp> followUps, String limitations)`, với `AttentionPoint(HealthTrendWarningCode warningCode, String text)`, `FollowUp(HealthTrendFollowUpType type, String text)`, `HealthTrendContent forParent()` (bỏ `followUps`).
  - M `service/health/impl/HealthGrowth.java`: gán `code`; thêm `static Set<HealthTrendFollowUpType> allowedFollowUps(List<TrendWarning> warnings, boolean remeasurePossible)`.
  - M `integration/ai/AiClient.java`: thêm `boolean jsonOutput` vào `AiRequest`; giữ constructor 4 tham số.
  - M `integration/ai/OpenAiCompatibleAiClient.java`: tách `static Map<String, Object> requestBody(AiRequest, String model)` và `static String contentOf(ChatResponse)` (đọc `finish_reason`); log `completionTokens`.
- Tests:
  - AC-1 → `HealthTrendServiceImplTest#deterministicWarnings`: thêm assert `code`.
  - AC-2 → `HealthTrendServiceImplTest#followUpsAllowedByWarnings`.
  - AC-3 → `OpenAiCompatibleAiClientTest#jsonOutputAddsResponseFormat`, `#truncatedOutputIsUnavailable`.
- Smoke test: BE local gọi POST analyze với Gemini và Groq có `response_format`; ghi `completionTokens`.
- Exit: `./mvnw -q test -Dtest=HealthTrendServiceImplTest,OpenAiCompatibleAiClientTest` → `BUILD SUCCESS`.
- **Dừng chờ user review.**

### Phase 2 — Prompt mới, kiểm output, lưu `content` (V129)
- Files:
  - C `service/health/impl/HealthTrendContentRules.java`:
    - `static Optional<HealthTrendContent> fromAi(String raw, List<TrendWarning> warnings, Set<HealthTrendFollowUpType> allowed)`: bóc fence, parse, kiểm theo AC-4 và AC-5.
    - `static String render(HealthTrendContent c)`: các khối "Tổng quan / Cần chú ý / Điểm tích cực / Gợi ý theo dõi / Lưu ý về dữ liệu"; bỏ khối rỗng.
    - Giới hạn: `overview` 1–500 ký tự; mỗi mục 1–300; `positives` ≤ 3; `limitations` ≤ 300 hoặc null.
  - C `db/migration/V129__health_interpretation_content.sql`: `ALTER TABLE health_interpretations ADD COLUMN content JSONB;`. Lấy số kế tiếp trên `dev` ngay trước khi tạo PR.
  - M `entity/health/HealthInterpretation.java`: field `content` (`@JdbcTypeCode(SqlTypes.JSON)`, nullable); `draft(...)` thêm `String contentJson` sau `summary`.
  - M `service/health/impl/HealthTrendServiceImpl.java`:
    - Viết lại `SYSTEM_PROMPT` theo AC-6.
    - `userPrompt(Gender, List<GrowthPoint>, List<TrendWarning>, Set<HealthTrendFollowUpType>)`.
    - Tính `remeasurePossible` (AC-2):
      - Kỳ của lần đo mới nhất có `campaignKind = MEASUREMENT` và `statusOn(rules.today()) == IN_PROGRESS` (lấy qua snapshot `HealthRecords`).
      - Và không bản giải thích nào trong `interpretationRepository.findByChildIdOrderByGeneratedAtDescIdDesc(childId)` có `REMEASURE` trong `content.followUps` mà `measurement_ids` chứa id lần đo đó.
    - Gọi AI với `maxTokens` 1200, `jsonOutput` true. `fromAi` trả rỗng ⇒ log `blocked=invalidOutput`, trả 503.
  - M `dto/health/HealthTrendAnalysisResponse.java`: thêm `HealthTrendContent content` sau `summary`.
  - M `repository/health/HealthInterpretationRepository.java`: thêm `List<HealthInterpretation> findByChildIdOrderByGeneratedAtDescIdDesc(Long childId)`.
- Tests:
  - AC-2 → `HealthTrendServiceImplTest#remeasureSuggestedOncePerMeasurement`, `#noRemeasureWhenRoundClosed`.
  - AC-4, AC-5 → `HealthTrendContentRulesTest`. Các case: hợp lệ, mã lạ, trùng mã, loại gợi ý không được phép, cảnh báo bị bỏ sót, chỉ `NORMAL`, từ cấm trong mục, từ cấm trong `overview`, `overview` dài cắt được và không cắt được, JSON hỏng, có fence.
  - AC-6 → `HealthTrendServiceImplTest#promptReplacesMeasurementDatesWithAge` (mở rộng).
  - AC-7 → `HealthTrendServiceImplTest#savesAiDraftWithoutIdentity`: mock AI trả JSON. `#blocksDiagnosticLanguage` đổi theo AC-5.
  - `HealthRecordIntegrationTest:265`: mock AI đổi từ text thường sang JSON.
- Exit:
  - `./mvnw -q test -Dtest=HealthTrendContentRulesTest,HealthTrendServiceImplTest` → `BUILD SUCCESS`.
  - `./mvnw -q test -Dtest=HealthRecordIntegrationTest` → `BUILD SUCCESS` (cần Docker; Flyway chạy V129).
- **Dừng chờ user review.**

### Phase 3 — Duyệt sửa từng mục, phụ huynh, `stale`, contract và docs
- Files:
  - M `dto/health/HealthTrendApproveRequest.java`: `record HealthTrendApproveRequest(HealthTrendContent content)`.
  - M `HealthTrendContentRules.java`: `static List<FieldErrorResponse> checkEdited(HealthTrendContent c, List<TrendWarning> warnings)`. Cùng giới hạn như Phase 2; không kiểm từ cấm (nhân sự chịu trách nhiệm nội dung).
  - M `HealthInterpretation.java`: `editContent(String contentJson, String summary)` đặt `source = HUMAN`; `approve(Long userId, Instant at)`.
  - M `HealthTrendServiceImpl`:
    - `approve`: so `content` gửi lên với bản lưu bằng record `equals`; khác ⇒ `editContent`.
    - Tính `stale` khi đọc (AC-10).
  - M `HealthTrendAnalysisResponse`: thêm `boolean stale`.
  - M `PublishedHealthResponse.ApprovedInterpretation`: thêm `HealthTrendContent content` (dùng `forParent()`) và `boolean stale`.
  - M `HealthMeasurementServiceImpl.java:247`: map `content.forParent()`, `summary = render(content.forParent())` (bản cũ giữ `summary` đã lưu), `stale`.
- Docs:
  - `docs/api/openapi.yaml`: cập nhật snapshot.
  - `docs/modules/health.md` (`:32`, `:58`, `:59`), `docs/modules/ai-assistance.md`, `docs/database/DATA_DICTIONARY.md` (`content`).
- Tests:
  - AC-8 → `HealthTrendServiceImplTest#approveWithEdit`, `#approveRejectsInvalidContent`.
  - AC-9, AC-10 → `HealthRecordIntegrationTest#interpretationVisibleToParentOnlyAfterApproval` (assert `content` không có `followUps`, `summary` không chứa "Gợi ý theo dõi") và `#interpretationStaleAfterNewMeasurement`.
- Exit:
  - `./mvnw -q test -Dtest=OpenApiSnapshotTest -Dopenapi.snapshot.update=true`.
  - Sau đó `./mvnw test` → `BUILD SUCCESS` (cần Docker).
- **Dừng chờ user review.**

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1, AC-2 | HLT-02, HLT-21 | `HealthTrendServiceImplTest` (unit) | 1 | PASS (`#warningCodes`, `#followUpsAllowedByWarnings`) |
| AC-3 | AI-05, AI-06 | `OpenAiCompatibleAiClientTest` (unit) | 1 | PASS; smoke test provider chưa chạy |
| AC-4, AC-5 | HLT-21, HLT-04 | `HealthTrendContentRulesTest` (unit) | 2 | PASS 9/9 |
| AC-6, AC-7 | AI-04, HLT-05 | `HealthTrendServiceImplTest` (unit) | 2 | PASS 12/12 (gồm AC-2 đo lại một lần, kỳ đóng) |
| AC-8 | AI-02 | `HealthTrendServiceImplTest` (unit) | 3 | |
| AC-9, AC-10 | PAR-03, HLT-21 | `HealthRecordIntegrationTest` (Testcontainers) | 3 | |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Nhánh G94-180 có commit chưa vào `dev` (`38d9ca7d` pickup classes) | PR sẽ gồm cả commit pickup | Đã merge `origin/dev` (`dc0ca0a0`, chưa push); nêu rõ trong mô tả PR | — |
| Provider từ chối `response_format` | 503 mọi lần phân tích | Smoke test cả Gemini và Groq ở Phase 1 | `jsonOutput = false`; parser vẫn đọc JSON nhờ prompt |
| AI trả JSON sai hoặc bị cắt cụt | 503 nhiều | Prompt có ví dụ, `temperature` 0.2, log `truncated` và `completionTokens` | Chỉnh `maxTokens` hoặc thêm retry trong plan sửa sau |
| Trùng số V129 | Flyway lỗi khi merge | Lấy số kế tiếp trên `dev` trước khi tạo PR | Migration mới `DROP COLUMN content` |
| Sửa số đo (HLT-13) sau khi phân tích, id giữ nguyên | `stale` không bật, bản giải thích lệch số liệu | Nhân sự bấm phân tích lại; ghi vào `health.md` | Lưu dấu vân tay số đo trong plan sau |
| FE gọi POST analyze mỗi lần mở trang (`AiTrendPanel.jsx:13`) | Tốn token, thêm bản nháp mỗi lần mở | Ghi sang plan FE: dùng GET latest, chỉ phân tích khi bấm nút | — |

Ngoài phạm vi: UI FE (plan FE riêng); luật `HEIGHT_DECREASE`; bản giải thích riêng cho phụ huynh.

## Validation log
- [x] Khẳng định về code có `file:line`
- [x] Đổi contract ⇒ consumer cụ thể: FE `AiTrendPanel.jsx` (POST analyze); approve và published chưa có client
- [x] Mỗi AC có test; mỗi phase có Exit là lệnh và kết quả mong đợi
- [x] >8 file ⇒ đã cân nhắc tách (1 plan, 3 phase; mỗi phase merge được)
- [x] HLT-21 PROPOSED, user chốt 14/11; không dùng rule PENDING/OPEN như đã chốt
- [x] Tự review: không placeholder; enum theo ADR-0014; giới hạn ký tự khớp `maxTokens`

## Progress log
### 2026-11-14 — Plan draft v4 (chờ duyệt)
- Commit: merge `origin/dev` vào nhánh (`dc0ca0a0`, user cho phép, chưa push). Conflict duy nhất ở dòng tổng của `docs/api/API_INDEX.md`, sửa thành 336 (hook xác nhận khớp `openapi.yaml`). `./mvnw -q -DskipTests test-compile` → exit 0.
- Khác v3: Q6 = A; Q3 = B; `REMEASURE` chỉ gợi ý một lần cho mỗi lần đo (Q2); `summary` của phụ huynh render từ `forParent()` để không lộ `followUps`.
- Tiếp: user duyệt plan, sau đó làm Phase 1.

### 2026-11-14 — Phase 1 (chờ review)
- User duyệt plan 14/11 ⇒ `in-progress`.
- Commit: chưa commit
- Verify: `./mvnw -q spotless:apply` → exit 0; `./mvnw -q test -Dtest=HealthTrendServiceImplTest,OpenAiCompatibleAiClientTest` → exit 0 (surefire: 10/0/0/0, 3/0/0/0).
- Khác plan: AC-1 dùng test mới `#warningCodes` thay vì sửa `#deterministicWarnings`; `contentOf` ném `UNAVAILABLE`, log `result=truncated` nằm ở `complete()`.
- Chưa kiểm chứng: smoke test `response_format` với Gemini/Groq thật (cần khóa API).
- Ghi chú: `spotless:apply` định dạng lại `PickupIntegrationTest.java` (file từ `dev`, chưa sạch spotless) — đã hoàn tác, ngoài phạm vi.
- Tiếp: Phase 2.

### 2026-11-14 — Phase 2 (chờ review)
- Commit: chưa commit
- Verify:
  - `./mvnw -q spotless:apply` → exit 0.
  - `./mvnw -q test -Dtest=HealthTrendContentRulesTest,HealthTrendServiceImplTest,OpenAiCompatibleAiClientTest,HealthRecordControllerTest` → exit 0 (surefire 9/12/3/6, 0 lỗi).
  - `./mvnw -q test -Dtest=HealthRecordIntegrationTest` → exit 0 (4/4, Testcontainers; log Flyway có `129 - health interpretation content`).
- Khác plan:
  - `FORBIDDEN` và `containsForbidden` chuyển từ `HealthTrendServiceImpl` sang `HealthTrendContentRules`.
  - `DATA_DICTIONARY.md` cập nhật ngay ở Phase 2 (rule database: đổi schema cùng tài liệu), không đợi Phase 3.
  - Thêm `HealthTrendServiceImpl.contentOf(...)` để đọc `content`; `HealthRecordControllerTest` sửa theo constructor mới của response.
- Còn treo tới Phase 3: `approve` vẫn nhận `summary` (sửa `summary` chưa đồng bộ `content`); `OpenApiSnapshotTest` sẽ lệch (thêm `code`, `content`) — cập nhật snapshot ở Phase 3.
- Chưa kiểm chứng: gọi Gemini/Groq thật.
- Tiếp: Phase 3.
