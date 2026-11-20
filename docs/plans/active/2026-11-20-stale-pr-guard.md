---
title: Chặn PR có kết quả test cũ merge vào dev và kiểm trùng số migration
status: in-progress
owner: thangnd
jira: G94-440 / BE-FIX-09
branch: fix/G94-440-BE-FIX-09-stale-pr-guard
modules: [deployment]
rules: []
risk: medium
created: 2026-11-20
updated: 2026-11-20
---
# Chặn PR có kết quả test cũ merge vào dev và kiểm trùng số migration

Liên quan: `2026-11-20-ci-parallel-tests.md` (G94-439, CI chạy song song 4 shard).

## Context
- Sự cố 19–20/11: PR #82 (G94-419, thêm `V154__supplier_contract_and_food_prices.sql`) CI xanh 16:18Z; PR #83 (G94-236, thêm `V154__school_year_config_per_year.sql`) CI xanh 16:29Z. Merge #83 (16:59Z) rồi #82 (17:11Z) ⇒ `dev` có 2 file V154 ⇒ Flyway từ chối khởi động, run 44574429268 đỏ (715 test lỗi), không deploy. Sửa tay: đổi số sang V155 (`668bfe25`), PR #85 deploy 18:21Z.
- Backup/rollback (`deploy/remote-deploy.sh`) không chạy vì bản lỗi bị chặn ở bước test, chưa lên VPS — đúng thiết kế. Lỗ hổng: PR được merge khi kết quả test đã cũ (test so với `dev` cũ).
- Ruleset `protect-dev` (id 26676793, `gh api repos/thang-04/CareNest_BE/rulesets/26676793`): chỉ có `deletion`, `non_fast_forward`, `pull_request` (1 approve, code owner, last-push approval, dismiss stale). **Không có** required status check, không bắt cập nhật theo `dev`. Bypass: role admin (id 5) và 1 user, mode `always`.
- `pull_request` có `paths` filter (`.github/workflows/deploy.yml:19-29`) ⇒ PR chỉ sửa docs không chạy workflow ⇒ nếu bắt check thì PR docs treo "Waiting for status".
- `api-test` là matrix bị skip khi tag đã có (`deploy.yml:109`) ⇒ tên check `api-test (1..4)` không luôn xuất hiện ⇒ không đặt làm required check trực tiếp được.
- Migration: `src/main/resources/db/migration` (157 file `V<n>__`), profile dev thêm `db/dev` (`application-dev.yml:7`, chỉ file `R__dev_*`).

## Quyết định đã chốt
1. Làm A + B: bắt PR cập nhật theo `dev` + check bắt buộc, và kiểm trùng số migration ở CI — user, 2026-11-20.
2. Tách task Jira mới G94-440 — user, 2026-11-20.
3. Thời gian mỗi lần CI không tăng đáng kể; chấp nhận PR phải chạy lại khi `dev` đổi — user, 2026-11-20 (đồng ý sau giải thích).

## Làm rõ nghiệp vụ
Không đổi nghiệp vụ, API, quyền, dữ liệu. Đổi hạ tầng CI (`deploy.yml`) + cấu hình repo (ruleset `protect-dev`). FE/APP không ảnh hưởng. Ảnh hưởng quy trình team: PR vào `dev` phải *Update branch* khi `dev` có commit mới.

### Câu hỏi mở
- [x] Có bỏ quyền bypass `always` của admin không? ⇒ Giữ.

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |
| Bỏ bypass admin? | Giữ (khẩn cấp vẫn merge được, phải tick bypass) | user duyệt plan với mặc định | 2026-11-20 | `docs/system/DEPLOYMENT.md` mục Luật merge PR |

