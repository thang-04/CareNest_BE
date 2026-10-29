---
title: Mục tiêu năm học (kế hoạch giáo dục năm theo nhóm tuổi) — API và nghiệp vụ
status: in-progress
owner: Nguyễn Quang Tiền
jira: G94-226 / BE-FEAT-09
branch: feature/G94-226-BE-FEAT-09-school-year-goals
modules: [education-plan, school-structure]
rules: [EDU-01, EDU-02, EDU-03, EDU-04, EDU-05, EDU-06, EDU-07, EDU-08, EDU-09, AUTH-03]
risk: high
created: 2026-10-22
updated: 2026-10-29
---
# Mục tiêu năm học — API và nghiệp vụ

## Bổ sung 29/10/2026 — Ba bảng cha–con (DONE, user duyệt 29/10/2026)

Phần này có cổng duyệt riêng; trạng thái in-progress của plan gốc không phê duyệt phần bổ sung.

### Context và làm rõ nghiệp vụ
- Actor: Tổ trưởng/Phó HT; outcome: lưu cấu trúc mục tiêu bằng quan hệ cha–con tường minh.
- Nguồn yêu cầu: user 29/10/2026 yêu cầu ba bảng vật lý, xác nhận giữ cấp cha tùy chọn.
- Rule giữ nguyên: EDU-09/24/25/26/28 (CONFIRMED); quyền, phiên sửa, revision và lưu tay giữ nguyên.
- Flow: [B] soạn/lưu/gửi/ban hành → [S] lưu cây ba bảng; đọc ra cấu trúc rows hiện có.
- Hiện trạng: `entity/eduplan/EduGoalRow.java:18` dùng một bảng và kind, chưa có FK cha.
- Lưu theo vị trí: `service/eduplan/impl/EduYearPlanServiceImpl.java:846`; chép phiên bản tại `:1017`.
- Tham chiếu học liệu: `service/eduplan/impl/LearningResourceServiceImpl.java:199`; giáo án qua compareVersions tại `EduClassDocServiceImpl.java:541`.
- Test hiện có cho phép sub→goal và goal trực tiếp: `EduYearPlanServiceIntegrationTest.java:276`, `:293`.
- Các đường dẫn Java trên tương đối với `src/main/java/com/carenest/`; test với `src/test/java/com/carenest/service/eduplan/impl/`.
- Sơ đồ ngoài repo chỉ là thiết kế đề xuất; khi triển khai phải bổ sung đường cha tùy chọn theo quyết định mới.

### Quyết định đã chốt cho yêu cầu
1. Ba bảng riêng: `edu_plan_sub_domains`, `edu_plan_expected_results`, `edu_plan_goals` — user, 29/10/2026.
2. Giữ các cấp cha tùy chọn, bảo toàn dữ liệu/cách dùng hiện tại — user chọn phương án đề xuất, 29/10/2026.
3. Các quyết định kỹ thuật dưới đây đã được user duyệt toàn phần bổ sung, 29/10/2026.

### Câu hỏi mở
Không còn câu hỏi nghiệp vụ; user đã duyệt phương án triển khai ở mục Key decisions, 29/10/2026.

### Key decisions
| Quyết định | Đề xuất | Phương án khác | Lý do |
| --- | --- | --- | --- |
| Lưu trữ | Ba entity/repository và FK thật; bỏ lưu kind trong DB | Giữ một bảng + parent_id | User yêu cầu đúng ba bảng |
| Cấp cha tùy chọn | Nhóm có sub_domain_id nullable; mục tiêu có expected_result_id nullable và sub_domain_id nullable để giữ goal trực tiếp dưới sub | Tạo tiêu đề cha giả | Không tự thêm nội dung nghiệp vụ |
| Ownership | Cả ba bảng giữ year_plan_id, domain_id; FK ghép kiểm tra cha cùng kế hoạch/lĩnh vực, và sub của goal khớp sub của nhóm khi có nhóm | Chỉ FK id đơn | Chặn liên kết khác phiên bản/lĩnh vực ở DB và service |
| API | Giữ domains[].rows[] và kind như hiện tại, service chuyển đổi với ba bảng | DTO cây lồng nhau | Tránh buộc Web đổi đồng thời; snapshot phải chứng minh |
| ID/thứ tự | Giữ id, lineage_id, sort_order cũ; dùng chung sequence cho ba bảng để ID dòng không trùng khi đưa ra API | Sequence riêng + đổi định dạng ID API | Bảo toàn khóa dòng Web, JSON giáo án và học liệu |
| Migration | Flyway mới (V35 nếu chưa được dùng), backfill từng plan/domain theo thứ tự cũ; đổi FK nội dung sang goal_id | Sửa V11/V32/V33 | Không sửa migration đã commit; không làm mất tham chiếu |
| Dữ liệu bất hợp lệ | Kiểm tra trước; FK/nội dung trỏ dòng không phải goal hoặc thứ tự mơ hồ thì fail transaction, báo để xử lý | Tự bỏ dòng/sửa dữ liệu | Không làm mất dữ liệu âm thầm |
| Triển khai | Một đợt BE gồm migration và source tương thích; tạo ADR theo convention repo | Tách deploy migration và service | Không chạy service cũ với bảng đã thay thế |

### Acceptance criteria
- **DB-AC1** (EDU-09): Given dữ liệu đủ/thiếu tiêu đề cha, When nâng cấp DB và đọc/lưu, Then ba bảng và FK cha đúng, chữ/thứ tự/ID giữ nguyên; không tạo tiêu đề giả.
- **DB-AC2** (EDU-09): Given ID cha khác plan/domain hoặc sub không khớp nhóm, When ghi, Then service và DB từ chối, transaction không lưu dở.
- **DB-AC3** (EDU-09/24/28): Given nháp có thêm/xóa/chuyển dòng và mục tiêu Bộ, When lưu rồi mở lại, Then đúng cha, số MT, nội dung/dấu tích và liên kết Bộ.
- **DB-AC4** (EDU-25/26): Given bản đã ban hành và giáo án/học liệu, When tạo phiên bản và so sánh, Then remap ba cấp theo lineage, giữ tham chiếu mục tiêu, bản cũ bất biến.
- **DB-AC5** (EDU-09/26): Given client hiện tại, When chạy snapshot và luồng lớp/học liệu, Then request/response hiện có và kiểm quyền/thuộc chủ đề được giữ đúng.

### Phase DB-1 — Chuyển dữ liệu và module đồng bộ
- Files C: migration mới; ba entity/repository; ADR theo số kế tiếp; test nâng cấp DB cũ.
- Files M: EduGoalContent, EduYearPlanServiceImpl, EduYearPlanMapper, LearningResourceServiceImpl và các repository liên quan; test schema/service/giáo án/học liệu.
- Files M: DATA_DICTIONARY, ERD, DOMAIN_MODEL, module card; đồng bộ hai tài liệu sơ đồ ngoài repo theo phạm vi đã yêu cầu.
- Files D: EduGoalRow/entity converter/repository khi đã thay hết caller; chỉ bỏ enum nếu DTO không còn dùng.
- Steps: migration/backfill/constraints → CRUD và query ba bảng → copy/diff/ownership → tài liệu và regression.
- >8 file: đề xuất chia review thành hai nhóm schema/backfill và service/caller; cùng một bản phát hành để tránh schema/source lệch nhau.
- Consumer cần kiểm: Web `src/services/education-plan/api/yearPlanMapper.js:63,140` vẫn dùng rows/kind; không sửa FE/APP. Mobile chưa triển khai màn này theo CROSS_REPO_MAP.
- Tests: DB-AC1/2 → EduYearPlanSchemaIntegrationTest + test nâng cấp V34; DB-AC3 → EduYearPlanServiceIntegrationTest; DB-AC4 → version/copy/diff; DB-AC5 → EduClassDocServiceIntegrationTest, LearningResourceServiceImplTest và snapshot OpenAPI.
- Tên DisplayName dùng EDU-09/24/25/26/28 tương ứng; test mới kiểm trường hợp khó, không chỉ phản chiếu implementation.
- Exit: `node scripts/verify.mjs` → VERIFY PASS full, skip 0; PostgreSQL Testcontainers chạy thật; snapshot không đổi; `node scripts/check-ai-layer.mjs` exit 0.
- **Dừng chờ user review sau phase.**

### Rủi ro và rollback
- Backfill sai cha: fixture đủ/thiếu cấp, nhiều nhóm, nhiều lĩnh vực/phiên bản; đối chiếu số dòng, ID, lineage, nội dung và dấu tích trước/sau.
- Đổi kind hoặc kéo dòng có thể đụng ID: kiểm ownership theo cả bảng/loại, không nhận lại ID thuộc loại khác; kiểm round-trip Web contract.
- Xóa/đổi cha: xử lý theo snapshot request trong một transaction; không cascade xóa mục tiêu đang được chuyển cha trước khi cập nhật.
- Rollback: sao lưu trước khi nâng cấp môi trường dùng chung; không tự chạy migration trên DB đang dùng; không hạ source về bản cũ sau khi đổi schema. Khi cần khôi phục phải dừng ghi và phục hồi toàn bộ snapshot tương ứng.

