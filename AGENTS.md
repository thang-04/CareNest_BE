# CareNest Backend — hướng dẫn cho coding agent

Repo này là **source of truth** của CareNest: business rule, domain, kiến trúc, API contract, database và engineering memory. Web (`CareNest_FE`) và Mobile (`CareNest_APP`) là client.

## Bắt đầu mọi task

1. Phân loại task bằng `.ai/ROUTER.md` → profile, workflow, mục guide, mức context (L1–L4).
2. Tìm module bằng `.ai/CONTEXT_MAP.yaml` (`keywords`) → đọc **module card** `docs/modules/<module>.md`. Chỉ mở flow/ADR/doc khác khi card hoặc `.ai/ESCALATION.md` yêu cầu.
3. **Bắt buộc** trước khi viết hoặc review code: đọc `docs/backend-coding-guide.md` (ít nhất các mục router chỉ tới). Không tự đặt convention ngoài guide; source đã implement là chuẩn.
4. Bug/lỗi/case lạ: **search `docs/knowledge/ISSUE_INDEX.md` trước** (chuỗi lỗi, module, từ khóa). Incident cũ là manh mối, phải kiểm chứng với code hiện tại.
5. Trước khi báo xong: chạy Checklist mục 17 của guide + `docs/quality/DEFINITION_OF_DONE.md` (gồm cập nhật engineering memory); báo mục nào không áp dụng hoặc chưa đạt.

**Tri thức mới** — user đưa nghiệp vụ mới / chốt PENDING, hoặc gặp **bug mới** / edge case ⇒ chạy `.ai/workflows/update-knowledge.md` ngay trong lượt (không đợi cuối task).

## Đọc tiết kiệm token

- **`.ai/CONTEXT_MAP.yaml`: grep, không đọc cả file** — `grep -iE "<từ khóa>" .ai/CONTEXT_MAP.yaml` để ra module/card. Chỉ mở cả file khi cần sửa map.
- **Coding guide** `docs/backend-coding-guide.md` (~9k token): chỉ đọc mục router chỉ tới — grep tiêu đề `^## <số>\.` rồi đọc mục đó; đọc cả file chỉ với profile `architecture`/`full`.
- **Engineering memory:** grep `docs/knowledge/ISSUE_INDEX.md` theo chuỗi lỗi/từ khóa; chỉ mở `incidents/<ID>-*.md` khi dòng index khớp. Ghi mới: 1 issue = 1 dòng ngắn trong index, chi tiết để trong file incident.
- **Coding rule theo loại file** ở `.claude/rules/<tên>.md` (frontmatter `paths`). Claude tự nạp; agent khác (Codex) tự mở rule khớp file đang sửa.
- `.agents/skills/` là bản mirror của `.claude/skills/` — sửa một bên thì chép y hệt sang bên kia.
- Không đọc toàn bộ `docs/` trừ profile `full`. Mức đọc theo `.ai/ESCALATION.md`.

## Nguyên tắc bất biến

1. **Không tự chế business rule.** Rule có ID trong `docs/business/BUSINESS_RULES.md`. `PENDING` / `OPEN` ⇒ nêu khoảng trống, hỏi hoặc làm cấu hình được.
2. Ưu tiên khi mâu thuẫn — nghiệp vụ: quyết định mới nhất có nguồn (team chốt, chỉ đạo giảng viên đã nhận) > khảo sát/Report 1 > đề xuất; code: source > guide > ADR ACCEPTED (chi tiết `PROJECT_CONTEXT.md`). Code ≠ docs ⇒ báo xung đột, xác minh intended behavior trước khi sửa.
3. **1 trường, 2 điểm trường** — không multi-school/multi-tenant. Mọi truy vấn dữ liệu trẻ/lớp kiểm tra quyền theo campus/class/child ở server.
4. **AI chỉ hỗ trợ:** AI tạo bản nháp, con người duyệt; nghiệp vụ chạy được khi AI tắt; AI không tạo dữ liệu dinh dưỡng nền, không chẩn đoán. Gọi AI chỉ qua `integration/`.
5. Không mở rộng ngoài V1 (`docs/context/PROJECT_CONTEXT.md` mục Exclusions).
6. Dữ liệu trẻ em, sức khỏe, tài khoản: tối thiểu quyền; không đưa dữ liệu thật hoặc secret vào prompt, log, test, commit.
7. Đổi API/auth ⇒ kiểm tra tác động FE/APP (`docs/system/CROSS_REPO_MAP.md`). Chỉ đọc repo FE/APP khi task có tác động liên repo; không tự sửa repo khác.
8. Lỗi không hiển nhiên hoặc phải thử >1 cách ⇒ ghi vào `docs/knowledge/` kể cả các cách đã thử thất bại.

