---
title: <Tiêu đề>
status: draft        # draft → approved (chỉ user duyệt) → in-progress → done | blocked | cancelled
owner: <người chịu trách nhiệm>
jira: none           # CN-123 hoặc none (user xác nhận)
branch: <branch làm việc>   # plan gate so với branch hiện tại
modules: []          # module id trong .ai/CONTEXT_MAP.yaml
rules: []            # rule ID dùng trong plan
risk: high           # high | medium — lý do ở "Rủi ro & rollback"
created: YYYY-MM-DD
updated: YYYY-MM-DD
---
# <Tiêu đề>

Chỉ dùng cho task làn L (`.ai/workflows/plan-change.md`). Plan liên repo đặt ở `BE:docs/plans/active/`; plan FE/APP chỉ phần client + link plan BE.

## Context
Vấn đề, vì sao làm bây giờ, hiện trạng code (`file:line`).

## Quyết định đã chốt
1. <quyết định> — <ai, ngày>. Không đảo khi chưa hỏi user.

## Làm rõ nghiệp vụ
Theo `.ai/workflows/clarify-business.md`.
- **Hiểu nghiệp vụ:** actor · outcome · rule ID + status · flow `[B]`/`[S]`
- **Đối chiếu thiết kế ban đầu:** tài liệu ghi gì (`file:line`) · yêu cầu khác ở đâu
- **Ảnh hưởng:** module (MODULE_MAP) · quyền/scope · dữ liệu dẫn xuất (CMR) · FE/APP cụ thể (`FE: <route>`, `APP: <màn hình>`) · dữ liệu cũ

### Câu hỏi mở
Còn `- [ ]` ⇒ không được `approved` (check-ai-layer E5).
- [ ] <câu hỏi nghiệp vụ>

| Câu hỏi | Trả lời | Ai | Ngày | Rule/doc đã cập nhật |
| --- | --- | --- | --- | --- |

## Acceptance criteria
- **AC-1** (`RULE-ID`): Given … When … Then …

## Key decisions
| Quyết định | Chọn | Phương án khác | Vì sao |
| --- | --- | --- | --- |
Quyết định kiến trúc ⇒ ADR mới.

## Phases
### Phase 1 — <mục tiêu>
- Files: C `path` · M `path` · D `path`
- Steps: 1. … 2. …
- Tests: AC-1 → `XxxTest#method` (`@DisplayName("RULE-ID: …")`)
- Exit: `node scripts/verify.mjs` → `VERIFY PASS full`, skip 0
- **Dừng chờ user review.**

## Test matrix
| AC | Rule | Test (tầng) | Phase | Kết quả |
| --- | --- | --- | --- | --- |

## Rủi ro & rollback
| Rủi ro | Tác động | Giảm thiểu | Rollback |
| --- | --- | --- | --- |

## Validation log
- [ ] Khẳng định về code có `file:line`
- [ ] Đổi contract ⇒ consumer cụ thể (cấm "mọi caller")
- [ ] Mỗi AC có test; mỗi phase có Exit = lệnh + kết quả mong đợi
- [ ] >8 file hoặc >3 phase ⇒ đã đề xuất tách plan
- [ ] Không dùng rule PENDING/OPEN như đã chốt
- [ ] Tự review: không placeholder · không mâu thuẫn · scope vừa 1 plan · không câu mơ hồ

## Progress log
### YYYY-MM-DD — Phase n (chờ review | done)
- Commit: `<sha>` | chưa commit
- Verify: `<lệnh>` → `<dòng VERIFY nguyên văn>`
- Khác plan: … (lý do)
- Tiếp: …
