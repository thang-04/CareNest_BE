---
id: BUG-261111-service-default-method-no-transaction
type: bug
modules: [nutrition, ai-assistance]
rules: [AI-02, NUT-08]
status: fixed
date: 2026-11-11
keywords: [default method, interface, Transactional, LazyInitializationException, no session, applyDraft, MenuAiDraftService, overload]
similar_to: []
---

# BUG-261111-service-default-method-no-transaction — default method của interface service chạy ngoài transaction

## Symptom
Thêm overload `applyDraft(user, id, grid)` vào `MenuAiDraftService` và giữ bản 2 tham số dưới dạng `default` method của interface gọi sang bản 3 tham số. 3 test cũ của `MenuAiDraftIntegrationTest` (gọi bản 2 tham số) hỏng; 5 test mới gọi thẳng bản 3 tham số vẫn pass.
```text
LazyInitialization Cannot lazily initialize collection of role 'com.carenest.entity.meals.MealPlanDay.items' with key '31' (no session)
```
Một test khác (`dayFixRejectedWhenCellChanged`) mong `GlobalException` nhưng nhận exception khác cùng nguyên nhân.

## Điều kiện tái hiện
`XxxServiceImpl` có `@Transactional` ở mức class; interface khai `default R m(a, b) { return m(a, b, null); }`; impl chỉ override bản 3 tham số; gọi `service.m(a, b)` qua bean Spring khi bên trong đọc collection lazy.

## Attempts — đã thử (cập nhật mỗi phiên điều tra, để phiên sau không lặp lại)
| # | Cách thử | Kết quả | Vì sao không đúng / bài học |
| --- | --- | --- | --- |
| 1 | Giữ `default applyDraft(user, id)` trong interface để khỏi sửa 14 lời gọi test | 3 test lỗi `LazyInitializationException ... (no session)` | Lời gọi qua default method không mở transaction |
| 2 | Bỏ default method, chỉ còn một phương thức 3 tham số; test và controller truyền `null` khi không có lưới | 40/40 test liên quan pass | Cách đã dùng |

## Root cause
Lời gọi vào `default` method của interface không được bọc transaction của `@Transactional` mức class trên impl, và lời gọi nội bộ từ default method sang bản 3 tham số đi qua `this` (không qua proxy) nên cũng không mở transaction ⇒ `weekRepository.findByWeekStart` trả entity đã detach, đọc `MealPlanDay.items` báo `no session`.

Bằng chứng: cùng thân hàm, gọi thẳng bản 3 tham số thì pass, gọi qua default method thì lỗi; bỏ default method thì hết lỗi (`MenuAiDraftIntegrationTest` 18/18). Chưa đào tới dòng mã Spring quyết định việc không áp advice cho default method; phần cơ chế proxy là suy luận từ hành vi quan sát được.

## Fix
`service/meals/MenuAiDraftService.java`: bỏ default method, giữ `applyDraft(CurrentUser, Long, MenuAiDraftGridRequest)`; controller và test truyền tham số thứ ba (null = dùng nguyên gợi ý).

## Regression test
`MenuAiDraftIntegrationTest.applyReplacesOnlyDraftBlockAndIsIdempotent`, `dayFixReplacesOnlyTargetCell`, `dayFixRejectedWhenCellChanged` (fail ở lần thử 1, pass sau fix).

## Ảnh hưởng
Chỉ trong nhánh đang làm, chưa lên `dev`. Không ảnh hưởng dữ liệu, FE/APP.

## Lesson
Không thêm `default` method có logic gọi sang method khác vào interface service: mọi method public của service phải được impl khai báo để nằm trong transaction. Cần overload thì khai đủ trong impl hoặc dùng một chữ ký duy nhất.
