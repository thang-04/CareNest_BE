---
name: update-api
description: Use for CareNest backend endpoint, DTO, error code or auth contract changes ("thêm API", "sửa endpoint", "DTO", "response", "mã lỗi", "contract"). Checks impact on Web (CareNest_FE) and Mobile (CareNest_APP).
---

# Update API — CareNest_BE

1. Read `../../../AGENTS.md` (invariants) if not already in context.
2. Follow `../../../.ai/workflows/update-api.md`; rules in `../../../.claude/rules/controller.md`.
3. Find the controller with `grep <path> docs/api/API_INDEX.md` and the Web callers with `grep <path> docs/system/FE_API_CALLS.md` (never read `openapi.yaml` whole); conventions in `docs/system/CROSS_REPO_MAP.md`; use profile `../../../.ai/profiles/cross-repo.md` when FE/APP are affected.
