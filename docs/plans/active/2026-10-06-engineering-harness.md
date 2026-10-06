---
title: Engineering harness — làn S/M/L, cổng làm rõ nghiệp vụ, Iron Law, hook, cổng Maven
status: in-progress
owner: thangnd
jira: none
branch: main
modules: []
rules: [NFR-MAINT-01, NFR-MAINT-02, NFR-MAINT-03]
risk: high
created: 2026-10-06
updated: 2026-10-06
---
# Engineering harness

Plan liên repo (BE + FE + APP). Quyết định: `docs/decisions/ADR-0012-engineering-harness.md`.

## Context
Lớp AI giúp tìm đúng chỗ nhưng chưa chặn sai: cổng chỉ là chữ (không CI/git hook/format), Stop hook cũ (memory-reminder, đã thay bằng stop-gate) chỉ nhắc và xét cả working tree, plan/review/fix chưa chuẩn, quy tắc chung + hook + skill nhân bản trôi lệch giữa 3 repo, `docs/api/` trống. Task nhỏ không được tốn token như task lớn.

## Quyết định đã chốt
1. BE sửa ở worktree `main` (`CareNest_BE-main`); không tạo branch; không đụng `feature/authentication`. FE/APP trên `main` — user, 2026-10-06.
2. BE đủ; FE/APP nhẹ (quy trình + hook/script dùng chung, config riêng) — user, 2026-10-06.
3. Cưỡng chế: Claude hooks, git hooks, check-ai-layer; không CI, không PR template, không JaCoCo — user, 2026-10-06.
4. Maven: ArchUnit, OpenAPI snapshot, Spotless (palantir 2.98.0, ratchet origin/main) — user, 2026-10-06.
5. Làn S/M/L tối ưu token; cổng làm rõ nghiệp vụ hỏi tới khi rõ; Superpowers brainstorming chỉ cho làn L/M có ≥2 phương án, spec ghi vào plan — user, 2026-10-06.
6. Không sửa `README.md` (user đang có bản rewrite chưa commit); không commit khi chưa được yêu cầu — user, 2026-10-06.

## Làm rõ nghiệp vụ
Không đổi nghiệp vụ (thay đổi quy trình/hạ tầng dev).

### Câu hỏi mở
- [x] Phạm vi, lớp cưỡng chế, công cụ Maven, làn, cổng nghiệp vụ — đã chốt ở trên.

## Acceptance criteria
- **AC-1**: số từ `AGENTS.md` + `CLAUDE.md` mỗi repo không tăng so với trước (BE 1057, FE 1128, APP 1190).
- **AC-2**: controller inject repository ⇒ `ArchitectureRulesTest` fail.
- **AC-3**: API đổi mà chưa cập nhật snapshot ⇒ `OpenApiSnapshotTest` fail kèm diff.
- **AC-4**: file Java đụng tới sai format ⇒ `verify` fail.
- **AC-5**: hook: đọc `.env` ⇒ deny; sửa `pom.xml` ⇒ ask; migration mới không có plan ⇒ ask; `git commit --no-verify` ⇒ deny; sửa code rồi dừng khi chưa verify ⇒ block 1 lần; repo sạch ⇒ orient không in.
- **AC-6**: plan `approved` còn `- [ ]` trong "Câu hỏi mở" ⇒ `check-ai-layer` lỗi.
- **AC-7**: file harness dùng chung giống hệt ở 3 repo; khối "Quy tắc chung" không lệch.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Quy trình theo độ lớn task | Làn S/M/L tính từ diff | Một quy trình cho mọi task | Tiết kiệm token ở task nhỏ |
| Output verify | `scripts/verify.mjs` tóm tắt ≤10 dòng | Đọc output Maven | Vài nghìn dòng → ~100 token |
| ArchUnit artifact | `archunit-junit6` 1.5.1 | `archunit-junit5` | Boot 4.1.1 dùng JUnit 6 |
| Spotless | 3.10.3 + ratchet | Format toàn repo | Worktree support; tránh diff lớn |

