---
name: update-knowledge
description: Use immediately when the user gives new business information, confirms or changes a PENDING/OPEN item, or when a new bug/error or surprising edge case is encountered in CareNest backend ("trường chốt", "đã xác nhận", "nghiệp vụ mới", "thay đổi quy định", "gặp lỗi mới", "bug mới", "lỗi lạ"). Records it in the project knowledge base so later agents do not re-ask or re-investigate.
---

# Update knowledge — CareNest_BE

1. Read `../../../AGENTS.md` (invariants) if not already in context.
2. Follow `../../../.ai/workflows/update-knowledge.md` (T1 business info, T2 new bug, T3 edge case). It is the source of truth; this skill does not redefine it.
3. Do it in the same turn the trigger appears; report which knowledge files were updated. Do not commit unless the user asks.