### Validation log DB
- Đã đối chiếu source/test và consumer cụ thể; giữ nguyên API là mục tiêu cần kiểm chứng, chưa là kết quả.
- Đã đưa ra phương án và chia nhóm review cho >8 file; phạm vi một module, không sửa repo client, không thêm dependency.
- Mỗi AC có nhóm test và exit rõ; không dùng rule PENDING/OPEN như đã chốt.
- Tự review: xử lý thiếu cấp cha, giữ ID duy nhất qua ba bảng, lineage và ownership; chưa sửa product code.

### Progress log DB — 29/10/2026
- User chọn cấp cha tùy chọn và nhắc lại đúng ba bảng vật lý có quan hệ cha–con.
- Đã chuẩn bị phần bổ sung DRAFT; chưa code/migration, chưa commit/push/tạo branch.
- Verify sản phẩm: chưa chạy vì mới lập kế hoạch. `node scripts/check-ai-layer.mjs`: 0 lỗi, 0 cảnh báo; E6 bỏ qua theo output công cụ.
- Tiếp: user duyệt phần bổ sung DB rồi triển khai DB-1.

## Context
- **UI là chuẩn.** CareNest_FE nhánh `feature/G94-178-FE-FEAT-41-lesson-assessment-parent` có đủ các màn: `GoalListPage.jsx`, `GoalFormPage.jsx`, `GoalDetailPage.jsx`.
- **FE gọi API giả lập.** Dữ liệu đi qua mock (`educationPlanMockRepository.js`). `educationPlanApi.js:4-5` ghi "PROPOSED endpoints only", và luồng duyệt do FE tự đặt status (`educationPlanMockRepository.js:5-8`).
- **DB tham chiếu.** CAN_DB trang 1 "Kế hoạch giáo dục" (Drive `10ZANrV0YVDZEcFbS6UZaRcRXHgvzwqsN`) có các bảng `edu_year_plans`, `edu_goal_rows`, `edu_goal_contents`, `edu_themes`, `edu_breaks`, `edu_content_theme_ticks`, `edu_plan_events`, `edu_plan_approvals`, `edu_domains`, `edu_expected_results`, `edu_approval_steps`.
- **Nghiệp vụ.** Theo Drive "Business process…", trang "Tạo mục tiêu năm học của phó hiệu trưởng", cùng file `huong_dan_nghiep_vu/phan_viec_cac_actor_giao_an.md` §1.
- **Sequence đã duyệt.** `huong_dan_nghiep_vu/sequence/muc_tieu_nam_hoc_sequence.drawio` (SD-01..SD-10), Tiền chốt 22/10.
- **Hiện trạng `main`.** Chỉ có scaffold, chưa có migration. Nhánh `G94-60` đã có:
  - các bảng `user_accounts`, `campuses`, `school_years`, `classrooms`, `staff_assignments` (V1–V6);
  - `CurrentUser` và các AccessPolicy.
- **Tài liệu BE mâu thuẫn.** OBS-08 (`BUSINESS_RULES.md:79`), PROJECT_CONTEXT Exclusions (`:66`) và ADR-0006 đều ghi CareNest không soạn và duyệt giáo án. Quyết định 1 bên dưới thay thế các nội dung này.

## Quyết định đã chốt (Tiền, 22/10/2026)
1. CareNest **có** soạn và duyệt kế hoạch giáo dục, giáo án. Phải cập nhật context BE: OBS-08 thành SUPERSEDED, sửa Exclusions và ADR-0006.
2. Xây trên nền bảng và security của nhánh G94-60.
3. Tổ trưởng khối giữ role `TEACHER`. Thêm phân công `AGE_GROUP_TEAM_LEADER` gắn `age_group_id` trong `staff_assignments`.
4. Được sửa lớp gọi API của FE (`educationPlanApi.js`, `useEducationPlan.jsx`), **không** sửa giao diện.
5. BE cấp id BIGINT. Chỉ xóa được bản DRAFT.
6. Về các bảng: tạo bảng `age_groups`, bỏ 2 cột `updated_at` thừa, `description` cho phép rỗng, giữ `attachment_key`, `kind` ∈ {sub, group, goal}, lưu `goal_no` khi lưu. Seed 5 lĩnh vực chuẩn.
7. Luồng duyệt kế hoạch năm cố định TT → PHT. `edu_approval_steps` chỉ dùng cho chữ ký khi in.
8. Chưa làm thông báo. Bản lưu sau ghi đè bản trước (không có version).
11. Bỏ tự lưu: lưu bằng nút Lưu hoặc Ctrl+S, rời trang/đóng tab khi chưa lưu thì hỏi; áp dụng cho Mục tiêu năm học, Kế hoạch chủ đề, Giáo án tuần (Tiền 22/10).
9. Quyền theo mã `goals.view|edit|approve`, tạm gán theo role tại một chỗ, sẽ thay bằng phân quyền động (EDU-08, cách A).
10. Sửa FE trên nhánh G94-178 hiện tại. Thêm cột `classrooms.age_group_id`. `config` chỉ làm lĩnh vực và `signers.year`. PHT có quyền (bất kể campus) thì duyệt được.

## Làm rõ nghiệp vụ
- **Actor:** Tổ trưởng khối (TT), Phó HT (PHT), Hiệu trưởng (HT, chỉ xem), Giáo viên (GV, chỉ xem bản đã ban hành).
- **Outcome:** mỗi (năm học, nhóm tuổi) có một kế hoạch đã ban hành, toàn trường dùng chung.
- **Luồng [S]:** `DRAFT → PENDING_VP → APPROVED`, nhánh `PENDING_VP → REJECTED → PENDING_VP`, và `DRAFT → APPROVED` khi PHT tự lập.
- **Rule mới (PROPOSED, ghi vào BUSINESS_RULES ở Phase 0):**
  - **EDU-01.** Mỗi (school_year, age_group) có đúng 1 kế hoạch. Trùng thì trả 409.
  - **EDU-02.** Người tạo là PHT, hoặc TT của đúng nhóm tuổi đó. Người tạo là owner.
  - **EDU-03.** Chỉ owner sửa khi DRAFT/REJECTED. Mọi PHT sửa được khi PENDING_VP. APPROVED thì khóa.
  - **EDU-04.** Gửi duyệt: chỉ TT owner, khi DRAFT/REJECTED, và kế hoạch có ít nhất 1 MT.
  - **EDU-05.** Ban hành: PHT, khi PENDING_VP hoặc khi là nháp do chính PHT soạn. Trả lại: PHT, khi PENDING_VP, bắt buộc lý do.
  - **EDU-06.** Xem: PHT và HT xem toàn trường (HT không xem DRAFT). TT xem nhóm tuổi của mình. GV chỉ xem APPROVED thuộc nhóm tuổi các lớp mình dạy.
  - **EDU-07.** Xóa: chỉ owner, khi DRAFT.
- **Ảnh hưởng:**
  - Module mới `education-plan` (package `eduplan`).
  - Module `school-structure` thêm `age_groups`, `classrooms.age_group_id` và phân công TT.
  - FE: các route `/education/goals*`, `/education/plan-reviews`, `/education/school`.

### Câu hỏi mở
| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| Sửa FE trên nhánh nào | Nhánh G94-178 hiện tại | Tiền | 22/10 | plan |
| Thêm `classrooms.age_group_id` | Đồng ý | Tiền | 22/10 | plan |
| `config` chỉ phần lĩnh vực + `signers.year` | Đúng | Tiền | 22/10 | plan |
| PHT campus nào cũng duyệt | Đúng; PHT có quyền là duyệt (phân quyền động) | Tiền | 22/10 | EDU-08 |
| Kiểm quyền khi BE chưa có phân quyền động | Cách A: mã permission, gán theo role tạm | Tiền | 22/10 | EDU-08 |

