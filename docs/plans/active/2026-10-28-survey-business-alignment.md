---
title: Đưa kết quả khảo sát team (bán trú, hồ sơ trẻ, sức khỏe) vào docs nghiệp vụ
status: in-progress
owner: thangnd
jira: G94-254 (không gắn mã công việc — user chốt 29/10)
branch: feature/G94-254-survey-business-context
modules: [attendance, nutrition, child, health]
rules: [ATT-01, ATT-02, ATT-03, ATT-04, ATT-05, ATT-05b, ATT-06, ATT-08, ATT-09, ATT-10, ATT-11, NUT-02, NUT-04, NUT-07, NUT-08, NUT-11, NUT-12, NUT-13, NUT-14, NUT-15, NUT-16, NUT-17, NUT-18, NUT-19, NUT-20, NUT-21, NUT-22, NUT-23, CHD-01, CHD-02, CHD-03, CHD-04, CHD-05, AUTH-05, AUTH-18, HLT-01, HLT-06, HLT-07, PAR-01]
risk: medium
created: 2026-10-28
updated: 2026-10-28
---
# Đưa kết quả khảo sát team (bán trú, hồ sơ trẻ, sức khỏe) vào docs nghiệp vụ

Liên quan: `2026-10-27-survey-auth-alignment.md` (đã làm SCO/ORG/ROL). Bộ khảo sát: `CareNest_CODE/docs/survey/carenest-business-survey.html` (ngoài repo), 106/210 câu đã trả lời.

## Context
Plan 27/10 đã đưa SCO/ORG/ROL vào AUTH/SCH và cố ý không sửa ATT/NUT/ADR-0009. Lần này đưa phần đã rõ của CHD, ARR, ATT, LEA, MEA, MNU, ALG, KIT, HLT-01, và 3 mục còn treo cho nutrition (ORG-01, SCO-04, ROL-02/P-05). Chỉ sửa docs; không đổi code, migration, API.

## Quyết định đã chốt
1. Trừ kho khô: công bố thực đơn ⇒ trừ dự kiến (định lượng × sĩ số dự kiến của nhóm tuổi từng cơ sở); mỗi ngày chốt suất ⇒ điều chỉnh chênh lệch. Thay NUT-11 bản 21/10 — user, 28/10.
2. Dị ứng 2 mức: nhẹ bỏ món, tăng khẩu phần món khác; nặng PHT chọn món thay thế, hệ thống gợi ý món không chứa chất đó; bếp thấy số suất theo lớp, không thấy tên — user, 28/10.
3. Tài khoản phụ huynh: gửi SMS tên đăng nhập + mật khẩu tạm, bắt đổi lần đầu; in phiếu là dự phòng tới khi có nhà cung cấp SMS; code SMS làm sau — user, 28/10 (đổi AUTH-18).
4. Ghi đè theo khảo sát: ATT-03 (sau giờ chốt trường không nhận trẻ), ATT-02 (có mặt là ăn mọi bữa của nhóm tuổi), NUT-12 + ADR-0009 (PHT ghi kiểm nhận hàng tươi 2 lần; có danh mục NCC), HLT-01 (cân đo 3 lần/năm) — user, 28/10.
5. Câu khảo sát ghi "chưa rõ" ⇒ PENDING, không nâng mức. Câu chưa trả lời giữ nguyên trạng thái cũ — user, 28/10.
6. Không sửa phần thuộc nhánh G94-226 (OBS-08, FE-04, giáo án, tổ trưởng) và phần chưa khảo sát (AI, CSVC, đánh giá, đón trẻ) — user, 28/10.

## Làm rõ nghiệp vụ
- **Actor:** HT, PHT, GV, bếp (App), phụ huynh (App). **Outcome:** rule bán trú, hồ sơ trẻ, sức khỏe đầu ngày khớp khảo sát team.
- **Đối chiếu:**
  - NUT-11 bản 21/10 (phiếu xuất theo ngày, bếp chọn lô): đổi theo quyết định 1.
  - NUT-12 / ADR-0009 23/10 (bếp ghi, PHT duyệt; không danh mục NCC): đổi theo quyết định 4.
  - AUTH-18 (in phiếu, không SMS): đổi theo quyết định 3.
