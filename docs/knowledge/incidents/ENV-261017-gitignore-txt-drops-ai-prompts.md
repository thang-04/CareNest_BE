---
id: ENV-261017-gitignore-txt-drops-ai-prompts
type: env
modules: [ai-assistance, learning-observation]
rules: [AI-06]
status: fixed
date: 2026-10-17
keywords: [gitignore, "*.txt", prompt, stash, worktree, PromptTemplateLoader, UncheckedIOException, Prompt template not found]
similar_to: [ENV-261016-flyway-duplicate-version-stale-target]
---

# ENV-261017-gitignore-txt-drops-ai-prompts — Prompt AI biến mất khi stash/commit vì `.gitignore` bỏ qua `*.txt`

## Symptom
Sau khi khôi phục stash nhánh AI sang worktree mới, `ExternalAiClientTest` fail 8 test (5 error):
```text
Expecting actual throwable to be an instance of: com.carenest.integration.ai.AiClientException
```
Ngoại lệ thật là lỗi đọc prompt của `PromptTemplateLoader` ("Prompt template not found: summary/draft_v2"), vì `src/main/resources/prompts/summary/*.txt` không có trong worktree.

## Điều kiện tái hiện
1. Tạo prompt `src/main/resources/prompts/**/<name>.txt`.
2. `git stash push -u` rồi `git stash apply` ở worktree/clone khác.
3. Hoặc commit rồi clone mới.

Ở cả hai cách, file prompt không đi theo.

## Attempts
| # | Cách thử | Kết quả | Vì sao không đúng / bài học |
| --- | --- | --- | --- |
| 1 | `git stash show --include-untracked --name-only` | Không có file `prompts/` | `stash -u` không lấy file bị ignore (phải `-a`) |
| 2 | `git check-ignore -v .../draft_v2.txt` | `.gitignore:28:*.txt` | Luật log `*.txt` vô tình ignore prompt |

## Root cause
`.gitignore` có `*.txt` (nhóm Logs). Prompt AI lưu dạng `.txt` nên bị ignore: không vào stash `-u`, không vào commit. Máy chỉ có file vì nó được tạo tại chỗ.

## Fix
Thêm ngoại lệ `!src/main/resources/prompts/**/*.txt` vào `.gitignore`. Chép lại `draft_v1.txt` và `draft_v2.txt` từ worktree cũ (vẫn còn vì file ignore không bị stash xóa).

## Regression test
`ExternalAiClientTest` (dùng `PromptTemplateLoader` thật) pass sau fix: `node scripts/verify.mjs` → `VERIFY PASS full | tests 256`.

## Ảnh hưởng
Chỉ BE. Nếu commit thiếu prompt, môi trường khác chạy AI sẽ lỗi `Prompt template not found`. Luồng nghiệp vụ vẫn chạy vì chuyển sang template, nhưng AI không bao giờ hoạt động.

## Lesson
Tài nguyên runtime (prompt, template) có đuôi trùng luật ignore chung phải có ngoại lệ rõ ràng. Trước khi stash hoặc commit file mới, chạy `git check-ignore -v`.
