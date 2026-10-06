# Router — chọn context cho task Backend

Làn S (`AGENTS.md`) không cần đọc file này.

1. Xác định **loại task** → bảng 1 (profile, workflow, skill, mục guide, mức khởi đầu).
2. Xác định **module** → grep từ khóa trong `.ai/CONTEXT_MAP.yaml` mục `keywords` → đọc module card.
3. Đọc thêm chỉ khi `.ai/ESCALATION.md` yêu cầu.

"Mục guide" = số mục trong `docs/backend-coding-guide.md`. Mọi task có sửa code kết thúc bằng mục 17 (Checklist) + bằng chứng `docs/quality/VERIFICATION.md`.

## Bảng 1 — Loại task

| Task | Profile | Workflow | Skill | Mục guide | Mức đầu |
| --- | --- | --- | --- | --- | --- |
| Bug, lỗi, exception, test fail, hành vi sai | `profiles/code.md` | `workflows/fix-bug.md` | fix-bug | 4, 5, 11 + mục của vùng sửa | L1 |
| Refactor nhỏ, validation, sửa cục bộ | `profiles/code.md` | `workflows/implement-feature.md` (rút gọn) | implement-feature | 4, 5, 6 | L1 |
| Feature mới / đổi nghiệp vụ | `profiles/feature.md` | `workflows/clarify-business.md` → `workflows/implement-feature.md` | implement-feature | 3–11, 13 | L2 |
| Làn L: migration, security, integration, contract, dependency/hạ tầng, ≥2 module, FE/APP, >8 file | `profiles/architecture.md` | `workflows/clarify-business.md` → `workflows/plan-change.md` → workflow theo loại | plan-change | 4 + theo loại | L3 |
| Gate fail / sửa harness (verify, ArchUnit, snapshot, Spotless, hook, check-ai-layer) | `profiles/code.md` | `workflows/fix-bug.md` (fail) · `workflows/plan-change.md` (đổi harness) | fix-bug | 14, 17 | L1 |
| Review code / PR | `profiles/code.md` | `workflows/review-code.md` | review-code | 17 + mục bị vi phạm | L1→L2 |
| Đổi endpoint, DTO, auth contract, lỗi API | `profiles/feature.md` | `workflows/update-api.md` | update-api | 7, 8, 9, 11 | L2; L3 nếu client bị ảnh hưởng |
| Đổi entity, bảng, migration, query | `profiles/feature.md` | `workflows/update-database.md` | update-database | 12, 13 | L2 |
| Docker, CI/CD, env, VPS, deploy | `profiles/operations.md` | `workflows/deploy.md` | deploy | 2, 14 | L2 |
| Thay đổi chạm ≥2 module | `profiles/architecture.md` | theo loại thay đổi | — | 4 + theo loại | L3 |
| Thay đổi ảnh hưởng FE/APP | `profiles/cross-repo.md` | theo loại thay đổi | — | 7, 8 | L3 |
| Kiến trúc, package/layer, security/RBAC, campus scope, AI layer | `profiles/architecture.md` | theo loại thay đổi | — | toàn bộ | L4 |
| Thiết kế lại hệ thống, onboarding toàn bộ, audit lớn | `profiles/full.md` | — | — | toàn bộ | FULL |

## Bảng 2 — Tín hiệu nâng mức ngay

Chạm bất kỳ mục nào ⇒ ít nhất L3: số suất ăn / xác nhận suất · access scope / phân quyền · dữ liệu sức khỏe / dị ứng · AI output · hiển thị cho phụ huynh · service gọi chéo nhiều chức năng · API dùng bởi FE/APP.

Rule chưa CONFIRMED/ACCEPTED hoặc yêu cầu lệch tài liệu ⇒ `workflows/clarify-business.md` (hỏi tới khi rõ) trước khi code.

## Bảng 3 — Yêu cầu ngoài scope

Kho/NCC (OPEN — ADR-0009), tài sản/khấu hao/bảo trì, chat, giáo án, chẩn đoán, multi-school, payroll/kế toán ⇒ **dừng**, đối chiếu `docs/context/PROJECT_CONTEXT.md` (Exclusions) và hỏi người dùng.

## Cập nhật tri thức (song song với mọi task)

User đưa thông tin nghiệp vụ mới / chốt PENDING, gặp **bug mới** hoặc edge case ⇒ chạy `workflows/update-knowledge.md` (skill `update-knowledge`) **ngay trong lượt**, rồi tiếp tục task chính. Claude có Stop hook nhắc khi session có tín hiệu bug khó (verify fail rồi pass) mà chưa ghi memory (ADR-0012).