## Stack

Java 21, Spring Boot 4.1.1, Maven (`mvnw`), PostgreSQL 17, Spring Data JPA, Flyway, springdoc. Auth chưa chốt. Chi tiết: guide mục 2; chạy/test: `README.md`.

## Quy tắc chung CareNest (bắt buộc)

Khối này giống nhau ở cả ba repo `CareNest_BE`, `CareNest_FE`, `CareNest_APP`; chỉ mục "Hỏi trước khi làm" và "Phạm vi" khác theo repo. Sửa ở một repo thì đồng bộ sang hai repo còn lại.

### Git — commit, push, pull request

- KHÔNG tự `git commit` / `git push` / tạo-merge-đóng PR / tạo branch khi user chưa cho phép rõ **trong tin nhắn hiện tại** (được phép một lần ≠ lần sau). Branch khi được phép: `feature/<KEY>-<mo-ta>`, `fix/<KEY>-<mo-ta>`, `chore/<mo-ta>`; làm trên branch khác `main` ⇒ hỏi trước.
- KHÔNG lệnh git phá hủy khi chưa hỏi (`reset --hard`, `push --force`, `rebase`, `branch -D`, `clean -fd`, `checkout -- .`, `restore .`, `stash drop`); KHÔNG `--no-verify`/bỏ qua hook.

### Commit message

- Conventional Commits tiếng Anh `<type>(<scope>): <subject>`, `type` ∈ `feat|fix|refactor|test|docs|chore|build|ci`; subject ≤72 ký tự, mệnh lệnh, không dấu chấm cuối; body tùy chọn ≤~5 gạch đầu dòng nói lý do/tác động (không liệt kê file, không kể quá trình). 1 commit = 1 thay đổi logic.
- **Jira:** user bảo commit mà chưa nêu task ⇒ hỏi "Thay đổi này thuộc task Jira nào (vd. `CN-123`)?". 1 commit = đúng 1 key ở footer `Refs: <KEY>`; nhiều task ⇒ tách commit; user xác nhận không có task ⇒ commit không key và nói rõ. Không đoán/bịa key.
- KHÔNG ghi tên model/công cụ AI, `Co-Authored-By` AI, "Generated with ..." trong commit, PR hay comment code (ghi đè attribution mặc định của công cụ).

```text
feat(response): add PageResponse for paginated APIs

- Avoid exposing Spring Page structure to clients

Refs: CN-123
```

### Comment trong code

- Chỉ comment ngắn (1 dòng, tối đa 2–3) ở logic chính/không hiển nhiên; nói *tại sao / quy tắc gì*, tiếng Việt, giữ identifier tiếng Anh. Không comment code tự giải thích, không Javadoc/JSDoc tràn lan.
- KHÔNG code comment-out, comment nhật ký, TODO mơ hồ (cần thì `// TODO(<người/issue>): <việc cụ thể>`), thông tin AI, dữ liệu thật/secret. Sửa code ⇒ sửa/xóa comment liên quan.

### Hỏi trước khi làm

- Thêm/xóa/nâng dependency hoặc đổi version (`pom.xml`, `Dockerfile`, `compose.yaml`).
- Sửa migration Flyway đã commit (phải tạo migration mới).
- Đổi API contract: endpoint, DTO request/response, `ResponseJson`, `ApiCode`, prefix API — kèm danh sách tác động FE/APP.

### Phạm vi

- Chỉ sửa trong phạm vi task; KHÔNG xóa/đổi tên/di chuyển file ngoài phạm vi khi chưa hỏi.
- KHÔNG sửa repo `CareNest_FE`, `CareNest_APP` trừ khi user cho phép rõ trong tin nhắn hiện tại (vd. đồng bộ tri thức theo `update-knowledge.md`).
- KHÔNG tự thêm thư viện/hạ tầng mới khi team chưa chốt (vd. Spring Security/JWT, Redis, message broker).

### Giao tiếp

- Trả lời user tiếng Việt; commit, branch, identifier tiếng Anh. Yêu cầu chưa rõ ⇒ hỏi trước. Báo kết quả đúng sự thật (test fail/skip/chưa chạy phải nói rõ).
