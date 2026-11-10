---
title: Đổi header chống gửi trùng X-Request-Id (bị proxy hosting ghi đè) sang Idempotency-Key
status: in-progress        # draft → approved (chỉ user duyệt) → in-progress → done | blocked | cancelled
owner: thangnd
jira: G94-282 / BE-FIX-02
branch: fix/G94-282-BE-FIX-02-idempotency-header
modules: [education-plan]
rules: [EDU-10, EDU-22]
risk: high           # đổi API contract, cần FE deploy cùng lúc
created: 2026-11-02
updated: 2026-11-02
---
# Đổi header chống gửi trùng X-Request-Id sang Idempotency-Key

## Context
Trên web.carenest.edu.vn, mọi thao tác ghi kế hoạch năm (tạo, Lưu, gửi duyệt, ban hành…) đều trả `400 / 9002 "Dữ liệu gửi lên không đúng định dạng"`. Đã tái hiện trên VPS ngày 2026-11-02:
- Edge proxy của hosting (response có `x-osh-dp: hn01-dnsgr01-dataplan01`, đứng trước Caddy) **luôn** đặt `X-Request-Id` thành ULID (`01M6C4Y401Q2TBAJP49WXFHSVT`), kể cả khi client đã gửi UUID hoặc không gửi gì.
- BE nhận header này dạng `@RequestHeader("X-Request-Id") UUID requestId` ⇒ ép kiểu thất bại ⇒ `MethodArgumentTypeMismatchException` ⇒ `GlobalExceptionHandler.handleMalformedRequest` trả 9002.
- Bằng chứng: body `title:null` ⇒ 9001 (JSON parse được); body tối thiểu có title hợp lệ + `schoolYearId` không tồn tại vẫn ra 9002 (không vào tới service); bỏ `X-Edit-Session`/`X-Plan-Revision` ⇒ 9003, nhưng bỏ `X-Request-Id` vẫn ra 9002.
- Local/test không có proxy này nên test và e2e đều xanh.

Hiện trạng code (`origin/main`):
- `EduYearPlanController.java:109,127,144,164,184,200,216,232,249,276`: 10 endpoint nhận `X-Request-Id` (create, update, submit, issue, return, start-editing, finish-editing, editing-heartbeat, versions, delete).
- `LearningResourceController.java:48`: upload media (`POST /education-plans/class-docs/theme/{docKey}/learning-resources`) cũng nhận header này ⇒ cùng lỗi trên VPS.
- `SecurityConfig.java:179`: danh sách header CORS cho phép.
- `docs/api/openapi.yaml`: 11 tham số `name: X-Request-Id` (dòng 1345…2097).
- FE: `src/services/education-plan/api/educationPlanApi.js:54` (`editHeaders`), `src/services/education-plan/learningResourceService.js:12`.

## Quyết định đã chốt
1. Sửa bằng cách đổi tên header, không nới BE nhận chuỗi bất kỳ — user, 2026-11-02 ("Lên plan đổi tên").
2. Header mới tên `Idempotency-Key`; log thêm tên tham số khi 9002; FE sửa cùng đợt bằng plan riêng — user, 2026-11-02.

## Làm rõ nghiệp vụ
- **Hiểu nghiệp vụ:** Actor: Tổ trưởng, Phó HT, GV (upload media) · Outcome: lưu/gửi/ban hành kế hoạch năm và tải media lên được trên VPS · Rule: EDU-10 (một phiên giữ quyền sửa, CONFIRMED), EDU-22 (lưu tay, CONFIRMED). Không đổi hành vi nghiệp vụ; chỉ đổi tên kênh truyền khóa chống gửi trùng.
- **Đối chiếu thiết kế ban đầu:** `docs/system/CROSS_REPO_MAP.md:64` và `specs/001-year-plan-autosave/contracts/year-plan-autosave.md:13` ghi `X-Request-Id` UUID, giữ nguyên khi gửi lại. Ý nghĩa giữ nguyên, chỉ đổi tên.
- **Ảnh hưởng:** module education-plan · quyền/scope không đổi · không có dữ liệu dẫn xuất · FE: `/education/goals/new`, `/education/goals/:id` (lưu/gửi/ban hành), màn upload học liệu theo chủ đề · APP: không dùng (đã grep) · dữ liệu cũ: cột `created_request_id` giữ nguyên, không cần migration.

