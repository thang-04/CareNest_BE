---
title: Đưa phần còn lại của khảo sát team (210/210) vào docs nghiệp vụ 3 repo
status: in-progress
owner: thangnd
jira: G94-254 (không gắn mã công việc — user chốt 29/10)
branch: feature/G94-254-survey-business-context
modules: [health, learning-observation, education-plan, attendance, notification, reporting, ai-assistance, identity-access]
rules: [HLT-01, HLT-03, HLT-08, HLT-09, HLT-10, HLT-11, HLT-12, HLT-13, HLT-14, EDU-01, EDU-02, EDU-03, EDU-04, EDU-05, EDU-06, EDU-07, EDU-08, EDU-09, EDU-10, OBS-01, OBS-02, OBS-03, OBS-05, OBS-06, OBS-07, OBS-08, OBS-09, OBS-10, OBS-12, OBS-13, OBS-14, OBS-15, PAR-01, PAR-03, PAR-05, PAR-06, PCK-01, PCK-02, PCK-03, PCK-04, RPT-01, RPT-02, RPT-03, AI-07, AI-08, AI-09, AI-10, AUTH-03, AUTH-21, FAC-01, FAC-02, FAC-03, FAC-04]
risk: medium
created: 2026-10-29
updated: 2026-10-29
---
# Đưa phần còn lại của khảo sát team (210/210) vào docs nghiệp vụ 3 repo

Liên quan:
- `2026-10-27-survey-auth-alignment.md` — SCO/ORG/ROL.
- `2026-10-28-survey-business-alignment.md` — CHD..KIT, HLT-01.
- Bộ khảo sát: `CareNest_CODE/docs/survey/carenest-business-survey.html`, ngoài repo. Đã trả lời 210/210, có 21 câu ghi "chưa rõ".

## Context
106 câu đầu đã vào docs ngày 27–28/10 (riêng HLT-01 nay thêm ghi chú "có thể tự cấu hình"). Plan này xử lý 104 câu còn lại: HLT-02..09, DHL, EDU, DAS, PER, REW, PAR, PCK, RPT, AI, FAC, OPS, INT, DOC.

Hiện trạng docs đang lệch khảo sát ở các điểm sau:
- `BUSINESS_RULES.md`:
  - OBS-08 vẫn ghi "không làm giáo án", còn ADR-0006 đang OPEN.
  - FAC-01..04 vẫn CONFIRMED.
  - Các pending P-01, P-02, P-11, P-12, P-13, P-13b, P-16b chưa đóng.
  - Chưa có rule cho đón trẻ, báo cáo, và phạm vi AI.
- NFR (`docs/quality/NFR.md`) chưa có số liệu theo khảo sát.

Plan chỉ sửa docs: không đổi code, migration, API; không commit; không sửa Report 1–4.

## Quyết định đã chốt
1. App vẫn cho bếp dùng: App = phụ huynh + GV + bếp; AUTH-21, AUTH-22 giữ nguyên. INT-10 hiểu là App tập trung cho phụ huynh/GV (điểm danh, sức khỏe, đánh giá, đón trẻ, thông báo); bếp chỉ giữ các màn tối thiểu (số suất theo lớp, thực đơn, ảnh mẫu, sổ lưu mẫu) — user, 29/10.
2. GV cũ được xem lại, chỉ đọc, dữ liệu của lớp và năm học mình từng được phân công; không xem lớp mới của năm mới. Đây là mở rộng AUTH-03 (PER-08); phần code policy làm sau — user, 29/10.
3. Plan này viết nhóm rule EDU lên `dev` theo khảo sát, giữ các rule của nhánh G94-226 không mâu thuẫn. EDU thay OBS-08; đóng ADR-0006 và P-01; thêm module card education-plan. Người phụ trách giáo án review — user, 29/10 (INT-02: làm lại trên dev).
4. CSVC: FAC-01..04 đổi sang REMOVED (giữ dòng để truy vết). Xóa `docs/modules/facility-issue.md` và `docs/business/flows/facility-issue.md`, gỡ khỏi các map và bảng tính năng. Code FE vẫn để ẩn (FAC-02) — user, 29/10.
5. Câu ghi "chưa rõ" chuyển thành PENDING, không nâng mức. Riêng INT-05 ("lấy mock web làm chuẩn") trái nguyên tắc "BE là chủ contract" nên giữ nguyên nguyên tắc cũ, ghi lại để hỏi tiếp — user, 29/10.
6. Docs của mỗi repo cập nhật trong phase riêng của repo đó (INT-11); FE/APP chỉ sửa khi user cho phép ngay lúc ấy.

