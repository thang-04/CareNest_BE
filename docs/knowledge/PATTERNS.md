# Patterns & Lessons

Bài học **đã tổng quát hóa** từ incident hoặc quyết định thiết kế. Mỗi pattern có nguồn. Pattern từ thiết kế (chưa có incident) đánh dấu `nguồn: thiết kế`.

## P-STALE-DERIVED — Dữ liệu dẫn xuất bị cũ
- Bối cảnh: MealCount, FoodQuantityPlan dẫn xuất từ attendance.
- Quy tắc: dữ liệu dẫn xuất đã được con người xác nhận không bị sửa ngầm khi nguồn đổi; lưu lịch sử, đánh dấu thay đổi, yêu cầu xác nhận lại.
- Nguồn: thiết kế (ADR-0005).

## P-SCOPE-IN-QUERY — Lọc phạm vi ở tầng query
- Quy tắc: role/annotation không đủ; service kiểm tra campus/class/child đối chiếu assignment, không tin id client gửi.
- Nguồn: thiết kế (ADR-0003).

## P-AI-DRAFT — AI chỉ tạo bản nháp
- Quy tắc: output AI là bản nháp ở feature nghiệp vụ, validate + rule xác định, người có quyền duyệt; luôn có đường không AI.
- Nguồn: thiết kế (ADR-0008).

## P-IDEMPOTENT-BATCH — Nhập hàng loạt idempotent
- Quy tắc: nhập theo lớp/ngày upsert + unique (trẻ, ngày) để lưu lặp không tạo trùng (guide 13.1).
- Nguồn: thiết kế.

## P-AFTER-COMMIT-EVENT — Event sau commit
- Quy tắc: listener phản ứng chéo feature chạy sau commit, idempotent.
- Nguồn: thiết kế (ADR-0004).

## Format thêm mới

```markdown
## P-<SLUG> — <tên>
- Bối cảnh / Quy tắc / Ví dụ sai
- Nguồn: BUG-xxx, CASE-xxx hoặc ADR
```
