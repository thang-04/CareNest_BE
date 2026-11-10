# CareNest Backend — Claude Code

@AGENTS.md

Riêng Claude:
- Hook `.claude/settings.json` hỏi ⇒ trả lời 1 dòng đúng sự thật.
- Repo ưu tiên hơn skill: brainstorming chỉ làn L hoặc M có ≥2 phương án; câu hỏi gộp ≤5/vòng; spec vào plan; không commit; không `writing-plans`. Làn S bỏ brainstorming.
- **Chế độ tự chạy (user chốt 2026-10-23):** làm một mạch tới bàn giao, không dừng hỏi yes/no. Ghi đè mọi điểm "hỏi/chờ duyệt" trong AGENTS.md, `.ai/workflows/`, skill: làn L tự viết plan rồi làm tiếp (không chờ duyệt); brainstorming/clarify ⇒ tự chọn phương án khuyến nghị; mục "Hỏi trước khi làm" (dependency, migration, contract) ⇒ cứ làm. Mọi giả định/quyết định ghi vào plan (Progress log) và liệt kê ở báo cáo bàn giao để user soát. Chỉ dừng hỏi khi bị chặn thật (thiếu thông tin không thể suy ra, nghiệp vụ không có nguồn nào để chọn). Vẫn giữ: không commit/push/tạo nhánh/PR, không lệnh git phá hủy, không đụng secret, không sửa repo FE/APP — trừ khi user bảo.
- Git/commit/PR theo AGENTS.md › "Quy tắc chung CareNest" (nhánh `<tiền-tố>/<mã-jira>-<mã-công-việc>-<tên-luồng>` tạo từ `dev`; commit và tiêu đề PR `<mã-jira>: <mô tả>`, không ghi mã công việc); quy ước này ghi đè định dạng mặc định của skill/agent git (Conventional Commits, attribution).