## Làm rõ nghiệp vụ
Nguồn ghi: "Khảo sát team 27/10 (<mã câu>)"; các quyết định 1–4 ghi thêm "user chốt 2026-10-29".

| Mảng (câu) | Rule BE sẽ ghi |
| --- | --- |
| HLT-02..09 | HLT-01 (thêm: chu kỳ cấu hình được); HLT-03 → CONFIRMED chuẩn WHO, đóng P-13; mới HLT-08 (khám bác sĩ 2 lần/năm), HLT-09 (GV cân đo và nhập), HLT-10 (hệ thống tự phân loại, GV chỉnh kèm lý do), HLT-11 (phụ huynh thấy sau khi GV/PHT công bố), HLT-12 (suy dinh dưỡng/thừa cân chỉ đánh dấu, không có quy trình can thiệp — HLT-07, PER-07), HLT-13 (lưu mọi lần đo; sửa kèm lý do, có lịch sử) |
| DHL-01..05 | Mới HLT-14: sức khỏe trong ngày — GV ghi triệu chứng và xử trí theo mức độ, phụ huynh nhận thông báo ngay; tai nạn nhỏ ghi vào ghi chú và báo phụ huynh. Về sớm → PCK-04. Tổng hợp cho BGH → RPT-01 |
| EDU-01..12 | Nhóm mới EDU-01..10 (module education-plan): mục tiêu năm → chủ đề nhánh → chủ đề tuần → kế hoạch các ngày; tổ trưởng soạn mục tiêu (cùng PHT), PHT được phân công duyệt và ban hành; GV lập chủ đề nhánh + kế hoạch, duyệt GV → tổ trưởng → PHT (tổ trưởng duyệt được kế hoạch lớp mình); chỉ duyệt một lần đầu năm, tuần/ngày không duyệt; bấm duyệt, không ký; mẫu có cấu trúc + file Word; các mục của kế hoạch ngày; đã duyệt thì nhân bản bản mới để sửa; phụ huynh thấy tên hoạt động; quyền theo mã (ADR-0017, AUTH-23). OBS-07 → CONFIRMED (ngữ cảnh lấy từ kế hoạch ngày); OBS-08 → SUPERSEDED |
| DAS-01..10 | OBS-01, OBS-02 sửa (mọi trẻ có mặt, mặc định "bình thường"; 7 mặt; 3 mức Đạt / Cần cố gắng / Chưa đạt); mới OBS-12 (kiến thức–kỹ năng lấy mục tiêu từ kế hoạch ngày), OBS-13 (chỉ sửa trong ngày), OBS-14 (phụ huynh xem đánh giá ngày). P-11 thu hẹp còn một câu "tiêu chí cố định theo Bộ hay trường cấu hình" (DAS-09 chưa rõ) |
| PER-01..08 | OBS-03, OBS-05, OBS-06 sửa (kỳ tuần/tháng/học kỳ/năm, mẫu riêng của trường, AI nháp, GV xác nhận là xong, phụ huynh nhận tất cả; hồ sơ phát triển không có mục theo dõi riêng); đóng P-12, P-16b; AUTH-03 mở rộng theo quyết định 2 |
| REW-01..06 | OBS-10 → CONFIRMED (≥4 ngày đi học + ≥3 cờ/tuần; 3 phiếu/tháng; GV tự quyết, không gợi ý; phụ huynh thấy ngay); mới OBS-15 (khen cuối năm: GV đề xuất → PHT xét → HT duyệt) |
| PAR-01..07 | PAR-01 sửa (10 mục phụ huynh xem); PAR-03 → CONFIRMED (danh sách chỉ hiện sau công bố), đóng P-13b; mới PAR-05 (thông báo chỉ BGH gửi, qua push + trong App, không theo dõi đã đọc), PAR-06 (1 tài khoản xem mọi con) |
| PCK-01..07 | Nhóm mới PCK-01..04 (module attendance): đón trẻ là Must; màn đón hiện danh sách phụ huynh, đúng người thì xác nhận, người lạ thì gọi phụ huynh rồi ghi lý do; không đăng ký trước người đón hộ trên App; ghi người đón, giờ đón (kể cả đón muộn, về sớm) và báo phụ huynh |
| RPT-01..06 | Nhóm mới RPT-01..03 (module reporting): báo cáo ngày cho BGH (sĩ số, suất ăn, việc chờ duyệt, trẻ ốm theo lớp); GV viết nhận xét lớp 1–2 câu/ngày; báo cáo định kỳ (ngày ăn/tháng, thực đơn tuần, kết quả học kỳ, tổng kết năm, tồn kho); xuất Excel + PDF; lọc theo cơ sở |
| AI-01..09 | Mới AI-07..10: 5 tính năng AI (nháp nhận xét định kỳ, nháp kế hoạch, giải thích sức khỏe, gợi ý thực đơn có bật/tắt, nháp tổng kết năm — phụ thuộc P-34); input dữ liệu cấu trúc + tiêu chí của trường + nhận xét tự do (vẫn theo AI-04); provider Gemini/Groq, đóng P-02 và ADR-0007; khi AI tắt dùng mẫu điền số liệu; giọng ngắn gọn, khách quan |
| FAC-01..02 | Theo quyết định 4 |
| OPS-01..08 | `NFR.md`: tải trang ≤3 s với 100 người dùng đồng thời; tổng hợp điểm danh ≤30 s; AI ≤30 s; ≥99% trong giờ học; backup hằng ngày; cao điểm 7–9h. `PROJECT_CONTEXT`: thiết bị, dữ liệu giả lập, UAT (1 PHT, 2 GV, 1 bếp, 1–2 tuần), hướng dẫn sử dụng |
| INT-01..11 | `PROJECT_CONTEXT`/`CURRENT_STATE`: đánh số migration theo thứ tự merge vào dev (giữ số trước khi viết); làm lại G94-226 trên dev; worktree BE-ai giữ local; mỗi người làm song song phần mình; mã rule BE là chuẩn (cần bảng đối chiếu GBR — việc riêng); đóng băng code 30/11; trưởng nhóm chốt sau khi nghe BA; INT-05 ghi là đang hỏi |
| DOC-01..05 | Ngoài repo (Report 3/4) — chỉ liệt kê cho BA, không ghi vào docs |