### Câu hỏi mở
- [x] Tên header mới: `Idempotency-Key`.
- [x] Mã Jira: G94-282 / BE-FIX-02 (Bug, Epic G94-88). Nhánh đề xuất `fix/G94-282-BE-FIX-02-idempotency-header` từ `dev`.
- [x] Log thêm tên tham số trong `handleMalformedRequest` (chỉ tên header/field, không log giá trị): có.
- [x] Cho phép sửa repo `CareNest_FE` trong cùng đợt (plan FE riêng, link về plan này): có.

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| Tên header mới | `Idempotency-Key` | user | 2026-11-02 | plan này; CROSS_REPO_MAP ở phase 2 |
| Log tên tham số khi 9002 | Có, không log giá trị | user | 2026-11-02 | plan này |
| Sửa CareNest_FE cùng đợt | Có, plan FE riêng | user | 2026-11-02 | plan này |
| Mã Jira | G94-282 / BE-FIX-02 (tạo theo yêu cầu user) | user | 2026-11-02 | plan này |

## Acceptance criteria
- **AC-1** (EDU-10): Given request ghi kế hoạch năm có `X-Request-Id: <ULID do proxy đặt>` và header mới mang UUID hợp lệ, When gọi create/update, Then BE xử lý bình thường (2xx), bỏ qua `X-Request-Id`.
- **AC-2** (EDU-10): Given gửi lại cùng giá trị header mới cho cùng snapshot, When create, Then trả kế hoạch đã tạo, không tạo bản trùng (giữ hành vi hiện có).
- **AC-3**: Given thiếu header mới, When gọi endpoint ghi, Then 400 / 9003 nêu đúng tên header mới.
- **AC-4**: Given upload media có `X-Request-Id: <ULID>` và header mới UUID, When POST learning-resources, Then 2xx và gửi lại cùng key thì không tạo bản trùng.
- **AC-5**: Given preflight CORS từ origin web, When xin header mới, Then được cho phép.
- **AC-6** (VPS): Given bản đã deploy, When lưu kế hoạch ở `/education/goals/new?buoc=3`, Then lưu thành công, không còn 9002.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Tên header mới | `Idempotency-Key` (user chốt 2026-11-02) | `X-CareNest-Request-Id`; giữ `X-Request-Id` và nhờ hosting tắt ghi đè | Tên chuẩn IETF cho đúng mục đích chống gửi trùng, proxy phổ biến không đụng tới; `X-Request-Id` là header truy vết proxy coi là của mình, không kiểm soát được hosting |
| Nhận cả header cũ trong giai đoạn chuyển | Không | BE đọc header mới, thiếu thì đọc `X-Request-Id` | Trên VPS header cũ đã hỏng nên giữ không giúp gì; client duy nhất là FE web, deploy cùng lúc (YAGNI) |
| Kiểu dữ liệu | Giữ `UUID` | `String` | Giữ kiểm tra định dạng, `created_request_id` vẫn là UUID |

