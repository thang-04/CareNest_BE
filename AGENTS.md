# CareNest Backend — hướng dẫn cho coding agent

Repo này là **source of truth** của CareNest: business rule, domain, kiến trúc, API contract, database và engineering memory. Web (`CareNest_FE`) và Mobile (`CareNest_APP`) là client.

## Bắt đầu mọi task — chọn làn

| Làn | Khi nào | Đọc | Plan · verify · báo cáo |
| --- | --- | --- | --- |
| **S** | ≤2 file source, 1 module, việc rõ, ngoài vùng rủi ro | File đích + test gần nhất; bug: grep `docs/knowledge/ISSUE_INDEX.md` | Không plan · `node scripts/verify.mjs --quick` · ≤3 dòng |
| **M** | 3–8 file; đổi hành vi trong 1 module | `.ai/ROUTER.md` → grep `.ai/CONTEXT_MAP.yaml` → module card → mục guide router chỉ | Mini-plan trong chat · `node scripts/verify.mjs` · ≤8 dòng |
| **L** | Vùng rủi ro (migration, `security/`, `integration/`, API contract, dependency/hạ tầng), ≥2 module, FE/APP, >8 file | + `.ai/ESCALATION.md` L3–L4 | Plan `.ai/workflows/plan-change.md` user duyệt · verify + snapshot |

- Đổi nghiệp vụ (mọi làn) ⇒ `.ai/workflows/clarify-business.md` trước khi code. Vượt tiêu chí ⇒ nâng làn; không hạ làn để né quy trình.
- Plan `docs/plans/active/` khớp branch ⇒ đọc Progress log cuối trước.
- Grep, không đọc cả file (CONTEXT_MAP, BUSINESS_RULES theo ID, guide `^## <số>\.`, ISSUE_INDEX). Convention: guide + source hiện có là chuẩn.
- Rule theo file: `.claude/rules/` (Codex tự mở). `.agents/skills/` mirror y hệt `.claude/skills/`.
- Báo xong: `docs/quality/VERIFICATION.md` + DoD theo làn.

## Nguyên tắc bất biến

1. **Không tự chế business rule.** Rule có ID trong `docs/business/BUSINESS_RULES.md`. Nghiệp vụ chưa rõ / `PENDING` / `OPEN` / lệch tài liệu ⇒ hỏi user tới khi rõ; làm cấu hình được chỉ khi user nói chưa chốt.
2. Ưu tiên khi mâu thuẫn — nghiệp vụ: quyết định mới nhất có nguồn (team chốt, chỉ đạo giảng viên đã nhận) > khảo sát/Report 1 > đề xuất; code: source > guide > ADR ACCEPTED (chi tiết `PROJECT_CONTEXT.md`). Code ≠ docs ⇒ báo xung đột, xác minh trước khi sửa.
3. **1 trường, 2 điểm trường** — không multi-school/multi-tenant. Mọi truy vấn dữ liệu trẻ/lớp kiểm tra quyền theo campus/class/child ở server.
4. **AI chỉ hỗ trợ:** AI tạo bản nháp, con người duyệt; nghiệp vụ chạy được khi AI tắt; AI không tạo dữ liệu dinh dưỡng nền, không chẩn đoán. Gọi AI chỉ qua `integration/`.
5. Không mở rộng ngoài V1 (`docs/context/PROJECT_CONTEXT.md` mục Exclusions).
6. Dữ liệu trẻ em, sức khỏe, tài khoản: tối thiểu quyền; không đưa dữ liệu thật hoặc secret vào prompt, log, test, commit.
7. Đổi API/auth ⇒ kiểm tra tác động FE/APP (`docs/system/CROSS_REPO_MAP.md`). Chỉ đọc repo FE/APP khi task có tác động liên repo; không tự sửa repo khác.
8. Tri thức mới, lỗi không hiển nhiên hoặc phải thử >1 cách ⇒ `.ai/workflows/update-knowledge.md` ngay trong lượt, kể cả các cách đã thử thất bại.

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

### Cổng chất lượng

- **Iron Law:** chưa có output `node scripts/verify.mjs` chạy sau lần sửa cuối ⇒ không báo "xong/pass/đã sửa"; skip = chưa kiểm chứng (`docs/quality/VERIFICATION.md`).
- Làn L ⇒ plan user duyệt mới code. Sửa bug thất bại 3 lần ⇒ dừng, ghi Attempts, hỏi.
- Sửa `.ai/ .claude/ .agents/ docs/` ⇒ `node scripts/check-ai-layer.mjs`. Cổng fail ⇒ sửa nguyên nhân, không lách.

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