### Câu hỏi mở
Không còn câu nào chặn plan. Các câu còn treo được đưa vào Pending register:
- Giữ P-11, P-16, P-18..P-28.
- Mới:
  - P-29: thời lượng chủ đề (EDU-07).
  - P-30: mẫu nhận xét định kỳ của trường (PER-02, cần xin mẫu).
  - P-31: phạm vi khen thưởng (REW-05).
  - P-32: kênh ý kiến phụ huynh (PAR-04).
  - P-33: nguồn ảnh để đối chiếu người đón (PCK-04 so với PCK-02).
  - P-34: tổng kết năm học trong hệ thống (RPT-04).
  - P-35: chi phí vận hành (OPS-03).

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc |
| --- | --- | --- | --- | --- |
| Bếp còn trên App (INT-10 vs AUTH-21) | Giữ, màn tối thiểu | user | 29/10 | AUTH-21, CROSS_REPO_MAP |
| GV cũ xem dữ liệu năm cũ (PER-08 vs AUTH-03) | Có, chỉ đọc | user | 29/10 | AUTH-03 |
| Ai viết EDU lên dev | Plan này | user | 29/10 | EDU-*, OBS-08, ADR-0006 |
| CSVC trong docs | Xóa card + flow, rule REMOVED | user | 29/10 | FAC-*, các map |

## Acceptance criteria
- **AC-1:** mỗi câu đã trả lời trong 14 mảng đều có rule, mục NFR/context tương ứng, hoặc được ghi là ngoài repo; 9 câu "chưa rõ" mới đều có P-xx hoặc ghi chú.
- **AC-2:** P-01, P-02, P-12, P-13, P-13b, P-16b nằm ở bảng "Đã đóng".
- **AC-3:** BE, FE, APP chạy `node scripts/check-ai-layer.mjs` không phát sinh lỗi mới (APP vẫn còn lỗi E7 có từ trước).
- **AC-4:** Grep `facility-issue|FAC-0` chỉ còn ở dòng REMOVED, trong plan và report cũ.