## Phases
1. BE quy trình theo làn (docs) — Exit: AC-1, skills mirror rỗng.
2. BE `scripts/check-ai-layer.mjs` + test — Exit: test xanh, 0 lỗi.
3. BE cổng Maven + `scripts/verify.mjs` — Exit: `VERIFY PASS full` skip 0; chứng minh ngược AC-2..4.
4. BE Claude hooks — Exit: `node --test` xanh; AC-5 bằng payload mẫu.
5. BE git hooks — Exit: test trên repo tạm.
6. FE + APP — Exit: AC-1, AC-7.
Mỗi phase **dừng chờ user review**.

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Hook chặn sai | Agent bị kẹt | Fail-open, chặn 1 lần, `CARENEST_HARNESS=off` | Xóa đăng ký trong `.claude/settings.json` |
| Spotless + CRLF | verify fail | `.gitattributes` LF, ratchet | Bỏ execution spotless trong pom |
| Snapshot không ổn định | Fail ngẫu nhiên | Sort key, prefix cố định | `-Dopenapi.snapshot.update=true` |

## Validation log
- [x] Claim về code có nguồn (pom, test hiện có, CONTEXT_MAP)
- [x] >3 phase: giữ 1 plan vì cùng mục tiêu, user đã duyệt bản đầy đủ
- [x] Không dùng rule PENDING/OPEN

## Progress log
### 2026-10-06 — Plan
- User duyệt plan đầy đủ (plan mode). Bắt đầu Phase 1.

### 2026-10-06 — Phase 1 (chờ review)
- Commit: chưa commit
- Mới: `.gitattributes`, `.editorconfig`, `docs/plans/_TEMPLATE.md`, `.ai/workflows/clarify-business.md`, `.ai/workflows/plan-change.md`, skill `plan-change` (+ mirror), `docs/quality/VERIFICATION.md`, ADR-0012, plan này.
- Sửa: AGENTS (bảng làn, bất biến 1, `### Cổng chất lượng`), CLAUDE (override Superpowers), ROUTER, ESCALATION, CONTEXT_MAP, 6 workflow, DoD theo làn, TEST_STRATEGY, README_AI mục 9, INDEX, guide §3/§14/§17, MODULE_MAP, ADR-0001, rules controller/testing.
- Verify: `cat AGENTS.md CLAUDE.md | wc -w` → 1056 (trước 1057) — AC-1 đạt; `diff -r .agents/skills .claude/skills` → rỗng; kiểm path tạm thời → chỉ false positive (tên package).
- Khác plan: không có.
- Tiếp: Phase 2 `scripts/check-ai-layer.mjs`.

### 2026-10-06 — Phase 2 (chờ review)
- Commit: chưa commit
- Mới: `scripts/check-ai-layer.mjs` (CLI + `runChecks()`), `scripts/ai-layer/{lib,context-map,docs-checks,knowledge-checks,cross-repo}.mjs` (683 dòng, zero-dep), `.claude/hooks/harness.config.json`, test `scripts/__tests__/{helpers,ai-layer-lib.test,check-ai-layer.test}.mjs`. `lib.mjs` để hook Phase 4 dùng lại.
- Verify: `node --test "scripts/__tests__/*.test.mjs"` → tests 20, pass 20, fail 0. `node scripts/check-ai-layer.mjs` → `0 lỗi, 11 cảnh báo` (đều là path planned: `scripts/verify.mjs`, `docs/api/openapi.yaml` — Phase 3), 0,2s.
- Chạy thử chỉ đọc trên repo khác: FE 0/0, APP 0/0; `CareNest_BE` (feature) bắt đúng lệch đã biết — plan Keycloak thiếu frontmatter (lỗi), 3 file skeleton_only đã có nội dung, `docs/api/` planned nhưng đã có, ID `ENV-001` kiểu cũ (cảnh báo) ⇒ xử lý khi merge main.
- Khác plan: thêm bỏ qua path bị gitignore (vd. `.claude/settings.local.json`); path thuộc `planned` của CONTEXT_MAP chỉ cảnh báo, không lỗi; `runChecks` nhận `overrides` để kiểm repo khác/test.
- Tiếp: Phase 3 cổng Maven + `scripts/verify.mjs`.