## Acceptance criteria
- **AC-1**: Given 2 file migration cùng version (vd. `V154__a.sql`, `V154__b.sql`; hoặc `V0154`/`V154`, `V1_1`/`V1.1`), When CI chạy, Then job `plan` đỏ trong < 1 phút, log in version trùng + tên 2 file, không chạy test.
- **AC-2**: Given repo hiện tại (không trùng), Then bước kiểm trùng xanh.
- **AC-3**: Given PR chỉ sửa `docs/**`, When mở PR vào `dev`, Then workflow chạy, `api-test` skip, check `ci-result` xanh trong ~1 phút ⇒ merge được.
- **AC-4**: Given 1 shard `api-test` fail hoặc `plan` fail, Then `ci-result` đỏ ⇒ ruleset chặn merge.
- **AC-5**: Given PR xanh nhưng `dev` đã có commit mới, Then nút merge khóa tới khi *Update branch* và CI chạy lại xanh.
- **AC-6**: Given PR đã cập nhật theo `dev` và xanh, When merge, Then run push `dev` thấy tag `api-<hash>` đã có ⇒ bỏ test, chỉ deploy (hash cây merge = hash cây PR).

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Check bắt buộc | 1 job tổng `ci-result` (`needs: [plan, api-test, api, keycloak]`, `if: always()`, đỏ khi có job `failure`/`cancelled`) | Require `plan` + `api-test (1..4)` | Matrix bị skip thì tên check theo shard không xuất hiện ⇒ PR treo. Job tổng luôn có đúng 1 tên; thêm/bớt shard không phải sửa ruleset |
| PR docs | Bỏ `paths` filter ở `pull_request`, giữ ở `push` | Workflow phụ cho docs cùng tên check | Hash tag chỉ tính `src pom.xml .mvn mvnw Dockerfile docs/api/openapi.yaml` (`deploy.yml:79`) ⇒ PR docs thấy tag có sẵn ⇒ skip test tự nhiên; 1 workflow dễ hiểu hơn |
| Kiểm trùng migration | Bước shell đầu job `plan`: lấy version từ tên file `V<ver>__*.sql` trong `db/migration` + `db/dev`, chuẩn hoá (`_`⇒`.`, bỏ số 0 đầu mỗi phần), tìm trùng | Chạy Flyway `validate`; test Java | Không cần JVM/DB ⇒ vài giây, báo lỗi trước khi tốn 6 phút test. Flyway trong test vẫn là lớp chặn thứ 2 |
| Cập nhật theo `dev` | Ruleset `required_status_checks` với `strict_required_status_checks_policy: true` | Merge queue | Merge queue không có cho repo của tài khoản cá nhân |
| Thứ tự áp dụng | Merge PR workflow trước, rồi mới sửa ruleset | Sửa ruleset ngay | Ruleset bắt `ci-result` trước khi job tồn tại trên `dev` ⇒ mọi PR treo |

## Phases
### Phase 1 — Workflow: kiểm trùng migration + job `ci-result` + PR luôn chạy
- Files: M `.github/workflows/deploy.yml` · M `docs/system/DEPLOYMENT.md` (ghi luật merge mới)
- Steps:
  1. Xoá khối `paths` dưới `pull_request`; sửa comment đầu file.
  2. Job `plan`: bước `Check duplicate migration versions` ngay sau checkout; trùng ⇒ `::error::` + `exit 1`.
  3. Job `ci-result`: `needs: [plan, api-test, api, keycloak]`, `if: always()`, `runs-on: ubuntu-latest`, 1 bước: nếu `contains(needs.*.result, 'failure') || contains(needs.*.result, 'cancelled')` ⇒ `exit 1`.
  4. DEPLOYMENT.md: mục "Luật merge vào dev": check `ci-result` bắt buộc, phải cập nhật theo `dev`, migration trùng số bị chặn ở `plan`.
- Tests:
  - AC-1/AC-2: chạy bước shell cục bộ (Git Bash) trên repo thật (xanh) và thư mục tạm có `V154__a.sql` + `V0154__b.sql` (đỏ); kiểm thêm `V1_1`/`V1.1`.
  - AC-3/AC-4: đọc `needs`/`if`; mở PR này (sửa `.github` + docs) ⇒ workflow chạy, `ci-result` xanh, `api-test` skip.
  - YAML parse được.
- Exit: PR vào `dev` xanh, `ci-result` xuất hiện trong checks.
- **Dừng chờ user review + merge.**