## Phases
### Phase 1 — Rule + ADR (BE)
- Files:
  - M `docs/business/BUSINESS_RULES.md`
  - C `docs/decisions/ADR-0019-education-plan-on-dev.md` — module education-plan làm lại trên dev, thay ADR-0006
  - M `ADR-0006` (SUPERSEDED), `ADR-0007` (chốt provider), `ADR-0010` (field đã chốt)
  - M `docs/INDEX.md`
- Exit: `node scripts/check-ai-layer.mjs` → 0 lỗi. **Dừng chờ review.**

### Phase 2 — Card, flow, context (BE)
- Files:
  - C `docs/modules/education-plan.md`, `docs/business/flows/{education-plan,pickup}.md`
  - M `docs/modules/{learning-observation,health,attendance,notification,reporting,ai-assistance,README}.md`
  - D `docs/modules/facility-issue.md`, `docs/business/flows/facility-issue.md`
  - M `docs/business/{USER_ROLES,DOMAIN_MODEL}.md`, `docs/context/{PROJECT_CONTEXT,CURRENT_STATE,GLOSSARY}.md`
  - M `docs/quality/NFR.md`
  - M `docs/system/{CROSS_REPO_MAP,MODULE_MAP,SYSTEM_ARCHITECTURE}.md`
  - M `docs/architecture/PACKAGE_STRUCTURE.md`, `docs/backend-coding-guide.md` (chỉ chỗ nhắc facility)
  - M `.ai/CONTEXT_MAP.yaml`, `.ai/ROUTER.md`
- Exit: check-ai-layer → 0 lỗi; grep AC-4 sạch. **Dừng chờ review.**

### Phase 3 — Docs FE (chỉ khi user cho phép lúc đó)
- Các file `CURRENT_STATE`, `REPOSITORY_CONTEXT`, `ROUTE_MAP`, `features/{README,daily-assessment,…}`, `.ai/*`: đổi pending đã đóng thành rule ID; ghi chú "Lệch BE (29/10)" ở các màn mock lệch rule (đánh giá, giáo án, đón trẻ, CSVC ẩn).
- Exit: FE check-ai-layer không có lỗi mới.

### Phase 4 — Docs APP (chỉ khi user cho phép lúc đó)
- Các file `CURRENT_STATE`, `NAVIGATION`, `features/{parent,teacher,kitchen}`, `.ai/*`: App tập trung cho phụ huynh/GV, bếp giữ màn tối thiểu.
- Exit: APP check-ai-layer không có lỗi mới.

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| EDU trên dev lệch nhánh G94-226 (ID EDU, ADR-0017 trùng số) | Merge khó | Dùng ADR-0019; ghi bảng đối chiếu EDU của G94-226 trong card education-plan | `git diff` |
| Xóa card/flow CSVC | Mất tài liệu cũ | File vẫn còn trong lịch sử git; rule giữ dòng REMOVED | `git checkout` file |
| AUTH-03 mở rộng nhưng code chưa đổi | Agent tưởng đã có quyền | Ghi rõ "code chưa làm" ở rule và CURRENT_STATE | — |
| Sửa chồng lên thay đổi chưa commit của plan 27/10, 28/10 | Lẫn diff | Chỉ sửa đúng dòng | — |

## Ngoài phạm vi plan
- Report 1–4 (INT-07, DOC-01..05, FAC-01 phần xóa khỏi SRS/R2/R4): BA sửa ngoài repo.
- Bảng đối chiếu GBR → mã BE (INT-06): task riêng.
- Sửa code FE/APP/BE theo rule mới.

## Validation log
- [x] Không đổi contract/code
- [x] Không dùng rule PENDING như đã chốt (21 câu chưa rõ → P-xx)
- [x] >8 file ⇒ chia 4 phase, mỗi phase có Exit
- [x] Tự review: không placeholder, không mâu thuẫn