- **Ảnh hưởng:**
  - Module: attendance, nutrition, child, health.
  - FE: các màn bán trú, kho, đơn nghỉ đang chạy mock theo SRS.
  - APP: bếp (ảnh mẫu, lưu mẫu), GV (điểm danh + nhiệt độ), phụ huynh (thực đơn, ảnh suất).
  - Code đã có: `GuardianAccountServiceImpl` trả mật khẩu tạm để in phiếu (`AddGuardianResponse`, `ParentPasswordResponse`), chưa đổi.

### Câu hỏi mở
Không còn câu chặn plan. Câu nghiệp vụ còn treo nằm ở Pending register (P-19..P-28).

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| Trừ kho khô lúc nào (KIT-04 vs NUT-11) | Trừ dự kiến khi công bố + điều chỉnh theo suất chốt | user | 28/10 | NUT-07, NUT-11, ADR-0009 |
| Trẻ dị ứng (ALG-01 vs khảo sát trường 18/10) | Chia 2 mức | user | 28/10 | NUT-23, AUTH-05 |
| Gửi tài khoản phụ huynh (CHD-04 vs AUTH-18) | SMS, in phiếu dự phòng | user | 28/10 | AUTH-18 |
| Ghi đè rule đã chốt | ATT-03, ATT-02, NUT-12 + NCC, HLT-01 | user | 28/10 | như cột trái |

## Acceptance criteria
Plan chỉ sửa docs, không có test code.
- **AC-1:** mọi câu đã trả lời trong 9 mảng có rule hoặc pending tương ứng, nguồn ghi mã câu khảo sát.
- **AC-2:** P đã chốt nằm ở bảng "Đã đóng".
- **AC-3:** `node scripts/check-ai-layer.mjs` → 0 lỗi.

## Phases
### Phase 1 — Docs/tri thức BE
- Files:
  - M `docs/business/BUSINESS_RULES.md`
  - M `docs/decisions/ADR-0009-nutrition-inventory-scope.md`
  - M `docs/modules/{attendance,nutrition,child,health}.md`
  - M `docs/business/flows/{attendance,leave-request,meal-management,health-check}.md`
  - M `docs/business/{USER_ROLES,DOMAIN_MODEL}.md`
  - M `docs/context/{PROJECT_CONTEXT,CURRENT_STATE}.md`
  - M `docs/database/MEAL_NUTRITION_AI_SCHEMA.md` (chỉ ghi chú)
  - M `docs/system/CROSS_REPO_MAP.md`
  - M `.ai/ROUTER.md`, `.ai/CONTEXT_MAP.yaml` (nếu có trỏ cũ)
- Exit: `node scripts/check-ai-layer.mjs` → 0 lỗi. **Dừng chờ user review.**

### Phase 2 — Đồng bộ docs FE/APP
Chỉ làm khi user cho phép trong tin nhắn lúc đó. Trước đó chỉ liệt kê các chỗ lệch.

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Sửa chồng lên docs chưa commit của phiên khác | Lẫn thay đổi | Chỉ sửa đúng dòng, không revert | `git diff` từng file |
| Rule mới lệch code đã có (AUTH-18 in phiếu) | Agent code theo docs sai thời điểm | Ghi rõ "code hiện tại: in phiếu; SMS chưa làm" | — |

## Validation log
- [x] Khẳng định về code có tên file
- [x] Không đổi contract
- [x] Không dùng rule PENDING như đã chốt
- [x] Tự review: không placeholder, không mâu thuẫn

## Progress log
### 2026-10-28 — Plan approved
- User duyệt plan trong phiên (ExitPlanMode 28/10).

### 2026-10-28 — Phase 1 (chờ review)
- Commit: chưa commit
- Sửa:
  - `BUSINESS_RULES.md`:
    - thêm nhóm CHD-01..05;
    - ATT-01..11 (mới ATT-08..11; ATT-05b viết lại, PROPOSED);
    - NUT-01..23 (NUT-14/15 REMOVED, mới NUT-17..23);
    - sửa AUTH-05, AUTH-18, HLT-01, HLT-06, PAR-01, SCH-06; thêm HLT-07;
    - đóng P-03..P-10, P-17; thêm P-19..P-28; thêm status `REMOVED`.
  - Flow: `attendance`, `leave-request`, `meal-management`, `health-check`.
  - Module card: `attendance`, `nutrition`, `child`, `health`, `school-structure`.
  - ADR-0009 (quyết định 28/10, sửa header lệch "B/C"), ADR-0005, ADR-0002.
  - Khác: `DOMAIN_MODEL`, `GLOSSARY`, `USER_ROLES`, `PROJECT_CONTEXT`, `CURRENT_STATE`, `CROSS_REPO_MAP`, `INDEX`, `NFR`, `.ai/ROUTER.md`, `.ai/CONTEXT_MAP.yaml`.
  - `MEAL_NUTRITION_AI_SCHEMA.md`: thêm §13 "Thay đổi theo khảo sát team", sửa §10, ghi chú đầu file.
