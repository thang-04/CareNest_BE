# CareNest Backend — hướng dẫn cho coding agent

Bạn đang làm việc trong repository Backend của CareNest. Đọc `.ai/ROUTER.md` để chọn đúng profile và workflow cho task; không nạp toàn bộ `.ai/` theo mặc định. Bối cảnh hiện được xác nhận nằm ở `.ai/REPO_CONTEXT.md`; các đường dẫn hiện có và dự kiến được phân biệt trong `.ai/CONTEXT_MAP.yaml`.

- **Bắt buộc:** trước khi viết hoặc review code BE, đọc `docs/backend-coding-guide.md` (ít nhất các mục router chỉ tới); trước khi báo xong, chạy "Checklist trước khi hoàn thành" ở cuối guide và báo mục nào không áp dụng hoặc chưa đạt. Không tự đặt convention ngoài guide.
- BE sở hữu business logic, dữ liệu, quyền truy cập và hợp đồng API dùng chung cho FE/APP. Kiểm tra tác động sang client khi thay đổi API hoặc auth.
- Quy tắc xác định phải nằm trong code/nguồn dữ liệu được kiểm chứng; AI/LLM chỉ gợi ý và không tạo dữ liệu dinh dưỡng nền hoặc tự phê duyệt quyết định.
- Xử lý dữ liệu trẻ em, sức khỏe và tài khoản theo nguyên tắc tối thiểu quyền; không đưa dữ liệu thật hoặc secret vào prompt, log, test hay commit.
- Nếu thiếu tài liệu, source hoặc quyết định công nghệ, nêu rõ khoảng trống và xác minh trước khi kết luận. Nếu tài liệu và implementation mâu thuẫn, báo xung đột và tìm intended behavior.
- Chỉ đọc repo FE/APP khi task có tác động liên repo. Không tự động sửa repo khác chỉ vì đang có quyền truy cập.

## Git, commit và comment (bắt buộc)

- Không tự ý commit, push, tạo/merge pull request khi user chưa cho phép rõ trong tin nhắn hiện tại; không commit thẳng `main`.
- Không chạy lệnh git phá hủy (`reset --hard`, `push --force`, `rebase`, `branch -D`, `clean -fd`...) khi chưa hỏi; không `--no-verify`.
- Commit theo Conventional Commits tiếng Anh, subject ≤ 72 ký tự, body ngắn nói lý do; không ghi tên model/công cụ AI hay `Co-Authored-By` của AI.
- Comment code chỉ ngắn gọn ở flow có logic chính; không comment code hiển nhiên, không để code comment-out.
- Hỏi trước khi đổi dependency, contract hoặc thứ ảnh hưởng cả nhóm; không sửa ngoài phạm vi task hoặc repo CareNest khác. Trả lời user bằng tiếng Việt.
- Chi tiết: mục "Quy tắc chung CareNest" trong `CLAUDE.md`.
