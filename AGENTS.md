# CareNest Backend — hướng dẫn cho coding agent

Repo này là **source of truth** của CareNest: business rule, domain, kiến trúc, API contract, database và engineering memory. Web (`CareNest_FE`) và Mobile (`CareNest_APP`) là client.

## Bắt đầu mọi task

1. Phân loại task bằng `.ai/ROUTER.md` → profile, workflow, mục guide, mức context (L1–L4).
2. Tìm module bằng `.ai/CONTEXT_MAP.yaml` (`keywords`) → đọc **module card** `docs/modules/<module>.md`. Chỉ mở flow/ADR/doc khác khi card hoặc `.ai/ESCALATION.md` yêu cầu.
3. **Bắt buộc** trước khi viết hoặc review code: đọc `docs/backend-coding-guide.md` (ít nhất các mục router chỉ tới). Không tự đặt convention ngoài guide; source đã implement là chuẩn.
4. Bug/lỗi/case lạ: **search `docs/knowledge/ISSUE_INDEX.md` trước** (chuỗi lỗi, module, từ khóa). Incident cũ là manh mối, phải kiểm chứng với code hiện tại.
5. Trước khi báo xong: chạy Checklist mục 17 của guide + `docs/quality/DEFINITION_OF_DONE.md` (gồm cập nhật engineering memory); báo mục nào không áp dụng hoặc chưa đạt.

## Đọc tiết kiệm token

- **`.ai/CONTEXT_MAP.yaml`: grep, không đọc cả file** — `grep -iE "<từ khóa>" .ai/CONTEXT_MAP.yaml` để ra module/card. Chỉ mở cả file khi cần sửa map.
- **Coding guide** `docs/backend-coding-guide.md` (~9k token): chỉ đọc mục router chỉ tới — grep tiêu đề `^## <số>\.` rồi đọc mục đó; đọc cả file chỉ với profile `architecture`/`full`.
- **Engineering memory:** grep `docs/knowledge/ISSUE_INDEX.md` theo chuỗi lỗi/từ khóa; chỉ mở `incidents/<ID>-*.md` khi dòng index khớp. Ghi mới: 1 issue = 1 dòng ngắn trong index, chi tiết để trong file incident.
- Không đọc toàn bộ `docs/` trừ profile `full`. Mức đọc theo `.ai/ESCALATION.md`.

## Nguyên tắc bất biến

1. **Không tự chế business rule.** Rule có ID trong `docs/business/BUSINESS_RULES.md`. `PENDING` / `OPEN` ⇒ nêu khoảng trống, hỏi hoặc làm cấu hình được.
2. Ưu tiên khi mâu thuẫn: Confirmed requirement > Latest validated decision (guide, source, ADR ACCEPTED) > Instructor proposal > Previous assumption. Code ≠ docs ⇒ báo xung đột, xác minh intended behavior trước khi sửa.
3. **1 trường, 2 điểm trường** — không multi-school/multi-tenant. Mọi truy vấn dữ liệu trẻ/lớp kiểm tra quyền theo campus/class/child ở server.
4. **AI chỉ hỗ trợ:** AI tạo bản nháp, con người duyệt; nghiệp vụ chạy được khi AI tắt; AI không tạo dữ liệu dinh dưỡng nền, không chẩn đoán. Gọi AI chỉ qua `integration/`.
5. Không mở rộng ngoài V1 (`docs/context/PROJECT_CONTEXT.md` mục Exclusions).
6. Dữ liệu trẻ em, sức khỏe, tài khoản: tối thiểu quyền; không đưa dữ liệu thật hoặc secret vào prompt, log, test, commit.
7. Đổi API/auth ⇒ kiểm tra tác động FE/APP (`docs/system/CROSS_REPO_MAP.md`). Chỉ đọc repo FE/APP khi task có tác động liên repo; không tự sửa repo khác.
8. Lỗi không hiển nhiên hoặc phải thử >1 cách ⇒ ghi vào `docs/knowledge/` kể cả các cách đã thử thất bại.

## Git, commit và comment (bắt buộc)

Bản đầy đủ (đồng bộ BE/FE/APP): `CLAUDE.md` mục "Quy tắc chung CareNest" — Claude tự nạp; **Codex phải đọc mục đó trước khi chạy lệnh git hoặc commit.** Tối thiểu:

- Không tự commit/push/tạo-merge PR khi user chưa cho phép rõ trong tin nhắn hiện tại; không tự tạo branch mới khi chưa được cho phép (hỏi trước); không lệnh git phá hủy hay `--no-verify` khi chưa hỏi.
- Conventional Commits tiếng Anh, footer `Refs: <JIRA-KEY>` (chưa có key ⇒ hỏi, không bịa); không ghi tên/attribution công cụ AI.
- Hỏi trước khi đổi dependency, contract hoặc thứ ảnh hưởng cả nhóm; không sửa repo CareNest khác. Trả lời user bằng tiếng Việt.

## Stack

Java 21, Spring Boot 4.1.1, Maven (`mvnw`), PostgreSQL 17, Spring Data JPA, Flyway, springdoc. Auth chưa chốt. Chi tiết: guide mục 2; chạy/test: `README.md`.
