---
title: AI gom nội dung vào chủ đề ở kế hoạch tổ (EDU-103)
status: in-progress
owner: Tiền
jira: G94-346 / BE-FEAT-30 (FE G94-413 / FE-FEAT-30)
branch: feature/G94-346-BE-FEAT-30-edu-plan-ai
modules: [education-plan]
rules: [EDU-103, EDU-95, EDU-22, EDU-09, EDU-55, EDU-73]
risk: medium
created: 2026-11-22
updated: 2026-11-22
---
# AI gom nội dung vào chủ đề ở kế hoạch tổ (EDU-103)

Phần 1/4 của thiết kế `../docs/superpowers/specs/2026-11-22-edu-ai-content-matching-design.md` (thư mục `UI-giaoan`). Phần 2 (EDU-104), 3 (EDU-105), 4 (EDU-106) là plan riêng sau.

## Context
- Tổ trưởng tick tay 166–177 nội dung vào 9–11 chủ đề (`GoalTables.jsx:294-306`, cờ `canAssignContents`). Chạy thử 22/11 (`UI-giaoan/_tmp_claude/spike-theme-map/`): Gemini 2.5 Flash, 5 lần gọi song song theo lĩnh vực, 181 gợi ý, phủ 105/166, 23–40 s/lần, căn cứ `from` đúng nguyên văn 181/181.
- AI hiện có: `EduAiServiceImpl.ask()` cố định task `EDU_PLAN` (`EduAiServiceImpl.java:323-327`); read timeout provider = min(deadline còn lại, `read-timeout` 30 s) (`OpenAiCompatibleProvider.java:65`).
- Dữ liệu cần đều có trong `YearPlanDetailResponse` qua `EduYearPlanService.getYearPlan` (kiểm quyền xem): `domains→rows→contents(id,text,themeIds)`, `themes(festivals)`, `branchTemplates(themeId,name)`, `canAssignContents` = sửa được && TEAM (`EduYearPlanServiceImpl.java:2035`).
- Cấu hình VPS dùng `CARENEST_AI_*` (`compose.vps.yaml:127-129`) nhưng `application.yml:238-256` đọc `AI_*` ⇒ AI đang tắt trên VPS.

## Quyết định đã chốt
1. Thiết kế + EDU-103, sửa EDU-22/95 — Tiền 22/11/2026 (bản chuẩn đã ghi, mốc `2dff565`).
2. Bấm AI tự Lưu trước; lưu lỗi thì không gọi AI — Tiền 22/11.
3. Thống nhất biến môi trường theo VPS `CARENEST_AI_*`, giữ `AI_*` làm dự phòng — Tiền 22/11.
4. Không chấm trước; đo bằng tỉ lệ giữ khi dùng thật (log) — Tiền 22/11.
5. Nút luôn hiện, AI tắt/lỗi báo 8001/8002 (EDU-95).

## Làm rõ nghiệp vụ
- **Actor:** tổ trưởng (người soạn kế hoạch tổ, bản DRAFT, năm học còn ghi được). **Outcome:** tick gợi ý thêm vào bản đang soạn, Lưu như thường.
- **Ảnh hưởng:** module education-plan; FE `GoalFormPage` (kế hoạch tổ, card 2); APP không. Không đổi DB, không dữ liệu trẻ.

### Câu hỏi mở
(không còn)

## Acceptance criteria
- **AC-1** (EDU-103): Given kế hoạch tổ người dùng có `canAssignContents`, When gọi API với yêu cầu từng chủ đề, Then trả `matches` chỉ gồm id nội dung/chủ đề của kế hoạch đó, không trùng cặp, `from` có nguyên văn trong yêu cầu/tên chủ đề/lễ hội/tên nhánh của đúng chủ đề.
- **AC-2** (EDU-103/73): Given người không có `canAssignContents` (kế hoạch năm, đã gửi, không phải người soạn), When gọi API, Then 403/lỗi quyền có sẵn, không gọi AI.
- **AC-3** (EDU-95): Given AI tắt, Then 8001; một lĩnh vực lỗi/hết giờ, Then các lĩnh vực khác vẫn trả và lĩnh vực lỗi nằm trong `failedDomains`.
- **AC-4** (EDU-103): FE modal gom theo chủ đề → căn cứ, bỏ dòng/cả cụm, hiện số chủ đề mỗi nội dung đang thuộc, Áp dụng chỉ thêm tick (không bỏ tick có sẵn), nội dung trùng chữ gộp một dòng.
- **AC-5** (EDU-22): Bấm "Gợi ý bằng AI" khi còn thay đổi chưa lưu ⇒ tự Lưu; Lưu lỗi ⇒ không gọi AI.
- **AC-6** (EDU-103): Bảng tick có ô tìm theo chữ và lọc "Chưa thuộc chủ đề nào".
- **AC-7**: Áp dụng gửi log số gợi ý/giữ; BE chỉ log.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Lấy dữ liệu | Gọi `getYearPlan` rồi kiểm `canAssignContents` | Truy vấn repo riêng | Dùng lại kiểm quyền + mapping sẵn có, ít code |
| Song song theo lĩnh vực | Virtual threads trong service, chờ tất cả | FE gọi từng lĩnh vực | Một API, FE đơn giản; tiến độ FE hiện "đang chạy" + thời gian |
| Task AI | `EDU_MATCH` deadline 60 s; gemini `read-timeout` 60 s | Ghi đè read-timeout theo task | Read timeout đã bị chặn bởi deadline từng task ⇒ tăng provider không đổi task khác |
| Đo chất lượng | `POST /education-plans/ai/feedback` chỉ log | Bảng thống kê | Không đổi DB (thiết kế) |