## Phases
### Phase 1 — BE: đổi header + test hồi quy
- Files: M `src/main/java/com/carenest/controller/eduplan/EduYearPlanController.java` · M `src/main/java/com/carenest/controller/eduplan/LearningResourceController.java` · M `src/main/java/com/carenest/config/SecurityConfig.java` · M `src/test/java/com/carenest/controller/eduplan/EduYearPlanControllerTest.java` · M `src/test/java/com/carenest/controller/eduplan/LearningResourceControllerTest.java` · M `src/test/java/com/carenest/config/SecurityConfigTest.java` · M `docs/api/openapi.yaml` (tạo lại snapshot) · M `src/main/java/com/carenest/exception/GlobalExceptionHandler.java`
- Steps: 1. Viết test AC-1/AC-4 gửi kèm `X-Request-Id: 01M6C4Y401Q2TBAJP49WXFHSVT`, chạy cho fail (9002) trước khi sửa. 2. Đổi 11 `@RequestHeader` sang tên mới. 3. Đổi danh sách CORS. 4. Sửa test cũ, thêm AC-3, AC-5. 5. Tạo lại openapi theo cách `OpenApiSnapshotTest` quy định. 6. Thêm tên tham số/field (không giá trị) vào log `handleMalformedRequest`.
- Tests: AC-1 → `EduYearPlanControllerTest#createIgnoresProxyRequestIdHeader` · AC-2 → test idempotent đang có của service (đổi tên header ở tầng controller) · AC-3 → `EduYearPlanControllerTest#missingIdempotencyKeyReturns9003` · AC-4 → `LearningResourceControllerTest#uploadIgnoresProxyRequestIdHeader` · AC-5 → `SecurityConfigTest` (header CORS)
- Exit: `node scripts/verify.mjs` → `VERIFY PASS full`, skip 0
- **Dừng chờ user review.**

### Phase 2 — Docs + engineering memory
- Files: M `docs/system/CROSS_REPO_MAP.md` · M `docs/modules/education-plan.md` · C `docs/knowledge/incidents/BUG-261102-proxy-overrides-x-request-id.md` · M `docs/knowledge/ISSUE_INDEX.md`
- Steps: 1. Ghi tên header mới + lý do (proxy hosting đặt `X-Request-Id`) ở CROSS_REPO_MAP và Known pitfalls của module card. 2. Ghi incident gồm cách dò (bisect 9001/9002/9003) và các giả thuyết đã loại. 3. `specs/*/contracts` là thiết kế lịch sử: thêm một dòng "đã đổi sang header mới, xem plan này", không viết lại.
- Exit: `node scripts/check-ai-layer.mjs` pass; `node scripts/verify.mjs` → `VERIFY PASS full`
- **Dừng chờ user review.**

### Ngoài plan này — FE (plan riêng ở `CareNest_FE`, cần user cho phép)
`educationPlanApi.js:54`, `learningResourceService.js:12`, `tests/e2e/support/realBe.mjs:76`, `tests/e2e/integration/edu-plan-api.spec.mjs:327,342`, `tests/education-plan/learning-resources-api.test.mjs:21`. Deploy BE và FE cùng đợt.

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1 | EDU-10 | EduYearPlanControllerTest (WebMvc) | 1 | |
| AC-2 | EDU-10 | test idempotent hiện có (service) | 1 | |
| AC-3 | — | EduYearPlanControllerTest (WebMvc) | 1 | |
| AC-4 | — | LearningResourceControllerTest (WebMvc) | 1 | |
| AC-5 | — | SecurityConfigTest | 1 | |
| AC-6 | EDU-10 | thử tay trên VPS sau deploy | sau deploy | |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| BE và FE deploy lệch nhau | FE cũ + BE mới ⇒ 9003; FE mới + BE cũ ⇒ vẫn 9002 (như hiện tại) | Merge 2 PR cùng đợt release; kiểm AC-6 ngay sau deploy | Revert cả hai PR (VPS vẫn lỗi như hiện nay) |
| Proxy hosting cũng đụng `Idempotency-Key` | Lỗi cũ quay lại | AC-6 kiểm trên VPS; bisect 9003/9002 như đã làm | Đổi sang `X-CareNest-Request-Id` |
| Nhánh đang có header cũ (feature/G94-226…) | Conflict khi merge | Rebase/merge `dev` trước khi PR | — |

