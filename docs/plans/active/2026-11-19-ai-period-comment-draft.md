---
title: AI viết nháp nhận xét tháng / học kỳ / cuối năm (AI-07 mục 1)
status: in-progress
owner: Thangnd04
jira: G94-200 / BE-FEAT-28
branch: feature/G94-200-BE-FEAT-28-ai-assessment-drafting
modules: [learning-observation, ai-assistance]
rules: [AI-01, AI-02, AI-03, AI-04, AI-05, AI-06, AI-07, AI-08, AI-10, OBS-05, OBS-06, OBS-30, OBS-32, OBS-33, OBS-34]
risk: high
created: 2026-11-19
updated: 2026-11-19
---
# AI viết nháp nhận xét tháng / học kỳ / cuối năm

Plan làn L. Phạm vi đợt này: **chỉ AI-07 mục 1**. Mục 2 (kế hoạch giáo dục) và mục 5 (tổng kết năm, P-34) để đợt sau, user chốt 19/11/2026. Slice cũ (`0d8938a`, plan `2026-10-14-ai-assessment-drafting.md`) đã gỡ, không khôi phục; chỉ tham khảo `AiOutputGuard`.

## Context
- Nhận xét kỳ đã có: bảng `child_evaluations` (MONTH/SEMESTER/YEAR, V48/V68/V79/V113), nháp tạo bằng mẫu câu `EvaluationDraftWriter.write` (`ChildEvaluationServiceImpl.java:746` tạo tự động, `:774` `draftOf`, `:291` `redraft`). Event `AI_DRAFTED`/`AI_REDRAFTED` đã có, nhưng nháp chưa gọi AI và chưa lưu source/model/prompt version.
- Cô Kiên (13/11): AI đọc lịch sử nhận xét GV trong kỳ rồi tổng hợp nhận xét cá nhân cho từng trẻ, không chỉ số +/-; GV kiểm tra và duyệt; mỗi tính năng AI phải đo được (~10 case, người tính lại bằng bảng tính).
- Khung dùng lại: `AiGateway`, task cấu hình `carenest.ai.tasks.*`, mẫu `AiWeeklyCommentDraftGenerator` + `WeeklyCommentServiceImpl.writeDrafts` (cờ `ai`, rơi về mẫu câu).

## Quyết định đã chốt
1. Làm (1) trước; (2), (5) đợt sau — user, 19/11/2026.
2. AI đọc: số liệu kỳ (code tính) + nhận xét hằng ngày trong kỳ + nhận xét tuần đã gửi trong kỳ; học kỳ / cuối năm đọc thêm nhận xét kỳ nhỏ hơn đã xác nhận — user, 19/11/2026.
3. GV bấm "AI viết nháp" (cờ `ai` trong request `redraft`), không tự gọi AI khi hệ thống tạo nháp; lỗi ⇒ mẫu câu — user, 19/11/2026.
4. Chỉ đo/dev bằng dữ liệu giả; bật cho trẻ thật chờ P-02. Code lọc họ tên + ngày sinh của chính trẻ; tên bạn khác do GV rà — user, 19/11/2026. Ghi ở OBS-34 (PROPOSED).

## Làm rõ nghiệp vụ
- **Hiểu nghiệp vụ:** Actor GV đang dạy lớp (OBS-32/33: dạy thay chỉ xem) · Outcome nháp nhận xét cá nhân hơn mẫu câu, GV sửa và xác nhận · Rule OBS-05, OBS-06, OBS-30 (CONFIRMED), OBS-34 (PROPOSED), AI-01..10 · Flow `[B]` GV mở nhận xét tháng/học kỳ/năm → "AI viết nháp" → sửa → xác nhận → phụ huynh nhận (PAR-03).
- **Đối chiếu:** AI-07 mục 1 và OBS-05/06 khớp yêu cầu. Nguồn "nhận xét tuần" nằm ở OBS-29 (dev), chưa nêu là đầu vào của nhận xét kỳ ⇒ đã hỏi, user chọn dùng (OBS-34).
- **Ảnh hưởng:** module observation (+ `integration/ai`, enum dùng chung `AiTask`) · quyền/scope giữ nguyên (`checkEditable`) · dữ liệu dẫn xuất từ trẻ ⇒ `CHILD_DERIVED`, chỉ Groq · FE: màn nhận xét tháng/học kỳ/năm (`assessmentApi.js` gọi `POST /assessment/evaluations/{id}/redraft`, `GET /assessment/evaluations`) cần nút + nhãn nguồn nháp · APP: không gọi các endpoint này (chưa xác minh, `FE_API_CALLS.md` chỉ liệt kê Web) · dữ liệu cũ: cột mới nullable/mặc định TEMPLATE.