### 2026-10-06 — Phase 3 (chờ review)
- Commit: chưa commit
- Baseline trước khi sửa: `./mvnw -B verify` → `Tests run: 12 … Skipped: 0`, BUILD SUCCESS.
- Mới: `pom.xml` (+`archunit-junit6` 1.5.1 test, plugin `spotless-maven-plugin` 3.10.3 + palantir 2.98.0, `ratchetFrom origin/main`, check ở phase verify); `ArchitectureRulesTest` (11 rule); `OpenApiSnapshotTest` + `docs/api/openapi.yaml` (main chưa có endpoint nghiệp vụ ⇒ `paths: {}`); `scripts/verify.mjs` (full/`--quick`, log ra file, tóm tắt ≤10 dòng); test `scripts/__tests__/verify.test.mjs`.
- Verify: `node scripts/verify.mjs` → `VERIFY PASS full | tests 24 fail 0 err 0 skip 0 | archunit ok | snapshot ok | spotless ok | 33s`. `node --test "scripts/__tests__/*.test.mjs"` → 30/30 pass. `node scripts/check-ai-layer.mjs` → 0 lỗi, 0 cảnh báo.
- Chứng minh ngược (đều đã khôi phục, `git status src/main` sạch):
  - AC-2: service tạm dùng `HttpStatus` ⇒ `VERIFY FAIL … archunit FAIL` + `TmpService.java:7`.
  - AC-3: đổi mô tả `OpenApiConfig` ⇒ `VERIFY FAIL … snapshot FAIL` (`OpenApiSnapshotTest…:68`).
  - AC-4: làm lệch format `OpenApiSnapshotTest.java` ⇒ `VERIFY FAIL … spotless FAIL` + tên file.
- Khác plan:
  - Gọi `mvnw.cmd` bằng đường dẫn tuyệt đối (môi trường không tìm .cmd trong thư mục hiện tại) và một chuỗi lệnh (tránh cảnh báo DEP0190).
  - Thêm `MAVEN_OPTS=-Dstdout.encoding=UTF-8` khi chạy để log tiếng Việt không lỗi font.
  - Mode `--quick` tự chuyển full khi đổi `pom.xml`/resources; class không map được test ⇒ `quick-all` (chạy toàn bộ test).
  - Palantir chạy được trên JDK 22, không cần `.mvn/jvm.config`.
- Tiếp: Phase 4 Claude hooks.

