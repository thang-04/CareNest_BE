# CareNest Backend — Claude Code

@AGENTS.md

Riêng Claude:
- Hook `.claude/settings.json` hỏi ⇒ trả lời 1 dòng đúng sự thật.
- Repo ưu tiên hơn skill: brainstorming chỉ làn L hoặc M có ≥2 phương án; câu hỏi gộp ≤5/vòng; spec vào plan; không commit; không `writing-plans`. Làn S bỏ brainstorming.
- Git/commit/PR theo AGENTS.md › "Quy tắc chung CareNest" (nhánh `<tiền-tố>/<mã-jira>-<mã-công-việc>-<tên-luồng>` tạo từ `dev`; commit và tiêu đề PR `[<mã-công-việc>] <mã-jira>: <mô tả>`); quy ước này ghi đè định dạng mặc định của skill/agent git (Conventional Commits, attribution).