## Acceptance criteria
- **AC-1** (EDU-01): Given đã có kế hoạch (2026-2027, ag-3). When POST cùng cặp. Then trả 409.
- **AC-2** (EDU-02): Given TT nhóm tuổi 3–4. When POST với ageGroupId 4–5. Then trả 403.
- **AC-3** (EDU-03): Given kế hoạch APPROVED. When PUT. Then trả 403. Given PENDING_VP, When PHT gửi PUT, Then trả 200 và ghi event `VP_EDIT`.
- **AC-4** (EDU-04): Given DRAFT không có MT. When submit. Then trả 409. Given hợp lệ, Then status là PENDING_VP, `sent_by` và `sent_at` được ghi.
- **AC-5** (EDU-05): Given PENDING_VP. When PHT issue. Then status APPROVED và có 1 dòng `edu_plan_approvals`. When return với note rỗng, Then trả 400.
- **AC-6** (EDU-06): Given GV và kế hoạch DRAFT. When GET detail. Then trả 403. Danh sách của GV chỉ có APPROVED.
- **AC-7** (EDU-07): Given REJECTED. When DELETE. Then trả 409.
- **AC-8**: PUT ghi đúng cây lĩnh vực > dòng > nội dung > tick chủ đề. `goal_no` được đánh lại 1..n theo thứ tự hiển thị.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Module | Tạo module mới `education-plan` và ADR-0017 (nay ADR-0020) | Nhét vào learning-observation | Card learning-observation cấm mở rộng thành giáo án |
| API | Mỗi hành động một endpoint (`/submit`, `/issue`, `/return`) | Giữ PUT upsert có status | BE phải tự quyết trạng thái (guide §7) |
| Lưu cây | PUT gửi cả cây. Dòng có id thì update, không có thì insert, dòng bị bỏ thì delete | Tách nhiều endpoint con | Khớp với cách FE lưu cả văn bản (nút Lưu / Ctrl+S) |

## Phases
Plan có hơn 8 file và 4 phase, nên đề xuất tách: Phase 3 (FE) làm thành plan hoặc task FE riêng.

### Phase 0 — Nền và tài liệu
- **Files:**
  - M: `BUSINESS_RULES.md` (thêm EDU, OBS-08 thành SUPERSEDED), `PROJECT_CONTEXT.md`, `ADR-0006`, `.ai/CONTEXT_MAP.yaml`
  - C: `docs/modules/education-plan.md`, `docs/decisions/ADR-0020-education-plan-module.md` (số cũ ADR-0017)
- **Steps:**
  1. Merge `origin/feature/G94-60-BE-FEAT-01-authentication` vào nhánh này. Việc này tạo commit merge, cần Tiền cho phép.
  2. Sửa tài liệu theo danh sách Files.
- **Exit:** `node scripts/check-ai-layer.mjs` pass. **Dừng chờ review.**

### Phase 1 — Schema, entity, repository
- **Files:**
  - C: `V10__create_age_groups.sql` (`age_groups`, `classrooms.age_group_id`, nới `staff_role` thành VARCHAR(30) và cho phép `AGE_GROUP_TEAM_LEADER` + `age_group_id`)
  - C: `V11__create_edu_year_plans.sql` (11 bảng `edu_*` + seed 5 lĩnh vực)
  - C: `entity/eduplan/*`, `repository/eduplan/*`, `entity/organization/AgeGroup` (+ SchoolYear)
  - M: `StaffRole`, `StaffAssignment`
- **Tests:** migration chạy qua Testcontainers + `ddl-auto: validate`, cùng test cho repository.
- **Exit:** `VERIFY PASS full`, skip 0. **Dừng chờ review.**

### Phase 2 — Service, controller, quyền
- **Files:**
  - C: `controller/eduplan/EduYearPlanController`, `EduConfigController`
  - C: `service/eduplan/*` (+ impl), `dto/eduplan/*`, `mapper/eduplan/*`, `security/authorization/EduYearPlanAccessPolicy`
  - M: `StaffAssignmentService` (thêm hàm tìm nhóm tuổi của TT)
- **API:** `GET/POST /education-plans/goals`, `GET/PUT/DELETE /education-plans/goals/{id}`, `POST …/{id}/submit|issue|return`, `GET /education-plans/config`, `GET /education-plans/expected-results`.
- **Tests:** AC-1..AC-8 qua `EduYearPlanServiceImplTest` và `EduYearPlanControllerTest`, mỗi test có `@DisplayName("EDU-0n: …")`. Có test cho trường hợp bị từ chối.
- **Exit:** `VERIFY PASS full`. Cập nhật OpenAPI snapshot, `DATA_DICTIONARY.md`, `CURRENT_STATE.md`, `CROSS_REPO_MAP`. **Dừng chờ review.**

### Phase 3 — FE lớp gọi API (CareNest_FE)
- **Files:** M `educationPlanApi.js`, `useEducationPlan.jsx` (đổi id thành số, map DTO sang shape UI hiện có, gọi submit/issue/return).
- Không đụng page, component hay style.
- **Exit:** `npm run build` và e2e của education-plan pass. **Dừng chờ review.**

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1..7 | EDU-01..07 | ServiceImplTest (unit) + ControllerTest (web) | 2 | chưa chạy |
| AC-8 | EDU-03 | Repository/migration IT (Testcontainers) | 1–2 | chưa chạy |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| G94-60 chưa vào `dev`, số migration V7 trùng với nhánh khác | Lỗi Flyway khi gộp | Báo team trước khi gộp, đánh lại số nếu cần | Đổi số migration trước khi merge |
| Sửa CHECK của `staff_assignments` | Ảnh hưởng auth G94-60 | Chỉ thêm giá trị, không đổi giá trị cũ | Migration mới khôi phục CHECK |

## Validation log
- [x] Khẳng định về code có `file:line`
- [x] Đổi contract ⇒ consumer cụ thể (FE `educationPlanApi.js`, `useEducationPlan.jsx`)
- [x] Mỗi AC có test, mỗi phase có Exit
- [x] Hơn 8 file ⇒ đã đề xuất tách Phase 3
- [x] Không dùng rule PENDING/OPEN như đã chốt (EDU-* là PROPOSED tới Phase 0)

## Progress log
### 2026-10-22 — Plan draft (chờ review)
- Commit: chưa commit
- Tiền duyệt plan và cho merge G94-60 (22/10).

### 2026-10-22 — Phase 0 (chờ review)
- Commit: `3246124` merge G94-60 (kèm sửa 1 báo nhầm secret + 5 link chết có sẵn để qua hook); phần tài liệu chưa commit
- Verify: `node scripts/check-ai-layer.mjs` → `0 lỗi, 0 cảnh báo`; `node scripts/verify.mjs` → `VERIFY PASS full | tests 204 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok`
- Khác plan: không
- Tiếp: Phase 1 sau khi Tiền review.

### 2026-10-22 — Phase 1 (chờ review)
- Commit: chưa commit
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 211 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok`; `EduYearPlanSchemaIntegrationTest` 7/7 trên PostgreSQL thật
- Khác plan: snapshot OpenAPI đổi 1 dòng (`StaffAssignmentSummaryResponse.staffRole` thêm `AGE_GROUP_TEAM_LEADER`, consumer FE `GET /accounts/me`); thêm `sort_order` cho `edu_breaks`; cột audit theo chuẩn repo (`created_at`/`updated_at`)
- Tiếp: Phase 2 sau khi Tiền review.

### 2026-10-22 — Phase 2 (chờ review)
- Commit: chưa commit
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 230 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok`; `EduYearPlanServiceIntegrationTest` 9/9 (AC-1..AC-8, PostgreSQL thật), `EduYearPlanControllerTest` 9/9
- Khác plan:
  - `V12` bỏ unique viết tắt + CHECK ngày của chủ đề/đợt nghỉ: bản nháp lưu được khi còn soạn dở; kiểm ở service khi gửi/ban hành.
  - `GET /education-plans/config` trả thêm `schoolYears`, `ageGroups` để FE đổi hằng số sang dữ liệu BE.
  - `StaffAssignmentSummaryResponse` thêm `ageGroupId`; `/accounts/me` tính phân công Tổ trưởng theo role TEACHER.
  - Không ghi lịch sử khi lập (UI không ghi); "Phó hiệu trưởng chỉnh sửa" ghi 1 lần cho tới thao tác kế tiếp.
  - Test service dạng tích hợp (service + policy + DB thật) thay cho Mockito.
- Tiếp: Phase 3 (FE lớp gọi API) sau khi Tiền review.

### 2026-10-22 — Phase 3 (chờ review)
- Commit: chưa commit (BE Phase 2 + FE nhánh G94-178)
- FE: `api/educationPlanApi.js` + `api/yearPlanMapper.js` nối các API mục tiêu năm; `axiosClient` bóc envelope, lỗi lấy `desc`; trang chi tiết dùng `permissions` của BE; trang lập kế hoạch dùng tiếp id BE cấp. Bỏ tự lưu ở 5 trang kế hoạch giáo dục (Lưu / Ctrl+S, hỏi khi rời trang).
- Verify: FE `node scripts/verify.mjs` → `VERIFY PASS full`, `tests/education-plan/year-plan-api.test.mjs` 4/4; BE xem dòng verify cuối báo cáo.
- Chưa kiểm chứng: FE chạy với BE thật (FE chưa có đăng nhập Keycloak, `/auth/login` vẫn PROPOSED); `PUT /education-plans/config` chưa có ở BE.