### Câu hỏi mở
Không còn câu hỏi chặn (5 câu đã trả lời 19/11/2026).

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| Thứ tự tính năng | (1) trước | user | 19/11 | plan này |
| Đầu vào lịch sử nhận xét | Cả ba nguồn | user | 19/11 | OBS-34 |
| Cách kích hoạt | GV bấm | user | 19/11 | OBS-34 |
| Dữ liệu thật / tên bạn | Dữ liệu giả + GV rà | user | 19/11 | OBS-34 |
| Phạm vi (2), (5) | Đợt sau | user | 19/11 | plan này |

## Acceptance criteria
- **AC-1** (OBS-34, AI-03): Given AI tắt/lỗi/hết deadline/trả từ chẩn đoán/rỗng, When GV bấm AI viết nháp, Then nháp là mẫu câu, `source=TEMPLATE`, không lỗi HTTP, response nói rõ đã rơi về mẫu.
- **AC-2** (OBS-34, AI-01): Given AI trả nội dung hợp lệ, When GV bấm AI viết nháp, Then `draftContent` = nội dung AI, `source=AI`, lưu model + prompt version, status vẫn chờ xác nhận, không tự thành chính thức (AI-02).
- **AC-3** (AI-04, AI-05, AI-08): Prompt không chứa họ tên, tên gọi, ngày sinh, mã trẻ của chính trẻ; log không có prompt/response.
- **AC-4** (AI-10, OBS-17): Output có số (ngày, lần, %) không có trong dữ liệu đầu vào, hoặc từ chẩn đoán, hoặc quá dài ⇒ bị chặn, rơi về mẫu.
- **AC-5** (OBS-30/32/33): Quyền, khóa kỳ, hạn không đổi: kỳ khóa / người dạy thay / chưa mở kỳ vẫn bị chặn như `redraft` hiện nay.
- **AC-6** (OBS-34): Học kỳ / cuối năm đọc thêm nhận xét kỳ nhỏ hơn đã xác nhận; MONTH đọc nhận xét tuần đã gửi có `weekStart` trong kỳ; số nhận xét đưa vào prompt có trần.
- **AC-7** (Cô Kiên): Có bộ ~10 case giả, xuất CSV (số liệu đầu vào + số trong output); test tự động kiểm đầu ra hợp lệ; eval tay chạy khi có key.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Lưu source/model/prompt version | Thêm 3 cột vào `child_evaluations` (migration mới) | Bảng nháp riêng | Cùng pattern `weekly_comments.draft_source`; nháp ở đúng module nghiệp vụ (AI-01) |
| Contract | `POST .../redraft` nhận body tùy chọn `{ai}` (thiếu body = như cũ); `Draft` thêm `source`; `EvaluationRow` thêm `aiEnabled` — chỉ thêm trường | Endpoint mới `/ai-draft` | Không phá FE hiện tại; FE tự thêm nút |
| Lỗi AI | Rơi về `EvaluationDraftWriter`, không thêm `ApiCode` | Trả 8001/8002 | AI-03; khớp nhận xét tuần |
| Task AI | `PERIOD_COMMENT`, `CHILD_DERIVED`, chain `[groq]`, deadline 20s, repair 0 | Dùng chung `WEEKLY_COMMENT` | Tách để đo và đổi chuỗi riêng |
| Đầu vào quá nhiều | Trần số nhận xét (dự kiến 40) và độ dài mỗi nhận xét (300 ký tự), chọn đều theo thời gian | Gửi hết | Giới hạn token Groq free, tránh cắt giữa chừng |

## Phases
### Phase 1 — Lõi AI + lưu nháp + API (>8 file, dừng review)
- Files: C `db/migration/V171__child_evaluation_draft_source.sql` (3 cột + CHECK source; đã đổi từ V156 khi nhập `dev`) · M `utils/enums/AiTask` (**vùng dùng chung, cần bản tác động**) · M `application.yml` (task `PERIOD_COMMENT`) · C `resources/prompts/observation/period_comment_v1.txt` · C `service/observation/impl/AiPeriodCommentDraftGenerator` + `PeriodCommentFacts` + `PeriodCommentGuard` (lọc tên/ngày sinh, kiểm số, từ chẩn đoán, độ dài; dùng `DIAGNOSTIC_TERMS`) · M `entity/ChildEvaluation` (+`redraft(content, source, model, promptVersion, at)`) · M `ChildEvaluationServiceImpl` (`redraft(…, ai)`, nạp lịch sử nhận xét) · M `ChildEvaluationService` · M `EvaluationDtos` (`Draft.source`, `EvaluationRow.aiEnabled`, `RedraftRequest`) · M `ChildEvaluationController` · M `WeeklyCommentRepository` (đọc nhận xét tuần đã gửi theo trẻ + khoảng).
- Steps: 1. migration + entity. 2. facts + guard + generator (không gọi mạng ngoài `AiGateway`). 3. nối `redraft` (AI chỉ khi GV bấm, `isAvailable`). 4. contract thêm trường. 5. tài liệu: `ai-assistance.md`, `learning-observation.md`, `docs/api/API_INDEX.md`, danh sách tác động FE.
- Tests: AC-1 → `PeriodCommentRedraftTest`; AC-2/5 → `ChildEvaluationAiIntegrationTest`; AC-3/4 → `PeriodCommentGuardTest`; AC-6 → `PeriodCommentFactsTest` (tất cả `@DisplayName("OBS-34: …")`).
- Exit: `./mvnw -q test -Dtest='PeriodComment*,ChildEvaluation*'` xanh, rồi `./mvnw test` toàn bộ (đổi vùng dùng chung); báo lệnh + kết quả thật. **Dừng chờ review.**

