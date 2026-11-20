---
title: Rút ngắn CI BE — chạy toàn bộ test song song, bỏ verify lặp khi merge
status: in-progress
owner: thangnd
jira: G94-439 / BE-FEAT-62
branch: feature/G94-439-BE-FEAT-62-ci-speedup
modules: [deployment]
rules: []
risk: medium
created: 2026-11-20
updated: 2026-11-20
---
# Rút ngắn CI BE — chạy toàn bộ test song song, bỏ verify lặp khi merge

Liên quan: `2026-11-12-ci-verify-speed-and-deploy-rollback.md` (G94-340; Phase 4 container dùng chung đã bỏ 13/11).

## Context
- Run 44661732855 (push `dev`) 21 phút; step `node scripts/verify.mjs` 18 phút (`.github/workflows/deploy.yml:113`). Mọi PR, kể cả đổi 1 dòng, chạy lần lượt toàn bộ 263 class test (~2.100 test) trên một máy, một JVM.
- Repo `thang-04/CareNest_BE` là PUBLIC (`gh repo view`) ⇒ GitHub Actions không giới hạn phút, runner `ubuntu-latest` 4 vCPU/16 GB.
- Không có required status check (ruleset `protect-dev`, `protect-main` rỗng) ⇒ đổi tên/tách job không chặn merge.
- Đo CI run 44692692339: Flyway 156 migration 2,6s/class, bật container 2–3s/class. Thử 1 Postgres dùng chung ⇒ 567/2092 test lỗi do dữ liệu lẫn giữa class ⇒ đã revert (`fee5576c`). Test class phải giữ DB riêng.

## Quyết định đã chốt
1. Giữ nguyên bộ cổng: PR chạy toàn bộ test + ArchUnit + snapshot OpenAPI + Spotless + junk — user, 2026-11-12; nhắc lại 2026-11-20 (chọn hướng A, không chạy test chọn lọc).
2. Tối ưu bằng chạy song song toàn bộ test (hướng A) — user, 2026-11-20.
3. PR nội bộ push image theo tag hash nội dung ⇒ merge `dev` không verify lại — user, 2026-11-20 (đã làm, commit `07d1e530`).
4. Không dùng chung DB giữa các class test — kết quả đo 2026-11-20, user chọn revert.

## Làm rõ nghiệp vụ
Không đổi nghiệp vụ, API, quyền, dữ liệu. Chỉ đổi hạ tầng CI (`deploy.yml`). FE/APP không ảnh hưởng.

### Câu hỏi mở
(không còn)

## Acceptance criteria
- **AC-1**: Given PR đổi bất kỳ file trong `src/`, When CI chạy, Then mọi class test của `src/test/java` chạy đúng một lần (tổng test các shard = tổng test chạy tuần tự, skip 0).
- **AC-2**: Given một test fail ở bất kỳ shard, Then job `api` đỏ, không build/push image, không deploy.
- **AC-3**: Given PR như AC-1, Then thời gian từ lúc job `plan` xong tới lúc mọi shard xong ≤ 8 phút (mốc hiện tại 18 phút).
- **AC-4**: Given verify xanh trên push `dev`/`main` hoặc PR nội bộ, Then image được push một lần với tag hash nội dung như cũ; deploy `dev`/`main` không đổi.
- **AC-5**: Mỗi shard lưu artifact `api-verify-reports-<n>` (log + surefire-reports) để cân lại shard theo thời gian thật.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Chia test | 4 shard job (matrix), class `@SpringBootTest`/`@Testcontainers` và class còn lại chia vòng tròn riêng theo tên đã sort | Chia theo module; chia theo thời gian đo | Module lệch nhau (eduplan 58 class, sms 2); chưa có số đo từng class. Chia vòng tròn tách riêng nhóm nặng ⇒ mỗi shard ~23 class tích hợp. Có artifact rồi cân lại |
| Song song trong 1 máy | `-DforkCount=2 -DreuseForks=true` qua CLI | Sửa `pom.xml` | Không chạm `pom.xml` (vùng hỏi trước); mỗi class vẫn container riêng nên 2 JVM không đụng dữ liệu nhau; không có test giữ cổng cố định hay file chung |
| Truyền danh sách test | Biến môi trường `MAVEN_ARGS` (Maven 3.9.16 đọc), gọi `node scripts/verify.mjs` nguyên trạng | Sửa `verify.mjs` thêm `--shard` | Giữ đủ cổng (Spotless, junk, tóm tắt) mà không sửa harness dùng chung với FE |
| Build image | Job `api` riêng, `needs` mọi shard, chỉ build/push | Build trong một shard | Image chỉ build khi cả 4 shard xanh |