### 2026-10-22 — Thu gọn schema (V23, trước là V10 rồi V20; Tiền chọn A)
- Bỏ `edu_plan_approvals`, `edu_approval_steps`, cột `edu_year_plans.attachment_key`; `GET /education-plans/config` bỏ `yearSigners` (FE dùng người ký mặc định).
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 230 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok`; FE `VERIFY PASS full`
- Commit: chưa commit


### 2026-10-23 — Bảng chủ đề: xác nhận xóa + đổi thứ tự (Tiền yêu cầu)
- FE `GoalTables.jsx` `ThemeEventTable`: thùng rác chủ đề/đợt nghỉ mở `ConfirmationModal` (báo số MT đã tích); cột "Sắp xếp" có tay nắm kéo-thả (`useSortableList`, cả bàn phím) và 2 nút lên/xuống. Không đổi API: thứ tự = thứ tự mảng PUT.
- BE: sửa `saveContent` lưu dấu tích theo chênh lệch ([BUG-261023-edu-theme-tick-lost-on-resave](../../knowledge/incidents/BUG-261023-edu-theme-tick-lost-on-resave.md)); test `reordersThemesAndBreaksKeepingIdsAndTicks`.
- Verify: BE `VERIFY PASS full | tests 231 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok`; FE `VERIFY PASS full` (`tests/education-plan/theme-order.test.mjs` 4/4); E2E Playwright (FE thật, API giả lập theo DTO BE) PASS 12 bước.
- Chưa kiểm chứng: E2E với Keycloak/BE thật (mật khẩu user dev ở `.env`, hook chặn đọc).
- Commit: chưa commit

### 2026-10-23 — Kéo-thả chủ đề rõ hơn (Tiền yêu cầu)
- FE `useSortableList`: hiệu ứng trượt (FLIP) khi đổi chỗ bằng kéo/bàn phím/nút lên-xuống (`sort.move`), dòng vừa chuyển nháy sáng, ảnh kéo là thẻ "↕ STT. tên" thay ảnh cả dòng; tôn trọng `prefers-reduced-motion`.
- Icon tay nắm `DragHandle` (Tabler `IconArrowsMoveVertical`) thay ⋮⋮ ở bảng chủ đề/đợt nghỉ và trang cấu hình (người ký, tuần); tay nắm có viền + nền xanh nhạt; vạch thả 3px; dòng đang kéo viền nét đứt.
- Giả định (chế độ tự chạy): áp kiểu mới cho mọi danh sách dùng `useSortableList` để thống nhất.
- Sau góp ý: kéo qua dòng nào thì cả dòng tô xanh (`sortable-item--over`); thả là chiếm vị trí dòng đó (bỏ chia nửa trên/dưới), vạch cạnh trên/dưới báo hướng.
- Verify: FE `VERIFY PASS full`. Chưa kiểm chứng bằng mắt trên trình duyệt.
- Commit: chưa commit

### 2026-10-23 — Kéo-thả chuẩn dnd-kit (Tiền yêu cầu "chuẩn như các web lớn")
- FE thêm dependency `@dnd-kit/core 6.3.1`, `@dnd-kit/sortable 10.0.0`, `@dnd-kit/utilities 3.2.2`, `@dnd-kit/modifiers 9.0.0` (ghim bản, cài `--legacy-peer-deps` vì xung đột peer vite 8 ↔ `@vitejs/plugin-react` 4 có sẵn, không đổi gói cũ). Lý do: thư viện sortable phổ biến nhất của React (kiểu Notion/Linear: dòng dạt ra nhường chỗ).
- `src/components/ui/Sortable.jsx` thay hook HTML5 cũ (`useSortableList.js` chỉ còn `moveItem`): chỗ sẽ thả tô xanh cả dòng, STT đổi ngay khi đang kéo (`SortIndex`), nút ↑↓ trượt mượt (`animateLayoutChanges`), bàn phím + thông báo đọc màn hình tiếng Việt. Áp cho bảng chủ đề, đợt nghỉ, trang cấu hình (người ký, dòng tuần). `DESIGN.md` cập nhật; baseline check-design +1 inline style (transform động của dnd-kit).
- Kiểm chứng trình duyệt (FE mock, port 5174): kéo bằng PointerEvent từng bước → dòng dạt, STT 1–4 cập nhật lúc kéo, thả đúng thứ tự; bàn phím Space/↑/Space đúng; nút ↑↓ đúng; console không lỗi.
- Attempts: `left_click_drag` của Claude in Chrome không kích hoạt dnd-kit (gửi quá ít pointermove) ⇒ test bằng chuỗi PointerEvent; chưa thử bằng chuột người dùng thật.
- Verify: FE `VERIFY PASS full`. Commit: chưa commit
- Sau góp ý (Tiền): STT không tự đánh lại khi kéo/xóa; dòng mới lấy số lớn nhất + 1; nút "Đánh số lại" (icon danh sách số) ở tiêu đề cột STT bảng chủ đề, đợt nghỉ, dòng tuần. Giả định: STT chỉ là hiển thị phía FE (BE lưu thứ tự mảng), mở lại kế hoạch hiện 1..n; danh sách người ký giữ số theo vị trí (là thứ tự ký). Kiểm chứng trình duyệt: kéo ⇒ STT 2,3,1,4 + nút bật; bấm ⇒ 1..4, nút tắt. FE `VERIFY PASS full`.
- Sau góp ý (Tiền): nút "Đánh số lại" xếp dưới chữ STT (hết tràn cột). Dòng vừa đổi chỗ bằng ↑↓/bàn phím/thả: tô xanh cả dòng + khung xanh ~1,1 giây bằng class tĩnh (JS gỡ). Attempts: bản dùng `@keyframes` không hiện vì `base.css` ép `animation-duration: 0.01ms !important` khi máy bật `prefers-reduced-motion` (Windows tắt hiệu ứng) ⇒ đổi sang class tĩnh. Kiểm chứng: computed style dòng vừa chuyển nền `rgb(197,229,252)` + outline `rgb(11,114,188)`. FE `VERIFY PASS full`.
- Sau góp ý (Tiền): ô tên chủ đề/đợt nghỉ — Enter vẫn thêm dòng dưới; Backspace khi đã hết chữ không xóa dòng nữa (ô trống, giống ô mục tiêu), xóa dòng chỉ bằng thùng rác. Kiểm chứng trình duyệt: Enter 1→2 dòng, focus dòng mới; xóa hết + Backspace vẫn 2 dòng. FE `VERIFY PASS full`.
- Sau góp ý (Tiền): tab "Mục tiêu, nội dung giáo dục" — 4 nút xóa (mục tiêu, nội dung, lĩnh vực con, nhóm kết quả mong đợi) mở `ConfirmationModal` nêu tên + những gì bị xóa kèm (nội dung, dấu tích chủ đề, mục tiêu trong nhóm); nút đổi sang `icon-btn--danger`. Backspace ở ô trống của tầng nhóm/lĩnh vực con vẫn xóa ngay như cũ (chưa được yêu cầu đổi). Kiểm chứng trình duyệt: modal hiện đúng câu, Quay lại giữ dữ liệu, Xóa thì xóa. FE `VERIFY PASS full`.
- Sau góp ý (Tiền): trang lập/sửa kế hoạch năm — chuyển tab Mục tiêu ↔ Chủ đề khi còn thay đổi chưa lưu ⇒ hiện ngay `LeaveConfirmModal` (thêm prop `message`/`discardLabel`/`saveLabel`, mặc định giữ như cũ): "Ở lại" / "Chuyển, lưu sau" (giữ dữ liệu trên trang) / "Lưu và chuyển". Kiểm chứng trình duyệt: hỏi khi có thay đổi, Lưu và chuyển ⇒ "Đã lưu" + sang tab, Chuyển lưu sau giữ chủ đề vừa nhập, Ở lại giữ tab. Ghi chú: kiểm thử đã lưu 1 kế hoạch nháp nhóm Mẫu giáo lớn trong mock localStorage. FE `VERIFY PASS full`.
- Sau góp ý (Tiền): `LeaveConfirmModal` (chuyển tab và rời trang, mọi trang kế hoạch giáo dục) đổi nút thành Hủy · Không lưu · Lưu; bỏ prop nhãn tùy biến. Kiểm chứng trình duyệt: modal có đúng 3 nút, Hủy đóng modal và giữ tab. FE `VERIFY PASS full`.
- Sau góp ý (Tiền): chuyển tab chọn "Không lưu" ⇒ bỏ thay đổi của tab đang rời, trở về bản đã lưu gần nhất (`useManualSave.cleanDoc()`; tab Chủ đề: themes + breaks, bỏ dấu tích trỏ tới chủ đề không còn; tab Mục tiêu: domains); thông tin chung không đổi. Chỉ hỏi khi chính tab đang rời có thay đổi. Kiểm chứng trình duyệt: sửa tên + thêm chủ đề → Không lưu ⇒ tên về cũ, 10→9 chủ đề, hết "Có thay đổi chưa lưu". FE `VERIFY PASS full`.
- Sau góp ý (Tiền): ô kết quả mong đợi và ô lĩnh vực con — Backspace khi đã hết chữ không xóa dòng nữa (ô trống, như ô mục tiêu/chủ đề), xóa dòng bằng thùng rác (có xác nhận). Kiểm chứng trình duyệt (không lưu): xóa hết + Backspace ×3 vẫn còn dòng ở cả 2 loại ô. FE `VERIFY PASS full`.

### 2026-10-23 — Merge nhánh điểm danh G94-236 (Tiền yêu cầu)
- Merge `origin/feature/G94-236-BE-FEAT-29-daily-attendance` (kèm cấu hình trường G94-235, migration V13–V22).
- Trùng version: `V10__drop_unused_edu_plan_tables` của nhánh này đổi thành `V20__…` (nội dung không đổi, độc lập với V10–V19). DB local đã chạy V10 cũ ⇒ cần dựng lại DB dev (Flyway báo lệch checksum/version). (Merge `dev` 29/10: dời tiếp +3, nay `V23__drop_unused_edu_plan_tables`.)
- `docs/api/openapi.yaml` xung đột ⇒ sinh lại từ code sau merge: giống nhánh kia, chỉ bỏ `EduConfigResponse.yearSigners` (đã bỏ ở `3438e72`).
- Sau merge FE G94-179: màn Mục tiêu năm học sập ("Hệ thống gặp sự cố", `id.replace is not a function` ở `toEduYear`). Nguyên nhân: header (`SchoolYearContext`) nối BE thật giữ id năm dạng số, module kế hoạch giáo dục cần tên năm `2026-2027`. Sửa FE `useEducationPlan.jsx`: đổi id → tên năm qua `label` của lựa chọn năm (chưa commit).

### 2026-10-23 — Khóa "Đang sửa" (EDU-10, Tiền chốt sau khảo sát)
- Tiền chốt: 1A (tự nhả sau 30 phút không lưu), 2A (chỉ Tổ trưởng gửi; PHT tự soạn vẫn ban hành thẳng), 3A (chỉ PHT xóa Nháp), 4 (giữ Bị trả lại, cả hai sửa), 5A (người kia xem được, thấy "Đang được [tên] sửa"). Khảo sát: `huong_dan_nghiep_vu/1_muc_tieu_nam_hoc/khao_sat_quy_trinh_lap_ke_hoach_nam.md`.
- Sửa EDU-02/03/04/06/07, thêm EDU-10. V24 thêm `EDITING` + `editing_by/since/at/from`. API mới `POST /{planId}/start-editing|finish-editing`; `editing` (người, lúc bắt đầu) trong chi tiết và danh sách.
- Giả định Claude tự chọn: lưu (PUT) tự lấy khóa nếu đang trống; chờ duyệt không khóa (chỉ PHT sửa); HT không xem bản Đang sửa (kể cả từ Bị trả lại); thao tác ghi khóa dòng (`SELECT … FOR UPDATE`); job nhả khóa chạy mỗi phút, khóa quá hạn coi như trống ngay.

### 2026-10-23/24 — Danh sách, quy tắc lưu/xóa, tài liệu (Tiền yêu cầu)
- FE: tổ trưởng và giáo viên dùng chung màn danh sách mục tiêu năm (không còn chuyển thẳng vào chi tiết); thẻ tổng quan, tìm kiếm, lọc nhóm tuổi chỉ cho PHT. Bỏ mô tả cứng dưới tiêu đề các trang kế hoạch giáo dục; thông báo khóa chỉ còn "Đang được [tên] sửa từ [giờ]".
- FE: áp quy tắc lưu tay, hỏi khi rời trang, xác nhận khi xóa cho trang Cấu hình và Chủ đề nhánh (EDU-22). Trang "Kế hoạch toàn trường" của HT đọc kế hoạch năm, chủ đề nhánh, hồ sơ chủ đề kiểu mới (trước đọc collection cũ `themes`/`lessons`).
- Docs: viết lại phần kế hoạch giáo dục theo context của Tiền: EDU-01..22, OBS-07 CONFIRMED, xóa OBS-08 và P-01, ADR-0006 ACCEPTED, flow `docs/business/flows/education-plan.md`; FE `AGENTS.md`, `.ai/ROUTER.md`, `.ai/CONTEXT_MAP.yaml`, `ROUTE_MAP.md` bỏ "không giáo án".
- Commit: BE `f5acbe3`; FE `18adb8c`, `8045031`, `190bd04`, `cea6088` (Tiền), `4b5355a` (gỡ đăng nhập demo tạm). Docs và trang Kế hoạch toàn trường: chưa commit.
### 2026-10-24 — Tài liệu của lớp lên BE (Tiền yêu cầu: chủ đề nhánh, kế hoạch tuần, ngày)
- BE: `V28__create_edu_class_docs.sql`, `EduClassDoc`, `EduClassDocService(Impl)`, `EduClassDocController` (`/education-plans/class-docs`): lưu, gửi, duyệt, trả lại theo EDU-12..18, chuỗi người ký lấy từ cấu hình. Nội dung soạn lưu JSON (bảng tuần đổi theo cấu hình dòng). Lịch tuần `teachingWeeks` tính giống Web.
- Quyết định: khóa tài liệu do Web đặt (`bp_/tp_/wp_`), BE kiểm khớp; GET trả kèm lớp, giáo viên, tổ trưởng để Web không gọi `/classes`, `/users` (BE chưa có). AI (EDU-20) chưa làm.
- FE: nối API thật, lớp/giáo viên lấy từ class-docs theo năm ở header; nhãn REJECTED đổi "Bị trả lại"; nạp lại sau gửi/duyệt hồ sơ chủ đề.
- Kiểm chứng: test tích hợp 5/5; Playwright trên BE thật: chủ đề nhánh (lưu, gửi, trả lại, gửi lại, duyệt), kế hoạch chủ đề, giáo án tuần + ngày, hồ sơ chủ đề gửi và duyệt; quyền giáo viên lớp khác, tổ trưởng, HT. BE verify PASS 373.
- Commit: chưa commit.

## Bổ sung 27/10/2026 — Tự lưu khi soạn (APPROVED, đang triển khai)

Phần này là đề nghị sửa đổi plan đang có, không kế thừa quyền triển khai từ các phase cũ. Trạng thái `in-progress` của plan gốc không có nghĩa phần bổ sung đã được duyệt. Đặc tả: [001-year-plan-autosave](../../../specs/001-year-plan-autosave/spec.md).

### Context và làm rõ nghiệp vụ

- Actor: Tổ trưởng nhóm tuổi, Phó hiệu trưởng; outcome: tự lưu và phục hồi công việc, chỉ một phiên sửa; không đổi quyền đọc theo EDU-03.
- Hiện trạng: `src/main/java/com/carenest/dto/eduplan/YearPlanRequest.java:20` bắt buộc tiêu đề; `src/main/java/com/carenest/service/eduplan/impl/EduYearPlanServiceImpl.java:220` lưu và gia hạn khóa; `src/main/java/com/carenest/entity/eduplan/EduYearPlan.java:149` khóa hết hạn theo lần hoạt động đã ghi.
- FE: `CareNest_FE/src/hooks/education-plan/useManualSave.js:10` chỉ lưu tay; `CareNest_FE/src/pages/education-plan/GoalFormPage.jsx:235` lấy/nhả khóa; chưa có tự lưu/khôi phục tại hai file này.
- Yêu cầu mới khác EDU-10/22: lưu nội dung đang soạn, giữ quyền theo phiên hoạt động thay vì lần lưu, khóa cả bản Chờ duyệt. Không dùng các mô tả cũ trái yêu cầu để triển khai tính năng mới.
- Phạm vi: education-plan, schema/contract của kế hoạch năm và Web; không sửa nghiệp vụ đánh giá trẻ/tài liệu lớp, không thay mục tiêu đã ban hành. Mobile không triển khai tính năng này trong đợt này.

### Quyết định user đã chốt và phần đề xuất

| Nội dung | Quyết định | Nguồn |
| --- | --- | --- |
| Cách lưu | Tự lưu trong lúc soạn, bảo vệ khi mất mạng/mất điện | User, 27/10/2026 |
| Nội dung thiếu/chưa hợp lệ | Giữ nội dung soạn dở; kiểm tra đầy đủ trước gửi duyệt/ban hành | Câu trả lời user, 27/10/2026 |
| Quyền sửa | Chỉ một phiên được sửa, kể cả Phó hiệu trưởng sửa bản Chờ duyệt | Câu trả lời user, 27/10/2026 |
| Nhịp lưu/khôi phục | Ngừng gõ 2 giây; nhập liên tục tối đa 15 giây trước khi bắt đầu lưu; giữ trên máy trong 1 giây | Đề xuất agent, chờ duyệt plan |
| Quyền khi mất liên lạc | Gia hạn bằng liên lạc định kỳ 20 giây; hết quyền sau 90 giây không liên lạc được | Đề xuất agent, chờ duyệt plan |
| Triển khai Web | Cần cho phép sửa CareNest_FE rõ trước khi thực hiện phase AS-2 | AGENTS.md, phạm vi |

Không còn câu hỏi nghiệp vụ chưa trả lời trong đặc tả. Phê duyệt kế hoạch, mốc thời gian và phạm vi sửa Web vẫn đang chờ; không tự đánh dấu approved.

### Acceptance criteria bổ sung

- **AS-AC1** (spec FR-001..004, FR-011..012): Given nội dung đang nhập dở, When đến nhịp lưu, Then giữ đúng giá trị và hiển thị đúng trạng thái; thiếu nội dung chỉ chặn bước gửi/ban hành, không chặn tự lưu.
- **AS-AC2** (EDU-03, spec FR-005..006, FR-013): Given hai phiên của cùng/khác tài khoản, When cùng sửa, Then chỉ một phiên được ghi/chuyển trạng thái; nhả khóa cũ không làm mất quyền phiên mới; Chờ duyệt không bị đổi về Nháp.
- **AS-AC3** (spec FR-004, FR-009, FR-014): Given lưu chậm, mất phản hồi hoặc kết nối lại, When thử lại, Then không trùng dữ liệu, không ghi đè bản mới, không đổi bản đã ban hành.
- **AS-AC4** (spec FR-007..008): Given bản nháp đã được giữ trên máy, When mở lại cùng tài khoản/thiết bị, Then phục hồi đúng; tài khoản khác không truy cập; lỗi giữ trên máy phải báo rõ.
- **AS-AC5** (spec FR-010, FR-015): Given còn nội dung chưa lưu/hợp lệ, When gửi/ban hành, Then chưa chuyển trạng thái; khi đủ điều kiện chỉ tạo sự kiện nghiệp vụ tương ứng, không tạo lịch sử duyệt sau mỗi lần tự lưu.

### Key decisions đề xuất để duyệt

| Quyết định | Đề xuất | Phương án khác | Lý do |
| --- | --- | --- | --- |
| Chống hai người ghi | Quyền theo phiên + kiểm tra phiên và phiên bản nội dung tại mọi thao tác ghi | Chỉ dùng quyền theo tài khoản hiện tại | Phân biệt hai tab, chặn yêu cầu cũ sau nhận lại quyền |
| Theo dõi người còn soạn | Liên lạc định kỳ, độc lập lưu nội dung | Chỉ gia hạn khi có lưu | Không mất quyền khi đang đọc nội dung |
| Phục hồi ngoại tuyến | Bản nháp theo tài khoản/kế hoạch trên thiết bị; đối chiếu trước đồng bộ | Chỉ lưu trên hệ thống | Mất mạng vẫn bảo vệ nội dung; không tự trộn xung đột |
| Hạ tầng | Dùng cơ chế giao dịch/lưu trữ hiện có, không thêm dependency/dịch vụ | Thêm hệ thống cộng tác trực tiếp | Giữ phạm vi một người sửa và giới hạn V1 |

### Contract và dữ liệu dự kiến — cần duyệt trước khi sửa

- Đề xuất giữ prefix và các đường dẫn goals hiện tại; bổ sung nhận diện phiên sửa và phiên bản nội dung khi lấy khóa/lưu/kết thúc sửa và các thao tác gửi, ban hành, trả lại, xóa. Thêm thao tác duy trì phiên sửa độc lập với lưu nội dung. Không dùng thông tin từ client thay cho kiểm tra vai trò/nhóm tuổi ở server.
- Chi tiết/list trả thông tin phiên bản, người đang sửa và hạn quyền; giá trị dùng để chứng minh quyền chỉ trả cho phiên được cấp, không hiển thị cho người chỉ xem hoặc ghi log. Dùng mã lỗi hiện có cho hết quyền/xung đột và thông báo rõ; không tự thêm ApiCode.
- Tạo/lưu hỗ trợ nội dung chưa hoàn chỉnh; bảo toàn kiểm tra liên kết, quyền và giới hạn kích thước. Gửi/ban hành áp đầy đủ validation đã có. Cần đối chiếu cả validation ngày ở service và ràng buộc database, không chỉ bỏ NotBlank.
- Migration mới bổ sung nhận diện phiên, phiên bản và dấu vết chống tạo/lưu trùng; điều chỉnh ràng buộc nháp cần thiết. Không sửa migration đã commit. Khóa cũ được chuyển an toàn khi triển khai; nội dung/ID/liên kết/bản ban hành giữ nguyên.
- Consumer cụ thể: FE `educationPlanApi.js` (`saveGoal`, `startEditingGoal`, `finishEditingGoal`), `yearPlanMapper.js`, `useEducationPlan.jsx`, `GoalFormPage.jsx`. Đổi đồng bộ contract trước khi triển khai cho người dùng; không đưa BE mới lên riêng khi client cũ có thể ghi sai.

### Phases bổ sung

Thay đổi vượt 8 file: tách hai đợt review BE và Web; mỗi đợt chỉ bắt đầu sau khi phạm vi tương ứng được duyệt. Không mở rộng sang tài liệu lớp.

#### Phase AS-1 — Backend và hợp đồng

- Cập nhật rule/flow/card và contract theo yêu cầu đã chốt, giữ nguồn quyết định; tạo migration mới. Sửa entity, repository, DTO, controller, service, cấu hình/cleanup khóa cùng test tương ứng của education-plan.
- Định nghĩa chi tiết dữ liệu phiên/phiên bản, chống thử lại tạo trùng và lỗi xung đột trước khi code; kiểm tra mọi thao tác ghi đều khóa dòng và kiểm tra quyền phiên. Tách trạng thái nghiệp vụ khỏi quyền sửa khi Chờ duyệt.
- Tests: `EduYearPlanServiceIntegrationTest`, `EduYearPlanControllerTest`, `EduYearPlanSchemaIntegrationTest` cho AS-AC1/2/3/5; thử đồng thời hai phiên bằng dữ liệu giả; kiểm migration cũ sang mới.
- Exit: `node scripts/check-ai-layer.mjs`; `node scripts/verify.mjs` đạt full, skip 0; cập nhật snapshot OpenAPI có chủ đích, báo diff và consumer. Thiếu Docker thì chưa đạt exit tích hợp.
- **Dừng chờ user review; chưa triển khai lên môi trường dùng chung.**

#### Phase AS-2 — Web và kiểm thử đầu cuối (chỉ khi được phép sửa CareNest_FE)

- Giữ useManualSave cho màn khác; thêm cơ chế tự lưu riêng cho GoalFormPage, tuần tự hóa lưu, giữ thay đổi đang gõ và ánh xạ ID sau lần lưu đầu. Lưu tạm theo tài khoản/kế hoạch, hiển thị trạng thái, duy trì/nhả quyền theo phiên và đối chiếu khi kết nối lại.
- Cập nhật consumer hợp đồng đã liệt kê, xử lý điều hướng/đóng trang và đợi lưu hoàn tất trước gửi/ban hành. Cập nhật hướng dẫn FE liên quan sau khi có phép; không tự sửa repo APP.
- Tests: AS-AC1..5 trên backend thật; hai tài khoản/hai tab, nhập liên tục, phản hồi trễ, mất phản hồi, offline/reload, mất quyền, dữ liệu thiếu và bản chờ duyệt. Dùng clock giả để kiểm mốc thời gian; không đợi thật 30 phút.
- Exit: lệnh verify chuẩn FE đạt; BE full skip 0; báo riêng kết quả E2E và giới hạn chưa kiểm chứng. Không dùng test mô phỏng thay cho kiểm quyền server.
- **Dừng chờ user review.**

### Test matrix bổ sung

| AC | Tầng/đợt | Kết quả hiện tại |
| --- | --- | --- |
| AS-AC1 | Service/controller AS-1; tự lưu UI AS-2 | Chưa triển khai/chạy |
| AS-AC2 | Giao dịch đồng thời AS-1; hai tab/người AS-2 | Chưa triển khai/chạy |
| AS-AC3 | Tích hợp chống trùng/phiên bản AS-1; nối mạng lại AS-2 | Chưa triển khai/chạy |
| AS-AC4 | Kiểm phục hồi/cách ly tài khoản AS-2 | Chưa triển khai/chạy |
| AS-AC5 | Validation/chuyển trạng thái AS-1; thao tác gửi/ban hành AS-2 | Chưa triển khai/chạy |

### Rủi ro và rollback bổ sung

- Lưu thường xuyên làm tăng tải và có thể ghi đè nếu phản hồi sai thứ tự: chỉ lưu khi đổi, tuần tự hóa, kiểm tra phiên bản ở server; không ghi log toàn nội dung.
- Bản nháp cũ còn trên máy sau mất quyền: không tự đồng bộ/hiển thị sang tài khoản khác; báo rõ phần chỉ giữ trên máy.
- Rollback: dừng mở trang sửa, bảo toàn dữ liệu đã lưu và bản khôi phục, quay lại BE/FE tương thích sau kiểm tra schema; không xóa migration hoặc khôi phục database làm mất nội dung. Bản đã ban hành không sửa.

### Validation log bổ sung

- Đã đối chiếu source về lưu tay, khóa theo tài khoản, validation tiêu đề và ràng buộc quyền Chờ duyệt; tham chiếu file/line ở Context.
- Đã chỉ rõ consumer và hai đợt review do >8 file; không thêm dependency/hạ tầng, không giả định đã được phép sửa Web.
- Quyết định nghiệp vụ có nguồn user; các mốc thời gian và thiết kế phiên/phiên bản được đánh dấu đề xuất, không tự coi EDU-10/22 cũ là đã thay đổi.
- Đặc tả đạt 16/16 mục chất lượng; test tính năng mới chưa có. Kế hoạch bổ sung này đang DRAFT, chưa được user duyệt.

### Progress log bổ sung — 2026-10-27

- Hoàn tất đặc tả và ghi hai câu trả lời user; chưa sửa source/contract/migration/FE, chưa commit.
- Kiểm tra tài liệu: `node scripts/check-ai-layer.mjs` đạt trước khi thêm phần kế hoạch này; sẽ chạy lại sau lần chỉnh cuối.
- Tiếp: user duyệt phần bổ sung, các mốc thời gian và cho phép sửa CareNest_FE trước khi thực hiện; không dùng trạng thái plan gốc để bỏ qua cổng duyệt.

### 2026-10-27 — User duyệt triển khai tự lưu

- Tiền: "duyệt sửa cả BE và FE"; duyệt các mốc thời gian và thay đổi contract/schema trong phần bổ sung, cho phép sửa CareNest_FE.
- Thực hiện hai phần BE và FE trong phạm vi đã duyệt; không commit/push/tạo nhánh. Giữ các cổng kiểm tra và báo riêng phần chưa kiểm chứng.
- RED: EduYearPlanEditTest thất bại đúng yêu cầu mới: expected PENDING_VP but was EDITING.

### 2026-10-27 — Tự lưu AS-1/AS-2: gate đạt, chờ ghi đối chiếu cuối

- BE: `VERIFY PASS full | tests 380 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 118s`. V24 (nay V30) chạy trên PostgreSQL thật; CHECK NULL có RED/GREEN; tạo/lưu retry, expiry và same-account sessions được kiểm.
- FE: `VERIFY PASS full | npm run verify | log verify.log`; fixture Date test thực đơn được cố định, không đổi nghiệp vụ thực đơn; định dạng SchoolPlansPage trong cùng module. Đang chạy lại sau sửa câu cảnh báo rời trang cuối.
- Browser mock: tự lưu không bấm Lưu; /new chuyển sang /edit giữ quyền; một tab sửa, tab kia chỉ xem; đóng holder rồi thử lại nhận quyền; rời trước 2s rồi mở lại thấy bản phục hồi. Review subagent không còn P1/P2 cụ thể.
- Giới hạn: chưa chạy browser E2E trên BE thật; offline/reconnect được mô phỏng ở node:test và fencing kiểm qua PostgreSQL thật; chưa đo SLA mạng production. Không claim các phần đó đã kiểm.
- Khác plan: bổ sung one-use handoff để route /new→/edit không làm mất quyền; cache riêng từng slot và fallback nhận diện lần tạo chưa có phản hồi; retry400 lấy snapshot đã sửa, retry mạng giữ request ID. Không thêm dependency.
- Không commit/push/tạo nhánh; giữ các thay đổi trước task, nhất là FE authService.js.

### 2026-10-27 — Chốt riêng phần tự lưu mục tiêu năm

- Các task 001 hoàn tất qua review và gate; Converge 2 không còn thiếu sót triển khai trong phạm vi này. Giữ plan gốc in-progress vì lịch sử/phạm vi kế hoạch giáo dục còn bước tiếp theo.
- FE gate chạy lại sau sửa câu cảnh báo đạt; browser mock phục hồi đúng nội dung rời trước nhịp lưu và có nút Khôi phục bản trên máy. Ảnh bằng chứng tạm trong target/autosave-recovery-demo.png (gitignored).
- Tiếp theo theo chỉ đạo user: quy trình Spec Kit cho chủ đề nhánh/chủ đề/tuần/ngày và học liệu/trình chiếu, giữ quyền và chuỗi duyệt đang có.

### 2026-10-27 — Đổi sang lưu tay + bản giữ ẩn (Tiền chốt)

- Tiền: "để cả tự lưu và lưu bằng tay, đánh lừa người dùng rằng họ vẫn cần lưu nhưng nếu mất điện thì vẫn có bản tự lưu". FE bỏ nhịp tự ghi 2s/15s và tự thử lại; chỉ ghi khi Lưu/Ctrl+S, gửi/ban hành. Bản giữ ẩn trên máy (750ms + pagehide) cho mục tiêu năm và chủ đề nhánh, kế hoạch chủ đề, giáo án tuần/ngày; không hiện chữ về tự lưu. EDU-22/23 cập nhật; BE không đổi code/contract.
- FE `node scripts/verify.mjs` PASS; browser mock kiểm mục tiêu năm và giáo án tuần (trạng thái, hộp rời màn, khôi phục, Không lưu/Lưu xóa bản giữ).

### 2026-10-27 — Nhận quyền cùng tài khoản, Việt hóa thông báo, sửa B1/B3 (Tiền chốt)

- A: cùng tài khoản start-editing bằng phiên mới ⇒ nhận quyền ngay; phiên cũ 409 "Đã mất quyền sửa…" ở heartbeat/lưu và giữ bản giữ; người khác vẫn chờ 90s. Test: `sameAccountNewSessionTakesOverAndOldSessionLosesLease`, `otherUserStillWaitsForLeaseExpiry`, `expiredLeaseCannotOverwriteOrReleaseNewSession`. EDU-10/22, flow mục 1, spec 001 (Clarifications) cập nhật.
- B2: thông báo phiên/revision kế hoạch năm là câu ngắn tiếng Việt ("Đang được [tên] sửa", "Đã mất quyền sửa…", "Kế hoạch đã được thay đổi ở nơi khác. Tải lại để đối chiếu").
- B3: thiếu part multipart/header bắt buộc ⇒ 400 "Thiếu dữ liệu bắt buộc: <tên>" (trước: 500 rồi câu tiếng Anh). Test: `LearningResourceControllerTest.uploadMissingPartReturns400WithClearMessage`, `ResponseContractTest.missingMultipartPartReturns400NotServerError`.
- FE (CareNest_FE): CSS hàng đầu trang chi tiết kế hoạch năm (B1, Tiền duyệt, chỉ ≥1024px); Playwright IT-05b, IT-16, ST-03b, ST-05b; 31/31 Pass trên BE dev.
- Không đổi contract/migration; không commit/push.


### 29/10/2026 — Duyệt DB-1 và vòng lặp Spec Kit
- User: "duyệt, dùng vòng lặp spec"; duyệt toàn bộ bổ sung DB và triển khai DB-1.
- Feature artifacts: specs/006-year-plan-goal-hierarchy; ba bảng/FK thật, cấp cha tùy chọn; không tạo branch/commit.

### 29/10/2026 — DB-1 triển khai và review, đang kiểm chứng cuối
- Source: V35 backfill giữ ID/lineage; ba bảng/entity/repository, FK cha optional kiểm ownership; mapped superclass thay entity bảng cũ; API rows/kind giữ nguyên.
- Lưu/đọc/copy/diff và membership học liệu đã chuyển sang ba bảng. Test chuyển/xóa cha kiểm giữ ID/nội dung/dấu tích, copy remap FK/lineage.
- Regression đợt đầu: 46 tests fail 0 err 0 skip 0; upgrade trên PostgreSQL 17 Testcontainers. Lỗi deferred-trigger DDL có RED/GREEN và incident BUG-261029-hierarchy-deferred-ddl.
- Verify trước convergence: VERIFY PASS full | tests 432 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 250s | log target/verify.log (đã giữ bản log tại target/hierarchy-full-before-convergence.log).
- Review độc lập vòng 2: PASS, không còn finding chặn. Converge 1 append T011 cho fixture nhiều domain/phiên bản/sequence; đã implement. Converge 2 không còn gap source/test, không sửa tasks.
- Khác plan: giữ EduGoalRow là mapped superclass để giảm lặp mapper/logic, không có bảng cha thứ tư; thêm tab V32 của drawio để bảo toàn bản vẽ user đang chỉnh. ADR-0019 ghi quyết định.
- Cổng sau T011 đang chạy; chưa chốt DB-1 trước output cuối. Không chạy migration trên DB đang dùng, không sửa FE/APP, không commit/push/branch. Plan gốc có phạm vi rộng nên giữ in-progress.

### 29/10/2026 — DB-1 DONE, chờ user review kết quả
- Commit: chưa commit.
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 433 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 247s | log target/verify.log`.
- Migration PostgreSQL 17: 5/5 test upgrade, skip 0; full gate gồm HBM validate, schema, service/copy, class-doc/học liệu, quyền và snapshot.
- API: snapshot ok và `git diff --exit-code -- docs/api/openapi.yaml` exit 0. Không thay DTO/endpoint.
- Docs: dictionary/ERD/domain model/module card + ADR-0019 (nay ADR-0021); markdown ngoài repo và tab drawio V32; XML/ID/edge valid, bản vẽ user đang chỉnh được giữ lại.
- Memory: BUG-261029-hierarchy-deferred-ddl fixed (RED/GREEN); ISSUE_INDEX và module pitfalls cập nhật.
- Spec loop: 8 FR/4 SC/3 US được đối chiếu; converge 1 append T011, implement; converge 2 không còn gap, không thêm task. 11/11 tasks done.
- DoD DB-1: phạm vi/quyền/ID/dữ liệu cũ/phiên bản đúng, full verify skip0, schema docs/ADR và check-ai-layer 0 lỗi/0 cảnh báo (E6 bỏ qua theo output).
- Giới hạn: chưa migration DB đang dùng hoặc production, chưa browser E2E Web; không commit/push/branch, không sửa FE/APP. Không cần test AI-off mới vì không chạm AI.
- Tiếp: user review DB-1; dừng triển khai tại đây. Giữ plan gốc active/in-progress vì phạm vi lịch sử còn rộng hơn DB-1; không tự đóng/chuyển cả plan cũ.