### Phase 2 — Bằng chứng đo (cô Kiên)
- Files: C `src/test/resources/ai-eval/period-comment-cases.json` (10 case giả: đủ dữ liệu, ít dữ liệu, nghỉ nhiều, nhận xét chứa tên trẻ, chứa tên bạn, chứa lệnh chèn, chứa từ chẩn đoán, học kỳ có tháng đã xác nhận, cuối năm, AI lỗi) · C `PeriodCommentLiveEvalTest` (`AI_LIVE_EVAL=true`, ghi `target/ai-eval/period-comment-<thời điểm>/{records.json,facts.csv,output.csv}`) · C `docs/quality/ai-eval/<ngày>/README.md` · M `ai-menu-quality-criteria.md` hoặc tài liệu chất lượng riêng cho mục đo.
- `facts.csv`: đầu vào từng case + số do code đếm; `output.csv`: số trong văn bản AI, khớp/lệch, từ cấm, độ dài, nguồn — người tính lại bằng bảng tính.
- Exit: bộ test không key chạy xanh (AI giả); chạy thật chỉ khi user có key Groq, ghi trung thực "chưa chạy với model thật" nếu không.
- **Dừng chờ review.**

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1 | OBS-34, AI-03 | `PeriodCommentRedraftTest` (service, AI giả) | 1 | đạt |
| AC-2 | OBS-34, AI-01 | `ChildEvaluationAiIntegrationTest` | 1 | đạt |
| AC-3 | AI-04/05/08 | `PeriodCommentGuardTest` + kiểm prompt | 1 | đạt |
| AC-4 | AI-10, OBS-17 | `PeriodCommentGuardTest` | 1 | đạt |
| AC-5 | OBS-30/32/33 | `ChildEvaluationAiIntegrationTest` | 1 | đạt |
| AC-6 | OBS-34 | `PeriodCommentFactsTest` | 1 | đạt |
| AC-7 | AI-07 | `PeriodCommentEvalCasesTest` (tự động) + `PeriodCommentLiveEvalTest` (tay, cần key) | 2 | tự động đạt; chạy model thật chưa |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Nhận xét tự do lộ tên bạn khác | Dữ liệu trẻ khác ra provider | Chỉ dữ liệu giả tới khi P-02; Groq không train; GV rà nháp; guard chặn tên của chính trẻ | Tắt `AI_ENABLED` hoặc bỏ chain task |
| AI gọi trong `@Transactional` giữ khóa lớp tới 20s | Nghẽn lớp khác | Khớp cách nhận xét tuần đang làm; deadline 20s; chỉ gọi 1 trẻ mỗi lần | Giảm deadline |
| Groq free giới hạn token/phút (429) | Rơi về mẫu | Không retry, mẫu câu thay thế | — |
| AI bịa số / sự việc | Nhận xét sai tới phụ huynh | Guard kiểm số, GV xác nhận, nhãn nguồn | — |
| Migration trùng số với `dev` | Flyway lỗi | Đánh số lại khi merge | Migration mới thêm cột nullable/mặc định, không drop |
| FE chưa có nút | Tính năng chưa dùng được | Contract chỉ thêm trường, mặc định cũ | — |