## Phases
### Phase 1 — Shard test song song trong `deploy.yml`
- Files: M `.github/workflows/deploy.yml`
- Steps:
  1. Job `api-test` (matrix `shard: [1,2,3,4]`, `fail-fast: false`, `if: needs.plan.outputs.api_needed == 'true'`): checkout `fetch-depth: 0`, setup Java/Node như cũ.
  2. Bước chọn class: `git ls-files src/test/java` lọc tên khớp surefire mặc định (`*Test`, `*Tests`, `Test*`, `*TestCase`), tách nhóm có `@SpringBootTest|@Testcontainers` và nhóm còn lại, sort, lấy phần tử thứ `i ≡ shard-1 (mod 4)` mỗi nhóm; in số class; ghi `MAVEN_ARGS="-Dtest=<list> -Dsurefire.failIfNoSpecifiedTests=false -DforkCount=2 -DreuseForks=true"`.
  3. `node scripts/verify.mjs`; fail ⇒ `tail -n 200 target/verify.log`; upload artifact `api-verify-reports-<shard>`.
  4. Job `api`: `needs: [plan, api-test]`, `if: needs.plan.outputs.api_needed == 'true' && PUSH_IMAGE`, chỉ buildx + login + build-push (giữ tag/label/cache như cũ).
  5. `deploy`, `deploy-dev`: `needs` thêm `api-test`; điều kiện `!failure() && !cancelled()` giữ nguyên.
- Tests: AC-1 (đếm `Tests run` tổng 4 shard so với run tuần tự của `dev` trên cùng commit), AC-2 (đọc điều kiện `needs`), AC-3/AC-5 trên PR #88.
- Exit: run PR #88 xanh, tổng test = run `dev` gần nhất cùng nội dung `src/`, skip 0; thời gian shard chậm nhất ghi vào Progress log.
- **Dừng chờ user review.**

## Test matrix
| AC | Kiểm | Phase | Kết quả |
| --- | --- | --- | --- |
| AC-1 | Tổng `Tests run` 4 artifact = run tuần tự | 1 | |
| AC-2 | Đọc `needs`/`if` trong `deploy.yml`; shard fail ⇒ `api` skipped | 1 | |
| AC-3 | Thời gian shard chậm nhất trên PR #88 | 1 | |
| AC-4 | Run PR #88: image push sau 4 shard xanh; deploy chỉ chạy trên push | 1 | |
| AC-5 | 4 artifact tải được | 1 | |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Class test bị bỏ sót khi chia (tên không khớp mẫu) | Test không chạy, lọt lỗi | Lọc theo đúng mẫu surefire mặc định; AC-1 so tổng test | Revert commit `deploy.yml` |
| 2 fork + container trong 1 máy hết RAM/CPU | Test timeout, chậm hơn | Đo trên PR; hạ `forkCount=1` nếu chậm/flaky | Bỏ `-DforkCount` |
| Test phụ thuộc thứ tự chạy | Fail khi đổi nhóm/thứ tự | Mỗi class đã có DB riêng; fail ⇒ ghi class, chạy riêng để xác định | Revert |
| Shard lệch thời gian | Không đạt AC-3 | Cân lại theo artifact surefire | — |

## Validation log
- [x] Khẳng định về code có `file:line` hoặc lệnh/run id
- [x] Đổi contract ⇒ không đổi contract
- [x] Mỗi AC có cách kiểm; phase có Exit
- [x] 1 file, 1 phase
- [x] Không dùng rule PENDING/OPEN
- [x] Tự review: không placeholder · không mâu thuẫn · scope 1 plan

## Progress log
### 2026-11-20 — Viết plan (chờ duyệt)
- Đã có trên nhánh: `07d1e530` (PR push image, artifact log), `fee5576c` (revert container dùng chung). PR #88 draft.
- Tiếp: user duyệt plan ⇒ Phase 1.

### 2026-11-20 — Duyệt plan, Phase 1 (chờ CI)
- User duyệt plan (hướng A, "làm luôn") 2026-11-20.
- Kiểm cục bộ: mô phỏng bước chọn class cho 4 shard ⇒ 270/270 class, không trùng; `ChildEnrollmentImportMealColumnTest$*` thêm cho `@Nested`; YAML parse được. Chưa có actionlint.
- Tiếp: đo trên PR #88 (AC-1…AC-5).