### 29/10/2026 — Merge `origin/dev` (G94-60, G94-254) vào nhánh, cả BE và FE
- User: "giải quyết merge cả be và fe đi, đổi số migration bên nhánh này"; nghiệp vụ lấy context `dev`, kế hoạch giáo dục theo nhánh này (cập nhật thêm phần `dev` đổi); code kế hoạch giáo dục theo nhánh. Chưa commit.
- Migration: V7–V9 giữ của `dev` (phân quyền); V7–V32 của nhánh đổi thành V10–V35 (`git mv`). Test target `flyway("31")` → `"34"`; comment V-số trong test/seed/migration đổi theo. DB dev/test đã chạy số cũ phải tạo lại (Flyway lệch checksum theo version).
- `GET /campuses` trùng mapping giữa `CampusController` (nhánh) và `SchoolStructureController` (`dev`) ⇒ giữ của nhánh; endpoint tra cứu của `dev` chuyển `GET /campuses/options`, DTO `CampusOptionResponse` (JSON `{id, code, name}` như cũ). FE chưa gọi endpoint này (tài khoản/phụ huynh FE đang mock) nên không sửa FE code.
- `GlobalException` của nhánh chỉ tạo qua `GlobalException.of(ApiCode)` ⇒ đổi mọi lỗi tiếng Anh trong code `dev` sang mã mới: 1112–1126 (tài khoản & phân quyền), 2308–2319 (phân công), 3004–3006 (phụ huynh–trẻ); test `dev` so mã cụ thể. API phân công chung của `dev` từ chối `AGE_GROUP_TEAM_LEADER` (2319) — tổ trưởng phân công qua TeacherAssignmentService.
- Seed dev: `primary_role` của `dev` dùng username mới của nhánh (`admin`, `teacher1`…); campus giữ `short_name`, `address`.
- Docs: đổi số rule HLT-07 (nhánh) → HLT-15; EDU-01..10 (`dev`) → EDU-29..38; ADR-0017 (nhánh) → ADR-0020, ADR-0019 (nhánh) → ADR-0021. ADR-0019 của `dev` ("làm lại kế hoạch giáo dục trên dev") lệch với merge này — giữ, thêm ghi chú, chờ user chốt. Lệch nghiệp vụ còn mở: EDU-31/32/35/38 vs EDU-11/17..19, ADR-0006 ACCEPTED (nhánh) vs SUPERSEDED (`dev`).
- Link hỏng có sẵn từ `dev` (`MEAL_NUTRITION_AI_SCHEMA.md`, plan `2026-10-27-survey-auth-alignment.md` chưa từng commit) ⇒ ghi tên + "chưa có trong repo" thay vì đường dẫn.
- `openapi.yaml` sinh lại bằng `-Dopenapi.snapshot.update=true`; khớp bản gộp tay trừ `/campuses/options`, GET `/campuses` của nhánh và enum `AGE_GROUP_TEAM_LEADER`.

