# Patterns & Lessons

Bài học tổng quát, mỗi pattern có nguồn. Pattern lấy từ thiết kế chỉ là 1 dòng trỏ tới nguồn; chi tiết đọc ở nguồn. Pattern rút ra từ incident thật thì ghi đủ: bối cảnh / quy tắc / ví dụ sai / nguồn.

| ID | Quy tắc (1 dòng) | Nguồn |
| --- | --- | --- |
| PAT-STALE-DERIVED | Dữ liệu dẫn xuất đã được người xác nhận (MealCount, FoodQuantityPlan) không bị sửa ngầm khi nguồn đổi | ADR-0005 |
| PAT-SCOPE-IN-QUERY | Service kiểm tra campus/class/child theo assignment; không tin id client gửi | ADR-0003 |
| PAT-AI-DRAFT | Output AI là bản nháp, có rule xác định + người duyệt, luôn có đường không AI | ADR-0008 |
| PAT-IDEMPOTENT-BATCH | Nhập theo lớp/ngày = upsert + unique (trẻ, ngày) | guide 13.1 |
| PAT-AFTER-COMMIT-EVENT | Listener chéo feature chạy sau commit, idempotent | ADR-0004 |

## Format pattern từ incident

```markdown
## PAT-<SLUG> — <tên>
- Bối cảnh / Quy tắc / Ví dụ sai
- Nguồn: BUG-YYMMDD-slug | CASE-...
```