## Progress log
### 2026-10-29 — Draft
- Lập plan từ khảo sát 210/210; 4 câu mâu thuẫn đã hỏi và user chốt trong phiên. Chờ user duyệt.

### 2026-10-29 — Plan approved
- User duyệt trong phiên ("Approve, start").

### 2026-10-29 — Phase 1 (chờ review)
- Commit: chưa commit
- Sửa `BUSINESS_RULES.md`:
  - Mở rộng AUTH-03 (code chưa làm); AUTH-21 ghi App tập trung cho phụ huynh/GV.
  - Nhóm mới: PCK-01..04 (sau ATT), EDU-01..10, RPT-01..03.
  - HLT: sửa HLT-01, HLT-03, HLT-05; thêm HLT-08..14.
  - OBS: viết lại OBS-01..10; thêm OBS-12..14. OBS-08 REMOVED, OBS-07 CONFIRMED.
  - FAC-01..04 REMOVED.
  - PAR: sửa PAR-01, PAR-03, PAR-04; thêm PAR-05, PAR-06.
  - AI: thêm AI-07..10.
  - Pending: P-11 thu hẹp; thêm P-29..P-35; đóng P-01, P-02, P-12, P-13, P-13b, P-16b.
- Sửa ADR:
  - C ADR-0019.
  - ADR-0006 → SUPERSEDED. ADR-0007: chốt provider. ADR-0010: danh sách đã chốt.
  - `INDEX.md`: các dòng 0006, 0007, 0010, 0019; sửa luôn dòng 0009 còn ghi OPEN (lỗi sót từ 28/10).
- Verify: `node scripts/check-ai-layer.mjs` → `check-ai-layer (BE): 0 lỗi, 1 cảnh báo · E1 ok · E2 1 · E3 ok · E4 ok · E5 ok · E6 bỏ qua · E7 ok`. Cảnh báo là plan nhắc file sẽ tạo ở Phase 2.
- Khác plan:
  - Ý "kiến thức–kỹ năng lấy mục tiêu từ kế hoạch ngày" gộp vào OBS-07 thay vì tạo OBS-12. OBS-12..14 dùng cho khóa sửa trong ngày, phụ huynh xem đánh giá ngày, khen cuối năm. Không có OBS-15.
  - Nhóm PAR đổi tên thành "Hiển thị cho phụ huynh, thông báo"; nhóm OBS đổi tên theo nội dung mới.
  - ADR-0019 chưa ghi đường dẫn card/flow (Phase 2 mới tạo), để tránh lỗi E2.
- Tiếp: Phase 2 sau khi user review.

### 2026-10-29 — Phase 2 (chờ review)
- User bảo "Start Phase 2". Commit: chưa commit.
- Tạo: card `docs/modules/education-plan.md` (có bảng đối chiếu rule G94-226 → `dev`), flow `docs/business/flows/education-plan.md`, `docs/business/flows/pickup.md`.
- Xóa: card và flow CSVC (theo quyết định 4).
- Viết lại: card learning-observation, health; flow child-observation, health-check.
- Sửa card: attendance (PCK), notification (PAR-05), reporting (RPT, P-34), ai-assistance (AI-07..10, provider), README.
- Context:
  - `PROJECT_CONTEXT`: bảng tính năng FE-03..FE-10, đón trẻ, báo cáo; Exclusions; mục mới "Vận hành và kiểm thử" (OPS) và "Quy trình nhóm" (INT, ghi INT-05 đang hỏi); nguồn khảo sát 210/210.
  - `CURRENT_STATE` (thêm dòng education-plan; AUTH-03 mở rộng ghi "code chưa làm"), `GLOSSARY`.