### 29/10/2026 — Context kế hoạch giáo dục theo đúng code (user chốt)
- User: "sửa context kế hoạch giáo dục theo đúng code của tôi". Đã đối chiếu code: EDU-18 bỏ bước Tổ trưởng (`EduClassDocServiceImpl`), hồ sơ chủ đề gửi một lần (EDU-17), dòng hoạt động tuần do HT/PHT cấu hình (`EduSettings.weekRows`, EDU-11), quyền theo role tại `EduPlanAccessPolicy` (EDU-08).
- BE: EDU-31/32/35/38 (khảo sát `dev`) ⇒ SUPERSEDED, trỏ EDU-18, EDU-17..19, EDU-11/16, EDU-08; ADR-0019 của `dev` ⇒ SUPERSEDED bởi ADR-0020; INT-02 không còn áp dụng; ADR-0006 giữ ACCEPTED. Sửa BUSINESS_RULES, flow + card education-plan, INDEX, PROJECT_CONTEXT, CURRENT_STATE, USER_ROLES.
- FE: ROUTE_MAP, CURRENT_STATE, REPOSITORY_CONTEXT, CONTEXT_MAP, AGENTS viết lại theo route/quyền thật (`AppRoutes.jsx`, EDU-01..28), bỏ ghi chú "UI mock, lệch BE" của `dev`. Không đổi code.