## Phases
### Phase 1 — BE: cấu hình + API gom nội dung + log
- Files: M `utils/enums/AiTask.java` · M `src/main/resources/application.yml` · M `compose.vps.yaml` `compose.dev.yaml` `deploy/vps.env.example` `deploy/dev.env.example` `.env.example` · M `service/eduplan/impl/EduAiServiceImpl.java` · M `service/eduplan/EduAiService.java` · M `controller/eduplan/EduAiController.java` · M `dto/eduplan/EduAiRequest.java` `EduAiResponse.java` · C/M test `EduAiServiceImplTest`
- Steps: 1. `EDU_MATCH` + yml (chain gemini→groq, PUBLIC_CATALOG, 60 s). 2. Provider gemini đọc `CARENEST_AI_*` dự phòng `AI_GEMINI_*`; `enabled: ${CARENEST_AI_ENABLED:${AI_ENABLED:false}}`; model mặc định compose `gemini-2.5-flash`. 3. `ask(AiTask, …)`. 4. `matchThemeContents(user, planId, req)`: getYearPlan → kiểm TEAM + `canAssignContents` → prompt theo lĩnh vực (song song) → lọc. 5. `logFeedback` chỉ log.
- Tests: AC-1/2/3 → `EduAiServiceImplTest` (`@DisplayName("EDU-103: …")`), AiGateway mock.
- Exit: `./mvnw -q test -Dtest=EduAiServiceImplTest` pass + compile toàn bộ.

### Phase 2 — FE: modal, tự Lưu, tìm/lọc
- Files: M `services/education-plan/api/educationPlanApi.js` (timeout 70 s) · C `components/education-plan/AiThemeContentModal.jsx` · M `pages/education-plan/GoalFormPage.jsx` (ActionsMenu card 2, chỉ kế hoạch tổ có quyền tick) · M `components/education-plan/GoalTables.jsx` (tìm + lọc) · C test logic gom/áp dụng.
- Steps: modal nhập yêu cầu → `saver.save` nếu unsaved → gọi API → màn duyệt → Áp dụng vào `form.domains[].rows[].contents[].themes` → gửi feedback.
- Exit: test FE liên quan + lint + build đạt.

### Phase 3 — Tài liệu
- `docs/api` (endpoint mới), `docs/system/FE_API_CALLS.md`, module card bỏ chữ PROPOSED cho phần đã làm; snapshot `nghiep_vu_chuan/tai_lieu/`; `kiem_tra_nghiep_vu.mjs`.

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1 | EDU-103 | EduAiServiceImplTest (unit) | 1 | Đạt (3 test: cặp hợp lệ/bỏ trùng, id lạ, `from` sai chủ đề) |
| AC-2 | EDU-103/73 | EduAiServiceImplTest (unit) | 1 | Đạt (không quyền ⇒ 7005, AI không được gọi) |
| AC-3 | EDU-95 | EduAiServiceImplTest (unit) | 1 | Đạt (AI tắt ⇒ 8001, hết giờ ⇒ 8002, một lĩnh vực lỗi ⇒ `failedDomains`) |
| AC-4/5/6/7 | EDU-103/22 | test logic FE + kiểm tay | 2 | Logic FE đạt (`ai-theme-content.test.mjs` 5, `team-plan-ui.test.mjs` +1); kiểm tay/e2e trên trình duyệt chưa làm |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Đổi tên biến env | VPS/dev AI bật lại (trước đang tắt) | Dự phòng tên cũ; mặc định `enabled=false` | Bỏ `CARENEST_AI_ENABLED` |
| Chờ AI lâu | Người dùng tưởng treo | Trạng thái đang chạy, deadline 60 s | — |
| Gợi ý lạc | Tổ trưởng phải bỏ tick | Prompt chính xác, gom theo căn cứ, log tỉ lệ giữ | Tắt AI |