- `USER_ROLES`, `DOMAIN_MODEL`.
- `NFR.md`: PERF-01/02 đổi từ 2 s/50 người sang 3 s/100 người; thêm PERF-04, PERF-05, AVAIL-02; OPS-01 sao lưu hằng ngày; OPS-03 PENDING (P-35).
- Map: `CROSS_REPO_MAP`, `MODULE_MAP` (education-plan ở L2), `SYSTEM_ARCHITECTURE`, `PACKAGE_STRUCTURE` (`eduplan`), `.ai/CONTEXT_MAP.yaml`, `.ai/ROUTER.md` (Bảng 3: bỏ "giáo án"; thêm CSVC, đăng ký trước người đón hộ).
- ADR-0019: thêm đường dẫn card và flow.
- Verify:
  - `node scripts/check-ai-layer.mjs` → `check-ai-layer (BE): 0 lỗi, 4 cảnh báo · E1 ok · E2 4 · E3 ok · E4 ok · E5 ok · E6 bỏ qua · E7 ok`. Cả 4 cảnh báo là plan này nhắc 2 file CSVC đã xóa.
  - Grep AC-4 (`facility-issue|FAC-0`, ngoài plan và report): chỉ còn nhóm FAC REMOVED trong `BUSINESS_RULES.md`.
- Khác plan:
  - Sửa thêm các file ngoài danh sách vì còn nhắc CSVC hoặc giáo án: `README.md`, `docs/INDEX.md`, card school-structure, mô tả skill implement-feature (`.claude/`, `.agents/`).
  - `BUSINESS_RULES.md`: ATT-10 trỏ sang PCK-02, PCK-04, bỏ ghi chú "chờ khảo sát PCK".
  - Sửa chỗ còn sót từ 28/10: MealParticipation trong `GLOSSARY`, `DOMAIN_MODEL`; "kho/NCC OPEN" trong bảng phạm vi của guide.
  - Nhận xét lớp hằng ngày (RPT-02) đặt ở learning-observation vì reporting không giữ dữ liệu. Đây là đề xuất, chốt khi thiết kế bảng.
- Thiếu nguồn:
  - Ngoài dị ứng, đợt khám bác sĩ (HLT-08) cần ghi gì: chưa có rule.
  - 3 rule của G94-226 chưa đưa lên `dev`: ai xem bản nào, ai xóa nháp, đánh số MT.
- Tiếp: Phase 3 (docs FE) chỉ làm khi user cho phép.

### 2026-10-29 — Phase 3 (chờ review)
- User duyệt Phase 2 và cho sửa docs `CareNest_FE` ("Duyệt, làm Phase 3"). FE đang ở nhánh `dev`, có sẵn thay đổi chưa commit của người khác; chỉ sửa đúng dòng. Commit: chưa commit.
- Sửa ở FE:
  - `docs/context/CURRENT_STATE.md`: thêm mục "Đồng bộ nghiệp vụ BE 2026-10-29" (pending đã đóng → rule ID, rule mới, danh sách màn lệch BE). Cập nhật bảng screen group (đón trẻ, kế hoạch giáo dục, CSVC gạch bỏ) và bảng quyết định (người nhập cân đo, ngữ cảnh hoạt động).
  - `docs/architecture/ROUTE_MAP.md`: sửa dòng BGH/GV theo OBS, HLT, RPT; thêm đón trẻ, kế hoạch giáo dục, khen thưởng, thông báo trường; gạch CSVC; mục actor PENDING đổi thành phiếu khám/cân đo (actor GV — HLT-09).
  - `docs/features/README.md`: bảng feature (đón trẻ, kế hoạch giáo dục, báo cáo, CSVC gạch bỏ); ghi chú mã HLT-08, HLT-09 ở BE nay là rule khác.
  - `docs/features/daily-assessment.md`: thêm mục "Lệch BE (29/10)".
  - `docs/features/daily-reception.md`, `docs/context/REPOSITORY_CONTEXT.md`, `README.md`, `AGENTS.md` (nguyên tắc 8, không thuộc khối dùng chung), `.ai/CONTEXT_MAP.yaml`, `.ai/ROUTER.md` (Bảng 3).
- Sửa BE: HLT-10 chép thiếu ý khảo sát (HLT-05: "giáo viên hoặc y tế"); thêm y tế trường, ghi rõ chưa có vai trò.
- Verify FE: `node scripts/check-ai-layer.mjs` → `check-ai-layer (FE): 0 lỗi, 13 cảnh báo · E1 ok · E2 10 · E3 ok · E4 ok · E5 3 · E6 bỏ qua · E7 ok`, giống trước khi sửa (13 cảnh báo cũ trong `docs/plans/**`).
- Khác plan:
  - Checker FE đối chiếu `BE:` với worktree `CareNest_BE-main` (nhánh `main`, 14/10). Card education-plan, flow pickup, flow education-plan chưa có ở đó nên docs FE ghi tên kèm "chưa merge nhánh chính BE", theo quy ước FE đã dùng cho `MEAL_NUTRITION_AI_SCHEMA.md`. Merge BE xong đổi lại thành link.
  - Không sửa `docs/architecture/CODING_GUIDE.md`: ví dụ module `facility-*` chỉ minh họa cấu trúc code.
  - Không sửa plan và incident cũ của FE.