### 30/10/2026 — Lịch chủ đề theo mẫu Thái Dương, đóng P-29 (user chốt)
- User: chặn chủ đề trùng thời gian (1) và chủ đề Thứ 2 → Thứ 6 (2) — "ok"; đợt nghỉ không cần trọn tuần, nghỉ 1 ngày vẫn ghi, các ngày khác soạn bình thường (3). Code chưa có cả ba (trùng chỉ cảnh báo FE; nghỉ bất kỳ ngày nào bỏ cả tuần) ⇒ chốt theo user.
- Rule: EDU-39 (thời lượng do người lập đặt, 1 tuần học = 1 nhánh, không trùng), EDU-40 (Thứ 2 → Thứ 6), EDU-41 (tuần chỉ thành tuần nghỉ khi nghỉ trọn Thứ 2 → Thứ 6). P-29 chuyển "Đã đóng".
- BE: `EduYearPlanServiceImpl.requireThemeWeeks` kiểm ở gửi/ban hành (mã 7031 `THEME_WEEK_ALIGNMENT`, 7032 `THEME_DATES_OVERLAP`); `EduClassDocServiceImpl.teachingWeeks` tính nghỉ theo từng ngày. Fixture chủ đề bắt đầu Chủ nhật 27/09 đổi thành Thứ 2 28/09. Test mới `EduTeachingWeeksTest`, `themesMustCoverWholeWeeksWithoutOverlap`.
- FE: `goalTable` (`breakOn`, `weekBreaks`, `isWholeWeekTheme`, `overlappingThemes`, `themeScheduleIssue`); `yearWeeks` trả `offDays`; form kế hoạch năm chặn ở bước Tiếp theo, báo tại dòng; giáo án tuần/ngày, hồ sơ chủ đề, bảng chủ đề nhánh, bản in hiện "Nghỉ: <tên đợt>". Không đổi API/DB (đợt nghỉ 1 ngày đã lưu được với ngày kết thúc trống).
- Giả định: ngày nghỉ lẻ không chặn soạn/gửi (chỉ ghi chú "không cần soạn"); kế hoạch đã ban hành trước 30/10 không bị kiểm lại, chỉ phiên bản mới mới phải theo EDU-39/40.

