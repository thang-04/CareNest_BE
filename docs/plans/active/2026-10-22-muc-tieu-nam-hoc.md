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
updated: 2026-10-22
---
# Mục tiêu năm học — API và nghiệp vụ

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
| Module | Tạo module mới `education-plan` và ADR-0017 | Nhét vào learning-observation | Card learning-observation cấm mở rộng thành giáo án |
| API | Mỗi hành động một endpoint (`/submit`, `/issue`, `/return`) | Giữ PUT upsert có status | BE phải tự quyết trạng thái (guide §7) |
| Lưu cây | PUT gửi cả cây. Dòng có id thì update, không có thì insert, dòng bị bỏ thì delete | Tách nhiều endpoint con | Khớp với cách FE lưu cả văn bản (nút Lưu / Ctrl+S) |

## Phases
Plan có hơn 8 file và 4 phase, nên đề xuất tách: Phase 3 (FE) làm thành plan hoặc task FE riêng.

### Phase 0 — Nền và tài liệu
- **Files:**
  - M: `BUSINESS_RULES.md` (thêm EDU, OBS-08 thành SUPERSEDED), `PROJECT_CONTEXT.md`, `ADR-0006`, `.ai/CONTEXT_MAP.yaml`
  - C: `docs/modules/education-plan.md`, `docs/decisions/ADR-0017-education-plan-module.md`
- **Steps:**
  1. Merge `origin/feature/G94-60-BE-FEAT-01-authentication` vào nhánh này. Việc này tạo commit merge, cần Tiền cho phép.
  2. Sửa tài liệu theo danh sách Files.
- **Exit:** `node scripts/check-ai-layer.mjs` pass. **Dừng chờ review.**

### Phase 1 — Schema, entity, repository
- **Files:**
  - C: `V7__create_age_groups.sql` (`age_groups`, `classrooms.age_group_id`, nới `staff_role` thành VARCHAR(30) và cho phép `AGE_GROUP_TEAM_LEADER` + `age_group_id`)
  - C: `V8__create_edu_year_plans.sql` (11 bảng `edu_*` + seed 5 lĩnh vực)
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
  - `V9` bỏ unique viết tắt + CHECK ngày của chủ đề/đợt nghỉ: bản nháp lưu được khi còn soạn dở; kiểm ở service khi gửi/ban hành.
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

### 2026-10-22 — Thu gọn schema (V10, Tiền chọn A)
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
- Merge `origin/feature/G94-236-BE-FEAT-29-daily-attendance` (kèm cấu hình trường G94-235, migration V10–V19).
- Trùng version: `V10__drop_unused_edu_plan_tables` của nhánh này đổi thành `V20__…` (nội dung không đổi, độc lập với V10–V19). DB local đã chạy V10 cũ ⇒ cần dựng lại DB dev (Flyway báo lệch checksum/version).
- `docs/api/openapi.yaml` xung đột ⇒ sinh lại từ code sau merge: giống nhánh kia, chỉ bỏ `EduConfigResponse.yearSigners` (đã bỏ ở `3438e72`).
