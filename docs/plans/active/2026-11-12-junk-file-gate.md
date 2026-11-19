---
title: Cổng file rác — chặn file tạm lọt vào commit/PR, nhắc bổ sung .gitignore
status: in-progress
owner: thangnd
jira: G94-254
branch: feature/G94-254-ai-harness-impact-gate
modules: [cross-repo]
rules: []
risk: medium
created: 2026-11-12
updated: 2026-11-12
---
# Cổng file rác — chặn file tạm lọt vào commit/PR, nhắc bổ sung .gitignore

Plan liên repo (BE là nguồn harness; FE/APP nhận qua `scripts/ai-layer/sync-shared.mjs`).

## Context
- Rà ngày 2026-11-12: `.tmp_doc_figma/` (300 file, 68MB) đang được track trên `dev`/`main` của BE dù `.gitignore` đã ghi `.tmp_doc_*/` — file commit trước khi có rule ignore, git không tự gỡ.
- FE có log cũ, `__pycache__/`, thư mục `ui-review/`, `plans/` untracked chưa ignore.
- `.githooks/pre-commit` chặn secret/migration/conflict marker nhưng chưa kiểm file rác; repo chưa có CI kiểm PR (`.githooks/pre-push:3`).

## Quyết định đã chốt
1. File lạ chưa track, chưa ignore: **chỉ cảnh báo**, không chặn — user, 2026-11-12.
2. Làm ở worktree harness (`CareNest_*-harness`, nhánh `feature/G94-254-ai-harness-impact-gate`) — user, 2026-11-12.
3. Đồng bộ cả ba repo BE/FE/APP — user, 2026-11-12.
4. Mã Jira G94-254 — user, 2026-11-12.

## Làm rõ nghiệp vụ
Không đổi nghiệp vụ; chỉ đổi harness.

### Câu hỏi mở
Không có.

## Acceptance criteria
- **AC-1**: Given file đang track khớp `.gitignore`, When chạy `junk-files.mjs` (toàn cây), Then exit 1 và nêu cách gỡ.
- **AC-2**: Given file mới khớp mẫu rác (`junk.globs`) hoặc nặng hơn `junk.maxBytes`, When commit, Then pre-commit chặn; file khớp `junk.allow` đi qua.
- **AC-3**: Given commit có file rác tạo bằng `--no-verify`, When push, Then pre-push chặn.
- **AC-4**: Given file/thư mục untracked chưa ignore ở gốc repo hoặc khớp mẫu rác, When commit hoặc verify, Then in `[JUNK] WARN` nhắc commit hoặc thêm `.gitignore`, không chặn.
- **AC-5**: Given file khớp mẫu rác đã có sẵn trên `origin/dev`, When verify trên nhánh task, Then không báo lỗi (chỉ xét file nhánh thêm).
- **AC-6**: `node scripts/verify.mjs` fail khi cổng file rác có lỗi.

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
| Nơi đặt luật | 1 script `scripts/ai-layer/junk-files.mjs`, gọi từ pre-commit, pre-push, verify | Chỉ thêm vào pre-commit; GitHub Action | Pre-commit đơn lẻ không bắt file đã track và bị `--no-verify` lách; repo chưa có CI kiểm PR, sau này Action gọi lại đúng script này |
| Nguồn định nghĩa "rác" | `.gitignore` (file track mà bị ignore = lỗi) + danh sách mẫu trong `DEFAULTS.junk` | Danh sách riêng hoàn toàn | `.gitignore` là nguồn sẵn có, không phải duy trì hai nơi; mẫu bổ sung bắt file chưa ai kịp ignore |
| Phạm vi luật mẫu rác/dung lượng | Chỉ file nhánh thêm so với `origin/dev` | Mọi file đang track | Không làm đỏ verify vì file cũ hợp lệ (vd. ảnh mockup FE 1,7MB) |
| Ngưỡng dung lượng | 5MB | 2MB | FE đang có ảnh mockup hợp lệ 1,3–1,7MB; 5MB vẫn bắt được file nén, bản build, dump |