### Progress log bổ sung — 2026-10-30 (bỏ `edu_expected_results`)
- User chốt bỏ bảng danh mục gợi ý `edu_expected_results` và `GET /education-plans/expected-results`. Lý do: bảng chưa từng có dữ liệu (0 dòng ở mọi DB dev), không bảng nào có FK trỏ vào, Web/App không gọi API; gợi ý theo Bộ đã có `edu_curriculum_*` (EDU-28, spec 005).
- BE: migration `V36__drop_edu_expected_results.sql`; xóa `EduExpectedResult`, `EduExpectedResultRepository`, `ExpectedResultResponse`, `getExpectedResults` (controller/service/mapper). Không đụng `EduPlanExpectedResult` (nhóm kết quả mong đợi thật trong kế hoạch).
- Contract: bỏ một endpoint đọc; tác động FE/APP: không (Web dùng hằng `src/models/education-plan/expectedResults.js`, App không tham chiếu). Cập nhật openapi, DATA_DICTIONARY, DOMAIN_MODEL, GLOSSARY, module card, CROSS_REPO_MAP, contract spec 005.
- Giả định: gợi ý ở Web giữ nguyên (hằng FE, mới có khối 3–4 tuổi); hướng bổ sung là lấy từ danh mục Bộ, không dựng lại bảng cũ.