### 2026-10-06 — Phase 4 (chờ review)
- Commit: chưa commit
- Mới: `.claude/hooks/{session-orient,guard-edits,track-activity,stop-gate}.mjs`, `lib/{hook-io,session,commands,verify-events}.mjs` (704 dòng, zero-dep, dùng chung `scripts/ai-layer/lib.mjs`); `.claude/settings.json` (5 event, exec form `node` + `args`, `attribution` tắt); config hook trong `harness.config.json`; test `.claude/hooks/__tests__/{units,hooks}.test.mjs`. Xóa `memory-reminder.mjs` (stop-gate thay, nhắc memory chỉ khi verify fail rồi pass).
- Verify: `node --test ".claude/hooks/__tests__/*.test.mjs" "scripts/__tests__/*.test.mjs"` → 47/47 pass. Smoke trên repo thật (payload qua `JSON.stringify`): `.env` deny, `.env.example` im lặng, `pom.xml`/repo khác/file hook ⇒ ask, scratchpad im lặng, path Git Bash `/d/...` đúng; orient in đúng 1 dòng plan đang làm. `check-ai-layer` 0/0.
- Đã xác minh từ docs Claude Code: PostToolUse chạy cả khi Bash exit ≠ 0, có PostToolUseFailure (`error`), Stop có `last_assistant_message` (không có `stop_hook_active` ⇒ dùng once-key + van 6 lần chặn/session), exec form `args` cần `.exe` thật (`node` OK), tool `PowerShell`.
- Khác plan: once-key của verify gồm cả lý do (test phát hiện: quick ⇒ full có skip bị nuốt lần chặn); tool Bash của môi trường nuốt dấu `\` nên smoke dùng script Node.
- AC-5: đạt (deny/ask/block 1 lần/orient im lặng khi sạch — có test tự động).
- Tiếp: Phase 5 git hooks.

### 2026-10-06 — Phase 5 (chờ review)
- Commit: chưa commit. Khi commit: `git add --chmod=+x .githooks/commit-msg .githooks/pre-commit .githooks/pre-push` (Windows không lưu exec bit).
- Mới: `.githooks/{lib.sh,commit-msg,pre-commit,pre-push}` (POSIX sh), test `scripts/__tests__/githooks.test.mjs` (repo tạm + bare remote).
- Verify: `node --test ".claude/hooks/__tests__/*.test.mjs" "scripts/__tests__/*.test.mjs"` → 53/53 pass; `sh .githooks/pre-commit` trên repo thật 0,4s; `check-ai-layer` 0/0.
- Hành vi: commit-msg chặn sai Conventional Commits/tiếng Việt/>72/dấu chấm/Refs sai/AI attribution, thiếu Refs chỉ cảnh báo; pre-commit chặn `.env`/khóa, secret dạng literal (bỏ qua `${...}`, placeholder, `carenest:allow-secret`), sửa migration đã commit (trừ `CARENEST_ALLOW_MIGRATION_EDIT=1`), trùng version, conflict marker, lỗi check-ai-layer; pre-push kiểm lại message (bắt commit `--no-verify`), chặn force push (trừ `CARENEST_ALLOW_FORCE_PUSH=1`), opt-in `git config carenest.verifyOnPush true`.
- Khác plan: bỏ kiểm `=======` (trùng heading Markdown setext).
- Chưa làm (cần user): `git config core.hooksPath .githooks` trong CareNest_BE.
- Tiếp: Phase 6 FE + APP.

### 2026-10-06 — Phase 6 (chờ review)
- Commit: chưa commit (FE, APP trên `main`; khi commit dùng `git add --chmod=+x` cho `.githooks/*`).
- FE + APP: chép y nguyên hook/lib/test, `settings.json`, `.githooks/`, `scripts/`, `.gitattributes`, `.editorconfig`, `docs/plans/_TEMPLATE.md`; xóa `memory-reminder.mjs`; config riêng `harness.config.json` (verify bật khi có `package.json`, dependency npm/native, ID incident `FE-`/`APP-`); `.gitignore` mới (APP thêm mobile).
- Docs: AGENTS bảng làn bản client + `### Cổng chất lượng` giống hệt BE; CLAUDE override Superpowers; `clarify-business.md`, `plan-change.md`, `VERIFICATION.md` bản client; skill `plan-change` (+ mirror); ROUTER (2 dòng mới, bỏ memory-reminder), CONTEXT_MAP, workflow implement-feature/fix-bug/review-code/integrate-api (trỏ snapshot `BE:docs/api/openapi.yaml`), DoD theo làn, INDEX; `docs/plans/{active,completed}/`.
- Verify: `node --test` FE 53/53, APP 53/53; `node scripts/verify.mjs` → `VERIFY SKIP` (chưa có code, đúng thiết kế); `check-ai-layer --cross-repo` BE/FE/APP đều 0 lỗi 0 cảnh báo (E6 ok: file dùng chung + khối quy tắc chung khớp).
- Từ AGENTS+CLAUDE (AC-1): BE 1057→1056, FE 1173→1171, APP 1239→1236 (so HEAD).
- Khác plan: không có. Phát hiện nhờ checker: tham chiếu cũ `memory-reminder.mjs` trong ROUTER/CLAUDE/CONTEXT_MAP FE/APP — đã sửa.
- Tiếp: user review toàn bộ; commit 3 repo khi user cho phép; user tự `git config core.hooksPath .githooks` ở mỗi repo. Sau đó plan ⇒ `done`, chuyển `completed/`.
