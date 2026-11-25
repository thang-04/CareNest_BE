---
title: Xóa Tóm tắt AI hỗ trợ duyệt giáo án ngày
status: done
owner: Tiền
jira: G94-467 / BE-FEAT-183
branch: feature/G94-467-BE-FEAT-183-ai-lesson-plan-rag
modules: [education-plan]
rules: [EDU-96]
risk: medium
created: 2026-11-26
updated: 2026-11-26
---
# Plan: xóa vĩnh viễn "AI duyệt giáo án" (Tóm tắt AI, EDU-96)

Ngày: 26/11/2026 · Trạng thái: **Tiền duyệt 26/11/2026 (chỉ xóa Tóm tắt AI), đã làm** · Nhánh: BE `feature/G94-467-BE-FEAT-183-ai-lesson-plan-rag`, FE `feature/G94-468-FE-FEAT-183-ai-lesson-plan-rag` (hoặc nhánh khác Tiền chỉ định)

## Rule đối chiếu

- EDU-96 (CONFIRMED, Tiền 18/11/2026) có 2 phần:
  1. Khung **"Kiểm tra"**: tiêu chí đếm được, code kiểm, **không dùng AI**.
  2. Tab **"Tóm tắt (AI)"**: AI viết 3–5 dòng + "Ý cần xem lại", nút "Đưa vào ghi chú" gom nháp ghi chú Trả lại.
- Không đụng EDU-95 (AI hỗ trợ soạn), EDU-103/105/106/113 (AI gom nội dung, RAG).
- Không đổi DB: tính năng không lưu gì (chỉ gợi ý), không migration.

**Mặc định của plan:** xóa phần 2 (Tóm tắt AI), giữ phần 1 (Kiểm tra). Nếu Tiền muốn bỏ cả khung Kiểm tra ⇒ xem mục "Tùy chọn B".

## BE (CareNest_BE)

| File | Sửa |
|---|---|
| `controller/eduplan/EduAiController.java` | Xóa endpoint `POST /education-plans/ai/class-docs/day/{docKey}/review` (`reviewDay`) |
| `service/eduplan/EduAiService.java` | Xóa `reviewDay` |
| `service/eduplan/impl/EduAiServiceImpl.java` | Xóa `reviewDay` (prompt `edu-day-review`) |
| `dto/eduplan/EduAiResponse.java` | Xóa record `DayReview`, `ReviewNote` |
| `service/eduplan/EduClassDocService.java` + `impl/EduClassDocServiceImpl.java` | Xóa `requireDayReviewAccess` (chỉ `reviewDay` gọi) |
| `docs/api/openapi.yaml`, `docs/api/API_INDEX.md`, `docs/system/FE_API_CALLS.md` | Xóa endpoint + schema `DayReview`, `ResponseJsonDayReview` |

Giữ nguyên: `GET /class-docs/{kind}/{docKey}/checks` (`getClassDocChecks`) và test của nó.

## FE (CareNest_FE)

| File | Sửa |
|---|---|
| `components/education-plan/ReviewAssistPanel.jsx` | Bỏ tab Tóm tắt (AI), state `summary`, nút "Đưa vào ghi chú"; bỏ props `dayKey`, `canSummarize`, `canNote`, `noteDraft`, `onNoteDraft`; panel chỉ còn danh sách Kiểm tra (không còn `Tabs`) |
| `pages/education-plan/WeekPlanPage.jsx` | Bỏ state `noteDraft` (+ `setNoteDraft('')` dòng ~336), bỏ `defaultNote={noteDraft}` ở `ReviewActions`, bỏ props đã xóa ở `ReviewAssistPanel`; sửa comment EDU-96 |
| `services/education-plan/educationPlanService.js`, `api/educationPlanApi.js` | Xóa `reviewDay` |
| `docs/integration/BE_API_INDEX.md`, `FE_API_CALLS.md` | Xóa dòng endpoint review |
| `docs/features/education-day-plan-template.md` | Bỏ đoạn Tóm tắt AI ở mục "AI hỗ trợ soạn và duyệt" |

Không đổi CSS/token/style. Kiểm `ReviewActions` vẫn chạy khi không truyền `defaultNote`.

## Tài liệu nghiệp vụ (theo AGENTS.md mục 4)

1. `nghiep_vu_chuan/ke_hoach_giao_duc.md`: sửa EDU-96 bỏ phần Tóm tắt AI ("Thay đổi 26/11/2026: bỏ Tóm tắt AI"), thêm Nhật ký thay đổi.
2. Đồng bộ `CareNest_BE/docs/business/BUSINESS_RULES.md` EDU-96 (nguồn/ngày), `docs/modules/education-plan.md` nếu có nhắc.
3. Chép lại bản chụp `nghiep_vu_chuan/tai_lieu/fe_education-day-plan-template.md` (+ `modules_education-plan.md` nếu đổi).
4. `node nghiep_vu_chuan/luu_lich_su.mjs "bo Tom tat AI EDU-96"`.

## Kiểm chứng (chỉ unit test liên quan)

- BE: compile + test `EduClassDocServiceIntegrationTest` chỉ case `getClassDocChecks`? — đây là Testcontainers ⇒ **không chạy** (theo AGENTS.md); chỉ `mvnw -q compile` cho chắc không còn tham chiếu.
- FE: `node --test` các test education-plan chạm `WeekPlanPage`/`ReviewAssistPanel` (nếu có); grep `reviewDay|DayReview|canSummarize` = 0.
- Không chạy e2e.

## Commit

- BE: `G94-467: Remove AI day plan review summary`
- FE: `G94-468: Remove AI summary tab from review panel`
- Push nhánh task, không PR.

## Tùy chọn B: bỏ cả khung "Kiểm tra"

Thêm: xóa `ReviewAssistPanel.jsx`, nút "Kiểm tra" + state `assistOpen` ở `WeekPlanPage`, `getClassDocChecks` FE; BE xóa endpoint `/checks` + `getClassDocChecks` (kiểm tiêu chí khi **gửi** vẫn giữ vì dùng chung code), sửa test `EduClassDocServiceIntegrationTest` ~dòng 1175; EDU-96 chuyển SUPERSEDED.
