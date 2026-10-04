# CareNest Backend — Claude Code

@AGENTS.md

Riêng Claude:
- `.claude/rules/` tự áp theo file đang sửa; skill `.claude/skills/<workflow>/` chỉ trỏ về `.ai/workflows/`.
- Stop hook `.claude/hooks/memory-reminder.mjs` nhắc cập nhật memory tối đa 1 lần/session (cần Node ≥ 18). Không cần ⇒ trả lời 1 dòng lý do.
