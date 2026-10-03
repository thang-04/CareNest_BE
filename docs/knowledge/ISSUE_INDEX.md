# Issue Index

> Giữ file nhỏ để grep rẻ: **1 issue = 1 dòng**, root cause 1 câu; chi tiết, stack, các cách đã thử nằm trong file incident. Không dán log vào đây.

**File đầu tiên agent search khi gặp bug, lỗi môi trường, hoặc case nghiệp vụ lạ.** Mỗi issue 1 dòng. Grep theo: chuỗi lỗi nguyên văn, tên module, từ khóa nghiệp vụ (VN/EN).

## Cách dùng

1. Grep chuỗi lỗi chính (vd. `LazyInitializationException`, `desc` lỗi (vd. `Meal count already confirmed`)) và từ khóa (vd. `đến muộn`, `meal count`).
2. Có match ⇒ đọc incident: xem **Attempts** (cách đã thử thất bại — đừng lặp lại) và **Fix**.
3. Incident cũ chỉ là **manh mối**: kiểm chứng với code hiện tại trước khi kết luận cùng root cause.
4. Không match ⇒ điều tra bình thường (`.ai/workflows/fix-bug.md`).

## Khi nào phải ghi

Ghi khi ít nhất một điều đúng: lỗi không hiển nhiên · phải thử >1 cách · liên quan nhiều module · có thể lặp lại · lỗi môi trường tốn >15 phút. Không ghi lỗi gõ nhầm/hiển nhiên. **Không tạo incident giả hoặc chưa xác nhận root cause** (ghi vào KNOWN_ISSUES thay vì vậy).

Ghi gồm: (1) file `incidents/<ID>-<slug>.md` từ `incidents/_TEMPLATE.md`, (2) 1 dòng bảng dưới, (3) 1 dòng "Known pitfalls" trong module card nếu là bẫy của module, (4) `PATTERNS.md` nếu tổng quát hóa được.

ID: `BUG-xxx` (bug code) · `CASE-xxx` (edge case nghiệp vụ) · `ENV-xxx` (môi trường/build/deploy). Số tăng dần, không tái sử dụng.

## Index

| ID | Module | Triệu chứng (từ khóa + chuỗi lỗi) | Root cause (1 câu) | Status | File |
| --- | --- | --- | --- | --- | --- |
| — | — | Chưa có issue | — | — | — |