## Validation log
- [x] Claim về code có `file:line`: `ChildEvaluationServiceImpl.java:746,:774,:291`; `EvaluationSources.java:~266` (`EvaluationDay.comment`); `WeeklyCommentServiceImpl.writeDrafts:~133` (mẫu).
- [x] Contract đổi ⇒ consumer: Web `assessmentApi.js` (`POST /assessment/evaluations/{x}/redraft`, `GET /assessment/evaluations`) theo `docs/system/FE_API_CALLS.md:51,242`; APP chưa xác minh.
- [x] Mỗi AC có test; Exit Phase 1 đã chạy (xem Progress log)
- [x] >8 file ⇒ chia 2 phase có dừng review; chưa đề xuất tách plan vì cùng một tính năng
- [x] Không dùng rule PENDING/OPEN như đã chốt (OBS-34 PROPOSED, P-02 vẫn PENDING)
- [x] Đã có plan cùng chủ đề (`2026-10-14-ai-assessment-drafting.md`) nhưng mô tả slice đã gỡ; tạo plan mới, không sửa plan cũ
- [ ] Chưa kiểm: `EvaluationDetailResponse`/mapper còn chỗ nào dựng `Draft` khác; cần đọc khi vào Phase 1
- [x] `impact.mjs` 19/11: `AiTask` = vùng dùng chung, 7 file dùng trực tiếp (AiGateway, AiProperties, AiRequest, ProviderChainAiGateway, HealthTrendServiceImpl, MenuSuggestionEngine, AiWeeklyCommentDraftGenerator), 0 endpoint, 0 file Web ⇒ chỉ thêm giá trị enum, không đổi hành vi cũ; `ChildEvaluationController`: 11 endpoint, 1 file Web (`src/services/assessment/api/assessmentApi.js`). Phụ trách: chưa khai ownership.
- [ ] Chưa mở FE/APP để xác minh (không được sửa)

## Progress log
### 2026-11-19 — Phase 2 (chờ review)
- Commit: chưa commit
- Verify: `./mvnw -o test -Dtest='PeriodComment*Test,ChildTextScrubberTest,AiPeriodCommentDraftGeneratorTest,EvaluationLockOrderTest,OpenApiSnapshotTest,ArchitectureRulesTest'` → `Tests run: 48, Failures: 0, Errors: 0, Skipped: 1` (skip = `PeriodCommentLiveEvalTest`, cần `AI_LIVE_EVAL=true`), `BUILD SUCCESS`; `spotless:check` đạt. Chưa chạy lại toàn bộ test sau Phase 2 (chỉ thêm file test + resource).
- Bằng chứng: 10 case giả (`period-comment-cases.json`, 459 ngày thô), `facts.csv` 0 dòng LỆCH, mẫu chạy không key ở `docs/quality/ai-eval/2026-11-19-period-comment-offline/`. Chạy tay không key: 9 lần `ai-disabled`, 1 lần `no-content`.
- Chưa kiểm: chất lượng văn bản AI với model thật (cần `AI_GROQ_MODEL` + `AI_GROQ_API_KEY`); FE/APP.
- Tiếp: user có key ⇒ chạy `PeriodCommentLiveEvalTest`, chấm tay `records.json`; review rồi mới commit (mã Jira do user cung cấp).

### 2026-11-19 — Phase 1 (chờ review)
- Commit: chưa commit
- Verify: `./mvnw -o test` toàn bộ → `Tests run: 2158, Failures: 1, Errors: 0, Skipped: 2` (lỗi duy nhất `OpenApiSnapshotTest`: hợp đồng đổi có chủ đích). Cập nhật `docs/api/openapi.yaml` bằng `-Dopenapi.snapshot.update=true` (+17 dòng: body `RedraftRequest`, `Draft.source`, `EvaluationRow.aiEnabled`). Chạy lại `OpenApiSnapshotTest` + test nhận xét kỳ → `Tests run: 27, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`; `./mvnw -o spotless:check` → `BUILD SUCCESS`. Không chạy `scripts/verify.mjs` (AGENTS.md, chỉ đạo 07/11/2026). 2 test skip của lần chạy toàn bộ chưa xác định là test nào.
- Khác plan: `EvaluationRow.aiEnabled` chỉ bật cho GV lớp; `EvaluationDraftSource` là enum mới ở `utils/enums` (không dùng lại `WeeklyCommentSource` vì khác nghĩa). Tên gọi trùng từ thường bị thay bằng `{TEN}` (ưu tiên không lộ tên).
- Chưa kiểm: gọi model thật; FE/APP.
- Tiếp: user review Phase 1 ⇒ Phase 2 (10 case giả, CSV, eval tay).

### 2026-11-19 — Plan duyệt, bắt đầu Phase 1
- User duyệt plan 19/11/2026 (tin nhắn "Duyệt plan"), kèm bản tác động `AiTask` và contract `redraft` (chỉ thêm trường).

### 2026-11-19 — Plan draft (đã duyệt ở trên)
- Commit: chưa commit
- Verify: chưa chạy (chưa có code)
- Tiếp: user duyệt plan + bản tác động `AiTask`/contract `redraft` ⇒ `status: approved` ⇒ Phase 1.