## Phases
### Phase 1 — Script, hook, verify, dọn file đã track (BE là nguồn) rồi đồng bộ FE/APP
- Files (BE): C `scripts/ai-layer/junk-files.mjs` · C `scripts/__tests__/junk-files.test.mjs` · M `scripts/ai-layer/lib.mjs` · M `.githooks/pre-commit` · M `.githooks/pre-push` · M `scripts/verify.mjs` · M `.claude/hooks/harness.config.json` · M `.gitignore` · M `AGENTS.md` · M `docs/quality/VERIFICATION.md` · D `.tmp_doc_figma/**`
- Files (FE, APP): file dùng chung qua `sync-shared.mjs` · M `scripts/verify.mjs` · M `.gitignore` · M `docs/quality/VERIFICATION.md`
- Tests: AC-1..AC-5 → `scripts/__tests__/junk-files.test.mjs`
- Exit: `node --test scripts/__tests__/` pass ở 3 repo; `node scripts/check-ai-layer.mjs` exit 0; `node scripts/ai-layer/junk-files.mjs` exit 0 ở 3 repo.

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |
| AC-1, AC-5 | — | `junk-files.test.mjs` › "tree: file track mà bị ignore…" (repo git tạm) | 1 | xem Progress log |
| AC-2 | — | `junk-files.test.mjs` › "staged: …", "pre-commit: …" (hook thật) | 1 | xem Progress log |
| AC-3 | — | `junk-files.test.mjs` › "pushed: …", "pre-push: …" (hook thật) | 1 | xem Progress log |
| AC-4 | — | `junk-files.test.mjs` › "tree: file untracked…", "pre-commit: …" | 1 | xem Progress log |
| AC-6 | — | Chạy tay `node scripts/verify.mjs` | 1 | xem Progress log |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |
| Mẫu rác chặn nhầm file hợp lệ (vd. `.zip` fixture, `.patch` có chủ đích) | Không commit được | `junk.allow` trong `harness.config.json` của repo | Revert commit harness |
| Nhánh cũ chưa merge `dev` còn track `.tmp_doc_figma/` | Không ảnh hưởng: script và việc gỡ thư mục đi cùng một lần merge | — | — |
| `.specify/memory/constitution.md` (BE) đang track và bị ignore có chủ đích | Luật AC-1 báo lỗi | Đã thêm vào `junk.allow` của BE | — |

## Validation log
- [x] Khẳng định về code có `file:line`
- [x] Đổi contract ⇒ consumer cụ thể (không đổi contract)
- [x] Mỗi AC có test; mỗi phase có Exit = lệnh + kết quả mong đợi
- [x] >8 file hoặc >3 phase ⇒ đã đề xuất tách plan (1 phase; phần lớn file là bản đồng bộ)
- [x] Không dùng rule PENDING/OPEN như đã chốt
- [x] Tự review: không placeholder · không mâu thuẫn · scope vừa 1 plan · không câu mơ hồ

## Progress log
### 2026-11-12 — Phase 1 (chờ review)
- Commit: chưa commit
- Test harness: `node --test scripts/__tests__/*.test.mjs .claude/hooks/__tests__/*.test.mjs` → BE 74/74, FE 80/80, APP 78/78 pass (gồm 7 test mới của `junk-files.test.mjs`).
- Verify BE: `node scripts/verify.mjs --quick` → `VERIFY PASS quick | tests 14 fail 0 err 0 skip 0 | archunit ok | snapshot - | spotless ok | 94s | log target/verify.log`
- Verify FE: `node scripts/verify.mjs --quick` → `VERIFY PASS quick | lint + format:check + check:design + check:api | không build, không chạy bộ test khác`
- Verify APP: `node scripts/verify.mjs` → `VERIFY FAIL full | format ok | analyze FAIL | tests - | 13s | log verify.log` — CHƯA kiểm chứng phần Dart: worktree chưa có `.dart_tool`, `flutter pub get` fail vì pubspec đòi Flutter SDK 3.50.0 khác bản cài trên máy. Không liên quan thay đổi này (không sửa file Dart); cổng file rác ở APP exit 0.
- AC-6: stage thử `dump.zip` ở BE rồi chạy `node scripts/verify.mjs --quick` → in `[JUNK] ERROR dump.zip …` và `VERIFY FAIL junk | 1 file rác trong nhánh`, exit 1; đã gỡ file thử.
- `node scripts/check-ai-layer.mjs --quiet` exit 0 ở 3 repo; `sync-shared.mjs --check` BE→FE, BE→APP: 0 file lệch.
- `.tmp_doc_figma/` (300 file) đã `git rm` ở BE-harness, chờ commit.
- Tiếp: user soát rồi cho phép commit (mỗi repo 1 commit `G94-254: …`); cài đúng Flutter SDK để chạy lại verify APP.
- Giả định tự chốt (chế độ tự chạy): ngưỡng 5MB thay vì 2MB đã nêu khi đề xuất; giữ nguyên rule `*.txt` trong `.gitignore` của BE (thu hẹp sẽ làm lộ file ghi chú cá nhân đang bị ẩn — cần user quyết); thêm `.specify/memory/constitution.md` vào `junk.allow`.