## Validation log
- [x] Khẳng định về code có `file:line`
- [x] Đổi contract ⇒ consumer cụ thể (FE web: 2 file service + 3 file test; APP: không dùng)
- [x] Mỗi AC có test; mỗi phase có Exit = lệnh + kết quả mong đợi
- [x] >8 file hoặc >3 phase ⇒ đã tách: FE thành plan riêng; BE 2 phase (8 + 4 file)
- [x] Không dùng rule PENDING/OPEN như đã chốt
- [x] Tự review: không placeholder (Jira/nhánh để ở Câu hỏi mở) · không mâu thuẫn · scope vừa 1 plan

## Progress log
### 2026-11-02 — Draft (chờ duyệt)
- Commit: chưa commit
- Verify: chưa chạy (chưa sửa code)
- Tiếp: user trả lời 3/4 câu (header `Idempotency-Key`, có log, có sửa FE). Còn chờ mã Jira ⇒ tạo nhánh fix từ `dev`, chuyển plan sang `docs/plans/active/`, user duyệt.

### 2026-11-02 — Duyệt plan
- User duyệt ("fix đi cho tôi", 2026-11-02) ⇒ approved, bắt đầu Phase 1 ⇒ in-progress.

### 2026-11-02 — Phase 1 (chờ review)
- Commit: chưa commit
- Trước sửa: `mvnw test -Dtest=EduYearPlanControllerTest,LearningResourceControllerTest,SecurityConfigTest` ⇒ `createIgnoresProxyRequestIdHeader: Status expected:<201> but was:<400>`, `createWithoutIdempotencyKeyIs400NamingHeader`, `multipartAcceptsActivityBindingAsParts`, CORS `Idempotency-Key` fail.
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 646 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 284s`
- Khác plan: AC-4 thêm header ULID vào test upload hiện có (`multipartAcceptsActivityBindingAsParts`) thay vì test mới; openapi tạo lại bằng `-Dopenapi.snapshot.update=true` (đúng 11 dòng đổi tên).
- Tiếp: user review ⇒ Phase 2 (docs + incident) ⇒ plan FE.

### 2026-11-02 — Phase 2 (chờ review)
- Commit: chưa commit
- Verify: `node scripts/check-ai-layer.mjs` → `0 lỗi` (6 cảnh báo cũ của plan khác); `node scripts/verify.mjs` → `VERIFY PASS full | tests 646 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 257s`
- Đã làm: CROSS_REPO_MAP (header mới + upload), module card (Known pitfalls + Related issues), incident `BUG-261102-proxy-overrides-x-request-id` + dòng ISSUE_INDEX, ghi chú đổi tên ở 3 file `specs/*/contracts`.
- Tiếp: plan FE (`CareNest_FE`) đổi `editHeaders`, `learningResourceService`, test e2e; commit/PR khi user cho phép; deploy BE + FE cùng đợt rồi kiểm AC-6 trên VPS.

### 2026-11-02 — FE (chờ review)
- Nhánh FE: `fix/G94-282-FE-FIX-02-idempotency-header` (worktree `CareNest_FE-fix-idempotency`, từ `origin/dev`), chưa commit. Làn M FE: mini-plan trong chat, không plan file riêng.
- Đổi: `educationPlanApi.js` (`editHeaders`), `learningResourceService.js`, e2e `realBe.mjs`, `edu-plan-api.spec.mjs`; test `year-plan-api.test.mjs`, `learning-resources-api.test.mjs` kiểm gửi `Idempotency-Key`, không gửi `X-Request-Id`.
- Trước sửa: 2 test fail (`actual: undefined, expected: 'request-demo'`). Sau sửa: 15/15 pass.
- Verify FE: `node scripts/verify.mjs` → `VERIFY PASS full | npm run verify | log verify.log`
- Tiếp: commit + PR BE và FE vào `dev` khi user cho phép; deploy cùng đợt; kiểm AC-6 trên VPS.