- Cần team chốt (ghi ở FE `CURRENT_STATE`): phiếu khám Phụ lục II, mức theo dõi và phiếu theo dõi (user chốt 17/10 ở FE) lệch HLT-10, HLT-12, OBS-03; luồng duyệt kế hoạch giáo dục mock lệch EDU-02..04; mở lại ngày đánh giá lệch OBS-12.
- Tiếp: Phase 4 (docs APP) chỉ làm khi user cho phép.

### 2026-10-29 — Phase 4 (chờ review)
- User duyệt Phase 3 và cho sửa docs `CareNest_APP` ("Duyệt, làm Phase 4"). APP đang ở nhánh `dev`, có sẵn thay đổi chưa commit; chỉ sửa đúng dòng. Commit: chưa commit.
- Sửa ở APP:
  - `docs/context/CURRENT_STATE.md`: cập nhật dòng ngày; thêm "Lệch BE (29/10)" vào dòng Parent, Teacher, Kitchen; viết lại danh sách PENDING (thêm P-30..P-33); thêm các pending đóng ngày 29/10.
  - `docs/architecture/NAVIGATION.md`: bảng Parent (phát triển & hoạt động, đón con, thông báo trường), Teacher (đánh giá ngày, đón trẻ, nhận xét định kỳ, cân đo, sức khỏe trong ngày; gạch CSVC), Kitchen (màn tối thiểu); OBS-10 → OBS-10, OBS-14.
  - `docs/features/teacher/README.md`: App tập trung cho GV, AUTH-03 mở rộng; đánh giá, cân đo, đón trẻ, lịch hoạt động theo rule mới, kèm "Lệch BE (29/10)"; CSVC REMOVED.
  - `docs/features/parent/README.md`: đóng P-12, P-13, P-13b; hoạt động theo EDU-09; thêm mục "Đón con"; thông báo trường (PAR-05).
  - `docs/features/{kitchen,principal,vice-principal}/README.md`, `docs/features/README.md`, `docs/context/REPOSITORY_CONTEXT.md`, `AGENTS.md` (nguyên tắc 6, 8 — không thuộc khối dùng chung), `.ai/CONTEXT_MAP.yaml` (thêm từ khóa đón trẻ; CSVC không còn card), `.ai/ROUTER.md` (Bảng 3).
- Verify APP: `node scripts/check-ai-layer.mjs` → `check-ai-layer (APP): 1 lỗi, 1 cảnh báo · E1 ok · E2 ok · E3 ok · E4 ok · E5 ok · E6 bỏ qua · E7 2`, giống trước khi sửa. Lỗi E7 (`.claude/rules/state.md` glob chết) có từ trước, ngoài phạm vi (AC-3).
- Khác plan:
  - Card education-plan, flow pickup của BE ghi bằng tên kèm "chưa merge nhánh chính BE", cùng quy ước Phase 3 (checker APP cũng đối chiếu `CareNest_BE-main`).
  - Bếp: quyết định 1 liệt kê 4 màn tối thiểu (số suất theo lớp, thực đơn, ảnh mẫu, sổ lưu mẫu), không có định lượng và dị ứng. Docs APP vẫn giữ 2 màn này vì BE `USER_ROLES` cho bếp xem; ghi "hỏi trước khi thêm màn".
  - Không sửa plan, incident, design cũ của APP.
- Còn ngoài phạm vi: khối "Quy tắc chung CareNest" trong `CareNest_APP/AGENTS.md` vẫn là bản cũ (Conventional Commits, `Refs: <KEY>`), khác BE/FE (`<mã-jira>: <mô tả>`). Đồng bộ là việc riêng.
- Tiếp: cả 4 phase xong; chờ user review. Plan chuyển `completed` khi user chốt.