- Verify:
  - `node scripts/check-ai-layer.mjs` → `check-ai-layer (BE): 0 lỗi, 0 cảnh báo · E1 ok · E2 ok · E3 ok · E4 ok · E5 ok · E6 bỏ qua · E7 ok`.
  - Grep `P-0[3-9]|P-10|P-17`: chỉ còn ở bảng "Đã đóng", ghi chú "đã đóng" và ví dụ trong workflow.
  - Không file `src/` nào do plan này sửa; 20 file `src/` đang đổi là của plan 27/10.
- Khác plan:
  - Không tạo ADR mới: quyết định kho sửa thẳng ADR-0009, theo cách các lần cập nhật trước.
  - Thêm `GLOSSARY.md`, `INDEX.md`, `NFR.md`, `ADR-0002`, `ADR-0005` vì có trỏ tới P đã đóng.
- Tiếp: Phase 2 (docs FE/APP) khi user cho phép.

### 2026-10-28 — Phase 2 (chờ review)
- User cho phép sửa docs FE + APP trong tin nhắn ("Sync FE + APP"). Chỉ docs/AI layer, không code, chưa commit.
- FE, 8 file:
  - `docs/context/CURRENT_STATE.md`, `docs/context/REPOSITORY_CONTEXT.md`, `docs/architecture/ROUTE_MAP.md`
  - `docs/features/{README,nutrition-inventory,daily-reception}.md`
  - `.ai/CONTEXT_MAP.yaml`, `.ai/ROUTER.md`
  - Nội dung: P đã đóng thay bằng rule ID; UI mock lệch rule ghi "Lệch BE (28/10)".
- APP, 15 file:
  - `docs/context/*`, `docs/architecture/NAVIGATION.md`, `docs/features/{README,kitchen,parent,teacher,principal,vice-principal}`
  - `docs/integration/BACKEND_INTEGRATION.md`, `docs/quality/DEFINITION_OF_DONE.md`
  - `AGENTS.md` (chỉ phần riêng repo), `DESIGN.md`, `.ai/ROUTER.md`, `.ai/CONTEXT_MAP.yaml`, `.claude/rules/state.md` (1 dòng P-03)
  - Màn BGH gắn ghi chú AUTH-21 (ngoài phạm vi App, giữ làm tham khảo).
- Verify:
  - FE `node scripts/check-ai-layer.mjs` → `check-ai-layer (FE): 0 lỗi, 13 cảnh báo · E1 ok · E2 10 · E3 ok · E4 ok · E5 3 · E6 bỏ qua · E7 ok` (13 cảnh báo có sẵn trong `docs/plans/**`).
  - APP → `check-ai-layer (APP): 1 lỗi, 1 cảnh báo · E1 ok · E2 ok · E3 ok · E4 ok · E5 ok · E6 bỏ qua · E7 2`. Lỗi có từ trước: `.claude/rules/state.md` glob không khớp file nào (rule chết), không do đợt này.
- Không sửa: `docs/plans/**`, `docs/knowledge/incidents/**`, `.ai/workflows/*` (chỉ dùng P-xx làm ví dụ), phần giáo án/AI/CSVC/đón trẻ.
- Code FE/APP còn lệch rule (chưa sửa, cần task riêng):
  - Công tắc Tham gia ăn (ATT-02); thiếu nhiệt độ đầu ngày (HLT-07).
  - Giờ chốt mặc định cứng `08:45` ở FE (ATT-06).
  - Trạng thái nấu, bàn giao suất (NUT-14/15).
  - Bếp ghi phiếu nhận, chọn lô (NUT-11/12); route bếp trên Web (AUTH-21).
  - Thực đơn dị ứng chưa có 2 mức (NUT-23); cho sửa thực đơn sau công bố (NUT-08).
  - App: điều chỉnh "+1 suất chờ duyệt" (ATT-03, NUT-04); GV đăng ảnh bữa (NUT-16); màn BGH (AUTH-21).
- Tiếp: user review; commit cần mã Jira.