### Phase 2 — Ruleset `protect-dev` (sau khi Phase 1 merge, user xác nhận lần nữa)
- Files: không. Lệnh: `gh api -X PUT repos/thang-04/CareNest_BE/rulesets/26676793` giữ nguyên rule cũ + thêm:
  ```json
  {"type":"required_status_checks","parameters":{"strict_required_status_checks_policy":true,
   "do_not_enforce_on_create":false,"required_status_checks":[{"context":"ci-result","integration_id":15368}]}}
  ```
  (`15368` = GitHub Actions app.) Lưu JSON ruleset cũ trước khi sửa để rollback.
- Tests: AC-5 trên 1 PR đang mở cũ hơn `dev` (nút merge khóa, hiện "This branch is out-of-date"); AC-6 xem run push `dev` sau merge PR kế tiếp (job `api-test` skip, `deploy-dev` xanh).
- Exit: `gh api .../rulesets/26676793` có rule `required_status_checks` strict; AC-5 quan sát được.

## Test matrix
| AC | Kiểm | Phase | Kết quả |
| --- | --- | --- | --- |
| AC-1 | Bước shell trên thư mục tạm có version trùng | 1 | |
| AC-2 | Bước shell trên repo thật + run CI PR | 1 | |
| AC-3 | Run CI của PR này (chỉ `.github` + docs) | 1 | |
| AC-4 | Đọc `ci-result` `needs`/`if` | 1 | |
| AC-5 | PR cũ hơn `dev` sau khi bật ruleset | 2 | |
| AC-6 | Run push `dev` sau merge PR đã cập nhật | 2 | |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Nhiều người merge liên tiếp | PR sau chạy lại CI 6–7 phút mỗi lần `dev` đổi | Đã báo user, chấp nhận | Tắt `strict_required_status_checks_policy` |
| Update branch tính là "last push" ⇒ cần approve lại (rule `require_last_push_approval`) | Thêm 1 vòng review | Quan sát ở Phase 2; báo user nếu xảy ra | Bàn với user bỏ `require_last_push_approval` |
| Admin bypass `always` | Admin vẫn merge được PR cũ nếu tick bypass | Ghi DEPLOYMENT.md; câu hỏi mở | — |
| Bước kiểm trùng nhận nhầm tên file | CI đỏ sai | Chỉ xét file khớp `^V[0-9._]+__.*\.sql$`; kiểm cục bộ trên 157 file | Revert commit |
| `ci-result` thiếu/sai tên khi bật ruleset | Mọi PR treo | Bật ruleset chỉ sau khi thấy check `ci-result` trên `dev` | PUT lại JSON ruleset cũ |

## Validation log
- [x] Khẳng định về code có `file:line` hoặc lệnh/run id
- [x] Đổi contract ⇒ không đổi API; đổi quy trình merge, đã nêu
- [x] Mỗi AC có cách kiểm; phase có Exit
- [x] 2 file, 2 phase
- [x] Không dùng rule PENDING/OPEN
- [x] Tự review: không placeholder · không mâu thuẫn · scope 1 plan

## Progress log
### 2026-11-20 — Viết plan (chờ duyệt)
- Nhánh `fix/G94-440-BE-FIX-09-stale-pr-guard` từ `origin/dev` (`4717bc2e`). Chưa commit.
- Tiếp: user duyệt ⇒ Phase 1.

### 2026-11-20 — Phase 1 (chờ review)
- User duyệt plan 2026-11-20 (giữ bypass admin — mặc định, user không phản đối).
- Kiểm cục bộ (Git Bash, bước shell trích từ YAML đã parse): repo hiện tại ⇒ exit 0 (AC-2); thư mục thử `V154`/`V0154`, `V1_1`/`V1.1`, `V2`/`V2.0` ⇒ báo 3 cặp, exit 1 (AC-1); cây `db/` của commit sự cố `55f8f81c` (run 44574429268) ⇒ báo `V154: V154__school_year_config_per_year.sql V154__supplier_contract_and_food_prices.sql`, exit 1.
- `ci-result`: thử 4 tổ hợp kết quả (skip ⇒ xanh; failure/cancelled ⇒ đỏ) (AC-4). YAML parse được; chưa có actionlint.
- Chưa commit. Tiếp: commit + PR ⇒ đo AC-3 trên CI ⇒ merge ⇒ Phase 2.
