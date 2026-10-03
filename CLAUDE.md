# CareNest Backend — Claude Code

Đọc `AGENTS.md` trước. Dùng `.ai/ROUTER.md` để chọn profile và workflow liên quan; `.ai/` là nguồn quy trình chung cho cả Codex và Claude. Chỉ đọc thêm context khi `.ai/ESCALATION.md` yêu cầu. Các file trong `.claude/skills/` là điểm vào, không phải bản sao của quy trình.

Mọi task viết/review code BE phải tuân theo `docs/backend-coding-guide.md` và chạy Checklist ở cuối guide trước khi báo hoàn thành.

## Quy tắc chung CareNest (bắt buộc)

Khối này giống nhau ở cả ba repo `CareNest_BE`, `CareNest_FE`, `CareNest_APP`; chỉ mục "Hỏi trước khi làm" khác theo repo. Sửa ở một repo thì đồng bộ sang hai repo còn lại.

### Git — commit, push, pull request

- KHÔNG tự ý `git commit`. Chỉ commit khi user yêu cầu rõ trong tin nhắn hiện tại; được phép một lần không có nghĩa là được phép lần sau.
- KHÔNG tự ý `git push`, tạo/merge/đóng pull request khi chưa được user cho phép rõ ràng.
- KHÔNG commit thẳng lên `main`; làm trên branch `feature/<mo-ta>`, `fix/<mo-ta>`, `chore/<mo-ta>`.
- KHÔNG chạy lệnh git phá hủy khi chưa hỏi: `reset --hard`, `push --force`, `rebase`, `branch -D`, `clean -fd`, `checkout -- .`, `restore .`, `stash drop`. KHÔNG dùng `--no-verify` hoặc bỏ qua hook.

### Commit message

- Conventional Commits, tiếng Anh: `<type>(<scope>): <subject>`; `type` thuộc `feat|fix|refactor|test|docs|chore|build|ci`.
- Subject tối đa 72 ký tự, thể mệnh lệnh, không dấu chấm cuối. Body tùy chọn, tối đa ~5 gạch đầu dòng nói lý do/tác động; không liệt kê từng file, không kể quá trình làm.
- Một commit = một thay đổi logic. Ví dụ: `feat(response): add PageResponse for paginated APIs`.
- KHÔNG ghi tên model/công cụ AI, `Co-Authored-By` của AI, "Generated with ..." hay link công cụ AI trong commit message, mô tả PR hoặc comment code. Rule này ghi đè attribution mặc định của công cụ.

### Comment trong code

- Chỉ comment ngắn gọn (1 dòng, tối đa 2–3 dòng) ở flow có logic chính hoặc không hiển nhiên; nói *tại sao / quy tắc gì*, không kể lại code làm gì.
- Không comment code tự giải thích (getter/setter, DTO, mapping, gọi hàm đơn giản); không viết Javadoc/JSDoc tràn lan.
- Viết tiếng Việt, giữ identifier/thuật ngữ tiếng Anh.
- KHÔNG để code bị comment-out, comment kiểu nhật ký ("sửa ngày..., thêm bởi..."), TODO mơ hồ (cần thì `// TODO(<người/issue>): <việc cụ thể>`), thông tin AI, dữ liệu thật hoặc secret. Sửa code thì sửa/xóa comment liên quan.

### Hỏi trước khi làm

- Thêm/xóa/nâng dependency hoặc đổi version (`pom.xml`, `Dockerfile`, `compose.yaml`).
- Sửa migration Flyway đã commit (phải tạo migration mới).
- Đổi API contract: endpoint, DTO request/response, `ResponseJson`, `ApiCode`, prefix API — kèm danh sách tác động FE/APP.

### Phạm vi

- Chỉ sửa trong phạm vi task; KHÔNG xóa/đổi tên/di chuyển file ngoài phạm vi khi chưa hỏi.
- KHÔNG sửa repo `CareNest_FE`, `CareNest_APP`.
- KHÔNG tự thêm thư viện/hạ tầng mới khi team chưa chốt (vd. Spring Security/JWT, Redis, message broker).

### Giao tiếp

- Trả lời user bằng tiếng Việt; commit message, tên branch, identifier bằng tiếng Anh.
- Yêu cầu chưa rõ hoặc có nhiều cách hiểu: hỏi trước khi làm.
- Báo kết quả đúng sự thật: test fail, bị skip hoặc chưa chạy phải nói rõ.