## Validation log
- [x] Khẳng định về code có `file:line`
- [x] Đổi contract ⇒ consumer: thêm 2 endpoint mới (không đổi cũ); consumer = `CareNest_FE/src/services/education-plan/api/educationPlanApi.js`; APP không dùng.
- [x] Mỗi AC có test; mỗi phase có Exit
- [x] >3 phase ⇒ đã tách: thiết kế 4 phần thành 4 plan; plan này 3 phase
- [x] Không dùng rule PENDING/OPEN (EDU-69 PROPOSED chỉ dẫn chứng phần thừa hợp lệ, không phụ thuộc)
- [x] Tự review

## Progress log
### 2026-11-22 — Plan
- Theo `CLAUDE.md` chế độ tự chạy: tự viết plan và làm tiếp; Tiền đã duyệt thiết kế 22/11. Bắt đầu phase 1.

### 2026-11-22 — Code (AI-DLC code-generation, intent 261121-edu-ai-content-map)
- BE: `AiTask.EDU_MATCH` + yml (deadline 60s, gemini read-timeout 60s, `CARENEST_AI_*` với `AI_*` dự phòng, model mặc định `gemini-2.5-flash`); `CARENEST_AI_ENABLED` vào compose dev/vps + env mẫu. `EduAiServiceImpl.matchThemeContents` (song song theo lĩnh vực, lọc chống bịa, `failedDomains`) + `logFeedback`; 2 endpoint trong `EduAiController`; openapi snapshot cập nhật (schema `EduAi*Request/Response` để không trùng tên).
- Quyết định: người không TEAM/`canAssignContents` ⇒ 7005 `YEAR_PLAN_EDIT_FORBIDDEN`; `themeId` lạ trong request bị bỏ qua (prompt chỉ dùng chủ đề của kế hoạch); mọi lĩnh vực lỗi ⇒ ném lỗi đầu tiên (8001/8002), một phần lỗi ⇒ `failedDomains`.
- FE: `AiThemeContentModal` (nút trong "Thao tác" card 2 kế hoạch tổ, Lưu trước khi gọi AI, duyệt theo chủ đề → căn cứ, áp dụng chỉ thêm tick, gửi feedback), `FilterableGoalTable` (tìm không dấu + "Chưa thuộc chủ đề nào") bọc `GoalTable` không sửa `GoalTables.jsx`; logic thuần `models/education-plan/aiThemeContent.mjs`.
- Kiểm: `EduAiServiceImplTest` 10/10, `OpenApiSnapshotTest` 4/4, `ArchitectureRulesTest` 14/14, `AiPropertiesTest`+`ProviderChainAiGatewayTest` 16/16; FE `npm run test:edu` 416/416, lint, check:api (0 lỗi), check:design ok. E2e chưa chạy (chỉ khi Tiền yêu cầu); chưa gọi AI thật.

### 2026-11-22 — Build and Test + Phase 3
- Build/test: BE `mvnw test` toàn bộ 2221 test (0 lỗi, 2 bỏ qua, BUILD SUCCESS); FE `npm run build`, lint, check:api (0 lỗi), test EDU-103 14/14, `test:edu` 416/416.
- Phase 3: docs/api, API_INDEX, FE_API_CALLS, module card đã cập nhật ở bước code; chép bản chụp `nghiep_vu_chuan/tai_lieu/modules_education-plan.md`; `kiem_tra_nghiep_vu.mjs` khớp 103 rule EDU + 6 tài liệu; lưu mốc `7ab6a35`.
- Tiền 22/11: chưa deploy. Việc đổi `hasRole` → permission ở kế hoạch giáo dục tách thành việc riêng sau EDU-103 (Q2: quyền theo loại hồ sơ × việc; Q3: chuỗi ký lọc role theo quyền "ký hồ sơ X" và kiểm lại lúc ký) — ghi ở `aidlc/spaces/default/intents/261121-edu-ai-content-map/inception/requirements-analysis/requirements.md` mục Ngoài phạm vi; chưa ghi bản chuẩn.
- Còn lại: kiểm tay/e2e trên trình duyệt, gọi AI thật (thời gian chờ, tỉ lệ giữ), soát diff tài liệu sinh tự động (route `menu-ai-drafts` có sẵn bị kéo theo) trước khi commit.
