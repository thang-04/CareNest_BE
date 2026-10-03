# Module cards

**Điểm đọc chính** cho task feature/bug trong 1 module. Đọc card trước; chỉ mở flow/ADR/doc khác khi card trỏ tới hoặc `.ai/ESCALATION.md` yêu cầu.

| Card | Một dòng |
| --- | --- |
| [identity-access](identity-access.md) | Tài khoản, role, permission, kiểm tra access scope |
| [school-structure](school-structure.md) | Trường, campus, năm học, lớp, phân công nhân sự |
| [child](child.md) | Hồ sơ trẻ, ghi danh lớp, liên kết phụ huynh, dị ứng |
| [attendance](attendance.md) | Điểm danh, báo ăn, đơn nghỉ |
| [nutrition](nutrition.md) | Thực phẩm, món, thực đơn, số suất, định lượng, bếp |
| [health](health.md) | Đo sức khỏe định kỳ, trend, diễn giải AI có duyệt |
| [learning-observation](learning-observation.md) | Quan sát, ngữ cảnh hoạt động, đánh giá, summary, hồ sơ phát triển |
| [facility-issue](facility-issue.md) | Báo và theo dõi sự cố CSVC |
| [notification](notification.md) | Gửi thông báo theo event |
| [audit](audit.md) | Nhật ký thay đổi nhạy cảm |
| [ai-assistance](ai-assistance.md) | Port + adapter AI provider, guardrail, data minimization |
| [reporting](reporting.md) | Báo cáo chỉ đọc theo lớp/campus/trường |

## Template card (khi thêm module)

```markdown
# Module — <name>
Feature: `<feature>` (`PACKAGE_STRUCTURE.md`) · Status: thiết kế | đang code | xong
## Mục đích            ## Owns            ## KHÔNG owns
## Rules (ID)          ## Flows           ## Phụ thuộc (dùng / được dùng bởi / event)
## API & bảng          ## PENDING         ## Known pitfalls
## Related issues      ## Đọc thêm khi
```

Agent: khi phát hiện edge case/lỗi dễ lặp lại trong module ⇒ thêm 1 dòng vào **Known pitfalls** (và link incident nếu có).
